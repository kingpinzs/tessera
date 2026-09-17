// Live Tile API client (R5 §4b "Client library"): zero dependencies beyond the Android SDK and the Kotlin stdlib.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.tileshell.livetile.client"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
