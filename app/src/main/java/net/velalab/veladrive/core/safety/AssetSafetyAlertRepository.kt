package net.velalab.veladrive.core.safety

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AssetSafetyAlertRepository(
    context: Context,
    private val assetName: String = DEFAULT_ASSET_NAME
) {
    private val assets = context.applicationContext.assets
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): List<SafetyAlert> =
        runCatching {
            val text = assets.open(assetName).bufferedReader().use { it.readText() }
            json.parseToJsonElement(text).jsonArray.mapNotNull { element ->
                val item = element.jsonObject
                val id = item["id"]?.jsonPrimitive?.content?.trim().orEmpty()
                val type =
                    item["type"]?.jsonPrimitive?.content
                        ?.let { runCatching { SafetyAlertType.valueOf(it) }.getOrNull() }
                val lat = item["lat"]?.jsonPrimitive?.doubleOrNull
                val lon = item["lon"]?.jsonPrimitive?.doubleOrNull
                val source = item["source"]?.jsonPrimitive?.content?.trim().orEmpty()

                if (id.isBlank() || type == null || lat == null || lon == null || source.isBlank()) {
                    return@mapNotNull null
                }

                SafetyAlert(
                    id = id,
                    type = type,
                    latitude = lat,
                    longitude = lon,
                    directionDegrees = item["direction"]?.jsonPrimitive?.doubleOrNull,
                    speedLimitKmh = item["speed_limit"]?.jsonPrimitive?.intOrNull,
                    warningDistanceMeters =
                        item["warning_distance"]?.jsonPrimitive?.doubleOrNull ?: 700.0,
                    source = source,
                    confidence = item["confidence"]?.jsonPrimitive?.doubleOrNull ?: 1.0,
                    label = item["label"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                )
            }
        }.getOrDefault(emptyList())

    companion object {
        const val DEFAULT_ASSET_NAME = "vela_safety_alerts.json"
    }
}
