package net.velalab.veladrive.core.poi

import java.io.File
import kotlin.math.max
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KrabiPilotPerformanceTest {
    @Test
    fun `measure Krabi pilot JSON load and representative searches`() {
        val pilotFile = findPilotFile()
        val runtime = Runtime.getRuntime()
        repeat(2) {
            VelaPoiDatasetParser.parse(pilotFile.readText())
        }

        System.gc()
        val heapBefore = runtime.totalMemory() - runtime.freeMemory()
        val totalStarted = System.nanoTime()
        val readStarted = System.nanoTime()
        val raw = pilotFile.readText()
        val readMillis = elapsedMillis(readStarted)
        val parseStarted = System.nanoTime()
        val dataset = VelaPoiDatasetParser.parse(raw)
        val parseMillis = elapsedMillis(parseStarted)
        val totalMillis = elapsedMillis(totalStarted)
        val heapAfter = runtime.totalMemory() - runtime.freeMemory()
        val heapDeltaBytes = max(0L, heapAfter - heapBefore)

        assertEquals(2_702, dataset.pois.size)
        println(
            "KRABI_PILOT_LOAD " +
                "asset_bytes=${pilotFile.length()} read_ms=$readMillis parse_ms=$parseMillis " +
                "total_ms=$totalMillis approximate_heap_delta_bytes=$heapDeltaBytes"
        )

        val queries = listOf(
            "โรงแรม",
            "ร้านอาหาร",
            "มัสยิด",
            "ปั๊มน้ำมัน",
            "ATM",
            "hospital",
            "hotel"
        )
        queries.forEach { query ->
            repeat(5) {
                VelaPoiSearch.search(dataset.pois, query, KRABI_LATITUDE, KRABI_LONGITUDE, 20)
            }
            val samples = LongArray(50)
            var resultCount = 0
            repeat(samples.size) { index ->
                val started = System.nanoTime()
                val results =
                    VelaPoiSearch.search(
                        dataset.pois,
                        query,
                        KRABI_LATITUDE,
                        KRABI_LONGITUDE,
                        20
                    )
                samples[index] = System.nanoTime() - started
                resultCount = results.size
            }
            samples.sort()
            assertTrue("Expected local OSM results for $query", resultCount > 0)
            println(
                "KRABI_PILOT_SEARCH query=$query results=$resultCount " +
                    "p50_ms=${nanosToMillis(samples[25])} p95_ms=${nanosToMillis(samples[47])}"
            )
        }
    }

    private fun findPilotFile(): File {
        val relative = "tools/osm_poi_importer/generated/krabi/vela_pois_krabi.json"
        val workingDirectory =
            File(System.getProperty("user.dir") ?: error("user.dir is unavailable"))
        var directory: File? = workingDirectory
        while (directory != null) {
            val candidate = File(directory, relative)
            if (candidate.isFile) return candidate
            directory = directory.parentFile
        }
        error("Krabi pilot dataset not found from $workingDirectory")
    }

    private fun elapsedMillis(started: Long): Double =
        nanosToMillis(System.nanoTime() - started)

    private fun nanosToMillis(nanoseconds: Long): Double = nanoseconds / 1_000_000.0

    private companion object {
        const val KRABI_LATITUDE = 8.0863
        const val KRABI_LONGITUDE = 98.9063
    }
}
