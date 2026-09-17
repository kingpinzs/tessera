// Test APK for the Live Tile API. QA rows E15 (tile, queue, badge, expiry through the client library)
// and E17 (setNumber(7) notification, legacy badge broadcast).
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "app.tileshell.testclient.a"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.tileshell.testclient.a"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            // VerbActivity and TestImageProvider are shared by the three test APKs.
            kotlin.directories.add(rootProject.file("testapps/common/src/main/kotlin").path)
        }
    }
}

dependencies {
    implementation(project(":livetile-client"))
}
