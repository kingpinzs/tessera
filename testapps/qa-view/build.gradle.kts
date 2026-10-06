// Phase 17 QA tooling, never shipped: "QA View", another app that starts the shell's player with a VIEW (the trust
// fixes' device legs for C-M4, row TRUST_VIDEO). It declares READ_MEDIA_VIDEO but holds it only when a row grants it
// (`pm grant`), so the same app is the caller with no media permission and the caller with one.
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "app.tileshell.testclient.qaview"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.tileshell.testclient.qaview"
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
