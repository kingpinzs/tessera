// Phase 05 QA tooling, never shipped. Two APKs come out of this module:
//  - assembleDebug: the IME fixture app, a plain-Views "any app" whose screen mirrors the focused
//    field's raw text, selectionStart/selectionEnd, length, inputType and last editor action into
//    TextViews, because `uiautomator dump` has no selection attributes and masks password text
//    (phase-05 Decisions, review F2-M9).
//  - assembleDebugAndroidTest: the gesture driver, one instrumentation test (Gesture#run) that
//    executes exactly one op per `am instrument` invocation (E4 swipe, E9 drag, E12 timed scripts,
//    and a window dump that reaches the IME window). See docs/plan/qa/phase-05/TOOLING.md.
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "app.tileshell.qa.imefixture"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.tileshell.qa.imefixture"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // The gesture-script parser (src/main/.../gesture) is plain Kotlin so its JVM unit test runs here.
    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.uiautomator)
}
