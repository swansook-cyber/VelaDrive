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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.velalab.veladrive.core.destination.Destination
import net.velalab.veladrive.core.location.LocationSnapshot
import net.velalab.veladrive.core.navigation.RouteOptions
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
    routeOptions: RouteOptions,
    guidance: VelaGuidanceSnapshot?,
    isNavigationStarting: Boolean,
    isSimulationStarting: Boolean,
    navigationError: String?,
    simulationError: String?,
    isSimulationMuted: Boolean,
    onRequestLocationPermission: () -> Unit,
    onCalculateRoute: () -> Unit,
    onRouteOptionsChanged: (RouteOptions) -> Unit,
    onStartNavigation: () -> Unit,
    onStartSimulation: () -> Unit,
    onStopNavigation: () -> Unit,
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
                onStopNavigation = onStopNavigation
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
                    isNavigationStarting = isNavigationStarting,
                    isSimulationStarting = isSimulationStarting,
                    navigationError = navigationError,
                    simulationError = simulationError,
                    routeOptions = routeOptions,
                    onCalculateRoute = onCalculateRoute,
                    onRouteOptionsChanged = onRouteOptionsChanged,
                    onStartNavigation = onStartNavigation,
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
    isNavigationStarting: Boolean,
    isSimulationStarting: Boolean,
    navigationError: String?,
    simulationError: String?,
    routeOptions: RouteOptions,
    onCalculateRoute: () -> Unit,
    onRouteOptionsChanged: (RouteOptions) -> Unit,
    onStartNavigation: () -> Unit,
    onStartSimulation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val minutes = (routePreview.durationSeconds / 60.0).roundToInt()
    var showRouteOptions by remember { mutableStateOf(false) }

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
                onClick = onStartNavigation,
                enabled = !isNavigationStarting && !isSimulationStarting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (isNavigationStarting) {
                        "กำลังเริ่มนำทาง…"
                    } else {
                        "เริ่มนำทาง"
                    }
                )
            }

            OutlinedButton(
                onClick = onStartSimulation,
                enabled = !isNavigationStarting && !isSimulationStarting,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(
                    if (isSimulationStarting) {
                        "กำลังเริ่มจำลอง…"
                    } else {
                        "ทดลองจำลองเส้นทาง"
                    }
                )
            }

            OutlinedButton(
                onClick = { showRouteOptions = true },
                enabled = !isNavigationStarting && !isSimulationStarting,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text("ตัวเลือกเส้นทาง • " + routeOptions.summaryLabel())
            }

            OutlinedButton(
                onClick = onCalculateRoute,
                enabled = !isNavigationStarting && !isSimulationStarting,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text("คำนวณเส้นทางใหม่")
            }

            navigationError?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
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

    if (showRouteOptions) {
        RouteOptionsDialog(
            current = routeOptions,
            onDismiss = { showRouteOptions = false },
            onApply = {
                showRouteOptions = false
                onRouteOptionsChanged(it)
            }
        )
    }
}

@Composable
private fun RouteOptionsDialog(
    current: RouteOptions,
    onDismiss: () -> Unit,
    onApply: (RouteOptions) -> Unit
) {
    var draft by remember(current) { mutableStateOf(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ตัวเลือกเส้นทาง") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RouteOptionSwitch(
                    label = "หลีกเลี่ยงถนนลูกรัง",
                    checked = draft.avoidUnpaved,
                    onCheckedChange = { draft = draft.copy(avoidUnpaved = it) }
                )
                RouteOptionSwitch(
                    label = "หลีกเลี่ยงเรือเฟอร์รี่",
                    checked = draft.avoidFerry,
                    onCheckedChange = { draft = draft.copy(avoidFerry = it) }
                )
                RouteOptionSwitch(
                    label = "หลีกเลี่ยงทางด่วน",
                    checked = draft.avoidHighways,
                    onCheckedChange = { draft = draft.copy(avoidHighways = it) }
                )
                RouteOptionSwitch(
                    label = "หลีกเลี่ยงทางเสียเงิน",
                    checked = draft.avoidTolls,
                    onCheckedChange = { draft = draft.copy(avoidTolls = it) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(draft) }) {
                Text("ใช้และคำนวณใหม่")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ยกเลิก")
            }
        }
    )
}

@Composable
private fun RouteOptionSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
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
