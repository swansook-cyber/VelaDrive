package net.velalab.veladrive.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle

class AndroidLocationController(context: Context) {
    private val locationManager =
        context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private var listener: LocationListener? = null

    @SuppressLint("MissingPermission")
    fun start(onLocation: (LocationSnapshot) -> Unit, onProviderUnavailable: () -> Unit) {
        stop()

        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                LocationManager.NETWORK_PROVIDER
            else -> {
                onProviderUnavailable()
                return
            }
        }

        val newListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                onLocation(location.toSnapshot())
            }

            override fun onProviderDisabled(provider: String) {
                onProviderUnavailable()
            }

            @Deprecated("Deprecated in Android")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }

        listener = newListener

        locationManager.getLastKnownLocation(provider)?.let {
            onLocation(it.toSnapshot())
        }

        locationManager.requestLocationUpdates(
            provider,
            1_000L,
            2f,
            newListener
        )
    }

    fun stop() {
        listener?.let(locationManager::removeUpdates)
        listener = null
    }

    private fun Location.toSnapshot() = LocationSnapshot(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        bearingDegrees = if (hasBearing()) bearing else null,
        speedMetersPerSecond = if (hasSpeed()) speed else null,
        timestampMillis = time
    )
}
