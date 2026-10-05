package net.velalab.veladrive.core.navigation

import android.content.Context
import com.stadiamaps.ferrostar.core.FerrostarCore
import com.stadiamaps.ferrostar.core.NavigationState
import com.stadiamaps.ferrostar.core.http.OkHttpClientProvider.Companion.toOkHttpClientProvider
import com.stadiamaps.ferrostar.core.location.SimulatedLocationProvider
import com.stadiamaps.ferrostar.core.withJsonOptions
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.StateFlow
import net.velalab.veladrive.core.destination.Destination
import net.velalab.veladrive.core.location.LocationSnapshot
import okhttp3.OkHttpClient
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
    valhallaBaseUrl: String
) {
    private val simulatedLocationProvider = SimulatedLocationProvider(warpFactor = 8u)

    private val httpClient =
        OkHttpClient.Builder()
            .callTimeout(Duration.ofSeconds(30))
            .build()
            .toOkHttpClientProvider()

    private val routeProvider =
        WellKnownRouteProvider
            .Valhalla(valhallaBaseUrl.trimEnd('/') + "/route", "auto")
            .withJsonOptions(
                mapOf(
                    "units" to "kilometers",
                    "language" to "en-US",
                    "turn_lanes" to true
                )
            )

    private val core =
        FerrostarCore(
            wellKnownRouteProvider = routeProvider,
            httpClient = httpClient,
            locationProvider = simulatedLocationProvider,
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

    fun shutdown() {
        core.stopNavigation()
    }

    fun stopSimulation() {
        core.stopNavigation()
    }

    suspend fun startSimulation(
        origin: LocationSnapshot,
        destination: Destination
    ) {
        val initialLocation =
            UserLocation(
                GeographicCoordinate(origin.latitude, origin.longitude),
                origin.accuracyMeters?.toDouble() ?: Double.MAX_VALUE,
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

        val route = routes.first()
        simulatedLocationProvider.setRoute(route)
        core.startNavigation(route)
    }
}
