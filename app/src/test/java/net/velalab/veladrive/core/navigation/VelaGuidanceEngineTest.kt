package net.velalab.veladrive.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun translatesValhallaLeftTurnToThaiWithRoadName() {
        val maneuver =
            VelaRouteManeuver(
                type = 15,
                instruction = "Turn left onto Maharaj Road.",
                verbalAlert = "Turn left.",
                streetNames = listOf("Maharaj Road"),
                lengthKilometers = 0.2,
                timeSeconds = 20.0,
                beginShapeIndex = 1,
                endShapeIndex = 2,
                lanes = emptyList()
            )

        assertEquals(
            "เลี้ยวซ้าย เข้า Maharaj Road",
            VelaGuidanceEngine.thaiInstruction(maneuver)
        )
    }

    @Test
    fun activeLaneWinsOverValidLane() {
        val maneuver =
            VelaRouteManeuver(
                type = 15,
                instruction = "Turn left.",
                verbalAlert = null,
                streetNames = emptyList(),
                lengthKilometers = 0.1,
                timeSeconds = 10.0,
                beginShapeIndex = 0,
                endShapeIndex = 1,
                lanes =
                    listOf(
                        VelaLane(directionsMask = 8, validMask = null, activeMask = 8),
                        VelaLane(directionsMask = 10, validMask = 8, activeMask = null)
                    )
            )

        assertEquals(listOf(0), maneuver.preferredLaneIndexes)
        assertTrue(maneuver.lanes[0].isActive)
        assertEquals("←", maneuver.lanes[0].displaySymbol)
        assertEquals("←↑", maneuver.lanes[1].displaySymbol)
    }
}
