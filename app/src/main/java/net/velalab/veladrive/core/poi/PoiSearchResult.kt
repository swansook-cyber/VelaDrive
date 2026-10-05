package net.velalab.veladrive.core.poi

data class PoiSearchResult(
    val id: String?,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String?,
    val distanceText: String?
)
