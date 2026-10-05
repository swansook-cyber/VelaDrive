package net.velalab.veladrive.core.poi

import android.content.Context

class VelaPoiSettingsStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun longdoApiKey(): String =
        prefs.getString(KEY_LONGDO_API_KEY, "").orEmpty().trim()

    fun saveLongdoApiKey(value: String) {
        prefs.edit().putString(KEY_LONGDO_API_KEY, value.trim()).apply()
    }

    fun clearLongdoApiKey() {
        prefs.edit().remove(KEY_LONGDO_API_KEY).apply()
    }

    private companion object {
        const val PREFS_NAME = "vela_poi_settings"
        const val KEY_LONGDO_API_KEY = "longdo_api_key"
    }
}
