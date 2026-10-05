plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
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
    implementation("com.stadiamaps.ferrostar:ui-maplibre:0.57.0")
    implementation("com.stadiamaps.ferrostar:ui-compose:0.57.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation(platform("com.squareup.okhttp3:okhttp-bom:5.3.2"))
    implementation("com.squareup.okhttp3:okhttp")
    implementation("org.conscrypt:conscrypt-android:2.5.3")

    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
