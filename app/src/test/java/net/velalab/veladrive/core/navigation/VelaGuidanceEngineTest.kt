package net.velalab.veladrive.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VelaGuidanceEngineTest {
    @Test
    fun warnsAboutCloselySpacedNextManeuver() {
        assertEquals(
            "หลังจากคำสั่งนี้ ให้เตรียม เลี้ยวซ้าย",
            VelaGuidanceEngine.buildPreparationInstruction(
                currentInstruction = "เลี้ยวขวา",
                nextInstruction = "เลี้ยวซ้าย",
                nextStepDistanceMeters = 90.0
            )
        )
    }

    @Test
    fun straightInstructionGetsEarlyPreparation() {
        assertEquals(
            "ตรงต่อไปก่อน แล้วเตรียม เลี้ยวขวา",
            VelaGuidanceEngine.buildPreparationInstruction(
                currentInstruction = "ตรงต่อไป",
                nextInstruction = "เลี้ยวขวา",
                nextStepDistanceMeters = 500.0
            )
        )
    }

    @Test
    fun distantNextManeuverDoesNotClutterGuidance() {
        assertNull(
            VelaGuidanceEngine.buildPreparationInstruction(
                currentInstruction = "เลี้ยวซ้าย",
                nextInstruction = "เลี้ยวขวา",
                nextStepDistanceMeters = 1200.0
            )
        )
    }

    @Test
    fun mapsLaneDirectionToReadableArrow() {
        assertEquals("←", VelaGuidanceEngine.directionSymbol("left"))
        assertEquals("↑", VelaGuidanceEngine.directionSymbol("straight"))
        assertEquals("↗", VelaGuidanceEngine.directionSymbol("slight right"))
    }
}
