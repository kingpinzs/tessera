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
        // Every CI build is an UPDATE of the one before it, so the version has to move: Android treats a
        // build whose versionCode never changes as a reinstall of the same version, and will refuse a
        // DOWNGRADE outright. The CI run number is the only monotonic thing available; a local build
        // stays at 1, which is what it has always been.
        versionCode = (System.getenv("TESSERA_VERSION_CODE") ?: "1").toInt()
        versionName = "0.1.0"
        // Phase 03: the speech runtime ships native code. arm64-v8a is the S25 Ultra, x86_64 is the AVD;
        // the other two ABIs in the AAR would add ~58 MB for hardware this plan never targets.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        // Phase 17 (build task 6b, BS-1): the panorama stitcher, libopencv_pano.so, is BUILT for arm64-v8a only — the
        // filter above still packages the other libraries for both ABIs, so the emulator's APK simply holds no
        // lib/x86_64/libopencv* and hides Panorama with its reason (E7, E17). The C++ runtime is linked statically,
        // so no libc++_shared.so is added to the APK.
        externalNativeBuild {
            cmake {
                abiFilters("arm64-v8a")
                arguments("-DANDROID_STL=c++_static")
            }
        }
    }

    // Phase 03 Decisions "Model variants and budget": the ASR and TTS models are read straight out of the
    // APK by the native asset loader, so they must not be deflated. espeak-ng-data.zip is read by the
    // extractor as a stream, so it is stored too rather than double-compressed.
    androidResources {
        // "model" is the BPE vocabulary's extension: sherpa-onnx may read it through a file descriptor
        // rather than the asset stream, and a deflated asset has no usable fd.
        noCompress += listOf("onnx", "bin", "zip", "model")
    }

    // Phase 17 (BS-1): the one native library this project compiles. Its input, the OpenCV Android SDK, is fetched by
    // tools/fetch-opencv.sh (git-ignored, pinned by sha256), as the speech runtime is; the versions are the ones the
    // build-start trial link used.
    ndkVersion = "30.0.16248370"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
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
        // Phase 17 (r3 D14): BuildConfig.DEBUG gates the QA base-URL prefs (qa_catalogue_base, qa_wikidata_base,
        // qa_server_base), so a release build cannot be redirected. It carries no other field: no key or token is
        // ever a build input (the owner's ruling Q-17-1 (a)).
        buildConfig = true
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
    // Phase 10: playback and the media session the tile rule keys on (build tasks 3 and 4).
    implementation(libs.media3.exoplayer)
    // Phase 20 (r3 D10): HLS radio stations — the same library's own module, not a second engine.
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.session)
    // Phase 17: video trim in Photos' editor (Media3 Transformer), and the Camera app (CameraX).
    implementation(libs.media3.transformer)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.video)
    implementation(libs.camerax.view)
    implementation(libs.camerax.extensions)
    // Phase 15: Calculator's engine, converter and date calculation (pure Kotlin, JVM-tested), also Tess's arithmetic.
    implementation(project(":calc"))
    testImplementation(libs.junit)
}
