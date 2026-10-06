package net.velalab.veladrive.core.location

import android.os.SystemClock
import kotlin.math.max

data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val bearingDegrees: Float?,
    val speedMetersPerSecond: Float?,
    val timestampMillis: Long,
    val provider: String? = null,
    val elapsedRealtimeNanos: Long? = null
) {
    fun ageMillis(
        nowElapsedRealtimeNanos: Long = SystemClock.elapsedRealtimeNanos(),
        nowWallClockMillis: Long = System.currentTimeMillis()
    ): Long {
        val elapsed = elapsedRealtimeNanos
        return if (elapsed != null && elapsed > 0L && nowElapsedRealtimeNanos >= elapsed) {
            (nowElapsedRealtimeNanos - elapsed) / 1_000_000L
        } else {
            max(0L, nowWallClockMillis - timestampMillis)
        }
    }

    fun isReadyForRouting(): Boolean =
        accuracyMeters == null || accuracyMeters <= MAX_ROUTING_ACCURACY_METERS

    fun diagnosticsText(): String {
        val ageSeconds = ageMillis() / 1000L
        val accuracy = accuracyMeters?.let { "${it.toInt()}m" } ?: "?"
        val source = provider?.ifBlank { null } ?: "unknown"
        return "$source • ±$accuracy • ${ageSeconds}s"
    }

    companion object {
        const val MAX_ROUTING_ACCURACY_METERS = 250f
    }
}
