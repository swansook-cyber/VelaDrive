package net.velalab.veladrive.core.navigation

import android.content.Context
import com.stadiamaps.ferrostar.core.FerrostarCore
import com.stadiamaps.ferrostar.core.NavigationState
import com.stadiamaps.ferrostar.core.http.OkHttpClientProvider.Companion.toOkHttpClientProvider
import com.stadiamaps.ferrostar.core.location.AndroidLocationProvider
import com.stadiamaps.ferrostar.core.location.NavigationLocationProvider
import com.stadiamaps.ferrostar.core.location.SimulatedLocationProvider
import com.stadiamaps.ferrostar.core.service.FerrostarForegroundServiceManager
import com.stadiamaps.ferrostar.composeui.notification.DefaultForegroundNotificationBuilder
import com.stadiamaps.ferrostar.core.withJsonOptions
import java.security.KeyStore
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.flow.StateFlow
import net.velalab.veladrive.core.destination.Destination
import net.velalab.veladrive.core.location.LocationSnapshot
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import org.conscrypt.Conscrypt
import uniffi.ferrostar.CourseFiltering
import uniffi.ferrostar.CourseOverGround
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.NavigationControllerConfig
import uniffi.ferrostar.RouteDeviationTracking
import uniffi.ferrostar.Speed
import uniffi.ferrostar.UserLocation
import uniffi.ferrostar.Waypoint
import uniffi.ferrostar.WaypointAdvanceMode
import uniffi.ferrostar.WaypointKind
import uniffi.ferrostar.WellKnownRouteProvider
import uniffi.ferrostar.stepAdvanceDistanceEntryAndExit
import uniffi.ferrostar.stepAdvanceDistanceToEndOfStep

class VelaFerrostarController(
    context: Context,
    valhallaBaseUrl: String,
    routeOptions: RouteOptions = RouteOptions()
) {
    private val simulatedLocationProvider = SimulatedLocationProvider(warpFactor = 8u)

    private val navigationLocationProvider =
        NavigationLocationProvider(
            liveProviding = AndroidLocationProvider(context.applicationContext),
            simulatedProvider = simulatedLocationProvider
        )

    private val foregroundServiceManager =
        FerrostarForegroundServiceManager(
            context.applicationContext,
            DefaultForegroundNotificationBuilder(context.applicationContext)
        )

    private val httpClient =
        compatibleHttpClient()
            .toOkHttpClientProvider()

    private val routeProvider =
        WellKnownRouteProvider
            .Valhalla(valhallaBaseUrl.trimEnd('/') + "/route", "auto")
            .withJsonOptions(
                mapOf(
                    "units" to "kilometers",
                    "language" to "en-US",
                    "turn_lanes" to true,
                    "alternates" to MAX_ALTERNATE_ROUTES,
                    "costing_options" to
                        mapOf(
                            "auto" to routeOptions.valhallaAutoOptions()
                        )
                )
            )

    private val core =
        FerrostarCore(
            wellKnownRouteProvider = routeProvider,
            httpClient = httpClient,
            locationProvider = navigationLocationProvider,
            foregroundServiceManager = foregroundServiceManager,
            navigationControllerConfig =
                NavigationControllerConfig(
                    WaypointAdvanceMode.WaypointWithinRange(100.0),
                    stepAdvanceDistanceEntryAndExit(30u, 5u, 32u),
                    stepAdvanceDistanceToEndOfStep(30u, 32u),
                    RouteDeviationTracking.StaticThreshold(15u, 50.0),
                    CourseFiltering.SNAP_TO_ROUTE
                )
        )

    val state: StateFlow<NavigationState>
        get() = core.state

    val isSimulating: StateFlow<Boolean>
        get() = navigationLocationProvider.isSimulating

    fun shutdown() {
        stopNavigation()
    }

    fun stopNavigation() {
        navigationLocationProvider.disableSimulation()
        core.stopNavigation()
    }

    suspend fun startLiveNavigation(
        origin: LocationSnapshot,
        destination: Destination,
        selectedRouteIndex: Int = 0
    ) {
        navigationLocationProvider.disableSimulation()
        val route = fetchRoute(origin, destination, selectedRouteIndex)
        core.startNavigation(route)
    }

    suspend fun startSimulation(
        origin: LocationSnapshot,
        destination: Destination,
        selectedRouteIndex: Int = 0
    ) {
        val route = fetchRoute(origin, destination, selectedRouteIndex)
        navigationLocationProvider.enableSimulationOn(route)
        core.startNavigation(route)
    }

    private fun compatibleHttpClient(): OkHttpClient {
        val provider = Conscrypt.newProvider()
        val trustManagerFactory =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(null as KeyStore?)
        val trustManager =
            trustManagerFactory.trustManagers
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

    private suspend fun fetchRoute(
        origin: LocationSnapshot,
        destination: Destination,
        selectedRouteIndex: Int
    ): uniffi.ferrostar.Route {
        val initialLocation =
            UserLocation(
                GeographicCoordinate(origin.latitude, origin.longitude),
                origin.accuracyMeters
                    ?.toDouble()
                    ?.takeIf { it.isFinite() && it >= 0.0 }
                    ?: DEFAULT_UNKNOWN_ACCURACY_METERS,
                origin.bearingDegrees?.let {
                    CourseOverGround(it.toUInt().toUShort(), null)
                },
                Instant.now(),
                origin.speedMetersPerSecond?.let {
                    Speed(it.toDouble(), null)
                }
            )

        val routes =
            core.getRoutes(
                initialLocation,
                listOf(
                    Waypoint(
                        coordinate =
                            GeographicCoordinate(
                                destination.latitude,
                                destination.longitude
                            ),
                        kind = WaypointKind.BREAK
                    )
                )
            )

        check(routes.isNotEmpty()) { "Ferrostar did not return a route" }
        check(selectedRouteIndex in routes.indices) {
            "เส้นทางทางเลือกที่เลือกไม่มีแล้ว กรุณาคำนวณเส้นทางใหม่"
        }
        return routes[selectedRouteIndex]
    }

    private companion object {
        const val DEFAULT_UNKNOWN_ACCURACY_METERS = 50.0
        const val MAX_ALTERNATE_ROUTES = 2
    }
}
