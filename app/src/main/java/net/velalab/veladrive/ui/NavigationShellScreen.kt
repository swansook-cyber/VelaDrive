package net.velalab.veladrive.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.velalab.veladrive.core.destination.Destination
import net.velalab.veladrive.core.location.LocationSnapshot
import net.velalab.veladrive.core.navigation.RoutePreview
import net.velalab.veladrive.core.navigation.VelaGuidanceSnapshot
import kotlin.math.roundToInt

private val PreviewGreen = Color(0xFF2E7D32)
private val PreviewDark = Color(0xEE202124)

@Composable
fun NavigationShellScreen(
    destination: Destination,
    currentLocation: LocationSnapshot?,
    locationPermissionGranted: Boolean,
    locationError: String?,
    routePreview: RoutePreview?,
    isLoadingRoute: Boolean,
    routeError: String?,
    guidance: VelaGuidanceSnapshot?,
    isSimulationStarting: Boolean,
    simulationError: String?,
    isSimulationMuted: Boolean,
    onRequestLocationPermission: () -> Unit,
    onCalculateRoute: () -> Unit,
    onStartSimulation: () -> Unit,
    onStopSimulation: () -> Unit,
    onToggleMute: () -> Unit,
    onBackToSearch: () -> Unit
) {
    if (
        guidance?.isNavigating == true &&
        routePreview != null &&
        currentLocation != null
    ) {
        MaterialTheme {
            DriveModeScreen(
                destination = destination,
                currentLocation = currentLocation,
                routePreview = routePreview,
                guidance = guidance,
                isMuted = isSimulationMuted,
                onToggleMute = onToggleMute,
                onStopNavigation = onStopSimulation
            )
        }
        return
    }

    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            if (routePreview != null && currentLocation != null) {
                RoutePreviewMap(
                    route = routePreview,
                    currentLocation = currentLocation,
                    destination = destination,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Surface(
                    color = Color(0xFFF2F2F2),
                    modifier = Modifier.fillMaxSize()
                ) {}
            }

            Surface(
                color = PreviewGreen,
                contentColor = Color.White,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onBackToSearch) {
                        Text("‹ กลับ")
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            destination.label ?: "ปลายทาง",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Vela Drive Route Preview",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (
                routePreview == null ||
                currentLocation == null ||
                !locationPermissionGranted
            ) {
                Surface(
                    tonalElevation = 10.dp,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(20.dp)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when {
                            !locationPermissionGranted -> {
                                Text(
                                    "ต้องเปิด GPS ก่อนคำนวณเส้นทาง",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Button(
                                    onClick = onRequestLocationPermission,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("อนุญาต GPS")
                                }
                            }

                            currentLocation == null -> {
                                CircularProgressIndicator()
                                Text("กำลังรอสัญญาณ GPS…")
                            }

                            isLoadingRoute -> {
                                CircularProgressIndicator()
                                Text("กำลังคำนวณเส้นทาง…")
                            }

                            else -> {
                                Text(
                                    "พร้อมคำนวณเส้นทางไปยังปลายทาง",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Button(
                                    onClick = onCalculateRoute,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("คำนวณเส้นทาง")
                                }
                            }
                        }

                        locationError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                        routeError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            if (routePreview != null && currentLocation != null) {
                RoutePreviewBottomCard(
                    routePreview = routePreview,
                    destination = destination,
                    isSimulationStarting = isSimulationStarting,
                    simulationError = simulationError,
                    onCalculateRoute = onCalculateRoute,
                    onStartSimulation = onStartSimulation,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
private fun RoutePreviewBottomCard(
    routePreview: RoutePreview,
    destination: Destination,
    isSimulationStarting: Boolean,
    simulationError: String?,
    onCalculateRoute: () -> Unit,
    onStartSimulation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val minutes = (routePreview.durationSeconds / 60.0).roundToInt()

    Surface(
        color = PreviewDark,
        contentColor = Color.White,
        shadowElevation = 12.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                destination.label ?: "ปลายทางที่เลือก",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PreviewStat(
                    value = "%.1f".format(routePreview.distanceKilometers),
                    label = "กม.",
                    modifier = Modifier.weight(1f)
                )
                PreviewStat(
                    value = minutes.toString(),
                    label = "นาที",
                    modifier = Modifier.weight(1f)
                )
                PreviewStat(
                    value = routePreview.maneuvers.size.toString(),
                    label = "คำสั่ง",
                    modifier = Modifier.weight(1f)
                )
            }

            Button(
                onClick = onStartSimulation,
                enabled = !isSimulationStarting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (isSimulationStarting) {
                        "กำลังเริ่ม…"
                    } else {
                        "ทดลองนำทาง"
                    }
                )
            }

            OutlinedButton(
                onClick = onCalculateRoute,
                enabled = !isSimulationStarting,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text("คำนวณเส้นทางใหม่")
            }

            simulationError?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun PreviewStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold
        )
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
