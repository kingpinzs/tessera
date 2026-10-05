// Phase 17 QA tooling, never shipped: the capture-intent caller (phase doc "Fixtures"; E9, and E5's ACTION_SEND
// receiver). It asks the shell's Camera for a photo or a video the ways another app can — every request names the
// shell (`setPackage("app.tileshell")`, Android's route to a third-party camera since Android 11) — and logs exactly
// what came back under the logcat tag TileShellQa. It holds no permission and has no dependency.
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "app.tileshell.testclient.qacapture"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.tileshell.testclient.qacapture"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
