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
    fun nonGoogleRedirectTargetIsRejected() {
        assertFalse(
            GoogleMapsShareResolver.isAllowedGoogleMapsDestinationUrl(
                "https://evil.example/maps/@7.88,98.39"
            )
        )
    }
}
