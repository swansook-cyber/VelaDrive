package net.velalab.veladrive.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.velalab.veladrive.core.destination.Destination
import net.velalab.veladrive.core.location.LocationSnapshot
import net.velalab.veladrive.core.navigation.RoutePreview

@Composable
fun NavigationShellScreen(
    destination: Destination,
    currentLocation: LocationSnapshot?,
    locationPermissionGranted: Boolean,
    locationError: String?,
    routePreview: RoutePreview?,
    isLoadingRoute: Boolean,
    routeError: String?,
    onRequestLocationPermission: () -> Unit,
    onCalculateRoute: () -> Unit,
    onOpenGoogleSearch: () -> Unit
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text("Vela Drive", style = MaterialTheme.typography.headlineLarge)
                Text("Navigation Shell", style = MaterialTheme.typography.titleMedium)

                Spacer(Modifier.height(24.dp))
                Text("ปลายทาง", style = MaterialTheme.typography.labelLarge)
                Text("${destination.latitude}, ${destination.longitude}")

                Spacer(Modifier.height(20.dp))
                Text("ตำแหน่งปัจจุบัน", style = MaterialTheme.typography.labelLarge)

                when {
                    !locationPermissionGranted -> {
                        Text("ต้องอนุญาตตำแหน่งก่อนเริ่มนำทาง")
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onRequestLocationPermission,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("อนุญาต GPS")
                        }
                    }

                    currentLocation != null -> {
                        Text("${currentLocation.latitude}, ${currentLocation.longitude}")
                        currentLocation.accuracyMeters?.let {
                            Text("ความแม่นยำประมาณ ${it.toInt()} เมตร")
                        }
                    }

                    else -> Text("กำลังรอสัญญาณ GPS…")
                }

                locationError?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it)
                }

                Spacer(Modifier.height(24.dp))
                OutlinedButton(
                    onClick = onOpenGoogleSearch,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("เปลี่ยนปลายทาง")
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = onCalculateRoute,
                    enabled = locationPermissionGranted && currentLocation != null && !isLoadingRoute,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (routePreview == null) "คำนวณเส้นทาง" else "คำนวณใหม่")
                }

                if (isLoadingRoute) {
                    Spacer(Modifier.height(16.dp))
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text("กำลังคำนวณเส้นทาง…")
                }

                routeError?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it)
                }

                if (routePreview != null && currentLocation != null) {
                    Spacer(Modifier.height(20.dp))
                    val minutes = (routePreview.durationSeconds / 60.0).toInt()
                    Text(
                        "ระยะทาง %.1f กม. • ประมาณ %d นาที".format(
                            routePreview.distanceKilometers,
                            minutes
                        ),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    RoutePreviewMap(
                        route = routePreview,
                        currentLocation = currentLocation,
                        destination = destination
                    )
                }
            }
        }
    }
}
