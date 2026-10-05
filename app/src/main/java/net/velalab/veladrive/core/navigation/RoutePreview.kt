package net.velalab.veladrive.core.navigation

data class RoutePoint(
    val latitude: Double,
    val longitude: Double
)

data class RoutePreview(
    val points: List<RoutePoint>,
    val distanceKilometers: Double,
    val durationSeconds: Double
)
