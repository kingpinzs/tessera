// Phase 17 QA tooling, never shipped: the capture-intent caller (phase doc "Fixtures"; E9, and E5's ACTION_SEND
// receiver). It asks the shell's Camera for a photo or a video the ways another app can — every request names the
// shell (`setPackage("app.tileshell")`, Android's route to a third-party camera since Android 11) — and logs exactly
// what came back under the logcat tag TileShellQa. It holds no permission; its one dependency (Media3's session
// client, for MediaProbeActivity) is at the end of this file.
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

// Ledger L18-1's device probe (MediaProbeActivity): another app's Media3 controller on the shell's exported music
// session. The one dependency this app has; it is this test app's alone and changes nothing the shell ships.
dependencies {
    implementation(libs.media3.session)
    // Offline builds: the versions the shell's own build resolves (and the Gradle cache therefore holds), in place of
    // the older ones Media3 names when it is this small app's only dependency.
    constraints {
        implementation("androidx.annotation:annotation-experimental:1.4.1")
        implementation("androidx.profileinstaller:profileinstaller:1.4.0")
        implementation("androidx.core:core:1.18.0")
        implementation("androidx.annotation:annotation:1.9.1")
    }
}
