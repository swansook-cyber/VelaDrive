package net.velalab.veladrive.core.safety

import net.velalab.veladrive.core.location.LocationSnapshot
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object SafetyAlertEngine {
    fun nearestRelevant(
        location: LocationSnapshot,
        alerts: List<SafetyAlert>
    ): ActiveSafetyAlert? {
        if (!location.hasValidCoordinates()) return null

        return alerts.asSequence()
            .filter(::hasValidCoordinates)
            .map { alert ->
                ActiveSafetyAlert(
                    alert = alert,
                    distanceMeters = distanceMeters(
                        location.latitude,
                        location.longitude,
                        alert.latitude,
                        alert.longitude
                    )
                )
            }
            .filter { it.distanceMeters <= it.alert.warningDistanceMeters }
            .filter { candidate ->
                isDirectionCompatible(
                    vehicleBearingDegrees = location.bearingDegrees?.toDouble(),
                    alertDirectionDegrees = candidate.alert.directionDegrees
                )
            }
            .minByOrNull { it.distanceMeters }
    }

    internal fun isDirectionCompatible(
        vehicleBearingDegrees: Double?,
        alertDirectionDegrees: Double?
    ): Boolean {
        if (alertDirectionDegrees == null || vehicleBearingDegrees == null) return true
        if (!vehicleBearingDegrees.isFinite() || !alertDirectionDegrees.isFinite()) return true

        val difference =
            abs(normalizeDegrees(vehicleBearingDegrees) - normalizeDegrees(alertDirectionDegrees))
        val shortest = minOf(difference, 360.0 - difference)
        return shortest <= MAX_DIRECTION_DIFFERENCE_DEGREES
    }

    internal fun distanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val lat1Rad = lat1.toRadians()
        val lat2Rad = lat2.toRadians()
        val deltaLat = (lat2 - lat1).toRadians()
        val deltaLon = (lon2 - lon1).toRadians()

        val a =
            sin(deltaLat / 2.0) * sin(deltaLat / 2.0) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(deltaLon / 2.0) * sin(deltaLon / 2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return EARTH_RADIUS_METERS * c
    }

    private fun hasValidCoordinates(alert: SafetyAlert): Boolean =
        alert.latitude.isFinite() &&
            alert.longitude.isFinite() &&
            alert.latitude in -90.0..90.0 &&
            alert.longitude in -180.0..180.0

    private fun normalizeDegrees(value: Double): Double =
        ((value % 360.0) + 360.0) % 360.0

    private fun Double.toRadians(): Double = this * PI / 180.0

    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val MAX_DIRECTION_DIFFERENCE_DEGREES = 55.0
}
