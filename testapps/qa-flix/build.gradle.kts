// Phase 17 QA tooling, never shipped: "QA-Flix", a stand-in streaming app for the "Watch on <service>" hand-off
// (build task 11; row E21). It answers the two forms the services table gives the fixture service —
// https://qa-flix.test/title/<id> and https://qa-flix.test/search?q=<title> — and shows the URI it was opened with.
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "app.tileshell.testclient.qaflix"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.tileshell.testclient.qaflix"
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
