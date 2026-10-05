buildscript {
    dependencies {
        // AGP 9 uses built-in Kotlin. Pin a newer KGP on the build classpath
        // because Ferrostar 0.57.0 is published with Kotlin 2.3.20 metadata.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.20")
    }
}

plugins {
    id("com.android.application") version "9.1.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20" apply false
}
