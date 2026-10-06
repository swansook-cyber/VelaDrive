package net.velalab.veladrive.core.destination

data class Destination(
    val latitude: Double,
    val longitude: Double,
    val label: String? = null,
    val source: DestinationSource
)

enum class DestinationSource {
    SHARED_TEXT,
    GEO_URI,
    RAW_COORDINATES,
    USER_PLACE,
    VELA_POI,
    LONGDO_POI,
    GOOGLE_MAPS_LINK
}
