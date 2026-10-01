// Phase 16 QA tooling, never shipped: a caller for People's ACTION_PICK (the Trust line (c), trust review B-F4).
// It holds NO permission. It asks the shell's People for one contact or phone number, logs exactly what came back, and
// then tries to reach more than the one row it was granted. The lead's TRUST row drives it and reads its log.
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "app.tileshell.qa.pickprobe"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.tileshell.qa.pickprobe"
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
