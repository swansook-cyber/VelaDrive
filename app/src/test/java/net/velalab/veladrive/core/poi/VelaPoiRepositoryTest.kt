package net.velalab.veladrive.core.poi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class VelaPoiRepositoryTest {
    @Test
    fun `Thai keyword matching normalizes whitespace`() {
        val repository = repository(
            fixturePoi(
                id = "test-thai",
                name = "สถานี ทดสอบ ภาษาไทย"
            )
        )

        val results = repository.search("  สถานี   ทดสอบ ", null, null).getOrThrow()

        assertEquals(listOf("test-thai"), results.map { it.id })
    }

    @Test
    fun `alias matching is case insensitive`() {
        val repository = repository(
            fixturePoi(
                id = "test-alias",
                name = "TEST ONLY PRIMARY NAME",
                alternateNames = listOf("Vela Alias Example")
            )
        )

        val results = repository.search("vELA aLIAS", null, null).getOrThrow()

        assertEquals(listOf("test-alias"), results.map { it.id })
    }

    @Test
    fun `partial keyword matches a name`() {
        val repository = repository(
            fixturePoi(id = "test-partial", name = "TEST ONLY Alpha Place")
        )

        val results = repository.search("pha pla", null, null).getOrThrow()

        assertEquals(listOf("test-partial"), results.map { it.id })
    }

    @Test
    fun `equally relevant matches are ranked by distance`() {
        val repository = repository(
            fixturePoi(id = "test-far", name = "TEST ONLY Station", latitude = 11.0),
            fixturePoi(id = "test-near", name = "TEST ONLY Station", latitude = 10.01)
        )

        val results = repository.search("TEST ONLY Station", 10.0, 100.0).getOrThrow()

        assertEquals(listOf("test-near", "test-far"), results.map { it.id })
        assertTrue(results.first().distanceText?.isNotBlank() == true)
    }

    @Test
    fun `Vela result wins coordinate and normalized-name deduplication against remote`() {
        val vela = fixturePoi(
            id = "vela-test",
            name = "TEST   ONLY Duplicate",
            latitude = 10.0,
            longitude = 100.0
        )
        val remote = fixturePoi(
            id = "longdo-test",
            name = "test only duplicate",
            latitude = 10.00005,
            longitude = 100.00005,
            source = PoiSource.LONGDO
        )

        val outcome = PoiSearchMerger.merge(
            savedResults = emptyList(),
            velaResults = Result.success(listOf(vela)),
            longdoResults = Result.success(listOf(remote))
        )

        assertEquals(listOf(vela), outcome.results)
    }

    @Test
    fun `stable source ID deduplicates before coordinate fallback`() {
        val first = fixturePoi(
            id = "stable-test-id",
            name = "TEST ONLY Original",
            latitude = 10.0,
            longitude = 100.0,
            source = PoiSource.LONGDO
        )
        val duplicate = first.copy(
            name = "TEST ONLY Updated Label",
            latitude = 11.0,
            longitude = 101.0
        )

        val outcome = PoiSearchMerger.merge(
            savedResults = listOf(first),
            velaResults = Result.success(emptyList()),
            longdoResults = Result.success(listOf(duplicate))
        )

        assertEquals(listOf(first), outcome.results)
    }

    @Test
    fun `Longdo failure keeps Vela results`() {
        val vela = fixturePoi(id = "vela-test", name = "TEST ONLY Offline Result")
        val failure = IllegalStateException("TEST ONLY Longdo failure")

        val outcome = PoiSearchMerger.merge(
            savedResults = emptyList(),
            velaResults = Result.success(listOf(vela)),
            longdoResults = Result.failure(failure)
        )

        assertEquals(listOf(vela), outcome.results)
        assertNull(outcome.velaFailure)
        assertSame(failure, outcome.longdoFailure)
    }

    @Test
    fun `saved place has priority over duplicate Vela POI`() {
        val saved = fixturePoi(
            id = "vela-test",
            name = "TEST ONLY Saved Priority",
            source = PoiSource.VELA_CURATED
        )
        val vela = saved.copy(address = "TEST ONLY lower-priority record")

        val outcome = PoiSearchMerger.merge(
            savedResults = listOf(saved),
            velaResults = Result.success(listOf(vela)),
            longdoResults = Result.success(emptyList())
        )

        assertEquals(1, outcome.results.size)
        assertSame(saved, outcome.results.single())
    }

    @Test
    fun `search tier ordering is saved then OpenStreetMap then Longdo`() {
        val saved = fixturePoi(
            id = "saved-test",
            name = "TEST ONLY Saved",
            source = PoiSource.USER_PLACE
        )
        val osm = fixturePoi(
            id = "osm:node:1",
            name = "TEST ONLY OSM",
            latitude = 10.1,
            source = PoiSource.OPENSTREETMAP
        )
        val longdo = fixturePoi(
            id = "longdo-test",
            name = "TEST ONLY Longdo",
            latitude = 10.2,
            source = PoiSource.LONGDO
        )

        val outcome = PoiSearchMerger.merge(
            savedResults = listOf(saved),
            velaResults = Result.success(listOf(osm)),
            longdoResults = Result.success(listOf(longdo))
        )

        assertEquals(
            listOf(PoiSource.USER_PLACE, PoiSource.OPENSTREETMAP, PoiSource.LONGDO),
            outcome.results.map(PoiSearchResult::source)
        )
    }

    @Test
    fun `asset schema retains provenance and dataset metadata`() {
        val dataset = VelaPoiDatasetParser.parse(
            """
            {
              "schemaVersion": 1,
              "datasetVersion": "test-only-v1",
              "updatedAt": "2000-01-01",
              "pois": [
                {
                  "id": "test-schema",
                  "name": "TEST ONLY Schema Record",
                  "alternateNames": ["TEST ONLY Alias"],
                  "category": "OTHER",
                  "latitude": 10.0,
                  "longitude": 100.0,
                  "source": "VELA_CURATED",
                  "sourceReference": "test-fixture",
                  "verified": true
                }
              ]
            }
            """.trimIndent()
        )

        val poi = dataset.pois.single()
        assertEquals("test-schema", poi.id)
        assertEquals(listOf("TEST ONLY Alias"), poi.alternateNames)
        assertEquals(PoiCategory.OTHER, poi.category)
        assertEquals(PoiSource.VELA_CURATED, poi.source)
        assertEquals("test-fixture", poi.sourceReference)
        assertTrue(poi.verified)
        assertEquals("test-only-v1", poi.datasetVersion)
        assertEquals("2000-01-01", poi.updatedAt)
    }

    @Test
    fun `OpenStreetMap asset retains source reference and classification tags`() {
        val dataset = VelaPoiDatasetParser.parse(
            """
            {
              "schemaVersion": 1,
              "datasetVersion": "osm-test-only",
              "updatedAt": "2000-01-01",
              "pois": [
                {
                  "id": "osm:node:123",
                  "name": "TEST ONLY OSM Record",
                  "category": "FUEL",
                  "latitude": 10.0,
                  "longitude": 100.0,
                  "source": "OPENSTREETMAP",
                  "sourceReference": "node/123",
                  "sourceUrl": "https://www.openstreetmap.org/node/123",
                  "sourceTags": {"amenity": "fuel"}
                }
              ]
            }
            """.trimIndent()
        )

        val poi = dataset.pois.single()
        assertEquals(PoiSource.OPENSTREETMAP, poi.source)
        assertEquals("node/123", poi.sourceReference)
        assertEquals(mapOf("amenity" to "fuel"), poi.sourceTags)
    }

    private fun repository(vararg pois: PoiSearchResult): VelaPoiRepository =
        InMemoryVelaPoiRepository(pois.toList())

    private fun fixturePoi(
        id: String,
        name: String,
        latitude: Double = 10.0,
        longitude: Double = 100.0,
        alternateNames: List<String> = emptyList(),
        source: PoiSource = PoiSource.VELA_CURATED
    ): PoiSearchResult =
        PoiSearchResult(
            id = id,
            name = name,
            latitude = latitude,
            longitude = longitude,
            address = null,
            distanceText = null,
            alternateNames = alternateNames,
            category = PoiCategory.OTHER,
            source = source,
            sourceReference = "test-fixture",
            verified = false,
            datasetVersion = "test-only"
        )
}
