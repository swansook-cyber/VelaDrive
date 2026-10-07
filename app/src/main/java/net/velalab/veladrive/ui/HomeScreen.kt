package net.velalab.veladrive.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.launch
import net.velalab.veladrive.core.location.LocationSnapshot
import net.velalab.veladrive.core.poi.PoiSearchResult
import net.velalab.veladrive.core.poi.ProvincePoiOption
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

@Composable
fun HomeScreen(
    currentLocation: LocationSnapshot?,
    locationPermissionGranted: Boolean,
    isSearchingPois: Boolean,
    poiResults: List<PoiSearchResult>,
    recentPlaces: List<PoiSearchResult>,
    savedPlaces: List<PoiSearchResult>,
    poiError: String?,
    poiDatasetLabel: String?,
    provinceOptions: List<ProvincePoiOption>,
    selectedProvinceCode: String?,
    onProvinceSelected: (String) -> Unit,
    onSearchPoi: (String) -> Unit,
    onSelectPoi: (PoiSearchResult) -> Unit,
    onSavePoi: (PoiSearchResult) -> Unit,
    onRequestLocationPermission: () -> Unit,
    onGoogleSearch: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var provinceMenuExpanded by remember { mutableStateOf(false) }
    val selectedProvince = provinceOptions.firstOrNull { it.code == selectedProvinceCode }
    val provinceReady = provinceOptions.isEmpty() || selectedProvince != null

    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            HomeMap(
                currentLocation = currentLocation,
                modifier = Modifier.fillMaxSize()
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    tonalElevation = 10.dp,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        poiDatasetLabel?.let { label ->
                            Text(
                                label,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        if (provinceOptions.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { provinceMenuExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    selectedProvince?.let { "จังหวัด: ${it.nameTh} (${it.count} จุด)" }
                                        ?: "เลือกจังหวัดก่อนค้นหา"
                                )
                            }
                        }
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("ค้นหาสถานที่ใน Vela Drive") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { onSearchPoi(query) }
                            )
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { onSearchPoi(query) },
                                enabled = query.isNotBlank() && !isSearchingPois && provinceReady,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("ค้นหา")
                            }
                            if (!locationPermissionGranted) {
                                OutlinedButton(onClick = onRequestLocationPermission) {
                                    Text("เปิด GPS")
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HOME_POI_SHORTCUTS.forEach { shortcut ->
                                OutlinedButton(
                                    onClick = {
                                        query = shortcut.query
                                        onSearchPoi(shortcut.query)
                                    },
                                    enabled = !isSearchingPois && provinceReady
                                ) {
                                    Text(shortcut.label)
                                }
                            }
                        }
                    }
                }

                if (
                    isSearchingPois ||
                    poiResults.isNotEmpty() ||
                    poiError != null ||
                    query.isNotBlank()
                ) {
                    Surface(
                        tonalElevation = 10.dp,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 330.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isSearchingPois) {
                                CircularProgressIndicator()
                                Text("กำลังค้นหา Vela POI…")
                            }

                            poiResults.forEach { poi ->
                                Surface(
                                    tonalElevation = 2.dp,
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { onSelectPoi(poi) }
                                        ) {
                                            Text(
                                                poi.name,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Text(
                                                poi.sourceAndCategoryLabel(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            poi.address?.let {
                                                Text(it, style = MaterialTheme.typography.bodySmall)
                                            }
                                            poi.distanceText?.let {
                                                Text("ระยะประมาณ $it")
                                            }
                                        }
                                        OutlinedButton(onClick = { onSavePoi(poi) }) {
                                            Text("บันทึก")
                                        }
                                    }
                                }
                            }

                            poiError?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium)
                            }

                            if (!isSearchingPois && poiResults.isEmpty() && query.isNotBlank()) {
                                OutlinedButton(
                                    onClick = { onGoogleSearch(query) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("หาไม่เจอ? ค้นหาเพิ่มเติมใน Google Maps")
                                }
                            }
                        }
                    }
                }
            }

            if (provinceMenuExpanded && provinceOptions.isNotEmpty()) {
                AlertDialog(
                    onDismissRequest = { provinceMenuExpanded = false },
                    title = { Text("เลือกจังหวัด") },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            provinceOptions.forEach { province ->
                                OutlinedButton(
                                    onClick = {
                                        provinceMenuExpanded = false
                                        onProvinceSelected(province.code)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("${province.nameTh} (${province.count} จุด)")
                                }
                            }
                        }
                    },
                    confirmButton = {
                        OutlinedButton(onClick = { provinceMenuExpanded = false }) {
                            Text("ปิด")
                        }
                    }
                )
            }

            Surface(
                tonalElevation = 10.dp,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
                    .fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (savedPlaces.isNotEmpty()) {
                        Text("★ บันทึก", style = MaterialTheme.typography.labelLarge)
                        savedPlaces.take(3).forEach { poi ->
                            Text(
                                poi.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectPoi(poi) }
                                    .padding(vertical = 4.dp)
                            )
                        }
                    } else if (recentPlaces.isNotEmpty()) {
                        Text("ล่าสุด", style = MaterialTheme.typography.labelLarge)
                        recentPlaces.take(3).forEach { poi ->
                            Text(
                                poi.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectPoi(poi) }
                                    .padding(vertical = 4.dp)
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Vela Drive", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (currentLocation != null) "GPS พร้อม" else "กำลังหาตำแหน่ง…",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Text(
                        currentLocation?.let { location ->
                            "GPS: ${location.diagnosticsText()} • ${"%.5f".format(location.latitude)}, ${"%.5f".format(location.longitude)}"
                        } ?: "GPS: ยังไม่มีพิกัด",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

        }
    }
}

@Composable
private fun HomeMap(
    currentLocation: LocationSnapshot?,
    modifier: Modifier
) {
    val validLocation = currentLocation?.takeIf(LocationSnapshot::hasValidCoordinates)
    val initialPosition = Position(
        longitude = validLocation?.longitude ?: 101.0,
        latitude = validLocation?.latitude ?: 13.0
    )
    val cameraState = rememberCameraState(
        CameraPosition(
            target = initialPosition,
            zoom = if (validLocation == null) 5.5 else HOME_LOCATION_ZOOM
        )
    )
    var hasCenteredOnFirstFix by rememberSaveable {
        mutableStateOf(validLocation != null)
    }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(validLocation?.latitude, validLocation?.longitude) {
        if (!hasCenteredOnFirstFix && validLocation != null) {
            hasCenteredOnFirstFix = true
            cameraState.animateTo(
                finalPosition = validLocation.toHomeCameraPosition(),
                duration = HOME_CAMERA_ANIMATION_MILLIS.milliseconds
            )
        }
    }

    Box(modifier = modifier) {
        MaplibreMap(
            modifier = Modifier.fillMaxSize(),
            baseStyle = BaseStyle.Uri("https://tiles.openfreemap.org/styles/liberty"),
            cameraState = cameraState
        ) {
            validLocation?.let { location ->
                val source = rememberGeoJsonSource(
                    GeoJsonData.Features(
                        Feature(
                            geometry = Point(
                                Position(
                                    longitude = location.longitude,
                                    latitude = location.latitude
                                )
                            ),
                            properties = null
                        )
                    )
                )

                CircleLayer(
                    id = "vela-home-location",
                    source = source,
                    color = const(Color(0xFF1565C0)),
                    radius = const(8.dp),
                    strokeColor = const(Color.White),
                    strokeWidth = const(3.dp)
                )
            }
        }

        validLocation?.let { location ->
            ExtendedFloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        cameraState.animateTo(
                            finalPosition = location.toHomeCameraPosition(),
                            duration = HOME_CAMERA_ANIMATION_MILLIS.milliseconds
                        )
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 84.dp)
            ) {
                Text("ตำแหน่งฉัน")
            }
        }
    }
}

private fun LocationSnapshot.hasValidCoordinates(): Boolean =
    latitude.isFinite() &&
        longitude.isFinite() &&
        latitude in -90.0..90.0 &&
        longitude in -180.0..180.0

private fun LocationSnapshot.toHomeCameraPosition(): CameraPosition =
    CameraPosition(
        target = Position(longitude = longitude, latitude = latitude),
        zoom = HOME_LOCATION_ZOOM
    )

private data class HomePoiShortcut(
    val label: String,
    val query: String
)

private val HOME_POI_SHORTCUTS = listOf(
    HomePoiShortcut(label = "⛽ ปั๊ม", query = "ปั๊ม"),
    HomePoiShortcut(label = "🏥 โรงพยาบาล", query = "โรงพยาบาล"),
    HomePoiShortcut(label = "☪ ฮาลาล", query = "ฮาลาล"),
    HomePoiShortcut(label = "🕌 มัสยิด", query = "มัสยิด"),
    HomePoiShortcut(label = "🏪 7-Eleven", query = "7-eleven"),
    HomePoiShortcut(label = "🏧 ATM", query = "atm")
)

private const val HOME_LOCATION_ZOOM = 15.0
private const val HOME_CAMERA_ANIMATION_MILLIS = 450

private fun PoiSearchResult.sourceAndCategoryLabel(): String =
    listOfNotNull(source.displayName, category?.displayName)
        .distinct()
        .joinToString(" • ")
