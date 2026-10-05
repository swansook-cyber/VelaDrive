package net.velalab.veladrive.core.navigation

import com.stadiamaps.ferrostar.core.NavigationUiState
import uniffi.ferrostar.LaneInfo

data class VelaLaneDisplay(
    val directions: List<String>,
    val activeDirection: String?,
    val isActive: Boolean
) {
    val symbols: String
        get() = directions.joinToString("") { VelaGuidanceEngine.directionSymbol(it) }.ifBlank { "•" }
}

data class VelaGuidanceSnapshot(
    val currentInstruction: String?,
    val nextInstruction: String?,
    val preparationInstruction: String?,
    val currentRoadName: String?,
    val distanceToNextManeuverMeters: Double?,
    val remainingStepCount: Int,
    val lanes: List<VelaLaneDisplay>,
    val isNavigating: Boolean,
    val isRerouting: Boolean
)

object VelaGuidanceEngine {
    fun from(uiState: NavigationUiState): VelaGuidanceSnapshot {
        val remainingSteps = uiState.remainingSteps.orEmpty()
        val nextStep = remainingSteps.drop(1).firstOrNull()
        val nextInstruction =
            nextStep
                ?.visualInstructions
                ?.firstOrNull()
                ?.primaryContent
                ?.text

        val currentVisual = uiState.visualInstruction
        val laneInfo =
            currentVisual?.subContent?.laneInfo
                ?: currentVisual?.primaryContent?.laneInfo
                ?: currentVisual?.secondaryContent?.laneInfo
                ?: emptyList()

        val distanceToNext = currentVisual?.triggerDistanceBeforeManeuver
        val nextStepDistance = nextStep?.distance

        return VelaGuidanceSnapshot(
            currentInstruction = currentVisual?.primaryContent?.text,
            nextInstruction = nextInstruction,
            preparationInstruction =
                buildPreparationInstruction(
                    currentInstruction = currentVisual?.primaryContent?.text,
                    nextInstruction = nextInstruction,
                    nextStepDistanceMeters = nextStepDistance
                ),
            currentRoadName = uiState.currentStepRoadName,
            distanceToNextManeuverMeters = distanceToNext,
            remainingStepCount = remainingSteps.size,
            lanes = laneInfo.map(::toLaneDisplay),
            isNavigating = uiState.isNavigating(),
            isRerouting = uiState.isCalculatingNewRoute == true
        )
    }

    fun buildPreparationInstruction(
        currentInstruction: String?,
        nextInstruction: String?,
        nextStepDistanceMeters: Double?
    ): String? {
        if (nextInstruction.isNullOrBlank()) return null
        val distance = nextStepDistanceMeters ?: return "จากนั้น $nextInstruction"

        return when {
            distance <= 150.0 ->
                "หลังจากคำสั่งนี้ ให้เตรียม $nextInstruction"
            distance <= 350.0 ->
                "อีกไม่นานหลังจากนี้ $nextInstruction"
            isStraightLike(currentInstruction) && distance <= 700.0 ->
                "ตรงต่อไปก่อน แล้วเตรียม $nextInstruction"
            else -> null
        }
    }

    fun directionSymbol(direction: String): String =
        when (direction.trim().lowercase()) {
            "straight", "through" -> "↑"
            "slight left" -> "↖"
            "left" -> "←"
            "sharp left" -> "↙"
            "slight right" -> "↗"
            "right" -> "→"
            "sharp right" -> "↘"
            "uturn", "u-turn" -> "↩"
            else -> "•"
        }

    private fun isStraightLike(instruction: String?): Boolean {
        val value = instruction?.lowercase().orEmpty()
        return value.contains("straight") ||
            value.contains("continue") ||
            value.contains("ตรง") ||
            value.contains("ต่อไป")
    }

    private fun toLaneDisplay(lane: LaneInfo) =
        VelaLaneDisplay(
            directions = lane.directions,
            activeDirection = lane.activeDirection,
            isActive = lane.active
        )
}
