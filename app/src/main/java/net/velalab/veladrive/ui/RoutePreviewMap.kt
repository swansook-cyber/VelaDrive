package net.velalab.veladrive.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.math.log2
import kotlin.time.Duration.Companion.milliseconds
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.expressions.value.IconRotationAlignment
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import net.velalab.veladrive.R
import net.velalab.veladrive.core.destination.Destination
import net.velalab.veladrive.core.location.LocationSnapshot
import net.velalab.veladrive.core.navigation.RoutePreview

@Composable
fun RoutePreviewMap(
    route: RoutePreview,
    alternativeRoutes: List<RoutePreview> = emptyList(),
    selectedRouteIndex: Int = 0,
    currentLocation: LocationSnapshot,
    destination: Destination,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(420.dp),
    driveMode: Boolean = false,
    distanceToNextManeuverMeters: Double? = null
) {
    val positions = route.points.map {
        Position(longitude = it.longitude, latitude = it.latitude)
    }
    val previewCameraPositions =
        if (!driveMode && alternativeRoutes.isNotEmpty()) {
            alternativeRoutes.flatMap { alternate ->
                alternate.points.map {
                    Position(longitude = it.longitude, latitude = it.latitude)
                }
            }.ifEmpty { positions }
        } else {
            positions
        }
    val camera =
        if (driveMode) {
            CameraPosition(
                target = Position(
                    longitude = currentLocation.longitude,
                    latitude = currentLocation.latitude
                ),
                bearing = currentLocation.bearingDegrees?.toDouble() ?: 0.0,
                tilt = DriveCameraPolicy.TILT_DEGREES,
                zoom = DriveCameraPolicy.zoom(
                    currentLocation.speedMetersPerSecond,
                    distanceToNextManeuverMeters
                ),
                padding = PaddingValues(top = 120.dp, bottom = 90.dp)
            )
        } else {
            routeCamera(previewCameraPositions)
        }

    val cameraState = rememberCameraState(camera)

    LaunchedEffect(
        driveMode,
        currentLocation.latitude,
        currentLocation.longitude,
        currentLocation.bearingDegrees,
        currentLocation.speedMetersPerSecond,
        distanceToNextManeuverMeters
    ) {
        if (driveMode) {
            val bearing =
                if (
                    DriveCameraPolicy.shouldFollowBearing(currentLocation.speedMetersPerSecond) &&
                    currentLocation.bearingDegrees != null
                ) {
                    currentLocation.bearingDegrees.toDouble()
                } else {
                    cameraState.position.bearing
                }

            cameraState.animateTo(
                finalPosition = CameraPosition(
                    target = Position(
                        longitude = currentLocation.longitude,
                        latitude = currentLocation.latitude
                    ),
                    bearing = bearing,
                    tilt = DriveCameraPolicy.TILT_DEGREES,
                    zoom = DriveCameraPolicy.zoom(
                        currentLocation.speedMetersPerSecond,
                        distanceToNextManeuverMeters
                    ),
                    padding = PaddingValues(top = 120.dp, bottom = 90.dp)
                ),
                duration = 450.milliseconds
            )
        }
    }

    MaplibreMap(
        modifier = modifier,
        baseStyle = BaseStyle.Uri("https://tiles.openfreemap.org/styles/liberty"),
        cameraState = cameraState
    ) {
        if (!driveMode && alternativeRoutes.size > 1) {
            alternativeRoutes.forEachIndexed { index, alternate ->
                if (index != selectedRouteIndex && alternate.points.size >= 2) {
                    val alternateSource = rememberGeoJsonSource(
                        GeoJsonData.Features(
                            Feature(
                                geometry = LineString(
                                    alternate.points.map {
                                        Position(longitude = it.longitude, latitude = it.latitude)
                                    }
                                ),
                                properties = null
                            )
                        )
                    )
                    LineLayer(
                        id = "vela-route-alt-$index",
                        source = alternateSource,
                        color = const(Color(0xFF777777)),
                        width = const(5.dp)
                    )
                }
            }
        }

        val routeSource = rememberGeoJsonSource(
            GeoJsonData.Features(
                Feature(
                    geometry = LineString(positions),
                    properties = null
                )
            )
        )

        LineLayer(
            id = "vela-route-selected",
            source = routeSource,
            color = const(Color(0xFFE000C7)),
            width = const(7.dp)
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
            id = "vela-origin-halo",
            source = originSource,
            color = const(Color.White.copy(alpha = 0.82f)),
            radius = const(16.dp),
            strokeColor = const(Color(0xFF2F80ED)),
            strokeWidth = const(2.dp)
        )

        val carPainter = painterResource(R.drawable.ic_nav_car_top)
        SymbolLayer(
            id = "vela-car-marker",
            source = originSource,
            iconImage = image(
                carPainter,
                size = DpSize(30.dp, 44.dp)
            ),
            iconAllowOverlap = const(true),
            iconRotationAlignment = const(IconRotationAlignment.Viewport),
            iconRotate = const(
                if (driveMode) {
                    0f
                } else {
                    currentLocation.bearingDegrees ?: 0f
                }
            )
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
