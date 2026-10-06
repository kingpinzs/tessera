// Phase 17 QA tooling, never shipped: the VIEW sender of the trust row TRUST_PHOTOS (review/2026-10-05-phase17-trust-
// fixes.md, legs (h)-(l)). It asks the shell's exported ViewerActivity to show an image the ways another app can and
// logs whether its own start threw, under the logcat tag TileShellQa. It DECLARES READ_MEDIA_IMAGES and is never
// installed with -g: without `pm grant` it is "an app with no media permission", with it "an app holding
// READ_MEDIA_IMAGES". (qa-capture holds no permission by design, so the sender is not added there; the Movies & TV rows have their own sender, testapps/qa-view, for videos.) No dependency.
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "app.tileshell.testclient.qaphotoview"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.tileshell.testclient.qaphotoview"
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
