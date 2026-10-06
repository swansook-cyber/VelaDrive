package net.velalab.veladrive.core.safety

import net.velalab.veladrive.core.location.LocationSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyAlertEngineTest {
    @Test
    fun picksNearestAlertInsideWarningDistance() {
        val location = location(lat = 8.0863, lon = 98.9063, bearing = 90f)
        val alerts = listOf(
            alert("far", lat = 8.0863, lon = 98.9120, direction = 90.0, warning = 1000.0),
            alert("near", lat = 8.0863, lon = 98.9090, direction = 90.0, warning = 1000.0)
        )

        val active = SafetyAlertEngine.nearestRelevant(location, alerts)

        assertEquals("near", active?.alert?.id)
        assertTrue((active?.distanceMeters ?: 9999.0) < 400.0)
    }

    @Test
    fun rejectsOppositeDirectionWhenBothDirectionsKnown() {
        val location = location(lat = 8.0863, lon = 98.9063, bearing = 90f)
        val alerts = listOf(
            alert("opposite", lat = 8.0863, lon = 98.9090, direction = 270.0, warning = 1000.0)
        )

        assertNull(SafetyAlertEngine.nearestRelevant(location, alerts))
    }

    @Test
    fun acceptsAlertWithoutDirectionMetadata() {
        val location = location(lat = 8.0863, lon = 98.9063, bearing = 90f)
        val alerts = listOf(
            alert("unknown-direction", lat = 8.0863, lon = 98.9090, direction = null, warning = 1000.0)
        )

        assertEquals(
            "unknown-direction",
            SafetyAlertEngine.nearestRelevant(location, alerts)?.alert?.id
        )
    }

    @Test
    fun ignoresAlertOutsideConfiguredWarningDistance() {
        val location = location(lat = 8.0863, lon = 98.9063, bearing = 90f)
        val alerts = listOf(
            alert("too-far", lat = 8.0863, lon = 98.9163, direction = 90.0, warning = 200.0)
        )

        assertNull(SafetyAlertEngine.nearestRelevant(location, alerts))
    }

    private fun location(
        lat: Double,
        lon: Double,
        bearing: Float?
    ) = LocationSnapshot(
        latitude = lat,
        longitude = lon,
        accuracyMeters = 10f,
        bearingDegrees = bearing,
        speedMetersPerSecond = 10f,
        timestampMillis = 0L,
        provider = "test"
    )

    private fun alert(
        id: String,
        lat: Double,
        lon: Double,
        direction: Double?,
        warning: Double
    ) = SafetyAlert(
        id = id,
        type = SafetyAlertType.SPEED_CAMERA,
        latitude = lat,
        longitude = lon,
        directionDegrees = direction,
        speedLimitKmh = 80,
        warningDistanceMeters = warning,
        source = "TEST"
    )
}
