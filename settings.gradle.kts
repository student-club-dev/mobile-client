rootProject.name = "StudentClubs"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// App entry points
include(":androidApp")

// Core modules
include(":dev:core:common")
include(":dev:core:designsystem")
include(":dev:core:network")
include(":dev:core:database")
include(":dev:core:domain")
include(":dev:core:data")
include(":dev:core:di")

// Feature modules
include(":dev:feature:auth")

// Profil feature'i — qatlamlarga ajratilgan (domain / data / presentation).
// Keyingi feature'lar (Jobs, Chat, Discounts...) aynan shu shakldan nusxa oladi.
include(":dev:feature:profile:domain")
include(":dev:feature:profile:data")
include(":dev:feature:profile:presentation")

// Chegirmalar feature'i — biznes egasi e'lon qo'yadi, talaba ko'radi.
// Spetsifikatsiya: DISCOUNTS_BUSINESS_API.md + openapi/student-clubs.json.
include(":dev:feature:discounts:domain")
include(":dev:feature:discounts:data")
include(":dev:feature:discounts:presentation")

// iOS framework + Compose App() aggregator
include(":dev:shared")
