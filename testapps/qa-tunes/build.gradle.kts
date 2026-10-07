// Phase 20 QA tooling, never shipped: "QA Tunes", a stand-in music service for the "Listen on <service>" hand-off
// (build task 11; row A6). It declares CATEGORY_APP_MUSIC, answers the search form the DEBUG-only row of
// MusicServicesTable carries — https://qa-tunes.test/search?q=<title artist> — and logs the intent it was opened with
// (action, data URI, the query and focus extras) under the logcat tag TileShellQa. It plays nothing and holds no permission.
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "app.tileshell.testclient.qatunes"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.tileshell.testclient.qatunes"
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
