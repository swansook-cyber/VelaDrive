package net.velalab.veladrive.core.navigation

import com.stadiamaps.ferrostar.core.withJsonOptions
import uniffi.ferrostar.WellKnownRouteProvider

/**
 * Creates the Ferrostar route provider for Vela Drive.
 *
 * The endpoint is injected so the app never depends on a paid routing vendor.
 * Production will point this at the Vela-hosted Valhalla service.
 */
object FerrostarValhallaFactory {
    fun create(endpointUrl: String): WellKnownRouteProvider {
        require(endpointUrl.startsWith("https://") || endpointUrl.startsWith("http://")) {
            "Valhalla endpoint must be an HTTP(S) URL"
        }

        val options: Map<String, Any> = mapOf(
            "units" to "kilometers"
        )

        return WellKnownRouteProvider
            .Valhalla(endpointUrl.trimEnd('/') + "/route", "auto")
            .withJsonOptions(options)
    }
}
