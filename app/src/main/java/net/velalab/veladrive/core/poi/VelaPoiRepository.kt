package net.velalab.veladrive.core.poi

import android.content.Context
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

interface VelaPoiRepository {
    fun search(
        keyword: String,
        latitude: Double?,
        longitude: Double?,
        limit: Int = 20
    ): Result<List<PoiSearchResult>>
}

class AssetVelaPoiRepository(
    context: Context,
    private val assetName: String = DEFAULT_ASSET_NAME
) : VelaPoiRepository {
    private val appContext = context.applicationContext
    private val dataset by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        runCatching {
            appContext.assets.open(assetName).bufferedReader().use { reader ->
                VelaPoiDatasetParser.parse(reader.readText())
            }
        }
    }

    override fun search(
        keyword: String,
        latitude: Double?,
        longitude: Double?,
        limit: Int
    ): Result<List<PoiSearchResult>> =
        dataset.map { loaded ->
            VelaPoiSearch.search(
                pois = loaded.pois,
                keyword = keyword,
                latitude = latitude,
                longitude = longitude,
                limit = limit
            )
        }

    private companion object {
        const val DEFAULT_ASSET_NAME = "vela_pois.json"
    }
}

internal class InMemoryVelaPoiRepository(
    private val pois: List<PoiSearchResult>
) : VelaPoiRepository {
    override fun search(
        keyword: String,
        latitude: Double?,
        longitude: Double?,
        limit: Int
    ): Result<List<PoiSearchResult>> =
        Result.success(VelaPoiSearch.search(pois, keyword, latitude, longitude, limit))
}

internal data class VelaPoiDataset(
    val schemaVersion: Int,
    val datasetVersion: String,
    val updatedAt: String?,
    val pois: List<PoiSearchResult>
)

internal object VelaPoiDatasetParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): VelaPoiDataset {
        val root = json.parseToJsonElement(raw).jsonObject
        val schemaVersion = root.requiredInt("schemaVersion")
        require(schemaVersion == SUPPORTED_SCHEMA_VERSION) {
            "Unsupported Vela POI schema version: $schemaVersion"
        }
        val datasetVersion = root.requiredText("datasetVersion")
        val datasetUpdatedAt = root.optionalText("updatedAt")
        val pois = root["pois"]?.jsonArray ?: JsonArray(emptyList())

        return VelaPoiDataset(
            schemaVersion = schemaVersion,
            datasetVersion = datasetVersion,
            updatedAt = datasetUpdatedAt,
            pois = pois.map { element ->
                parsePoi(
                    item = element.jsonObject,
                    datasetVersion = datasetVersion,
                    datasetUpdatedAt = datasetUpdatedAt
                )
            }
        )
    }

    private fun parsePoi(
        item: JsonObject,
        datasetVersion: String,
        datasetUpdatedAt: String?
    ): PoiSearchResult {
        val source = enumValueOf<PoiSource>(item.requiredText("source"))
        require(source == PoiSource.VELA_CURATED || source == PoiSource.OPENSTREETMAP) {
            "Vela POI assets may only contain VELA_CURATED or OPENSTREETMAP records"
        }
        val latitude = item.requiredDouble("latitude")
        val longitude = item.requiredDouble("longitude")
        require(latitude.isFinite() && latitude in -90.0..90.0) {
            "Invalid Vela POI latitude"
        }
        require(longitude.isFinite() && longitude in -180.0..180.0) {
            "Invalid Vela POI longitude"
        }

        return PoiSearchResult(
            id = item.requiredText("id"),
            name = item.requiredText("name"),
            latitude = latitude,
            longitude = longitude,
            address = item.optionalText("address"),
            distanceText = null,
            alternateNames = item.optionalTextList("alternateNames"),
            category = enumValueOf<PoiCategory>(item.requiredText("category")),
            phone = item.optionalText("phone"),
            province = item.optionalText("province"),
            district = item.optionalText("district"),
            source = source,
            sourceReference = item.optionalText("sourceReference"),
            sourceUrl = item.optionalText("sourceUrl"),
            sourceTags = item.optionalTextMap("sourceTags"),
            verified = item["verified"]?.jsonPrimitive?.booleanOrNull ?: false,
            updatedAt = item.optionalText("updatedAt") ?: datasetUpdatedAt,
            datasetVersion = item.optionalText("datasetVersion") ?: datasetVersion
        )
    }

    private fun JsonObject.requiredText(key: String): String =
        optionalText(key) ?: error("Missing Vela POI field: $key")

    private fun JsonObject.optionalText(key: String): String? =
        this[key]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.trim()
            ?.takeIf(String::isNotEmpty)

    private fun JsonObject.requiredInt(key: String): Int =
        this[key]?.jsonPrimitive?.intOrNull ?: error("Missing Vela POI field: $key")

    private fun JsonObject.requiredDouble(key: String): Double =
        this[key]?.jsonPrimitive?.doubleOrNull ?: error("Missing Vela POI field: $key")

    private fun JsonObject.optionalTextList(key: String): List<String> =
        (this[key] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
            .orEmpty()

    private fun JsonObject.optionalTextMap(key: String): Map<String, String> =
        (this[key] as? JsonObject)
            ?.mapNotNull { (mapKey, value) ->
                value.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotEmpty)?.let {
                    mapKey to it
                }
            }
            ?.toMap()
            .orEmpty()

    private const val SUPPORTED_SCHEMA_VERSION = 1
}

internal object VelaPoiSearch {
    fun search(
        pois: List<PoiSearchResult>,
        keyword: String,
        latitude: Double?,
        longitude: Double?,
        limit: Int
    ): List<PoiSearchResult> {
        val query = normalizePoiText(keyword)
        if (query.isEmpty() || limit <= 0) return emptyList()

        val hasOrigin =
            latitude != null && longitude != null &&
                latitude.isFinite() && longitude.isFinite() &&
                latitude in -90.0..90.0 && longitude in -180.0..180.0

        return pois.asSequence()
            .mapNotNull { poi ->
                val score = matchScore(poi, query) ?: return@mapNotNull null
                val distance =
                    if (hasOrigin) {
                        distanceMeters(latitude, longitude, poi.latitude, poi.longitude)
                    } else {
                        Double.POSITIVE_INFINITY
                    }
                RankedPoi(poi, score, distance)
            }
            .sortedWith(
                compareBy<RankedPoi> { it.matchScore }
                    .thenByDescending { it.poi.verified }
                    .thenBy { it.distanceMeters }
                    .thenBy { normalizePoiText(it.poi.name) }
                    .thenBy { it.poi.id.orEmpty() }
            )
            .take(limit)
            .map { ranked ->
                if (ranked.distanceMeters.isFinite()) {
                    ranked.poi.copy(distanceText = formatDistance(ranked.distanceMeters))
                } else {
                    ranked.poi
                }
            }
            .toList()
    }

    private fun matchScore(poi: PoiSearchResult, query: String): Int? {
        val name = normalizePoiText(poi.name)
        val aliases = poi.alternateNames.map(::normalizePoiText)
        val metadataTerms =
            listOfNotNull(
                poi.sourceTags["brand"],
                poi.sourceTags["operator"]
            ).map(::normalizePoiText)
        val queryTerms = equivalentSearchTerms(query)

        // Strongest signal: the user typed the actual place/brand name.
        // Brand equivalents (for example 7-Eleven / 7 Eleven / 7-11 / เซเว่น)
        // are expanded here, never through a generic category alias.
        if (queryTerms.any { name == it }) return 0
        if (aliases.any { alias -> queryTerms.any { alias == it } }) return 1
        if (metadataTerms.any { term -> queryTerms.any { term == it } }) return 2
        if (queryTerms.any { name.startsWith(it) }) return 3
        if (aliases.any { alias -> queryTerms.any { alias.startsWith(it) } }) return 4
        if (metadataTerms.any { term -> queryTerms.any { term.startsWith(it) } }) return 5
        if (queryTerms.any { name.contains(it) }) return 6
        if (aliases.any { alias -> queryTerms.any { alias.contains(it) } }) return 7
        if (metadataTerms.any { term -> queryTerms.any { term.contains(it) } }) return 8

        // Generic searches such as "ปั๊ม", "โรงพยาบาล", "มัสยิด", "halal"
        // should resolve through the Vela category vocabulary and then rank by distance.
        val categoryTerms = poi.category?.searchTerms.orEmpty()
        if (categoryTerms.any { it == query }) return 9
        if (categoryTerms.any { it.startsWith(query) || query.startsWith(it) }) return 10
        if (categoryTerms.any { it.contains(query) || query.contains(it) }) return 11

        // Address/area is useful, but should never outrank a place-name or category match.
        val supportingText =
            listOfNotNull(
                poi.address,
                poi.province,
                poi.district
            ).map(::normalizePoiText)
        return if (supportingText.any { it.contains(query) }) 12 else null
    }

    private val PoiCategory.searchTerms: List<String>
        get() =
            (
                listOf(name.replace('_', ' '), displayName) +
                    CATEGORY_ALIASES[this].orEmpty()
            ).map(::normalizePoiText)

    private val CATEGORY_ALIASES: Map<PoiCategory, List<String>> = mapOf(
        PoiCategory.FUEL to listOf("ปั๊ม", "ปั้ม", "น้ำมัน", "fuel", "gas station", "petrol"),
        PoiCategory.HOSPITAL to listOf("รพ", "ร.พ.", "hospital", "โรงพยาบาล"),
        PoiCategory.RESTAURANT to listOf("ร้านอาหาร", "อาหาร", "restaurant", "food"),
        PoiCategory.HALAL_RESTAURANT to listOf("ฮาลาล", "ร้านฮาลาล", "อาหารฮาลาล", "halal"),
        PoiCategory.HOTEL to listOf("ที่พัก", "โรงแรม", "รีสอร์ท", "hotel", "resort"),
        PoiCategory.MARKET to listOf("ตลาด", "market"),
        PoiCategory.SHOPPING to listOf("ห้าง", "ศูนย์การค้า", "shopping", "mall"),
        PoiCategory.POLICE to listOf("ตำรวจ", "สถานีตำรวจ", "police"),
        PoiCategory.AIRPORT to listOf("สนามบิน", "airport"),
        PoiCategory.BUS_TERMINAL to listOf("บขส", "สถานีขนส่ง", "bus terminal"),
        PoiCategory.TOURISM to listOf("ที่เที่ยว", "สถานที่ท่องเที่ยว", "tourism", "attraction"),
        PoiCategory.CONVENIENCE_STORE to listOf("ร้านสะดวกซื้อ", "convenience store"),
        PoiCategory.AUTO_SERVICE to listOf("อู่", "ศูนย์บริการรถ", "บริการรถยนต์", "auto service", "car service"),
        PoiCategory.TIRE_SERVICE to listOf("ยาง", "ร้านยาง", "tire", "tyre"),
        PoiCategory.BANK to listOf("ธนาคาร", "bank"),
        PoiCategory.ATM to listOf("เอทีเอ็ม", "atm"),
        PoiCategory.EV_CHARGER to listOf("ชาร์จรถไฟฟ้า", "ev charger", "charging station"),
        PoiCategory.PIER to listOf("ท่าเรือ", "pier"),
        PoiCategory.FERRY to listOf("เฟอร์รี่", "เรือเฟอร์รี่", "ferry"),
        PoiCategory.MOSQUE to listOf("มัสยิด", "สุเหร่า", "mosque", "masjid"),
        PoiCategory.GOVERNMENT to listOf("ราชการ", "หน่วยงานราชการ", "government")
    )

    private fun equivalentSearchTerms(query: String): List<String> =
        BRAND_EQUIVALENTS.firstOrNull { query in it } ?: listOf(query)

    private val BRAND_EQUIVALENTS = listOf(
        listOf("7-eleven", "7 eleven", "7-11", "711", "เซเว่น")
            .map(::normalizePoiText)
    )

    private fun distanceMeters(
        fromLatitude: Double,
        fromLongitude: Double,
        toLatitude: Double,
        toLongitude: Double
    ): Double {
        val lat1 = Math.toRadians(fromLatitude)
        val lat2 = Math.toRadians(toLatitude)
        val deltaLat = Math.toRadians(toLatitude - fromLatitude)
        val deltaLon = Math.toRadians(toLongitude - fromLongitude)
        val a =
            sin(deltaLat / 2) * sin(deltaLat / 2) +
                cos(lat1) * cos(lat2) * sin(deltaLon / 2) * sin(deltaLon / 2)
        return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun formatDistance(meters: Double): String =
        if (meters < 1_000.0) {
            "${meters.toInt()} ม."
        } else {
            String.format(Locale.ROOT, "%.1f กม.", meters / 1_000.0)
        }

    private data class RankedPoi(
        val poi: PoiSearchResult,
        val matchScore: Int,
        val distanceMeters: Double
    )

    private const val EARTH_RADIUS_METERS = 6_371_000.0
}

internal fun normalizePoiText(value: String): String =
    value.trim()
        .replace(WHITESPACE_REGEX, " ")
        .lowercase(Locale.ROOT)

private val WHITESPACE_REGEX = Regex("\\s+")
