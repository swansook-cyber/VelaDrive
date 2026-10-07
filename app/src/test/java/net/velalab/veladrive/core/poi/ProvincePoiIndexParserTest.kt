package net.velalab.veladrive.core.poi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvincePoiIndexParserTest {
    @Test
    fun parsesProvinceIndex() {
        val raw = """
            {
              "schemaVersion": 1,
              "datasetVersion": "osm-thailand-test",
              "provinceCount": 2,
              "totalPois": 30,
              "provinces": [
                {
                  "code": "81",
                  "nameTh": "กระบี่",
                  "nameEn": "Krabi",
                  "asset": "poi_provinces/81.json",
                  "count": 12,
                  "bbox": [98.5, 7.4, 99.4, 8.7]
                },
                {
                  "code": "83",
                  "nameTh": "ภูเก็ต",
                  "nameEn": "Phuket",
                  "asset": "poi_provinces/83.json",
                  "count": 18,
                  "bbox": [98.2, 7.7, 98.5, 8.2]
                }
              ]
            }
        """.trimIndent()

        val result = ProvincePoiIndexParser.parse(raw)

        assertEquals(2, result.size)
        assertEquals("กระบี่", result[0].nameTh)
        assertEquals("poi_provinces/81.json", result[0].asset)
        assertEquals(12, result[0].count)
        assertTrue(result[0].centerLatitude != null)
        assertTrue(result[0].centerLongitude != null)
    }
}
