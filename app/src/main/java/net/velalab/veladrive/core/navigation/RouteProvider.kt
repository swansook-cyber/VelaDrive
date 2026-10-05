package net.velalab.veladrive.core.navigation

import net.velalab.veladrive.core.destination.Destination

data class RouteRequest(
    val originLat: Double,
    val originLon: Double,
    val destination: Destination
)

data class RouteSummary(
    val distanceMeters: Double,
    val durationSeconds: Double,
    val provider: String
)

interface RouteProvider {
    suspend fun route(request: RouteRequest): Result<RouteSummary>
}
