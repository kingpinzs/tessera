// R4, the helper feasibility spike (PLAN.md R4: "phone-only, standalone").
//
// A separate APK on purpose. R4 runs BEFORE phase 04's interview and must not leave anything in the
// shell: no helper rows, no pages, no permission the shell would then be carrying for a probe. It is
// installed, read, and uninstalled.
//
// No Compose and no dependency of any kind: the probe has to build and run even if it is the first
// thing tried on a phone that turns out to be hostile to everything else in this plan.
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "app.tessera.r4probe"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.tessera.r4probe"
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
