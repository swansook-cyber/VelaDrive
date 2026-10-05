package net.velalab.veladrive.core.navigation

import com.stadiamaps.ferrostar.core.NavigationUiState

data class VelaGuidanceSnapshot(
    val currentInstruction: String?,
    val nextInstruction: String?,
    val currentRoadName: String?,
    val distanceToNextManeuverMeters: Double?,
    val remainingStepCount: Int,
    val isNavigating: Boolean,
    val isRerouting: Boolean
) {
    companion object {
        fun from(uiState: NavigationUiState): VelaGuidanceSnapshot {
            val remainingSteps = uiState.remainingSteps.orEmpty()
            val nextInstruction =
                remainingSteps
                    .drop(1)
                    .firstOrNull()
                    ?.visualInstructions
                    ?.firstOrNull()
                    ?.primaryContent
                    ?.text

            return VelaGuidanceSnapshot(
                currentInstruction = uiState.visualInstruction?.primaryContent?.text,
                nextInstruction = nextInstruction,
                currentRoadName = uiState.currentStepRoadName,
                distanceToNextManeuverMeters =
                    uiState.visualInstruction?.triggerDistanceBeforeManeuver,
                remainingStepCount = remainingSteps.size,
                isNavigating = uiState.isNavigating(),
                isRerouting = uiState.isCalculatingNewRoute == true
            )
        }
    }
}
