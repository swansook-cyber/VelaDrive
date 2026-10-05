package net.velalab.veladrive.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValhallaRouteClientTest {
    @Test
    fun decodesPolyline6Coordinates() {
        val expected = listOf(
            RoutePoint(8.086300, 98.906300),
            RoutePoint(8.080000, 98.910000),
            RoutePoint(8.059000, 98.916700)
        )
        val encoded = encodePolyline6(expected)

        val decoded = Polyline6Decoder.decode(encoded)

        assertEquals(expected.size, decoded.size)
        expected.zip(decoded).forEach { (a, b) ->
            assertEquals(a.latitude, b.latitude, 0.000001)
            assertEquals(a.longitude, b.longitude, 0.000001)
        }
    }

    @Test
    fun parsesValhallaTripSummaryAndShape() {
        val points = listOf(
            RoutePoint(8.086300, 98.906300),
            RoutePoint(8.059000, 98.916700)
        )
        val shape = encodePolyline6(points)
        val payload = """
            {
              "trip": {
                "summary": {
                  "length": 4.2,
                  "time": 540.0
                },
                "legs": [
                  {"shape": "$shape"}
                ]
              }
            }
        """.trimIndent()

        val route = ValhallaRouteClient("https://example.com").parseRoute(payload)

        assertEquals(4.2, route.distanceKilometers, 0.0001)
        assertEquals(540.0, route.durationSeconds, 0.0001)
        assertEquals(2, route.points.size)
        assertTrue(route.points.first().latitude > 8.0)
    }

    private fun encodePolyline6(points: List<RoutePoint>): String {
        var lastLat = 0L
        var lastLon = 0L
        val result = StringBuilder()

        points.forEach { point ->
            val lat = kotlin.math.round(point.latitude * 1_000_000).toLong()
            val lon = kotlin.math.round(point.longitude * 1_000_000).toLong()
            encodeValue(lat - lastLat, result)
            encodeValue(lon - lastLon, result)
            lastLat = lat
            lastLon = lon
        }

        return result.toString()
    }

    private fun encodeValue(delta: Long, output: StringBuilder) {
        var value = if (delta < 0) (delta shl 1).inv() else delta shl 1
        while (value >= 0x20) {
            output.append(((0x20 or (value and 0x1f).toInt()) + 63).toChar())
            value = value shr 5
        }
        output.append((value.toInt() + 63).toChar())
    }
}
