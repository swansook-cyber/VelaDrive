package net.velalab.veladrive.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.velalab.veladrive.core.destination.Destination
import net.velalab.veladrive.core.location.LocationSnapshot
import net.velalab.veladrive.core.navigation.RoutePreview
import net.velalab.veladrive.core.navigation.VelaGuidanceSnapshot
import net.velalab.veladrive.core.safety.ActiveSafetyAlert
import kotlin.math.roundToInt

private val GarminGreen = Color(0xFF3B7F0B)
private val GarminGreenDark = Color(0xFF285A08)
private val GarminBlack = Color(0xF0181818)
private val GarminWhitePanel = Color(0xF7FFFFFF)
private val GarminLanePanel = Color(0xE61B1B1B)
private val GarminWarning = Color(0xFFF9A825)

@Composable
fun DriveModeScreen(
    destination: Destination,
    currentLocation: LocationSnapshot,
    routePreview: RoutePreview,
    guidance: VelaGuidanceSnapshot,
    isMuted: Boolean,
    activeSafetyAlert: ActiveSafetyAlert?,
    onToggleMute: () -> Unit,
    onStopNavigation: () -> Unit
) {
    val speedKmh =
        currentLocation.speedMetersPerSecond
            ?.times(3.6f)
            ?.roundToInt()
            ?: 0
    val remainingMinutes = (routePreview.durationSeconds / 60.0).roundToInt()

    Box(modifier = Modifier.fillMaxSize()) {
        RoutePreviewMap(
            route = routePreview,
            currentLocation = currentLocation,
            destination = destination,
            modifier = Modifier.fillMaxSize(),
            driveMode = true,
            distanceToNextManeuverMeters = guidance.distanceToNextManeuverMeters
        )

        Column(
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            GarminManeuverBanner(guidance)

            guidance.junctionInstruction?.let { junction ->
                Surface(
                    color = GarminGreenDark,
                    contentColor = Color.White,
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = junction,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            activeSafetyAlert?.let { safety ->
                Surface(
                    color = Color(0xFFD84315),
                    contentColor = Color.White,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚠",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = safety.title(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = formatDistance(safety.distanceMeters) +
                                    " • " + safety.alert.source,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            guidance.nextInstruction?.let { next ->
                Surface(
                    color = GarminWhitePanel,
                    contentColor = Color.Black,
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "ถัดไป",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            maneuverArrow(next),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            next,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (guidance.isRerouting) {
                Surface(
                    color = GarminWarning,
                    contentColor = Color.Black,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "กำลังคำนวณเส้นทางใหม่…",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }

        if (guidance.lanes.isNotEmpty()) {
            LanePanel(
                guidance = guidance,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 72.dp)
            )
        }

        GarminBottomBar(
            speedKmh = speedKmh,
            remainingDistanceKm = routePreview.distanceKilometers,
            remainingMinutes = remainingMinutes,
            roadName = guidance.currentRoadName,
            preparationInstruction = guidance.preparationInstruction,
            isMuted = isMuted,
            onToggleMute = onToggleMute,
            onStopNavigation = onStopNavigation,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun GarminManeuverBanner(guidance: VelaGuidanceSnapshot) {
    val instruction = guidance.currentInstruction ?: "ตรงต่อไป"
    val distance = guidance.distanceToNextManeuverMeters

    Surface(
        color = GarminGreen,
        contentColor = Color.White,
        shadowElevation = 10.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.width(118.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = distance?.let(::formatDistance) ?: "—",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black
                )
            }

            Text(
                text = maneuverArrow(instruction),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = instruction,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2
                )
                guidance.currentRoadName?.takeIf { it.isNotBlank() }?.let { road ->
                    Text(
                        text = road,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun LanePanel(
    guidance: VelaGuidanceSnapshot,
    modifier: Modifier = Modifier
) {
    val preferred = guidance.lanes.withIndex()
        .filter { it.value.isActive }
        .map { it.index + 1 }

    Surface(
        color = GarminLanePanel,
        contentColor = Color.White,
        shadowElevation = 8.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = guidance.lanes.joinToString("   ") { lane ->
                    if (lane.isActive) {
                        "▰ " + lane.symbols
                    } else {
                        "▱ " + lane.symbols
                    }
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            if (preferred.isNotEmpty()) {
                Text(
                    "ใช้เลน " + preferred.joinToString(", ") + " จากซ้าย",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun GarminBottomBar(
    speedKmh: Int,
    remainingDistanceKm: Double,
    remainingMinutes: Int,
    roadName: String?,
    preparationInstruction: String?,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onStopNavigation: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = GarminBlack,
        contentColor = Color.White,
        shadowElevation = 12.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            preparationInstruction?.let { preparation ->
                Surface(
                    color = Color(0xFF262626),
                    contentColor = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = preparation,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DriveMetric(
                    value = speedKmh.toString(),
                    label = "กม./ชม.",
                    modifier = Modifier.weight(0.95f)
                )

                Surface(
                    color = Color(0xFF202020),
                    contentColor = Color.White,
                    modifier = Modifier.weight(1.8f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = roadName?.takeIf { it.isNotBlank() } ?: "กำลังนำทาง",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                        Text(
                            text = "%.1f กม. • %d นาที".format(
                                remainingDistanceKm,
                                remainingMinutes
                            ),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                OutlinedButton(
                    onClick = onToggleMute,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text(if (isMuted) "เปิดเสียง" else "เงียบ")
                }

                Button(
                    onClick = onStopNavigation,
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text("จบ", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DriveMetric(
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
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun maneuverArrow(instruction: String): String {
    val text = instruction.lowercase()
    return when {
        "กลับรถ" in text -> "↶"
        "หักศอก" in text && "ขวา" in text -> "↘"
        "หักศอก" in text && "ซ้าย" in text -> "↙"
        "เบี่ยงขวา" in text || "ชิดขวา" in text || "ออกทางขวา" in text -> "↗"
        "เบี่ยงซ้าย" in text || "ชิดซ้าย" in text || "ออกทางซ้าย" in text -> "↖"
        "เลี้ยวขวา" in text || "ขวา" in text -> "↱"
        "เลี้ยวซ้าย" in text || "ซ้าย" in text -> "↰"
        "วงเวียน" in text -> "↻"
        else -> "↑"
    }
}

private fun formatDistance(meters: Double): String {
    return if (meters >= 1000.0) {
        "%.1f กม.".format(meters / 1000.0)
    } else {
        meters.roundToInt().toString() + " ม."
    }
}
