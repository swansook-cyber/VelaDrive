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
    GOOGLE_MAPS_LINK,
    LONGDO_POI
}
