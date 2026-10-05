package net.velalab.veladrive.core.navigation

import com.stadiamaps.ferrostar.core.NavigationUiState
import kotlin.math.roundToInt

data class VelaLaneDisplay(
    val directions: List<String>,
    val activeDirection: String?,
    val isActive: Boolean
) {
    val symbols: String
        get() = directions.joinToString("").ifBlank { "•" }
}

data class VelaGuidanceSnapshot(
    val currentInstruction: String?,
    val nextInstruction: String?,
    val junctionInstruction: String?,
    val preparationInstruction: String?,
    val currentRoadName: String?,
    val distanceToNextManeuverMeters: Double?,
    val preparationDistanceMeters: Int,
    val remainingStepCount: Int,
    val lanes: List<VelaLaneDisplay>,
    val isNavigating: Boolean,
    val isRerouting: Boolean
)

object VelaGuidanceEngine {
    fun from(
        uiState: NavigationUiState,
        routePreview: RoutePreview? = null,
        speedMetersPerSecond: Double? = null
    ): VelaGuidanceSnapshot {
        val remainingSteps = uiState.remainingSteps.orEmpty()
        val matched = matchCurrentManeuver(uiState, routePreview)
        val nextMatched = matched?.let { current ->
            routePreview?.maneuvers
                ?.indexOf(current)
                ?.takeIf { it >= 0 }
                ?.let { index -> routePreview.maneuvers.getOrNull(index + 1) }
        }

        val currentInstruction =
            matched?.let(::thaiInstruction)
                ?: uiState.visualInstruction?.primaryContent?.text

        val nextInstruction =
            nextMatched?.let(::thaiInstruction)
                ?: remainingSteps
                    .drop(1)
                    .firstOrNull()
                    ?.visualInstructions
                    ?.firstOrNull()
                    ?.primaryContent
                    ?.text

        val laneSource = matched?.lanes.orEmpty()
        val hasUsableLaneGuidance = laneSource.any { it.isActive || it.isValid }
        val laneDisplays =
            if (hasUsableLaneGuidance) {
                laneSource.map { lane ->
                    VelaLaneDisplay(
                        directions = lane.directions.map { it.symbol },
                        activeDirection = lane.preferredDirection?.symbol,
                        isActive = lane.isActive || (laneSource.none { it.isActive } && lane.isValid)
                    )
                }
            } else {
                emptyList()
            }

        val distanceToNext = uiState.progress?.distanceToNextManeuver
        val nextStepDistance = remainingSteps.drop(1).firstOrNull()?.distance
        val preparationDistance = preparationDistanceMeters(speedMetersPerSecond)

        return VelaGuidanceSnapshot(
            currentInstruction = currentInstruction,
            nextInstruction = nextInstruction,
            junctionInstruction =
                buildJunctionInstruction(
                    currentManeuver = matched,
                    nextManeuver = nextMatched,
                    distanceToCurrentManeuverMeters = distanceToNext,
                    preparationDistanceMeters = preparationDistance.toDouble()
                ),
            preparationInstruction =
                buildPreparationInstruction(
                    currentInstruction = currentInstruction,
                    nextInstruction = nextInstruction,
                    distanceToCurrentManeuverMeters = distanceToNext,
                    distanceAfterCurrentManeuverMeters = nextStepDistance,
                    preparationDistanceMeters = preparationDistance.toDouble()
                ),
            currentRoadName = matched?.primaryStreetName ?: uiState.currentStepRoadName,
            distanceToNextManeuverMeters = distanceToNext,
            preparationDistanceMeters = preparationDistance,
            remainingStepCount = remainingSteps.size,
            lanes = laneDisplays,
            isNavigating = uiState.isNavigating(),
            isRerouting = uiState.isCalculatingNewRoute == true
        )
    }

    internal fun preparationDistanceMeters(speedMetersPerSecond: Double?): Int {
        val speed = speedMetersPerSecond?.coerceAtLeast(0.0) ?: 0.0
        return (120.0 + speed * 18.0)
            .coerceIn(220.0, 700.0)
            .roundToInt()
    }

    internal fun buildJunctionInstruction(
        currentManeuver: VelaRouteManeuver?,
        nextManeuver: VelaRouteManeuver?,
        distanceToCurrentManeuverMeters: Double?,
        preparationDistanceMeters: Double
    ): String? {
        if (currentManeuver == null || nextManeuver == null) return null
        if (currentManeuver.type !in setOf(7, 8, 22)) return null
        if (!isTurnLike(nextManeuver.type)) return null

        val distance = distanceToCurrentManeuverMeters ?: return null
        if (distance > preparationDistanceMeters) return null

        val next = thaiInstruction(nextManeuver)
        return if (distance <= 180.0) {
            "แยกนี้ตรงไป — แยกถัดไป $next"
        } else {
            "ยังไม่เลี้ยว — เตรียม $next ที่แยกถัดไป"
        }
    }

    internal fun thaiInstruction(maneuver: VelaRouteManeuver): String {
        val road = maneuver.primaryStreetName
        fun withRoad(prefix: String): String =
            if (road.isNullOrBlank()) prefix else "$prefix เข้า $road"

        return when (maneuver.type) {
            1, 2, 3 -> if (road.isNullOrBlank()) "ออกเดินทาง" else "ออกเดินทางบน $road"
            4, 5, 6 -> "ถึงจุดหมาย"
            7, 8, 22 -> if (road.isNullOrBlank()) "ตรงต่อไป" else "ตรงต่อไปบน $road"
            9 -> withRoad("เบี่ยงขวาเล็กน้อย")
            10 -> withRoad("เลี้ยวขวา")
            11 -> withRoad("เลี้ยวขวาหักศอก")
            12 -> "กลับรถทางขวา"
            13 -> "กลับรถทางซ้าย"
            14 -> withRoad("เลี้ยวซ้ายหักศอก")
            15 -> withRoad("เลี้ยวซ้าย")
            16 -> withRoad("เบี่ยงซ้ายเล็กน้อย")
            17 -> "ขึ้นทางลาดตรงไป"
            18, 20 -> withRoad("ออกทางขวา")
            19, 21 -> withRoad("ออกทางซ้าย")
            23 -> if (road.isNullOrBlank()) "ชิดขวา" else "ชิดขวาไปทาง $road"
            24 -> if (road.isNullOrBlank()) "ชิดซ้าย" else "ชิดซ้ายไปทาง $road"
            25 -> if (road.isNullOrBlank()) "รวมเข้าเส้นทางหลัก" else "รวมเข้า $road"
            26 -> "เข้าสู่วงเวียน"
            27 -> if (road.isNullOrBlank()) "ออกจากวงเวียน" else "ออกจากวงเวียนเข้า $road"
            37 -> "รวมทางด้านขวา"
            38 -> "รวมทางด้านซ้าย"
            else -> maneuver.instruction.ifBlank { "ตรงต่อไป" }
        }
    }

    internal fun matchCurrentManeuver(
        uiState: NavigationUiState,
        routePreview: RoutePreview?
    ): VelaRouteManeuver? {
        val maneuvers = routePreview?.maneuvers.orEmpty()
        if (maneuvers.isEmpty()) return null

        val currentText = uiState.visualInstruction?.primaryContent?.text
            ?.trim()
            ?.lowercase()
            .orEmpty()

        if (currentText.isNotBlank()) {
            maneuvers.firstOrNull { maneuver ->
                val raw = maneuver.instruction.trim().lowercase()
                raw.isNotBlank() &&
                    (raw == currentText || raw.contains(currentText) || currentText.contains(raw))
            }?.let { return it }
        }

        val remainingCount = uiState.remainingSteps?.size ?: return null
        val index = (maneuvers.size - remainingCount).coerceIn(0, maneuvers.lastIndex)
        return maneuvers.getOrNull(index)
    }

    fun buildPreparationInstruction(
        currentInstruction: String?,
        nextInstruction: String?,
        distanceToCurrentManeuverMeters: Double?,
        distanceAfterCurrentManeuverMeters: Double?,
        preparationDistanceMeters: Double = 700.0
    ): String? {
        if (nextInstruction.isNullOrBlank()) return null

        val currentDistance = distanceToCurrentManeuverMeters
        val followingDistance = distanceAfterCurrentManeuverMeters

        if (currentDistance != null && currentDistance <= 180.0) {
            return when {
                followingDistance != null && followingDistance <= 120.0 ->
                    "ทำคำสั่งนี้ แล้วเตรียม $nextInstruction ทันที"
                followingDistance != null && followingDistance <= 300.0 ->
                    "ทำคำสั่งนี้ก่อน จากนั้นเตรียม $nextInstruction"
                else ->
                    "ทำคำสั่งนี้ก่อน จากนั้น $nextInstruction"
            }
        }

        if (isStraightLike(currentInstruction)) {
            return when {
                currentDistance != null && currentDistance <= preparationDistanceMeters ->
                    "ตรงต่อไปก่อน แล้วเตรียม $nextInstruction"
                else -> null
            }
        }

        return if (followingDistance != null && followingDistance <= 150.0) {
            "หลังจากคำสั่งถัดไป ให้เตรียม $nextInstruction ต่อทันที"
        } else {
            null
        }
    }

    private fun isTurnLike(type: Int): Boolean =
        type in setOf(
            9, 10, 11, 12, 13, 14, 15, 16,
            18, 19, 20, 21, 23, 24, 26, 27, 37, 38
        )

    private fun isStraightLike(instruction: String?): Boolean {
        val value = instruction?.lowercase().orEmpty()
        return value.contains("straight") ||
            value.contains("continue") ||
            value.contains("ตรง") ||
            value.contains("ต่อไป")
    }
}
