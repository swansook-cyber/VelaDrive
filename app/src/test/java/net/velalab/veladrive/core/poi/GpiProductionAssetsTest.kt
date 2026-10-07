package net.velalab.veladrive.core.poi

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpiProductionAssetsTest {
    @Test
    fun productionSafetyAsset_hasExpectedCountUniqueIdsAndValidCoordinates() {
        val root = Json.parseToJsonElement(readProductionAsset("vela_safety_alerts.json")).jsonArray

        assertEquals(160, root.size)

        val ids = root.map { it.jsonObject.getValue("id").jsonPrimitive.content }
        assertEquals(ids.size, ids.toSet().size)

        root.forEach { element ->
            val item = element.jsonObject
            val lat = item.getValue("lat").jsonPrimitive.doubleOrNull
            val lon = item.getValue("lon").jsonPrimitive.doubleOrNull
            val direction = item["direction"]?.jsonPrimitive?.doubleOrNull

            assertTrue(lat != null && lat in -90.0..90.0)
            assertTrue(lon != null && lon in -180.0..180.0)
            assertTrue(direction == null || direction in setOf(0.0, 90.0, 180.0, 270.0))
            assertEquals("SPEED_CAMERA", item.getValue("type").jsonPrimitive.content)
            assertEquals(700.0, item.getValue("warning_distance").jsonPrimitive.doubleOrNull ?: -1.0, 0.001)
        }
    }

    @Test
    fun productionIsuzuAsset_hasExpectedCountAndSearchesInThaiAndEnglish() {
        val dataset = VelaPoiDatasetParser.parse(readProductionAsset("vela_pois.json"))

        assertEquals(289, dataset.pois.size)
        assertEquals(dataset.pois.size, dataset.pois.mapNotNull { it.id }.toSet().size)

        dataset.pois.forEach { poi ->
            assertEquals(PoiCategory.AUTO_SERVICE, poi.category)
            assertEquals(PoiSource.VELA_CURATED, poi.source)
            assertTrue(poi.alternateNames.any { it.equals("Isuzu", ignoreCase = true) })
            assertTrue(poi.alternateNames.contains("อีซูซุ"))
        }

        val english = VelaPoiSearch.search(dataset.pois, "isuzu", null, null, 20)
        val thai = VelaPoiSearch.search(dataset.pois, "อีซูซุ", null, null, 20)

        assertFalse(english.isEmpty())
        assertFalse(thai.isEmpty())
        assertTrue(english.all { it.category == PoiCategory.AUTO_SERVICE })
        assertTrue(thai.all { it.category == PoiCategory.AUTO_SERVICE })
    }

    private fun readProductionAsset(name: String): String {
        val candidates = listOf(
            File("src/main/assets/$name"),
            File("app/src/main/assets/$name")
        )
        val file = candidates.firstOrNull(File::isFile)
            ?: error("Production asset not found: $name; cwd=${File(".").absolutePath}")
        return file.readText()
    }
}
