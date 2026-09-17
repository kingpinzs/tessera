// Test APK for the Live Tile API. QA row E16: shares a uid with tileclient-b through android:sharedUserId,
// so calls from either package answer error "shared-uid".
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "app.tileshell.testclient.b2"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.tileshell.testclient.b2"
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
