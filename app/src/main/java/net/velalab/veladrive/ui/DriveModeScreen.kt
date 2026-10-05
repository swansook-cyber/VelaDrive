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
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.velalab.veladrive.core.destination.Destination
import net.velalab.veladrive.core.location.LocationSnapshot
import net.velalab.veladrive.core.navigation.RoutePreview
import net.velalab.veladrive.core.navigation.VelaGuidanceSnapshot
import kotlin.math.roundToInt

@Composable
fun DriveModeScreen(
    destination: Destination,
    currentLocation: LocationSnapshot,
    routePreview: RoutePreview,
    guidance: VelaGuidanceSnapshot,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onStopNavigation: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        RoutePreviewMap(
            route = routePreview,
            currentLocation = currentLocation,
            destination = destination,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                tonalElevation = 8.dp,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    guidance.distanceToNextManeuverMeters?.let {
                        Text(
                            text = formatDistance(it),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    Text(
                        text = guidance.currentInstruction ?: "ตรงต่อไป",
                        style = MaterialTheme.typography.headlineMedium
                    )

                    guidance.currentRoadName?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            guidance.junctionInstruction?.let {
                Surface(
                    tonalElevation = 10.dp,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            guidance.nextInstruction?.let {
                Surface(
                    tonalElevation = 5.dp,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text("จากนั้น", style = MaterialTheme.typography.labelLarge)
                        Text(it, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }

            if (guidance.lanes.isNotEmpty()) {
                Surface(
                    tonalElevation = 6.dp,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("เลือกเลน", style = MaterialTheme.typography.labelLarge)
                        Text(
                            guidance.lanes.joinToString("   ") { lane ->
                                if (lane.isActive) "[${lane.symbols}]" else lane.symbols
                            },
                            style = MaterialTheme.typography.headlineMedium
                        )

                        val preferred = guidance.lanes.withIndex()
                            .filter { it.value.isActive }
                            .map { it.index + 1 }

                        if (preferred.isNotEmpty()) {
                            Text(
                                "แนะนำเลน ${preferred.joinToString(", ")} จากซ้าย",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            if (guidance.isRerouting) {
                Surface(
                    tonalElevation = 8.dp,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "กำลังคำนวณเส้นทางใหม่…",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        Surface(
            tonalElevation = 10.dp,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val speedKmh =
                        currentLocation.speedMetersPerSecond
                            ?.times(3.6f)
                            ?.roundToInt()
                            ?: 0
                    Text(
                        "${speedKmh} กม./ชม.",
                        style = MaterialTheme.typography.headlineSmall
                    )

                    val minutes = (routePreview.durationSeconds / 60.0).roundToInt()
                    Text(
                        "%.1f กม. • ประมาณ %d นาที".format(
                            routePreview.distanceKilometers,
                            minutes
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                OutlinedButton(onClick = onToggleMute) {
                    Text(if (isMuted) "เปิดเสียง" else "ปิดเสียง")
                }

                Spacer(Modifier.width(8.dp))

                Button(onClick = onStopNavigation) {
                    Text("จบ")
                }
            }
        }
    }
}

private fun formatDistance(meters: Double): String {
    return if (meters >= 1000.0) {
        "%.1f กม.".format(meters / 1000.0)
    } else {
        "${meters.roundToInt()} ม."
    }
}
