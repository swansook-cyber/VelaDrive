package net.velalab.veladrive.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import kotlin.math.max

class AndroidLocationController(context: Context) {
    private val locationManager =
        context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private var listener: LocationListener? = null

    @SuppressLint("MissingPermission")
    fun start(onLocation: (LocationSnapshot) -> Unit, onProviderUnavailable: () -> Unit) {
        stop()

        val providers = buildList {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                add(LocationManager.GPS_PROVIDER)
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                add(LocationManager.NETWORK_PROVIDER)
            }
        }

        if (providers.isEmpty()) {
            onProviderUnavailable()
            return
        }

        val newListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (location.isFreshLiveFix()) {
                    onLocation(location.toSnapshot())
                }
            }

            override fun onProviderDisabled(provider: String) {
                val anyUsable =
                    locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                if (!anyUsable) onProviderUnavailable()
            }

            @Deprecated("Deprecated in Android")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }

        listener = newListener

        providers
            .mapNotNull { provider -> locationManager.getLastKnownLocation(provider) }
            .filter(Location::isFreshLastKnownFix)
            .maxByOrNull { it.time }
            ?.let { onLocation(it.toSnapshot()) }

        providers.forEach { provider ->
            locationManager.requestLocationUpdates(
                provider,
                1_000L,
                2f,
                newListener
            )
        }
    }

    fun stop() {
        listener?.let(locationManager::removeUpdates)
        listener = null
    }

    private fun Location.isFreshLastKnownFix(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val age = max(0L, nowMillis - time)
        val accuracyOk = !hasAccuracy() || accuracy <= MAX_LAST_KNOWN_ACCURACY_METERS
        return age <= MAX_LAST_KNOWN_AGE_MILLIS && accuracyOk
    }

    private fun Location.isFreshLiveFix(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val age = max(0L, nowMillis - time)
        return age <= MAX_LIVE_FIX_AGE_MILLIS
    }

    private fun Location.toSnapshot() = LocationSnapshot(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        bearingDegrees = if (hasBearing()) bearing else null,
        speedMetersPerSecond = if (hasSpeed()) speed else null,
        timestampMillis = time
    )

    private companion object {
        const val MAX_LAST_KNOWN_AGE_MILLIS = 2 * 60 * 1000L
        const val MAX_LIVE_FIX_AGE_MILLIS = 30 * 1000L
        const val MAX_LAST_KNOWN_ACCURACY_METERS = 150f
    }
}
