package net.velalab.veladrive.core.navigation

import android.content.Context

class RouteOptionsStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): RouteOptions =
        RouteOptions(
            avoidUnpaved = prefs.getBoolean(KEY_AVOID_UNPAVED, true),
            avoidFerry = prefs.getBoolean(KEY_AVOID_FERRY, false),
            avoidHighways = prefs.getBoolean(KEY_AVOID_HIGHWAYS, false),
            avoidTolls = prefs.getBoolean(KEY_AVOID_TOLLS, false)
        )

    fun save(options: RouteOptions) {
        prefs.edit()
            .putBoolean(KEY_AVOID_UNPAVED, options.avoidUnpaved)
            .putBoolean(KEY_AVOID_FERRY, options.avoidFerry)
            .putBoolean(KEY_AVOID_HIGHWAYS, options.avoidHighways)
            .putBoolean(KEY_AVOID_TOLLS, options.avoidTolls)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "vela_route_options"
        const val KEY_AVOID_UNPAVED = "avoid_unpaved"
        const val KEY_AVOID_FERRY = "avoid_ferry"
        const val KEY_AVOID_HIGHWAYS = "avoid_highways"
        const val KEY_AVOID_TOLLS = "avoid_tolls"
    }
}
