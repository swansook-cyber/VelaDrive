package net.velalab.veladrive.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.velalab.veladrive.core.destination.Destination
import net.velalab.veladrive.core.location.LocationSnapshot

@Composable
fun NavigationShellScreen(
    destination: Destination,
    currentLocation: LocationSnapshot?,
    locationPermissionGranted: Boolean,
    locationError: String?,
    onRequestLocationPermission: () -> Unit,
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
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onOpenGoogleSearch,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("เปลี่ยนปลายทาง")
                    }

                    Button(
                        onClick = {},
                        enabled = locationPermissionGranted && currentLocation != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("พร้อมคำนวณเส้นทาง")
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "ขั้นนี้ยังไม่เริ่มนำทางจริงจนกว่า Valhalla endpoint ของ Vela จะพร้อม",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
