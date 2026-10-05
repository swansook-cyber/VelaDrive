package net.velalab.veladrive.core.navigation

import java.security.KeyStore
import java.security.SecureRandom
import java.time.Duration
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.velalab.veladrive.core.destination.Destination
import net.velalab.veladrive.core.location.LocationSnapshot
import okhttp3.ConnectionSpec
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.conscrypt.Conscrypt

class ValhallaRouteClient(
    baseUrl: String,
    private val injectedHttpClient: OkHttpClient? = null
) {
    private val routeUrl = baseUrl.trimEnd('/') + "/route"
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient: OkHttpClient by lazy {
        injectedHttpClient ?: defaultClient()
    }

    suspend fun route(
        origin: LocationSnapshot,
        destination: Destination
    ): Result<RoutePreview> = withContext(Dispatchers.IO) {
        runCatching {
            val body = """
                {
                  "locations": [
                    {"lat": ${origin.latitude}, "lon": ${origin.longitude}},
                    {"lat": ${destination.latitude}, "lon": ${destination.longitude}}
                  ],
                  "costing": "auto",
                  "units": "kilometers",
                  "language": "en-US",
                  "turn_lanes": true,
                  "directions_options": {
                    "units": "kilometers"
                  }
                }
            """.trimIndent()

            val request = Request.Builder()
                .url(routeUrl)
                .post(body.toRequestBody("application/json".toMediaType()))
                .header("User-Agent", "VelaDrive/0.1")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body.string()
                check(response.isSuccessful) {
                    "Valhalla HTTP ${response.code}: ${responseBody.take(200)}"
                }
                parseRoute(responseBody)
            }
        }.recoverCatching { error ->
            val chain = generateSequence(error as Throwable?) { it.cause }
                .take(5)
                .joinToString(" -> ") { cause ->
                    "${cause.javaClass.simpleName}: ${cause.message.orEmpty()}"
                }
            error("Route request failed at $routeUrl | $chain")
        }
    }

    internal fun parseRoute(payload: String): RoutePreview {
        val root = json.parseToJsonElement(payload).jsonObject
        val trip = root["trip"]?.jsonObject ?: error("Missing trip")
        val summary = trip["summary"]?.jsonObject ?: error("Missing trip summary")

        val distanceKm = summary["length"]?.jsonPrimitive?.double
            ?: error("Missing route length")
        val durationSeconds = summary["time"]?.jsonPrimitive?.double
            ?: error("Missing route time")

        val legs = trip["legs"]?.jsonArray ?: error("Missing route legs")
        val points = legs.flatMap { legElement ->
            val leg = legElement.jsonObject
            val shape = leg["shape"]?.jsonPrimitive?.content ?: error("Missing route shape")
            Polyline6Decoder.decode(shape)
        }.fold(mutableListOf<RoutePoint>()) { acc, point ->
            if (acc.lastOrNull() != point) acc += point
            acc
        }

        check(points.size >= 2) { "Route shape is empty" }

        val maneuvers = legs.flatMap { legElement ->
            legElement.jsonObject["maneuvers"]?.jsonArray.orEmpty().map { maneuverElement ->
                val maneuver = maneuverElement.jsonObject
                val lanes =
                    maneuver["lanes"]?.jsonArray.orEmpty().map { laneElement ->
                        val lane = laneElement.jsonObject
                        VelaLane(
                            directionsMask = lane["directions"]?.jsonPrimitive?.intOrNull ?: 0,
                            validMask = lane["valid"]?.jsonPrimitive?.intOrNull,
                            activeMask = lane["active"]?.jsonPrimitive?.intOrNull
                        )
                    }

                VelaRouteManeuver(
                    type = maneuver["type"]?.jsonPrimitive?.intOrNull ?: 0,
                    instruction = maneuver["instruction"]?.jsonPrimitive?.content.orEmpty(),
                    verbalAlert =
                        maneuver["verbal_transition_alert_instruction"]
                            ?.jsonPrimitive
                            ?.content,
                    streetNames =
                        maneuver["street_names"]?.jsonArray.orEmpty().mapNotNull {
                            runCatching { it.jsonPrimitive.content }.getOrNull()
                        },
                    lengthKilometers = maneuver["length"]?.jsonPrimitive?.double ?: 0.0,
                    timeSeconds = maneuver["time"]?.jsonPrimitive?.double ?: 0.0,
                    beginShapeIndex = maneuver["begin_shape_index"]?.jsonPrimitive?.intOrNull,
                    endShapeIndex = maneuver["end_shape_index"]?.jsonPrimitive?.intOrNull,
                    lanes = lanes
                )
            }
        }

        return RoutePreview(
            points = points,
            distanceKilometers = distanceKm,
            durationSeconds = durationSeconds,
            maneuvers = maneuvers
        )
    }

    companion object {
        private fun defaultClient(): OkHttpClient {
            val provider = Conscrypt.newProvider()
            val trustManagerFactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm()
            )
            trustManagerFactory.init(null as KeyStore?)
            val trustManager = trustManagerFactory.trustManagers
                .filterIsInstance<X509TrustManager>()
                .single()

            val sslContext = SSLContext.getInstance("TLS", provider)
            sslContext.init(
                null,
                arrayOf<TrustManager>(trustManager),
                SecureRandom()
            )

            return OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustManager)
                .connectionSpecs(
                    listOf(
                        ConnectionSpec.MODERN_TLS,
                        ConnectionSpec.COMPATIBLE_TLS
                    )
                )
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(20))
                .callTimeout(Duration.ofSeconds(30))
                .build()
        }
    }
}
