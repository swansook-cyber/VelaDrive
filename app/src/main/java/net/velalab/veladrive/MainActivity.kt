package net.velalab.veladrive

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.velalab.veladrive.core.destination.Destination
import net.velalab.veladrive.core.destination.DestinationResolver
import net.velalab.veladrive.ui.HomeScreen

class MainActivity : ComponentActivity() {
    private val resolver = DestinationResolver()
    private var destination by mutableStateOf<Destination?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeIntent(intent)
        setContent {
            HomeScreen(
                sharedDestination = destination,
                onGoogleSearch = { GoogleMapsLauncher.openSearch(this) }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIntent(intent)
    }

    private fun consumeIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        destination = resolver.resolveLocally(text)
        // V1 next step: resolve maps.app.goo.gl redirects safely when coordinates are not embedded.
    }
}
