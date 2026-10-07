package net.velalab.veladrive.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class VelaThaiTtsStageTest {
    @Test
    fun voiceStage_changesAsVehicleApproachesManeuver() {
        assertEquals(
            VelaThaiTts.Companion.VoiceStage.FAR,
            VelaThaiTts.voiceStage(1500.0)
        )
        assertEquals(
            VelaThaiTts.Companion.VoiceStage.PREPARE,
            VelaThaiTts.voiceStage(700.0)
        )
        assertEquals(
            VelaThaiTts.Companion.VoiceStage.NEAR,
            VelaThaiTts.voiceStage(250.0)
        )
        assertEquals(
            VelaThaiTts.Companion.VoiceStage.NOW,
            VelaThaiTts.voiceStage(80.0)
        )
    }
}
