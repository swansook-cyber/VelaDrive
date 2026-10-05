package net.velalab.veladrive.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveCameraPolicyTest {
    @Test
    fun zoomsInNearManeuver() {
        assertEquals(
            17.5,
            DriveCameraPolicy.zoom(
                speedMetersPerSecond = 20f,
                distanceToNextManeuverMeters = 90.0
            ),
            0.0
        )
    }

    @Test
    fun zoomsOutAtHighSpeedWhenTurnIsFar() {
        assertEquals(
            14.6,
            DriveCameraPolicy.zoom(
                speedMetersPerSecond = 30f,
                distanceToNextManeuverMeters = 1500.0
            ),
            0.0
        )
    }

    @Test
    fun doesNotRotateMapWhileNearlyStationary() {
        assertFalse(DriveCameraPolicy.shouldFollowBearing(0.8f))
        assertTrue(DriveCameraPolicy.shouldFollowBearing(3.0f))
    }
}
