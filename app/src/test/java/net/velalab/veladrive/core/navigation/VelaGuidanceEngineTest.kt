package net.velalab.veladrive.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VelaGuidanceEngineTest {
    @Test
    fun warnsAboutImmediateFollowingManeuver() {
        val text = VelaGuidanceEngine.buildPreparationInstruction(
            currentInstruction = "เลี้ยวซ้าย",
            nextInstruction = "เลี้ยวขวา",
            distanceToCurrentManeuverMeters = 80.0,
            distanceAfterCurrentManeuverMeters = 90.0
        )

        assertEquals("ทำคำสั่งนี้ แล้วเตรียม เลี้ยวขวา ทันที", text)
    }

    @Test
    fun saysDoCurrentFirstWhenNextIsNear() {
        val text = VelaGuidanceEngine.buildPreparationInstruction(
            currentInstruction = "เลี้ยวซ้าย",
            nextInstruction = "ชิดขวา",
            distanceToCurrentManeuverMeters = 140.0,
            distanceAfterCurrentManeuverMeters = 250.0
        )

        assertEquals("ทำคำสั่งนี้ก่อน จากนั้นเตรียม ชิดขวา", text)
    }

    @Test
    fun straightSegmentPreparesForUpcomingTurn() {
        val text = VelaGuidanceEngine.buildPreparationInstruction(
            currentInstruction = "ตรงต่อไป",
            nextInstruction = "เลี้ยวซ้าย",
            distanceToCurrentManeuverMeters = 500.0,
            distanceAfterCurrentManeuverMeters = 600.0
        )

        assertEquals("ตรงต่อไปก่อน แล้วเตรียม เลี้ยวซ้าย", text)
    }

    @Test
    fun doesNotAddNoiseWhenManeuversAreFarApart() {
        val text = VelaGuidanceEngine.buildPreparationInstruction(
            currentInstruction = "เลี้ยวขวา",
            nextInstruction = "เลี้ยวซ้าย",
            distanceToCurrentManeuverMeters = 900.0,
            distanceAfterCurrentManeuverMeters = 1200.0
        )

        assertNull(text)
    }

    @Test
    fun mapsLaneDirectionsToCompactSymbols() {
        assertEquals("↑", VelaGuidanceEngine.directionSymbol("straight"))
        assertEquals("←", VelaGuidanceEngine.directionSymbol("left"))
        assertEquals("→", VelaGuidanceEngine.directionSymbol("right"))
        assertEquals("↗", VelaGuidanceEngine.directionSymbol("slight right"))
        assertEquals("↩", VelaGuidanceEngine.directionSymbol("uturn"))
    }
}
