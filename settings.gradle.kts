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
// R4, the helper feasibility spike: phone-only and standalone (PLAN.md R4).
include(":r4probe")
include(":testapps:tileclient-a", ":testapps:tileclient-b", ":testapps:tileclient-b2")
