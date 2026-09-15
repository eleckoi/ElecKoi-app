pluginManagement {
    includeBuild("build-logic")
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

rootProject.name = "ElecKoiAndroid"
include(":foundation:design")
include(":foundation:diagnostics")
include(":foundation:network")
include(":foundation:paging")
include(":foundation:serialization")
include(":foundation:storage")
include(":engine")
include(":sdk:author")
include(":compatibility:mvu")
include(":feature:studio")
include(":feature:appfont")
include(":feature:characters")
include(":feature:conversation")
include(":feature:modelconfig")
include(":feature:preferences")
include(":feature:settings")
include(":app")
