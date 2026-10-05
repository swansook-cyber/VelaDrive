package net.velalab.veladrive.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import net.velalab.veladrive.core.navigation.VelaGuidanceSnapshot

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
    onOpenGoogleSearch: () -> Unit
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.Top
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

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = onStartSimulation,
                        enabled = !isSimulationStarting && guidance?.isNavigating != true,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isSimulationStarting) "กำลังเริ่มจำลอง…" else "ทดลองนำทางจำลอง")
                    }
                }

                if (isSimulationStarting) {
                    Spacer(Modifier.height(12.dp))
                    CircularProgressIndicator()
                }

                simulationError?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it)
                }

                guidance?.takeIf { it.isNavigating }?.let { g ->
                    Spacer(Modifier.height(20.dp))
                    Text("Vela Guidance", style = MaterialTheme.typography.titleLarge)

                    g.currentInstruction?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.headlineSmall)
                    }

                    g.currentRoadName?.let {
                        Text("ถนนปัจจุบัน: $it")
                    }

                    g.junctionInstruction?.let {
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            tonalElevation = 4.dp,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }

                    g.preparationInstruction?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    g.nextInstruction?.let {
                        Spacer(Modifier.height(8.dp))
                        Text("ถัดไป: $it", style = MaterialTheme.typography.titleMedium)
                    }

                    g.distanceToNextManeuverMeters?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "อีกประมาณ ${it.toInt()} ม. • เตือนล่วงหน้า ${g.preparationDistanceMeters} ม.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (g.lanes.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("เลน", style = MaterialTheme.typography.labelLarge)
                        Text(
                            g.lanes.joinToString("   ") { lane ->
                                if (lane.isActive) "[${lane.symbols}]" else lane.symbols
                            },
                            style = MaterialTheme.typography.headlineSmall
                        )

                        val preferred = g.lanes.withIndex()
                            .filter { it.value.isActive }
                            .map { it.index + 1 }

                        if (preferred.isNotEmpty()) {
                            Text("แนะนำเลน ${preferred.joinToString(", ")} จากซ้าย")
                        }
                    }

                    if (g.isRerouting) {
                        Spacer(Modifier.height(8.dp))
                        Text("กำลังคำนวณเส้นทางใหม่…")
                    }

                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onToggleMute,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isSimulationMuted) "เปิดเสียงนำทาง" else "ปิดเสียงนำทาง")
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onStopSimulation,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("หยุดการจำลอง")
                    }
                }
            }
        }
    }
}
