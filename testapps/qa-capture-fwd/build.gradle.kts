// Phase 17 QA tooling, never shipped: the SECOND app of E9's forwarded-result leg (the trust fixes' leg (e)). It starts
// qa-capture's go-between for a result with a URI of its OWN provider; the go-between forwards the result
// (FLAG_ACTIVITY_FORWARD_RESULT), so the shell's capture page sees this app as the "caller" although the go-between
// started it. It holds no permission and has no dependency; it logs under the logcat tag TileShellQa.
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "app.tileshell.testclient.qacapturefwd"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.tileshell.testclient.qacapturefwd"
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
