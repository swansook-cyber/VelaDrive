package net.velalab.veladrive.core.location

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationSnapshotTest {
    @Test
    fun `poor GPS accuracy does not block routing`() {
        val location =
            LocationSnapshot(
                latitude = 8.0863,
                longitude = 98.9063,
                accuracyMeters = 1500f,
                bearingDegrees = null,
                speedMetersPerSecond = null,
                timestampMillis = 0L
            )

        assertTrue(location.isReadyForRouting())
    }

    @Test
    fun `valid coordinate with unknown accuracy can route`() {
        val location =
            LocationSnapshot(
                latitude = 8.0863,
                longitude = 98.9063,
                accuracyMeters = null,
                bearingDegrees = null,
                speedMetersPerSecond = null,
                timestampMillis = 0L
            )

        assertTrue(location.isReadyForRouting())
    }

    @Test
    fun `invalid coordinates block routing`() {
        val invalidLatitude =
            LocationSnapshot(
                latitude = 95.0,
                longitude = 98.9063,
                accuracyMeters = 5f,
                bearingDegrees = null,
                speedMetersPerSecond = null,
                timestampMillis = 0L
            )
        val invalidLongitude =
            LocationSnapshot(
                latitude = 8.0863,
                longitude = 190.0,
                accuracyMeters = 5f,
                bearingDegrees = null,
                speedMetersPerSecond = null,
                timestampMillis = 0L
            )

        assertFalse(invalidLatitude.isReadyForRouting())
        assertFalse(invalidLongitude.isReadyForRouting())
    }
}
