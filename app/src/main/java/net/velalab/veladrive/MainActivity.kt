package net.velalab.veladrive

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.velalab.veladrive.core.destination.Destination
import net.velalab.veladrive.core.destination.DestinationSource
import net.velalab.veladrive.core.destination.GoogleMapsShareResolver
import net.velalab.veladrive.core.destination.ShareResolution
import net.velalab.veladrive.core.location.AndroidLocationController
import net.velalab.veladrive.core.location.LocationSnapshot
import com.stadiamaps.ferrostar.core.NavigationUiState
import net.velalab.veladrive.core.navigation.RoutePreview
import net.velalab.veladrive.core.navigation.ValhallaRouteClient
import net.velalab.veladrive.core.navigation.VelaFerrostarController
import net.velalab.veladrive.core.navigation.VelaGuidanceEngine
import net.velalab.veladrive.core.navigation.VelaGuidanceSnapshot
import net.velalab.veladrive.core.navigation.VelaThaiTts
import net.velalab.veladrive.core.poi.LongdoPoiClient
import net.velalab.veladrive.core.poi.PoiSearchResult
import net.velalab.veladrive.core.poi.VelaPlaceStore
import net.velalab.veladrive.core.poi.VelaPoiSettingsStore
import net.velalab.veladrive.ui.NavigationShellScreen

class MainActivity : ComponentActivity() {
    private val shareResolver = GoogleMapsShareResolver()
    private lateinit var locationController: AndroidLocationController
    private val routeClient by lazy { ValhallaRouteClient(BuildConfig.VALHALLA_BASE_URL) }
    private var ferrostarController: VelaFerrostarController? = null
    private var thaiTts: VelaThaiTts? = null
    private var navigationStateJob: Job? = null
    private val placeStore by lazy { VelaPlaceStore(this) }
    private val poiSettingsStore by lazy { VelaPoiSettingsStore(this) }

    private var destination by mutableStateOf<Destination?>(null)
    private var isResolvingShare by mutableStateOf(false)
    private var shareError by mutableStateOf<String?>(null)
    private var currentLocation by mutableStateOf<LocationSnapshot?>(null)
    private var locationPermissionGranted by mutableStateOf(false)
    private var locationError by mutableStateOf<String?>(null)
    private var routePreview by mutableStateOf<RoutePreview?>(null)
    private var isLoadingRoute by mutableStateOf(false)
    private var routeError by mutableStateOf<String?>(null)
    private var guidance by mutableStateOf<VelaGuidanceSnapshot?>(null)
    private var isNavigationStarting by mutableStateOf(false)
    private var isSimulationStarting by mutableStateOf(false)
    private var navigationError by mutableStateOf<String?>(null)
    private var simulationError by mutableStateOf<String?>(null)
    private var isSimulationMuted by mutableStateOf(false)
    private var poiResults by mutableStateOf<List<PoiSearchResult>>(emptyList())
    private var isSearchingPois by mutableStateOf(false)
    private var poiError by mutableStateOf<String?>(null)
    private var recentPlaces by mutableStateOf<List<PoiSearchResult>>(emptyList())
    private var savedPlaces by mutableStateOf<List<PoiSearchResult>>(emptyList())
    private var isLongdoConfigured by mutableStateOf(false)

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


    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Navigation is not blocked when notification permission is denied.
            // Android still keeps the location foreground service visible in system UI.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationController = AndroidLocationController(this)
        locationPermissionGranted = hasLocationPermission()
        refreshStoredPlaces()
        refreshPoiSettings()

        consumeIntent(intent)

        if (locationPermissionGranted) {
            startLocationUpdates()
        }

        setContent {
            val activeDestination = destination
            if (activeDestination == null) {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Vela Drive Diagnostic",
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Text(
                                "Safe mode: ยังไม่โหลด MapLibre / Ferrostar",
                                modifier = Modifier.padding(top = 12.dp)
                            )
                            Text(
                                if (locationPermissionGranted) {
                                    "GPS permission: พร้อม"
                                } else {
                                    "GPS permission: ยังไม่ได้อนุญาต"
                                },
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            } else {
                NavigationShellScreen(
                    destination = activeDestination,
                    currentLocation = currentLocation,
                    locationPermissionGranted = locationPermissionGranted,
                    locationError = locationError,
                    routePreview = routePreview,
                    isLoadingRoute = isLoadingRoute,
                    routeError = routeError,
                    guidance = guidance,
                    isNavigationStarting = isNavigationStarting,
                    isSimulationStarting = isSimulationStarting,
                    navigationError = navigationError,
                    simulationError = simulationError,
                    isSimulationMuted = isSimulationMuted,
                    onRequestLocationPermission = ::requestLocationPermission,
                    onCalculateRoute = ::calculateRoute,
                    onStartNavigation = ::startNavigation,
                    onStartSimulation = ::startSimulation,
                    onStopNavigation = ::stopNavigation,
                    onToggleMute = ::toggleSimulationMute,
                    onBackToSearch = ::clearDestination
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        thaiTts?.start()
        locationPermissionGranted = hasLocationPermission()
        if (locationPermissionGranted) {
            startLocationUpdates()
        }
    }

    override fun onStop() {
        locationController.stop()
        super.onStop()
    }

    override fun onDestroy() {
        navigationStateJob?.cancel()
        navigationStateJob = null
        ferrostarController?.shutdown()
        ferrostarController = null
        thaiTts?.shutdown()
        thaiTts = null
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIntent(intent)
    }

    private fun consumeIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("text/") != true) return
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        if (text.isBlank()) return

        isResolvingShare = true
        shareError = null

        lifecycleScope.launch {
            when (val result = shareResolver.resolve(text)) {
                is ShareResolution.Resolved -> {
                    val resolved = result.destination
                    placeStore.addRecent(
                        PoiSearchResult(
                            id = "shared:${resolved.latitude},${resolved.longitude}",
                            name = resolved.label ?: "จุดหมายจาก Google Maps",
                            latitude = resolved.latitude,
                            longitude = resolved.longitude,
                            address = null,
                            distanceText = null
                        )
                    )
                    refreshStoredPlaces()
                    destination = resolved
                    routePreview = null
                    routeError = null
                    ferrostarController?.stopNavigation()
                    guidance = null
                    thaiTts?.resetDeduplication()
                    simulationError = null
                    shareError = null
                }
                is ShareResolution.Unsupported -> {
                    shareError =
                        "ลิงก์ที่แชร์มายังไม่ใช่รูปแบบ Google Maps ที่รองรับ\n\nDIAG:\n${result.detail}"
                }
                is ShareResolution.CouldNotResolve -> {
                    shareError =
                        "อ่านพิกัดจาก Google Maps ไม่สำเร็จ\n\nDIAG:\n${result.detail}"
                }
            }
            isResolvingShare = false
        }
    }

    private fun searchPoi(keyword: String) {
        val cleanKeyword = keyword.trim()
        if (cleanKeyword.isBlank()) return

        isSearchingPois = true
        poiError = null
        poiResults = emptyList()
        val location = currentLocation

        lifecycleScope.launch {
            val localResults = placeStore.searchSaved(cleanKeyword)
            val remoteResult = withContext(Dispatchers.IO) {
                LongdoPoiClient(activeLongdoApiKey()).search(
                    keyword = cleanKeyword,
                    latitude = location?.latitude,
                    longitude = location?.longitude
                )
            }

            remoteResult
                .onSuccess { remote ->
                    poiResults = mergePoiResults(localResults, remote)
                    if (poiResults.isEmpty()) {
                        poiError = "ไม่พบสถานที่ใน Vela POI"
                    }
                }
                .onFailure {
                    poiResults = localResults
                    poiError =
                        if (localResults.isEmpty()) {
                            it.message ?: "ค้นหาสถานที่ไม่สำเร็จ"
                        } else {
                            "Longdo ใช้งานไม่ได้ชั่วคราว แสดงสถานที่ที่บันทึกไว้"
                        }
                }

            isSearchingPois = false
        }
    }

    private fun selectPoi(poi: PoiSearchResult) {
        placeStore.addRecent(poi)
        refreshStoredPlaces()
        destination = Destination(
            latitude = poi.latitude,
            longitude = poi.longitude,
            label = poi.name,
            source = DestinationSource.LONGDO_POI
        )
        poiResults = emptyList()
        poiError = null
        routePreview = null
        routeError = null
        ferrostarController?.stopNavigation()
        guidance = null
        thaiTts?.resetDeduplication()
        simulationError = null
    }

    private fun activeLongdoApiKey(): String {
        val runtime = poiSettingsStore.longdoApiKey()
        return runtime.ifBlank { BuildConfig.LONGDO_MAP_API_KEY }
    }

    private fun refreshPoiSettings() {
        isLongdoConfigured = activeLongdoApiKey().isNotBlank()
    }

    private fun saveLongdoApiKey(value: String) {
        poiSettingsStore.saveLongdoApiKey(value)
        refreshPoiSettings()
        poiError = null
    }

    private fun savePoi(poi: PoiSearchResult) {
        placeStore.save(poi)
        refreshStoredPlaces()
    }

    private fun refreshStoredPlaces() {
        recentPlaces = placeStore.recentPlaces().map { it.toPoiSearchResult() }
        savedPlaces = placeStore.savedPlaces().map { it.toPoiSearchResult() }
    }

    private fun mergePoiResults(
        local: List<PoiSearchResult>,
        remote: List<PoiSearchResult>
    ): List<PoiSearchResult> {
        val merged = mutableListOf<PoiSearchResult>()
        (local + remote).forEach { candidate ->
            val duplicate = merged.any {
                kotlin.math.abs(it.latitude - candidate.latitude) < 0.00001 &&
                    kotlin.math.abs(it.longitude - candidate.longitude) < 0.00001
            }
            if (!duplicate) merged += candidate
        }
        return merged
    }

    private fun clearDestination() {
        ferrostarController?.stopNavigation()
        destination = null
        routePreview = null
        routeError = null
        guidance = null
        thaiTts?.resetDeduplication()
        simulationError = null
    }

    private fun calculateRoute() {
        val origin = currentLocation ?: return
        val target = destination ?: return

        isLoadingRoute = true
        routeError = null

        lifecycleScope.launch {
            routeClient.route(origin, target)
                .onSuccess { routePreview = it }
                .onFailure { routeError = it.message ?: "คำนวณเส้นทางไม่สำเร็จ" }
            isLoadingRoute = false
        }
    }

    private fun startNavigation() {
        requestNotificationPermissionIfNeeded()
        val origin = currentLocation ?: return
        val target = destination ?: return

        isNavigationStarting = true
        navigationError = null
        simulationError = null

        val controller = ensureNavigationStack() ?: run {
            isNavigationStarting = false
            return
        }

        lifecycleScope.launch {
            runCatching {
                controller.startLiveNavigation(origin, target)
            }.onFailure {
                navigationError = it.message ?: "เริ่มนำทางด้วย GPS ไม่สำเร็จ"
            }
            isNavigationStarting = false
        }
    }

    private fun startSimulation() {
        requestNotificationPermissionIfNeeded()
        val origin = currentLocation ?: return
        val target = destination ?: return

        isSimulationStarting = true
        simulationError = null

        val controller = ensureNavigationStack() ?: run {
            isSimulationStarting = false
            return
        }

        lifecycleScope.launch {
            runCatching {
                controller.startSimulation(origin, target)
            }.onFailure {
                simulationError = it.message ?: "เริ่มการจำลองนำทางไม่สำเร็จ"
            }
            isSimulationStarting = false
        }
    }

    private fun stopNavigation() {
        ferrostarController?.stopNavigation()
        guidance = null
        thaiTts?.resetDeduplication()
        navigationError = null
        simulationError = null
        isNavigationStarting = false
        isSimulationStarting = false
    }

    private fun toggleSimulationMute() {
        val tts = thaiTts ?: return
        tts.toggleMuted()
        isSimulationMuted = tts.isMuted
    }

    private fun ensureNavigationStack(): VelaFerrostarController? {
        ferrostarController?.let { return it }

        return runCatching {
            val tts = VelaThaiTts(this).also {
                thaiTts = it
                it.start()
            }
            val controller =
                VelaFerrostarController(this, BuildConfig.VALHALLA_BASE_URL).also {
                    ferrostarController = it
                }

            navigationStateJob?.cancel()
            navigationStateJob =
                lifecycleScope.launch {
                    controller.state.collect { state ->
                        val uiState = NavigationUiState.fromFerrostar(
                            state,
                            tts.isMuted,
                            null
                        )
                        val nextGuidance = VelaGuidanceEngine.from(
                            uiState = uiState,
                            routePreview = routePreview,
                            speedMetersPerSecond = currentLocation?.speedMetersPerSecond?.toDouble()
                        )
                        guidance = nextGuidance
                        tts.speakGuidance(nextGuidance)
                        isSimulationMuted = tts.isMuted
                    }
                }

            controller
        }.onFailure { error ->
            navigationError =
                "เริ่มระบบนำทางไม่สำเร็จ: " +
                    (error.message ?: error.javaClass.simpleName)
            thaiTts?.shutdown()
            thaiTts = null
            ferrostarController = null
        }.getOrNull()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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
