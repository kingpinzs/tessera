pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // R4's on-device pairing: Kadb's SPAKE2 dependency (com.github.Flyfish233:spake2-java) is published only on
        // JitPack. Scoped to that one group, so nothing else in the build can resolve from here.
        maven("https://jitpack.io") { content { includeGroup("com.github.Flyfish233") } }
    }
}
rootProject.name = "tessera"
include(":app")
include(":livetile-client")
// Phase 15: Calculator's engine, converter and date calculation — pure Kotlin, JVM-tested, used by the app and Tess.
include(":calc")
// R4, the helper feasibility spike: phone-only and standalone (PLAN.md R4).
include(":r4probe")
include(":testapps:tileclient-a", ":testapps:tileclient-b", ":testapps:tileclient-b2")
// Phase 05 QA tooling, never shipped: the IME fixture app (mirrors the focused field's raw text and
// selection into TextViews) and, in its androidTest, the UiAutomator gesture driver.
include(":testapps:ime-fixture")
// Phase 16 QA tooling, never shipped: a permission-less caller for People's ACTION_PICK (the TRUST row's probe).
include(":testapps:pick-probe")
// Phase 17 QA tooling, never shipped: the capture-intent caller (E9) and ACTION_SEND receiver (E5).
include(":testapps:qa-capture")
include(":testapps:qa-flix") // Phase 17 QA tooling, never shipped: the stand-in streaming app of the "Watch on" row (E21).
// Phase 17 QA tooling, never shipped: another app that starts the shell's player with a VIEW (row TRUST_VIDEO, C-M4's legs).
include(":testapps:qa-view")
