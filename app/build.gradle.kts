plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val krabiPoiDataset =
    rootProject.file("tools/osm_poi_importer/generated/krabi/vela_pois_krabi.json")
val krabiPoiAssetsDirectory =
    layout.buildDirectory.dir("generated/krabiPoiAssets").get().asFile
val prepareKrabiPoiAsset by tasks.registering(Sync::class) {
    group = "build"
    description = "Copies the audited Krabi OSM POI dataset into app assets."
    from(krabiPoiDataset)
    into(krabiPoiAssetsDirectory)
    rename { "vela_pois_krabi.json" }
    doFirst {
        require(krabiPoiDataset.isFile) {
            "Generate the Krabi OSM POI dataset before building the app"
        }
    }
}

android {
    namespace = "net.velalab.veladrive"
    compileSdk = 37

    defaultConfig {
        applicationId = "net.velalab.veladrive"
        minSdk = 25
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        val valhallaBaseUrl = providers.gradleProperty("VELA_VALHALLA_BASE_URL")
            .orElse("https://valhalla1.openstreetmap.de")
            .get()
        buildConfigField("String", "VALHALLA_BASE_URL", "\"$valhallaBaseUrl\"")

        val longdoMapApiKey = providers.gradleProperty("LONGDO_MAP_API_KEY")
            .orElse("")
            .get()
        buildConfigField("String", "LONGDO_MAP_API_KEY", "\"$longdoMapApiKey\"")
        buildConfigField("String", "VELA_POI_ASSET_NAME", "\"vela_pois_krabi.json\"")
        buildConfigField("boolean", "KRABI_OSM_PILOT", "false")
        buildConfigField("String", "POI_DATASET_LABEL", "\"Krabi OSM · 2,702 POIs\"")
    }

    buildTypes {
        create("pilot") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".pilot"
            versionNameSuffix = "-krabi-osm-pilot"
            matchingFallbacks += listOf("debug")
            buildConfigField(
                "String",
                "VELA_POI_ASSET_NAME",
                "\"vela_pois_krabi.json\""
            )
            buildConfigField("boolean", "KRABI_OSM_PILOT", "true")
            buildConfigField("String", "POI_DATASET_LABEL", "\"Krabi OSM Pilot\"")
        }
    }

    sourceSets {
        getByName("main").assets.directories.add(krabiPoiAssetsDirectory.absolutePath)
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
}

tasks.named("preBuild").configure {
    dependsOn(prepareKrabiPoiAsset)
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation(platform("androidx.compose:compose-bom:2026.02.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Technical Spike #1: navigation + MapLibre compatibility.
    implementation("com.stadiamaps.ferrostar:core:0.57.0")
    implementation("com.stadiamaps.ferrostar:ui-maplibre:0.57.0") {
        // maplibre-compose 0.13.0 defaults to MapLibre Native's Vulkan runtime.
        // Several older/OEM Android Vulkan drivers crash as soon as the first
        // map surface is created. Keep the same MapLibre version and API while
        // selecting its supported OpenGL renderer for broad device stability.
        exclude(group = "org.maplibre.gl", module = "android-sdk")
    }
    implementation("org.maplibre.gl:android-sdk-opengl:13.0.2")
    implementation("com.stadiamaps.ferrostar:ui-compose:0.57.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation(platform("com.squareup.okhttp3:okhttp-bom:5.3.2"))
    implementation("com.squareup.okhttp3:okhttp")
    implementation("org.conscrypt:conscrypt-android:2.5.3")

    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
