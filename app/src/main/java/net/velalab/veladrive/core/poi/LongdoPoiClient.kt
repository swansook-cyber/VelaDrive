package net.velalab.veladrive.core.poi

import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class LongdoPoiClient(
    private val apiKey: String,
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun isConfigured(): Boolean = apiKey.isNotBlank()

    fun search(
        keyword: String,
        latitude: Double?,
        longitude: Double?,
        limit: Int = 12
    ): Result<List<PoiSearchResult>> {
        if (apiKey.isBlank()) {
            return Result.failure(
                IllegalStateException("ยังไม่ได้ตั้งค่า LONGDO_MAP_API_KEY")
            )
        }

        val cleanKeyword = keyword.trim()
        if (cleanKeyword.isBlank()) return Result.success(emptyList())

        return runCatching {
            val builder = SEARCH_URL.toHttpUrl().newBuilder()
                .addQueryParameter("keyword", cleanKeyword)
                .addQueryParameter("limit", limit.toString())
                .addQueryParameter("locale", "th")
                .addQueryParameter("key", apiKey)

            if (latitude != null && longitude != null) {
                builder
                    .addQueryParameter("lat", latitude.toString())
                    .addQueryParameter("lon", longitude.toString())
                    .addQueryParameter("span", "50km")
            }

            val request = Request.Builder()
                .url(builder.build())
                .header("User-Agent", "VelaDrive/0.1")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Longdo POI HTTP \${response.code}")
                }

                val body = response.body.string()
                val root = json.parseToJsonElement(body).jsonObject
                root["data"]
                    ?.jsonArray
                    ?.mapNotNull(::parseResult)
                    .orEmpty()
            }
        }
    }

    private fun parseResult(element: kotlinx.serialization.json.JsonElement): PoiSearchResult? {
        val item = element as? JsonObject ?: return null
        val name = item["name"]?.jsonPrimitive?.content?.trim().orEmpty()
        val lat = item["lat"]?.jsonPrimitive?.doubleOrNull ?: return null
        val lon = item["lon"]?.jsonPrimitive?.doubleOrNull ?: return null
        if (name.isBlank()) return null

        return PoiSearchResult(
            id = item["id"]?.jsonPrimitive?.content,
            name = name,
            latitude = lat,
            longitude = lon,
            address = item["address"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
            distanceText = item["distance"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        )
    }

    private companion object {
        const val SEARCH_URL = "https://search.longdo.com/mapsearch/json/search"
    }
}
