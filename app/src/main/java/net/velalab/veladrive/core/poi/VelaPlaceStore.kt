package net.velalab.veladrive.core.poi

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class StoredPlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val saved: Boolean = false,
    val lastUsedAt: Long = System.currentTimeMillis()
) {
    fun toPoiSearchResult(): PoiSearchResult =
        PoiSearchResult(
            id = "local:$latitude,$longitude",
            name = name,
            latitude = latitude,
            longitude = longitude,
            address = address,
            distanceText = null
        )
}

class VelaPlaceStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun recentPlaces(): List<StoredPlace> =
        readArray(KEY_RECENT)
            .sortedByDescending { it.lastUsedAt }
            .take(MAX_RECENT)

    fun savedPlaces(): List<StoredPlace> =
        readArray(KEY_SAVED)
            .filter { it.saved }
            .sortedBy { it.name.lowercase() }
            .take(MAX_SAVED)

    fun addRecent(poi: PoiSearchResult) {
        val place =
            StoredPlace(
                name = poi.name,
                latitude = poi.latitude,
                longitude = poi.longitude,
                address = poi.address,
                lastUsedAt = System.currentTimeMillis()
            )
        val updated =
            listOf(place) +
                recentPlaces().filterNot { sameCoordinate(it.latitude, it.longitude, poi.latitude, poi.longitude) }
        writeArray(KEY_RECENT, updated.take(MAX_RECENT))
    }

    fun save(poi: PoiSearchResult) {
        val place =
            StoredPlace(
                name = poi.name,
                latitude = poi.latitude,
                longitude = poi.longitude,
                address = poi.address,
                saved = true,
                lastUsedAt = System.currentTimeMillis()
            )
        val updated =
            listOf(place) +
                savedPlaces().filterNot { sameCoordinate(it.latitude, it.longitude, poi.latitude, poi.longitude) }
        writeArray(KEY_SAVED, updated.take(MAX_SAVED))
    }

    fun removeSaved(latitude: Double, longitude: Double) {
        val updated =
            savedPlaces().filterNot {
                sameCoordinate(it.latitude, it.longitude, latitude, longitude)
            }
        writeArray(KEY_SAVED, updated)
    }

    fun searchSaved(keyword: String): List<PoiSearchResult> {
        val needle = keyword.trim().lowercase()
        if (needle.isBlank()) return emptyList()

        return savedPlaces()
            .filter {
                it.name.lowercase().contains(needle) ||
                    it.address?.lowercase()?.contains(needle) == true
            }
            .map(StoredPlace::toPoiSearchResult)
    }

    private fun readArray(key: String): List<StoredPlace> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        StoredPlace(
                            name = item.getString("name"),
                            latitude = item.getDouble("latitude"),
                            longitude = item.getDouble("longitude"),
                            address = item.optString("address").takeIf { it.isNotBlank() },
                            saved = item.optBoolean("saved", false),
                            lastUsedAt = item.optLong("lastUsedAt", 0L)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeArray(key: String, places: List<StoredPlace>) {
        val array = JSONArray()
        places.forEach { place ->
            array.put(
                JSONObject()
                    .put("name", place.name)
                    .put("latitude", place.latitude)
                    .put("longitude", place.longitude)
                    .put("address", place.address ?: "")
                    .put("saved", place.saved)
                    .put("lastUsedAt", place.lastUsedAt)
            )
        }
        prefs.edit().putString(key, array.toString()).apply()
    }

    private fun sameCoordinate(
        aLat: Double,
        aLon: Double,
        bLat: Double,
        bLon: Double
    ): Boolean =
        kotlin.math.abs(aLat - bLat) < 0.00001 &&
            kotlin.math.abs(aLon - bLon) < 0.00001

    private companion object {
        const val PREFS_NAME = "vela_places"
        const val KEY_RECENT = "recent"
        const val KEY_SAVED = "saved"
        const val MAX_RECENT = 12
        const val MAX_SAVED = 100
    }
}
