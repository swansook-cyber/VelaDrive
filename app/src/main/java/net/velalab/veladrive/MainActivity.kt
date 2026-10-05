package net.velalab.veladrive

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import net.velalab.veladrive.core.destination.Destination
import net.velalab.veladrive.core.destination.GoogleMapsShareResolver
import net.velalab.veladrive.core.destination.ShareResolution
import net.velalab.veladrive.ui.HomeScreen

class MainActivity : ComponentActivity() {
    private val shareResolver = GoogleMapsShareResolver()
    private var destination by mutableStateOf<Destination?>(null)
    private var isResolvingShare by mutableStateOf(false)
    private var shareError by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeIntent(intent)
        setContent {
            HomeScreen(
                sharedDestination = destination,
                isResolvingShare = isResolvingShare,
                shareError = shareError,
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
        if (text.isBlank()) return

        isResolvingShare = true
        shareError = null

        lifecycleScope.launch {
            when (val result = shareResolver.resolve(text)) {
                is ShareResolution.Resolved -> {
                    destination = result.destination
                    shareError = null
                }
                ShareResolution.Unsupported -> {
                    shareError = "ลิงก์ที่แชร์มายังไม่ใช่รูปแบบ Google Maps ที่รองรับ"
                }
                ShareResolution.CouldNotResolve -> {
                    shareError = "อ่านพิกัดจาก Google Maps ไม่สำเร็จ"
                }
            }
            isResolvingShare = false
        }
    }
}
