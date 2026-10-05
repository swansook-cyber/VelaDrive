package net.velalab.veladrive.core.navigation

import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.velalab.veladrive.core.destination.Destination
import net.velalab.veladrive.core.location.LocationSnapshot
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ValhallaRouteClient(
    baseUrl: String,
    private val httpClient: OkHttpClient = defaultClient()
) {
    private val routeUrl = baseUrl.trimEnd('/') + "/route"
    private val json = Json { ignoreUnknownKeys = true }

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
                  "language": "th-TH",
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

        return RoutePreview(
            points = points,
            distanceKilometers = distanceKm,
            durationSeconds = durationSeconds
        )
    }

    companion object {
        private fun defaultClient() =
            OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(20))
                .callTimeout(Duration.ofSeconds(30))
                .build()
    }
}
