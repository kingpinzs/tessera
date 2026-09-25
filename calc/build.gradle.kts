// Phase 15: Calculator's engine, converter and date calculation (docs/plan/phase-15-inbox-clock-calculator-recorder.md,
// Decisions "Calculator engine"). Pure Kotlin with no Android API in it, so its rules are proven by JVM unit tests, and
// both the Calculator app and Tess's arithmetic (T15-2) call the one engine.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.tileshell.calc"
    compileSdk = 36

    defaultConfig {
        minSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation(libs.junit)
}
