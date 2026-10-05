package net.velalab.veladrive

import android.content.Context
import android.content.Intent
import android.net.Uri

object GoogleMapsLauncher {
    fun openSearch(context: Context, query: String = "") {
        val url = Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(query)}")
        val intent = Intent(Intent.ACTION_VIEW, url).apply {
            setPackage("com.google.android.apps.maps")
        }
        runCatching { context.startActivity(intent) }
            .getOrElse {
                context.startActivity(Intent(Intent.ACTION_VIEW, url))
            }
    }
}
