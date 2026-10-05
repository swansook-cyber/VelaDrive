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

private val VelaGuidanceGreen = Color(0xFF2E7D32)
private val VelaGuidanceGreenDark = Color(0xFF1B5E20)
private val VelaPanel = Color(0xEEFFFFFF)
private val VelaDarkPanel = Color(0xEE202124)

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
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier.align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            ManeuverBanner(guidance)

            guidance.junctionInstruction?.let { junction ->
                Surface(
                    color = VelaGuidanceGreenDark,
                    contentColor = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = junction,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }

            guidance.nextInstruction?.let { next ->
                Surface(
                    color = VelaPanel,
                    contentColor = Color.Black,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ถัดไป",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = maneuverArrow(next),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = next,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (guidance.lanes.isNotEmpty()) {
                LanePanel(guidance)
            }

            if (guidance.isRerouting) {
                Surface(
                    color = Color(0xFFF9A825),
                    contentColor = Color.Black,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "กำลังคำนวณเส้นทางใหม่…",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }

        Surface(
            color = VelaDarkPanel,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Column {
                guidance.preparationInstruction?.let { preparation ->
                    Text(
                        text = preparation,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DriveStat(
                        value = speedKmh.toString(),
                        label = "กม./ชม.",
                        modifier = Modifier.weight(0.9f)
                    )
                    DriveStat(
                        value = "%.1f".format(routePreview.distanceKilometers),
                        label = "กม. เหลือ",
                        modifier = Modifier.weight(0.9f)
                    )
                    DriveStat(
                        value = remainingMinutes.toString(),
                        label = "นาที",
                        modifier = Modifier.weight(0.8f)
                    )

                    OutlinedButton(onClick = onToggleMute) {
                        Text(if (isMuted) "เสียง" else "ปิดเสียง")
                    }

                    Button(onClick = onStopNavigation) {
                        Text("จบ")
                    }
                }

                guidance.currentRoadName?.let { road ->
                    Surface(
                        color = Color(0xFF111214),
                        contentColor = Color.White,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = road,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ManeuverBanner(guidance: VelaGuidanceSnapshot) {
    val instruction = guidance.currentInstruction ?: "ตรงต่อไป"

    Surface(
        color = VelaGuidanceGreen,
        contentColor = Color.White,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(92.dp)
            ) {
                guidance.distanceToNextManeuverMeters?.let { distance ->
                    Text(
                        text = formatDistance(distance),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Text(
                    text = maneuverArrow(instruction),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black
                )
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = instruction,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                guidance.currentRoadName?.let { road ->
                    Text(
                        text = road,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun LanePanel(guidance: VelaGuidanceSnapshot) {
    Surface(
        color = VelaDarkPanel,
        contentColor = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
            val preferred = guidance.lanes.withIndex()
                .filter { it.value.isActive }
                .map { it.index + 1 }

            Text(
                text = guidance.lanes.joinToString("    ") { lane ->
                    if (lane.isActive) "▰ \${lane.symbols}" else "▱ \${lane.symbols}"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            if (preferred.isNotEmpty()) {
                Text(
                    "ใช้เลน \${preferred.joinToString(", ")} จากซ้าย",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun DriveStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall
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
        "\${meters.roundToInt()} ม."
    }
}
