import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing material stays outside git (keystore.properties is git-ignored). Phone installs are always
// release-signed and non-debuggable (phase 01 Decisions: "Build types").
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "app.tileshell"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.tileshell"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        // Phase 03: the speech runtime ships native code. arm64-v8a is the S25 Ultra, x86_64 is the AVD;
        // the other two ABIs in the AAR would add ~58 MB for hardware this plan never targets.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    // Phase 03 Decisions "Model variants and budget": the ASR and TTS models are read straight out of the
    // APK by the native asset loader, so they must not be deflated. espeak-ng-data.zip is read by the
    // extractor as a stream, so it is stored too rather than double-compressed.
    androidResources {
        noCompress += listOf("onnx", "bin", "zip")
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    // Unit tests exercise the layout rules (LayoutOps), which log to the shell's diagnostics ring buffer;

    // android.os.SystemClock is not mocked in a JVM test, so unstubbed framework calls return defaults.

    testOptions { unitTests.isReturnDefaultValues = true }


    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            isDebuggable = false
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // Phase 03: the speech engines live in their own process (Decisions "Model storage and process"),
        // so the shell reaches them across a Binder.
        aidl = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.activity.compose)
    // Phase 03: one runtime for ASR and TTS (Decisions, R2 §5.2). The AAR is a pinned GitHub release
    // asset fetched by tools/fetch-speech.sh, not a repo dependency: k2-fsa publishes no Maven artifact.
    implementation(files("libs/sherpa-onnx-1.13.8.aar"))
    testImplementation(libs.junit)
}
