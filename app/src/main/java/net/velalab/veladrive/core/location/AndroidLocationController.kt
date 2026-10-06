package net.velalab.veladrive.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import kotlin.math.max

class AndroidLocationController(context: Context) {
    private val locationManager =
        context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private var listener: LocationListener? = null

    @SuppressLint("MissingPermission")
    fun start(onLocation: (LocationSnapshot) -> Unit, onProviderUnavailable: () -> Unit) {
        stop()

        val providers = preferredEnabledProviders()
        if (providers.isEmpty()) {
            onProviderUnavailable()
            return
        }

        val newListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                onLocation(location.toSnapshot())
            }

            override fun onProviderDisabled(provider: String) {
                if (preferredEnabledProviders().isEmpty()) onProviderUnavailable()
            }

            @Deprecated("Deprecated in Android")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }

        listener = newListener

        providers
            .mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            .filter { it.isFreshLastKnownFix() }
            .minByOrNull { it.ageMillis() }
            ?.let { onLocation(it.toSnapshot()) }

        var registered = 0
        providers.forEach { provider ->
            val success = runCatching {
                locationManager.requestLocationUpdates(
                    provider,
                    1_000L,
                    2f,
                    newListener
                )
            }.isSuccess
            if (success) registered += 1
        }

        if (registered == 0) {
            listener = null
            onProviderUnavailable()
        }
    }

    fun stop() {
        listener?.let { runCatching { locationManager.removeUpdates(it) } }
        listener = null
    }

    private fun preferredEnabledProviders(): List<String> {
        val enabled = locationManager.getProviders(true).toSet()
        return buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                LocationManager.FUSED_PROVIDER in enabled
            ) {
                add(LocationManager.FUSED_PROVIDER)
            }
            if (LocationManager.GPS_PROVIDER in enabled) add(LocationManager.GPS_PROVIDER)
            if (LocationManager.NETWORK_PROVIDER in enabled) add(LocationManager.NETWORK_PROVIDER)
            if (LocationManager.PASSIVE_PROVIDER in enabled) add(LocationManager.PASSIVE_PROVIDER)
        }.distinct()
    }

    private fun Location.ageMillis(): Long {
        val elapsed = elapsedRealtimeNanos
        return if (elapsed > 0L && android.os.SystemClock.elapsedRealtimeNanos() >= elapsed) {
            (android.os.SystemClock.elapsedRealtimeNanos() - elapsed) / 1_000_000L
        } else {
            max(0L, System.currentTimeMillis() - time)
        }
    }

    private fun Location.isFreshLastKnownFix(): Boolean {
        val accuracyOk = !hasAccuracy() || accuracy <= MAX_LAST_KNOWN_ACCURACY_METERS
        return ageMillis() <= MAX_LAST_KNOWN_AGE_MILLIS && accuracyOk
    }

    private fun Location.toSnapshot() = LocationSnapshot(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        bearingDegrees = if (hasBearing()) bearing else null,
        speedMetersPerSecond = if (hasSpeed()) speed else null,
        timestampMillis = time,
        provider = provider,
        elapsedRealtimeNanos = elapsedRealtimeNanos.takeIf { it > 0L }
    )

    private companion object {
        const val MAX_LAST_KNOWN_AGE_MILLIS = 5 * 60 * 1000L
        const val MAX_LAST_KNOWN_ACCURACY_METERS = 500f
    }
}
