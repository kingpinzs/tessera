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
include(":testapps:tileclient-a", ":testapps:tileclient-b", ":testapps:tileclient-b2")
