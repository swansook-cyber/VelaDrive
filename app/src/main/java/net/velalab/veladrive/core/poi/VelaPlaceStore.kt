package net.velalab.veladrive.core.poi

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class StoredPlace(
    val id: String? = null,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val alternateNames: List<String> = emptyList(),
    val category: PoiCategory? = null,
    val phone: String? = null,
    val province: String? = null,
    val district: String? = null,
    val source: PoiSource = PoiSource.LEGACY_UNKNOWN,
    val sourceReference: String? = null,
    val sourceUrl: String? = null,
    val sourceTags: Map<String, String> = emptyMap(),
    val verified: Boolean = false,
    val updatedAt: String? = null,
    val datasetVersion: String? = null,
    val saved: Boolean = false,
    val lastUsedAt: Long = System.currentTimeMillis()
) {
    fun toPoiSearchResult(): PoiSearchResult =
        PoiSearchResult(
            id = id ?: "local:$latitude,$longitude",
            name = name,
            latitude = latitude,
            longitude = longitude,
            address = address,
            distanceText = null,
            alternateNames = alternateNames,
            category = category,
            phone = phone,
            province = province,
            district = district,
            source = source,
            sourceReference = sourceReference,
            sourceUrl = sourceUrl,
            sourceTags = sourceTags,
            verified = verified,
            updatedAt = updatedAt,
            datasetVersion = datasetVersion
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
                id = poi.id,
                name = poi.name,
                latitude = poi.latitude,
                longitude = poi.longitude,
                address = poi.address,
                alternateNames = poi.alternateNames,
                category = poi.category,
                phone = poi.phone,
                province = poi.province,
                district = poi.district,
                source = poi.source,
                sourceReference = poi.sourceReference,
                sourceUrl = poi.sourceUrl,
                sourceTags = poi.sourceTags,
                verified = poi.verified,
                updatedAt = poi.updatedAt,
                datasetVersion = poi.datasetVersion,
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
                id = poi.id,
                name = poi.name,
                latitude = poi.latitude,
                longitude = poi.longitude,
                address = poi.address,
                alternateNames = poi.alternateNames,
                category = poi.category,
                phone = poi.phone,
                province = poi.province,
                district = poi.district,
                source = poi.source,
                sourceReference = poi.sourceReference,
                sourceUrl = poi.sourceUrl,
                sourceTags = poi.sourceTags,
                verified = poi.verified,
                updatedAt = poi.updatedAt,
                datasetVersion = poi.datasetVersion,
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
        val needle = normalizePoiText(keyword)
        if (needle.isBlank()) return emptyList()

        return savedPlaces()
            .filter {
                (
                    sequenceOf(it.name) +
                        it.alternateNames.asSequence() +
                        listOfNotNull(it.address, it.province, it.district).asSequence()
                ).any { field -> normalizePoiText(field).contains(needle) }
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
                            id = item.optString("id").takeIf { it.isNotBlank() },
                            name = item.getString("name"),
                            latitude = item.getDouble("latitude"),
                            longitude = item.getDouble("longitude"),
                            address = item.optString("address").takeIf { it.isNotBlank() },
                            alternateNames = item.optStringList("alternateNames"),
                            category = item.optEnum<PoiCategory>("category"),
                            phone = item.optString("phone").takeIf { it.isNotBlank() },
                            province = item.optString("province").takeIf { it.isNotBlank() },
                            district = item.optString("district").takeIf { it.isNotBlank() },
                            source = item.optEnum<PoiSource>("source") ?: PoiSource.LEGACY_UNKNOWN,
                            sourceReference =
                                item.optString("sourceReference").takeIf { it.isNotBlank() },
                            sourceUrl = item.optString("sourceUrl").takeIf { it.isNotBlank() },
                            sourceTags = item.optStringMap("sourceTags"),
                            verified = item.optBoolean("verified", false),
                            updatedAt = item.optString("updatedAt").takeIf { it.isNotBlank() },
                            datasetVersion =
                                item.optString("datasetVersion").takeIf { it.isNotBlank() },
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
                    .put("id", place.id ?: "")
                    .put("name", place.name)
                    .put("latitude", place.latitude)
                    .put("longitude", place.longitude)
                    .put("address", place.address ?: "")
                    .put("alternateNames", JSONArray(place.alternateNames))
                    .put("category", place.category?.name ?: "")
                    .put("phone", place.phone ?: "")
                    .put("province", place.province ?: "")
                    .put("district", place.district ?: "")
                    .put("source", place.source.name)
                    .put("sourceReference", place.sourceReference ?: "")
                    .put("sourceUrl", place.sourceUrl ?: "")
                    .put("sourceTags", JSONObject(place.sourceTags))
                    .put("verified", place.verified)
                    .put("updatedAt", place.updatedAt ?: "")
                    .put("datasetVersion", place.datasetVersion ?: "")
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

    private fun JSONObject.optStringList(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                array.optString(index).trim().takeIf { it.isNotEmpty() }?.let(::add)
            }
        }
    }

    private fun JSONObject.optStringMap(key: String): Map<String, String> {
        val value = optJSONObject(key) ?: return emptyMap()
        return buildMap {
            value.keys().forEach { mapKey ->
                value.optString(mapKey).takeIf { it.isNotBlank() }?.let { put(mapKey, it) }
            }
        }
    }

    private inline fun <reified T : Enum<T>> JSONObject.optEnum(key: String): T? =
        optString(key).takeIf { it.isNotBlank() }?.let { value ->
            enumValues<T>().firstOrNull { it.name == value }
        }

    private companion object {
        const val PREFS_NAME = "vela_places"
        const val KEY_RECENT = "recent"
        const val KEY_SAVED = "saved"
        const val MAX_RECENT = 12
        const val MAX_SAVED = 100
    }
}
