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
        assertTrue(route.maneuvers.isEmpty())
    }

    @Test
    fun parsesPrimaryAndAlternateRoutes() {
        val primaryShape = encodePolyline6(
            listOf(
                RoutePoint(8.086300, 98.906300),
                RoutePoint(8.059000, 98.916700)
            )
        )
        val alternateShape = encodePolyline6(
            listOf(
                RoutePoint(8.086300, 98.906300),
                RoutePoint(8.070000, 98.930000),
                RoutePoint(8.059000, 98.916700)
            )
        )
        val payload = """
            {
              "trip": {
                "summary": {"length": 4.2, "time": 540.0},
                "legs": [{"shape": "$primaryShape"}]
              },
              "alternates": [
                {
                  "trip": {
                    "summary": {"length": 4.8, "time": 600.0},
                    "legs": [{"shape": "$alternateShape"}]
                  }
                }
              ]
            }
        """.trimIndent()

        val routes = ValhallaRouteClient("https://example.com").parseRoutes(payload)

        assertEquals(2, routes.size)
        assertEquals(4.2, routes[0].distanceKilometers, 0.0001)
        assertEquals(4.8, routes[1].distanceKilometers, 0.0001)
        assertEquals(3, routes[1].points.size)
    }

    @Test
    fun parsesValhallaTurnLanes() {
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
                  {
                    "shape": "$shape",
                    "maneuvers": [
                      {
                        "type": 15,
                        "instruction": "Turn left onto Maharaj Road.",
                        "verbal_transition_alert_instruction": "Turn left.",
                        "street_names": ["Maharaj Road"],
                        "length": 0.2,
                        "time": 30,
                        "begin_shape_index": 0,
                        "end_shape_index": 1,
                        "lanes": [
                          {"directions": 8, "active": 8},
                          {"directions": 10, "valid": 8}
                        ]
                      }
                    ]
                  }
                ]
              }
            }
        """.trimIndent()

        val route = ValhallaRouteClient("https://example.com").parseRoute(payload)

        assertEquals(1, route.maneuvers.size)
        val maneuver = route.maneuvers.first()
        assertEquals("Turn left onto Maharaj Road.", maneuver.instruction)
        assertEquals(15, maneuver.type)
        assertEquals("Maharaj Road", maneuver.primaryStreetName)
        assertEquals(listOf(0), maneuver.preferredLaneIndexes)
        assertEquals("←", maneuver.lanes[0].displaySymbol)
        assertEquals("←↑", maneuver.lanes[1].displaySymbol)
        assertTrue(maneuver.lanes[0].isActive)
        assertTrue(maneuver.lanes[1].isValid)
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
