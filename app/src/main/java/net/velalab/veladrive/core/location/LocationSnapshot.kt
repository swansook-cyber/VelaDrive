package net.velalab.veladrive.core.location

import kotlin.math.max

data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val bearingDegrees: Float?,
    val speedMetersPerSecond: Float?,
    val timestampMillis: Long
) {
    fun isFreshForRouting(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val age = max(0L, nowMillis - timestampMillis)
        val accuracyOk = accuracyMeters == null || accuracyMeters <= MAX_ROUTING_ACCURACY_METERS
        return age <= MAX_ROUTING_AGE_MILLIS && accuracyOk
    }

    companion object {
        const val MAX_ROUTING_AGE_MILLIS = 30 * 1000L
        const val MAX_ROUTING_ACCURACY_METERS = 200f
    }
}
