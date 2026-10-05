package net.velalab.veladrive.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.log2
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import net.velalab.veladrive.core.destination.Destination
import net.velalab.veladrive.core.location.LocationSnapshot
import net.velalab.veladrive.core.navigation.RoutePreview

@Composable
fun RoutePreviewMap(
    route: RoutePreview,
    currentLocation: LocationSnapshot,
    destination: Destination
) {
    val positions = route.points.map {
        Position(longitude = it.longitude, latitude = it.latitude)
    }
    val camera = routeCamera(positions)

    val cameraState = rememberCameraState(camera)

    MaplibreMap(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp),
        baseStyle = BaseStyle.Uri("https://tiles.openfreemap.org/styles/liberty"),
        cameraState = cameraState
    ) {
        val routeSource = rememberGeoJsonSource(
            GeoJsonData.Features(
                Feature(
                    geometry = LineString(positions),
                    properties = null
                )
            )
        )

        LineLayer(
            id = "vela-route",
            source = routeSource,
            color = const(Color(0xFF1565C0)),
            width = const(6.dp)
        )

        val originSource = rememberGeoJsonSource(
            GeoJsonData.Features(
                Feature(
                    geometry = Point(
                        Position(
                            longitude = currentLocation.longitude,
                            latitude = currentLocation.latitude
                        )
                    ),
                    properties = null
                )
            )
        )

        CircleLayer(
            id = "vela-origin",
            source = originSource,
            color = const(Color(0xFF0D47A1)),
            radius = const(7.dp),
            strokeColor = const(Color.White),
            strokeWidth = const(2.dp)
        )

        val destinationSource = rememberGeoJsonSource(
            GeoJsonData.Features(
                Feature(
                    geometry = Point(
                        Position(
                            longitude = destination.longitude,
                            latitude = destination.latitude
                        )
                    ),
                    properties = null
                )
            )
        )

        CircleLayer(
            id = "vela-destination",
            source = destinationSource,
            color = const(Color(0xFFC62828)),
            radius = const(8.dp),
            strokeColor = const(Color.White),
            strokeWidth = const(2.dp)
        )
    }
}

private fun routeCamera(positions: List<Position>): CameraPosition {
    val west = positions.minOf { it.longitude }
    val east = positions.maxOf { it.longitude }
    val south = positions.minOf { it.latitude }
    val north = positions.maxOf { it.latitude }

    val center = Position(
        longitude = (west + east) / 2.0,
        latitude = (south + north) / 2.0
    )

    val span = maxOf(east - west, north - south).coerceAtLeast(0.001)
    val zoom = (8.5 - log2(span)).coerceIn(7.0, 16.0)

    return CameraPosition(target = center, zoom = zoom)
}
