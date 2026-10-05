package net.velalab.veladrive.ui

internal object DriveCameraPolicy {
    fun zoom(
        speedMetersPerSecond: Float?,
        distanceToNextManeuverMeters: Double?
    ): Double {
        val distance = distanceToNextManeuverMeters
        if (distance != null) {
            when {
                distance <= 120.0 -> return 17.5
                distance <= 300.0 -> return 17.0
                distance <= 800.0 -> return 16.2
            }
        }

        val speedKmh = (speedMetersPerSecond ?: 0f) * 3.6f
        return when {
            speedKmh >= 90f -> 14.6
            speedKmh >= 60f -> 15.0
            speedKmh >= 35f -> 15.5
            else -> 16.0
        }
    }

    fun shouldFollowBearing(speedMetersPerSecond: Float?): Boolean =
        (speedMetersPerSecond ?: 0f) >= 1.5f

    const val TILT_DEGREES: Double = 45.0
}
