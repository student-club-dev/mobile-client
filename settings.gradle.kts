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

// iOS framework + Compose App() aggregator
include(":dev:shared")
