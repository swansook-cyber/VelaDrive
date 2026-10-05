package net.velalab.veladrive.core.destination

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DestinationResolverTest {
    private val resolver = DestinationResolver()

    @Test
    fun parsesGoogleAtCoordinates() {
        val result = resolver.resolveLocally(
            "https://www.google.com/maps/place/Test/@7.880447,98.392250,17z"
        )

        requireNotNull(result)
        assertEquals(7.880447, result.latitude, 0.000001)
        assertEquals(98.392250, result.longitude, 0.000001)
        assertEquals(DestinationSource.GOOGLE_MAPS_LINK, result.source)
    }

    @Test
    fun parsesEncodedGoogleQueryCoordinates() {
        val result = resolver.resolveLocally(
            "https://www.google.com/maps/search/?api=1&query=7.880447%2C98.392250"
        )

        requireNotNull(result)
        assertEquals(7.880447, result.latitude, 0.000001)
        assertEquals(98.392250, result.longitude, 0.000001)
    }

    @Test
    fun parsesGoogleDataCoordinates() {
        val result = resolver.resolveLocally(
            "https://www.google.com/maps/place/Test/data=!3d7.880447!4d98.392250"
        )

        requireNotNull(result)
        assertEquals(7.880447, result.latitude, 0.000001)
        assertEquals(98.392250, result.longitude, 0.000001)
    }

    @Test
    fun parsesGeoUri() {
        val result = resolver.resolveLocally("geo:7.880447,98.392250?q=Test")

        requireNotNull(result)
        assertEquals(DestinationSource.GEO_URI, result.source)
    }

    @Test
    fun parsesRawCoordinates() {
        val result = resolver.resolveLocally("7.880447, 98.392250")

        requireNotNull(result)
        assertEquals(DestinationSource.RAW_COORDINATES, result.source)
    }

    @Test
    fun rejectsOutOfRangeCoordinates() {
        assertNull(resolver.resolveLocally("95.0, 190.0"))
    }

    @Test
    fun shortLinkNeedsNetworkResolution() {
        assertNull(resolver.resolveLocally("https://maps.app.goo.gl/abc123"))
    }
}
