package net.velalab.veladrive.core.destination

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleMapsShareResolverTest {
    @Test
    fun extractsMapsAppShortLinkFromShareText() {
        val text = "ร้านตัวอย่าง\nhttps://maps.app.goo.gl/AbCdEf123"
        assertEquals(
            "https://maps.app.goo.gl/AbCdEf123",
            GoogleMapsShareResolver.extractSupportedGoogleShortUrl(text)
        )
    }

    @Test
    fun acceptsLegacyGooGlMapsLink() {
        assertEquals(
            "https://goo.gl/maps/AbCdEf123",
            GoogleMapsShareResolver.extractSupportedGoogleShortUrl("https://goo.gl/maps/AbCdEf123")
        )
    }

    @Test
    fun rejectsArbitraryLinks() {
        assertNull(
            GoogleMapsShareResolver.extractSupportedGoogleShortUrl(
                "https://example.com/maps.app.goo.gl/fake"
            )
        )
    }

    @Test
    fun doesNotTreatNonMapsGooGlAsGoogleMapsShare() {
        assertNull(
            GoogleMapsShareResolver.extractSupportedGoogleShortUrl("https://goo.gl/AbCdEf123")
        )
    }

    @Test
    fun finalGoogleMapsUrlsAreAllowed() {
        assertTrue(
            GoogleMapsShareResolver.isAllowedGoogleMapsDestinationUrl(
                "https://www.google.com/maps/place/Test/@7.88,98.39,17z"
            )
        )
        assertTrue(
            GoogleMapsShareResolver.isAllowedGoogleMapsDestinationUrl(
                "https://maps.google.com/?q=7.88,98.39"
            )
        )
    }

    @Test
    fun resolvesCoordinatesFromIntermediateGoogleRedirect() {
        val resolver = GoogleMapsShareResolver()

        val result = resolver.resolveFromGoogleRedirectUrls(
            listOf(
                "https://www.google.com/maps/search/?api=1&query=Coffee",
                "https://www.google.com/maps/place/Test/@7.880447,98.392250,17z",
                "https://maps.app.goo.gl/AbCdEf123"
            )
        )

        requireNotNull(result)
        assertEquals(7.880447, result.latitude, 0.000001)
        assertEquals(98.392250, result.longitude, 0.000001)
    }

    @Test
    fun ignoresNonGoogleUrlsWhileScanningRedirectChain() {
        val resolver = GoogleMapsShareResolver()

        val result = resolver.resolveFromGoogleRedirectUrls(
            listOf(
                "https://evil.example/@1.0,2.0",
                "https://www.google.com/maps/place/Test/data=!3d7.880447!4d98.392250"
            )
        )

        requireNotNull(result)
        assertEquals(7.880447, result.latitude, 0.000001)
        assertEquals(98.392250, result.longitude, 0.000001)
    }

    @Test
    fun nonGoogleRedirectTargetIsRejected() {
        assertFalse(
            GoogleMapsShareResolver.isAllowedGoogleMapsDestinationUrl(
                "https://evil.example/maps/@7.88,98.39"
            )
        )
    }
}
