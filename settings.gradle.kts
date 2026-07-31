rootProject.name = "StudentClub"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    // Convention plugin'lar (yangi modul = bitta qator) shu included build'da yashaydi.
    includeBuild("build-logic")
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

// API-generatsiya — `iym-native-business` joylashuvining moslashtirilgan varianti.
// `:dev:api-client-generator` spec'ni ushlaydi va generatsiya tasklarini beradi
// (cleanSwagger / openApiGenerate / generateAllApi); `:dev:api-client` — generatsiya
// qilingan klient (paket `dev.core.network.generated`).
include(":dev:api-client-generator")
include(":dev:api-client")

// Core modules
include(":dev:core:common")
include(":dev:core:uikit")
// Xavfsiz navigatsiya primitivlari (navigateSafe/popSafe/encodeArg) — barcha shell'lar uchun.
include(":dev:core:navigation")
include(":dev:core:network")
include(":dev:core:database")
include(":dev:core:domain")
include(":dev:core:data")
include(":dev:core:di")

// Feature modules
include(":dev:feature:auth")

// Biznesmen tomoni — alohida modul (sof UI). Auth shuni ishlatadi.

// Profil feature'i — qatlamlarga ajratilgan (domain / data / presentation).
// Keyingi feature'lar (Jobs, Chat, Discounts...) aynan shu shakldan nusxa oladi.
include(":dev:feature:profile:domain")
include(":dev:feature:profile:data")
include(":dev:feature:profile:presentation")

// E'lonlar (Listing) feature'i — biznes egasi e'lon qo'yadi, talaba "Siz uchun" feed'ida ko'radi.
// Spetsifikatsiya: DISCOUNTS_BUSINESS_API.md + openapi/student-clubs.json.
include(":dev:feature:listings:domain")
include(":dev:feature:listings:data")
include(":dev:feature:listings:presentation")
include(":dev:feature:jobs:domain")
include(":dev:feature:jobs:data")
include(":dev:feature:jobs:presentation")
include(":dev:feature:students:domain")
include(":dev:feature:students:data")
include(":dev:feature:students:presentation")
include(":dev:feature:notifications:domain")
include(":dev:feature:notifications:data")
include(":dev:feature:notifications:presentation")
include(":dev:feature:clubs:domain")
include(":dev:feature:clubs:data")
include(":dev:feature:clubs:presentation")
include(":dev:feature:settings:domain")
include(":dev:feature:settings:data")
include(":dev:feature:settings:presentation")
include(":dev:feature:university:domain")
include(":dev:feature:university:data")
include(":dev:feature:university:presentation")
include(":dev:feature:ads:domain")
include(":dev:feature:ads:data")
include(":dev:feature:ads:presentation")
// Bog'lanishlar (Connections) — talaba qidirish, so'rov, blok, shikoyat.
// Chat'ning "eshigi": bog'lanmaganlar yozisha olmaydi. Handoff: handoff/connections.md.
include(":dev:feature:connections:domain")
include(":dev:feature:connections:data")
include(":dev:feature:connections:presentation")
// Chat — REST (`/v1/conversations…`) + Socket.IO (`/chat`). Handoff: handoff/chat.md.
include(":dev:feature:chat:domain")
include(":dev:feature:chat:data")
include(":dev:feature:chat:presentation")
// Story — 24 soatlik lavhalar. Eshik chat bilan bir xil: faqat bog'langanlar ko'radi.
// Handoff: handoff/07-STORIES.md.
include(":dev:feature:stories:domain")
include(":dev:feature:stories:data")
include(":dev:feature:stories:presentation")
// Home — sof agregator ekran (o'z domeni/data'si yo'q; boshqa feature domenlaridan o'qiydi).
include(":dev:feature:home:presentation")

// iOS framework + Compose App() aggregator
include(":dev:shared")
