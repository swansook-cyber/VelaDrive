package net.velalab.veladrive

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import net.velalab.veladrive.core.destination.Destination
import net.velalab.veladrive.core.destination.GoogleMapsShareResolver
import net.velalab.veladrive.core.destination.ShareResolution
import net.velalab.veladrive.core.location.AndroidLocationController
import net.velalab.veladrive.core.location.LocationSnapshot
import net.velalab.veladrive.ui.HomeScreen
import net.velalab.veladrive.ui.NavigationShellScreen

class MainActivity : ComponentActivity() {
    private val shareResolver = GoogleMapsShareResolver()
    private lateinit var locationController: AndroidLocationController

    private var destination by mutableStateOf<Destination?>(null)
    private var isResolvingShare by mutableStateOf(false)
    private var shareError by mutableStateOf<String?>(null)
    private var currentLocation by mutableStateOf<LocationSnapshot?>(null)
    private var locationPermissionGranted by mutableStateOf(false)
    private var locationError by mutableStateOf<String?>(null)

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            locationPermissionGranted =
                grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (locationPermissionGranted) {
                startLocationUpdates()
            } else {
                locationError = "ไม่ได้รับสิทธิ์ตำแหน่ง จึงยังเริ่มนำทางไม่ได้"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationController = AndroidLocationController(this)
        locationPermissionGranted = hasLocationPermission()

        consumeIntent(intent)

        if (locationPermissionGranted) {
            startLocationUpdates()
        }

        setContent {
            val activeDestination = destination
            if (activeDestination == null) {
                HomeScreen(
                    sharedDestination = null,
                    isResolvingShare = isResolvingShare,
                    shareError = shareError,
                    onGoogleSearch = { GoogleMapsLauncher.openSearch(this) }
                )
            } else {
                NavigationShellScreen(
                    destination = activeDestination,
                    currentLocation = currentLocation,
                    locationPermissionGranted = locationPermissionGranted,
                    locationError = locationError,
                    onRequestLocationPermission = ::requestLocationPermission,
                    onOpenGoogleSearch = { GoogleMapsLauncher.openSearch(this) }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        locationPermissionGranted = hasLocationPermission()
        if (locationPermissionGranted) {
            startLocationUpdates()
        }
    }

    override fun onStop() {
        locationController.stop()
        super.onStop()
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

    private fun requestLocationPermission() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startLocationUpdates() {
        if (!hasLocationPermission()) return

        locationError = null
        locationController.start(
            onLocation = {
                currentLocation = it
                locationError = null
            },
            onProviderUnavailable = {
                locationError = "ไม่พบ GPS หรือ Location Provider กรุณาเปิดตำแหน่งของเครื่อง"
            }
        )
    }
}
