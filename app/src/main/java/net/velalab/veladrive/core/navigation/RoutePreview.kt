package net.velalab.veladrive.core.navigation

data class RoutePoint(
    val latitude: Double,
    val longitude: Double
)

enum class LaneDirection(val bit: Int, val symbol: String) {
    THROUGH(2, "↑"),
    SHARP_LEFT(4, "↙"),
    LEFT(8, "←"),
    SLIGHT_LEFT(16, "↖"),
    SLIGHT_RIGHT(32, "↗"),
    RIGHT(64, "→"),
    SHARP_RIGHT(128, "↘"),
    REVERSE(256, "↩"),
    MERGE_LEFT(512, "↖"),
    MERGE_RIGHT(1024, "↗");

    companion object {
        fun fromMask(mask: Int): List<LaneDirection> =
            entries.filter { mask and it.bit != 0 }
    }
}

data class VelaLane(
    val directionsMask: Int,
    val validMask: Int?,
    val activeMask: Int?
) {
    val directions: List<LaneDirection>
        get() = LaneDirection.fromMask(directionsMask)

    val isValid: Boolean
        get() = (validMask ?: 0) != 0

    val isActive: Boolean
        get() = (activeMask ?: 0) != 0

    val displaySymbol: String
        get() = directions.joinToString("") { it.symbol }.ifBlank { "•" }
}

data class VelaRouteManeuver(
    val instruction: String,
    val verbalAlert: String?,
    val lengthKilometers: Double,
    val timeSeconds: Double,
    val beginShapeIndex: Int?,
    val endShapeIndex: Int?,
    val lanes: List<VelaLane>
) {
    val preferredLaneIndexes: List<Int>
        get() {
            val active = lanes.mapIndexedNotNull { index, lane -> index.takeIf { lane.isActive } }
            if (active.isNotEmpty()) return active
            return lanes.mapIndexedNotNull { index, lane -> index.takeIf { lane.isValid } }
        }
}

data class RoutePreview(
    val points: List<RoutePoint>,
    val distanceKilometers: Double,
    val durationSeconds: Double,
    val maneuvers: List<VelaRouteManeuver> = emptyList()
)
