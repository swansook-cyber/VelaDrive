plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "net.velalab.veladrive"
    compileSdk = 37

    defaultConfig {
        applicationId = "net.velalab.veladrive"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Navigation stack candidate validated during research.
    // Enable after technical spike confirms all transitive versions together.
    // implementation("com.stadiamaps.ferrostar:core:0.57.0")
    // implementation("com.stadiamaps.ferrostar:ui-maplibre:0.57.0")
    // implementation("com.stadiamaps.ferrostar:ui-compose:0.57.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
