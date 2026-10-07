package net.velalab.veladrive.core.poi

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ProvincePoiOption(
    val code: String,
    val nameTh: String,
    val nameEn: String,
    val asset: String,
    val count: Int,
    val bbox: List<Double>
) {
    val centerLatitude: Double?
        get() = if (bbox.size == 4) (bbox[1] + bbox[3]) / 2.0 else null
    val centerLongitude: Double?
        get() = if (bbox.size == 4) (bbox[0] + bbox[2]) / 2.0 else null
}

class AssetProvincePoiRepository(
    context: Context,
    private val indexAssetName: String = "poi_provinces/provinces.json"
) {
    private val appContext = context.applicationContext
    private val optionsResult by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        runCatching {
            appContext.assets.open(indexAssetName).bufferedReader().use { reader ->
                ProvincePoiIndexParser.parse(reader.readText())
            }
        }
    }
    private val datasets = mutableMapOf<String, Result<VelaPoiDataset>>()

    fun provinces(): Result<List<ProvincePoiOption>> = optionsResult

    fun search(
        provinceCode: String,
        keyword: String,
        latitude: Double?,
        longitude: Double?,
        limit: Int = 20
    ): Result<List<PoiSearchResult>> {
        val option = optionsResult.getOrThrow().firstOrNull { it.code == provinceCode }
            ?: return Result.failure(IllegalArgumentException("Unknown province code: $provinceCode"))
        val dataset = synchronized(datasets) {
            datasets.getOrPut(provinceCode) {
                runCatching {
                    appContext.assets.open(option.asset).bufferedReader().use { reader ->
                        VelaPoiDatasetParser.parse(reader.readText())
                    }
                }
            }
        }
        return dataset.map { loaded ->
            VelaPoiSearch.search(loaded.pois, keyword, latitude, longitude, limit)
        }
    }
}

internal object ProvincePoiIndexParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): List<ProvincePoiOption> {
        val root = json.parseToJsonElement(raw).jsonObject
        require(root["schemaVersion"]?.jsonPrimitive?.intOrNull == 1) {
            "Unsupported province POI index schema"
        }
        val provinces = root["provinces"]?.jsonArray ?: JsonArray(emptyList())
        return provinces.map { element ->
            val item = element.jsonObject
            ProvincePoiOption(
                code = item.getValue("code").jsonPrimitive.content,
                nameTh = item.getValue("nameTh").jsonPrimitive.content,
                nameEn = item.getValue("nameEn").jsonPrimitive.content,
                asset = item.getValue("asset").jsonPrimitive.content,
                count = item.getValue("count").jsonPrimitive.intOrNull ?: 0,
                bbox = item["bbox"]?.jsonArray?.mapNotNull { it.jsonPrimitive.doubleOrNull }.orEmpty()
            )
        }
    }
}
