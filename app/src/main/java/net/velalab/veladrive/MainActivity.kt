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
import net.velalab.veladrive.core.navigation.RouteOptions
import net.velalab.veladrive.core.navigation.RouteOptionsStore
import net.velalab.veladrive.core.navigation.RoutePreview
import net.velalab.veladrive.core.navigation.ValhallaRouteClient
import net.velalab.veladrive.core.navigation.VelaFerrostarController
import net.velalab.veladrive.core.navigation.VelaGuidanceEngine
import net.velalab.veladrive.core.navigation.VelaGuidanceSnapshot
import net.velalab.veladrive.core.navigation.VelaThaiTts
import net.velalab.veladrive.core.poi.AssetProvincePoiRepository
import net.velalab.veladrive.core.poi.AssetVelaPoiRepository
import net.velalab.veladrive.core.poi.LongdoPoiClient
import net.velalab.veladrive.core.poi.PoiSearchResult
import net.velalab.veladrive.core.poi.ProvincePoiOption
import net.velalab.veladrive.core.poi.PoiSearchMerger
import net.velalab.veladrive.core.poi.PoiSource
import net.velalab.veladrive.core.poi.VelaPlaceStore
import net.velalab.veladrive.core.poi.VelaPoiSettingsStore
import net.velalab.veladrive.core.safety.ActiveSafetyAlert
import net.velalab.veladrive.core.safety.AssetSafetyAlertRepository
import net.velalab.veladrive.core.safety.SafetyAlert
import net.velalab.veladrive.core.safety.SafetyAlertEngine
import net.velalab.veladrive.ui.HomeScreen
import net.velalab.veladrive.ui.NavigationShellScreen
import org.maplibre.android.MapLibre

class MainActivity : ComponentActivity() {
    private val shareResolver = GoogleMapsShareResolver()
    private lateinit var locationController: AndroidLocationController
    private val routeClient by lazy { ValhallaRouteClient(BuildConfig.VALHALLA_BASE_URL) }
    private var ferrostarController: VelaFerrostarController? = null
    private var thaiTts: VelaThaiTts? = null
    private var navigationStateJob: Job? = null
    private val placeStore by lazy { VelaPlaceStore(this) }
    private val velaPoiRepository by lazy {
        AssetVelaPoiRepository(this, BuildConfig.VELA_POI_ASSET_NAME)
    }
    private val provincePoiRepository by lazy { AssetProvincePoiRepository(this) }
    private val poiSettingsStore by lazy { VelaPoiSettingsStore(this) }
    private val routeOptionsStore by lazy { RouteOptionsStore(this) }
    private val safetyAlertRepository by lazy { AssetSafetyAlertRepository(this) }

    private var destination by mutableStateOf<Destination?>(null)
    private var isResolvingShare by mutableStateOf(false)
    private var shareError by mutableStateOf<String?>(null)
    private var currentLocation by mutableStateOf<LocationSnapshot?>(null)
    private var locationPermissionGranted by mutableStateOf(false)
    private var locationError by mutableStateOf<String?>(null)
    private var routePreview by mutableStateOf<RoutePreview?>(null)
    private var routeAlternatives by mutableStateOf<List<RoutePreview>>(emptyList())
    private var selectedRouteIndex by mutableStateOf(0)
    private var isLoadingRoute by mutableStateOf(false)
    private var routeError by mutableStateOf<String?>(null)
    private var routeOptions by mutableStateOf(RouteOptions())
    private var guidance by mutableStateOf<VelaGuidanceSnapshot?>(null)
    private var isNavigationStarting by mutableStateOf(false)
    private var isSimulationStarting by mutableStateOf(false)
    private var navigationError by mutableStateOf<String?>(null)
    private var simulationError by mutableStateOf<String?>(null)
    private var isSimulationMuted by mutableStateOf(false)
    private var poiResults by mutableStateOf<List<PoiSearchResult>>(emptyList())
    private var isSearchingPois by mutableStateOf(false)
    private var poiError by mutableStateOf<String?>(null)
    private var provinceOptions by mutableStateOf<List<ProvincePoiOption>>(emptyList())
    private var selectedProvinceCode by mutableStateOf<String?>(null)
    private var recentPlaces by mutableStateOf<List<PoiSearchResult>>(emptyList())
    private var savedPlaces by mutableStateOf<List<PoiSearchResult>>(emptyList())
    private var safetyAlerts: List<SafetyAlert> = emptyList()
    private var activeSafetyAlert by mutableStateOf<ActiveSafetyAlert?>(null)

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
        MapLibre.getInstance(this)
        locationController = AndroidLocationController(this)
        locationPermissionGranted = hasLocationPermission()
        refreshStoredPlaces()
        routeOptions = routeOptionsStore.load()
        safetyAlerts = safetyAlertRepository.load()
        provinceOptions = provincePoiRepository.provinces().getOrDefault(emptyList())

        consumeIntent(intent)

        if (locationPermissionGranted) {
            startLocationUpdates()
        }

        setContent {
            val activeDestination = destination
            if (activeDestination == null) {
                HomeScreen(
                    currentLocation = currentLocation,
                    locationPermissionGranted = locationPermissionGranted,
                    isSearchingPois = isSearchingPois || isResolvingShare,
                    poiResults = poiResults,
                    recentPlaces = recentPlaces,
                    savedPlaces = savedPlaces,
                    poiError = poiError ?: shareError,
                    poiDatasetLabel =
                        BuildConfig.POI_DATASET_LABEL.takeIf { it.isNotBlank() },
                    provinceOptions = provinceOptions,
                    selectedProvinceCode = selectedProvinceCode,
                    onProvinceSelected = { code ->
                        selectedProvinceCode = code
                        poiResults = emptyList()
                        poiError = null
                    },
                    onSearchPoi = ::searchPoi,
                    onSelectPoi = ::selectPoi,
                    onSavePoi = ::savePoi,
                    onRequestLocationPermission = ::requestLocationPermission,
                    onGoogleSearch = { query -> GoogleMapsLauncher.openSearch(this, query) }
                )
            } else {
                NavigationShellScreen(
                    destination = activeDestination,
                    currentLocation = currentLocation,
                    locationPermissionGranted = locationPermissionGranted,
                    locationError = locationError,
                    routePreview = routePreview,
                    routeAlternatives = routeAlternatives,
                    selectedRouteIndex = selectedRouteIndex,
                    isLoadingRoute = isLoadingRoute,
                    routeError = routeError,
                    routeOptions = routeOptions,
                    guidance = guidance,
                    isNavigationStarting = isNavigationStarting,
                    isSimulationStarting = isSimulationStarting,
                    navigationError = navigationError,
                    simulationError = simulationError,
                    isSimulationMuted = isSimulationMuted,
                    activeSafetyAlert = activeSafetyAlert,
                    onRequestLocationPermission = ::requestLocationPermission,
                    onCalculateRoute = ::calculateRoute,
                    onSelectRoute = ::selectRoute,
                    onRouteOptionsChanged = ::applyRouteOptions,
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
                            distanceText = null,
                            source = PoiSource.GOOGLE_MAPS
                        )
                    )
                    refreshStoredPlaces()
                    destination = resolved
                    routePreview = null
                    routeAlternatives = emptyList()
                    selectedRouteIndex = 0
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

        val selectedProvince =
            selectedProvinceCode?.let { code ->
                provinceOptions.firstOrNull { it.code == code }
            }
        if (provinceOptions.isNotEmpty() && selectedProvince == null) {
            poiResults = emptyList()
            poiError = "กรุณาเลือกจังหวัดก่อนค้นหา"
            return
        }

        isSearchingPois = true
        poiError = null
        poiResults = emptyList()
        val location = currentLocation

        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                val savedResults = placeStore.searchSaved(cleanKeyword)
                val velaResults =
                    if (selectedProvince != null) {
                        provincePoiRepository.search(
                            provinceCode = selectedProvince.code,
                            keyword = cleanKeyword,
                            latitude = location?.latitude,
                            longitude = location?.longitude
                        )
                    } else {
                        velaPoiRepository.search(
                            keyword = cleanKeyword,
                            latitude = location?.latitude,
                            longitude = location?.longitude
                        )
                    }
                val longdoKeyword =
                    selectedProvince?.let { "${cleanKeyword} ${it.nameTh}" } ?: cleanKeyword
                val longdoResults =
                    LongdoPoiClient(activeLongdoApiKey()).search(
                        keyword = longdoKeyword,
                        latitude = if (selectedProvince == null) location?.latitude else null,
                        longitude = if (selectedProvince == null) location?.longitude else null
                    )
                PoiSearchMerger.merge(
                    savedResults = savedResults,
                    velaResults = velaResults,
                    longdoResults = longdoResults
                )
            }

            poiResults = outcome.results
            poiError =
                when {
                    outcome.results.isEmpty() &&
                        outcome.velaFailure != null &&
                        outcome.longdoFailure != null ->
                        "ค้นหาสถานที่ไม่สำเร็จทั้งฐานข้อมูล Vela และ Longdo"
                    outcome.results.isEmpty() ->
                        "ไม่พบสถานที่ใน Vela POI หรือ Longdo"
                    outcome.velaFailure != null ->
                        "ฐานข้อมูล Vela POI ใช้งานไม่ได้ชั่วคราว แสดงผลลัพธ์ที่เหลือ"
                    outcome.longdoFailure != null ->
                        "Longdo ใช้งานไม่ได้ชั่วคราว แสดงผลลัพธ์ในเครื่องที่พบ"
                    else -> null
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
            source = when (poi.source) {
                PoiSource.USER_PLACE,
                PoiSource.LEGACY_UNKNOWN -> DestinationSource.USER_PLACE
                PoiSource.VELA_CURATED,
                PoiSource.OPENSTREETMAP -> DestinationSource.VELA_POI
                PoiSource.LONGDO -> DestinationSource.LONGDO_POI
                PoiSource.GOOGLE_MAPS -> DestinationSource.GOOGLE_MAPS_LINK
            }
        )
        poiResults = emptyList()
        poiError = null
        routePreview = null
        routeAlternatives = emptyList()
        selectedRouteIndex = 0
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

    private fun savePoi(poi: PoiSearchResult) {
        placeStore.save(poi)
        refreshStoredPlaces()
    }

    private fun refreshStoredPlaces() {
        recentPlaces = placeStore.recentPlaces().map { it.toPoiSearchResult() }
        savedPlaces = placeStore.savedPlaces().map { it.toPoiSearchResult() }
    }

    private fun clearDestination() {
        ferrostarController?.stopNavigation()
        destination = null
        routePreview = null
        routeAlternatives = emptyList()
        selectedRouteIndex = 0
        routeError = null
        guidance = null
        thaiTts?.resetDeduplication()
        simulationError = null
    }

    private fun calculateRoute() {
        val origin = currentLocation
        if (origin == null || !origin.isReadyForRouting()) {
            routeError =
                origin?.let { location -> "GPS ยังไม่พร้อมใช้งาน: ${location.diagnosticsText()}" }
                    ?: "กำลังรอพิกัด GPS ใหม่ กรุณารอสักครู่แล้วลองอีกครั้ง"
            routePreview = null
            routeAlternatives = emptyList()
            selectedRouteIndex = 0
            return
        }
        val target = destination ?: return

        isLoadingRoute = true
        routeError = null

        lifecycleScope.launch {
            routeClient.routes(origin, target, routeOptions)
                .onSuccess { routes ->
                    routeAlternatives = routes
                    selectedRouteIndex = 0
                    routePreview = routes.firstOrNull()
                    if (routes.isEmpty()) {
                        routeError = "ไม่พบเส้นทางที่ใช้งานได้"
                    }
                }
                .onFailure {
                    routeAlternatives = emptyList()
                    selectedRouteIndex = 0
                    routePreview = null
                    routeError = it.message ?: "คำนวณเส้นทางไม่สำเร็จ"
                }
            isLoadingRoute = false
        }
    }

    private fun selectRoute(index: Int) {
        val selected = routeAlternatives.getOrNull(index) ?: return
        selectedRouteIndex = index
        routePreview = selected
        ferrostarController?.stopNavigation()
        guidance = null
        navigationError = null
        simulationError = null
    }

    private fun applyRouteOptions(options: RouteOptions) {
        routeOptions = options
        routeOptionsStore.save(options)

        navigationStateJob?.cancel()
        navigationStateJob = null
        ferrostarController?.shutdown()
        ferrostarController = null
        guidance = null
        navigationError = null
        simulationError = null

        calculateRoute()
    }

    private fun startNavigation() {
        requestNotificationPermissionIfNeeded()
        val origin = currentLocation
        if (origin == null || !origin.isReadyForRouting()) {
            navigationError =
                origin?.let { location -> "GPS ยังไม่พร้อมใช้งานสำหรับนำทาง: ${location.diagnosticsText()}" }
                    ?: "GPS ยังไม่พร้อมใช้งานสำหรับนำทาง กรุณารอพิกัดใหม่ก่อน"
            return
        }
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
                controller.startLiveNavigation(origin, target, selectedRouteIndex)
            }.onFailure {
                navigationError = it.message ?: "เริ่มนำทางด้วย GPS ไม่สำเร็จ"
            }
            isNavigationStarting = false
        }
    }

    private fun startSimulation() {
        requestNotificationPermissionIfNeeded()
        val origin = currentLocation
        if (origin == null || !origin.isReadyForRouting()) {
            simulationError =
                origin?.let { location -> "GPS ยังไม่พร้อมใช้งานสำหรับจำลอง: ${location.diagnosticsText()}" }
                    ?: "GPS ยังไม่พร้อมใช้งานสำหรับตั้งจุดเริ่มจำลอง กรุณารอพิกัดใหม่ก่อน"
            return
        }
        val target = destination ?: return

        isSimulationStarting = true
        simulationError = null

        val controller = ensureNavigationStack() ?: run {
            isSimulationStarting = false
            return
        }

        lifecycleScope.launch {
            runCatching {
                controller.startSimulation(origin, target, selectedRouteIndex)
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
                VelaFerrostarController(
                    this,
                    BuildConfig.VALHALLA_BASE_URL,
                    routeOptions
                ).also {
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
                activeSafetyAlert = SafetyAlertEngine.nearestRelevant(it, safetyAlerts)
                locationError =
                    if (it.isReadyForRouting()) {
                        it.accuracyMeters
                            ?.takeIf { accuracyMeters -> accuracyMeters > 250f }
                            ?.let { accuracyMeters ->
                                "GPS ความแม่นยำต่ำ ±" + accuracyMeters.toInt() + "m แต่ยังนำทางได้"
                            }
                    } else {
                        "พิกัด GPS ไม่ถูกต้อง กรุณารอสัญญาณใหม่"
                    }
            },
            onProviderUnavailable = {
                locationError = "ไม่พบ GPS หรือ Location Provider กรุณาเปิดตำแหน่งของเครื่อง"
            }
        )
    }
}
