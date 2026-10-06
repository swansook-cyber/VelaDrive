package net.velalab.veladrive.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteOptionsTest {
    @Test
    fun defaultsAvoidUnpavedButAllowFerryHighwaysAndTolls() {
        val options = RouteOptions()
        val valhalla = options.valhallaAutoOptions()

        assertTrue(options.avoidUnpaved)
        assertFalse(options.avoidFerry)
        assertFalse(options.avoidHighways)
        assertFalse(options.avoidTolls)
        assertEquals(true, valhalla["exclude_unpaved"])
        assertEquals(0.5, valhalla["use_ferry"])
        assertEquals(0.5, valhalla["use_highways"])
        assertEquals(0.5, valhalla["use_tolls"])
    }

    @Test
    fun avoidFlagsMapToZeroPreference() {
        val valhalla =
            RouteOptions(
                avoidUnpaved = false,
                avoidFerry = true,
                avoidHighways = true,
                avoidTolls = true
            ).valhallaAutoOptions()

        assertEquals(false, valhalla["exclude_unpaved"])
        assertEquals(0.0, valhalla["use_ferry"])
        assertEquals(0.0, valhalla["use_highways"])
        assertEquals(0.0, valhalla["use_tolls"])
    }

    @Test
    fun summaryIsHeadUnitFriendly() {
        assertEquals("เลี่ยงลูกรัง", RouteOptions().summaryLabel())
        assertEquals(
            "เลี่ยงลูกรัง • เลี่ยงเรือ • เลี่ยงทางเสียเงิน",
            RouteOptions(
                avoidUnpaved = true,
                avoidFerry = true,
                avoidTolls = true
            ).summaryLabel()
        )
    }
}
