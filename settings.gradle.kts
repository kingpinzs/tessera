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
