plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework { baseName = "di" }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.dev.core.data)
            api(projects.dev.feature.auth)
            // Profil feature'ining Koin moduli (`profileModule`) shu yerda ulanadi.
            api(projects.dev.feature.profile.presentation)
            // listingsModule() — e'lonlar (Listing) feature'i + "Siz uchun" feed/xarita.
            api(projects.dev.feature.listings.presentation)
            // jobsModule() — ishlar feature'i.
            api(projects.dev.feature.jobs.presentation)
            api(projects.dev.feature.students.presentation)
            api(projects.dev.feature.notifications.presentation)
            api(projects.dev.feature.clubs.presentation)
            api(projects.dev.feature.settings.presentation)
            api(projects.dev.feature.university.presentation)
            api(projects.dev.feature.ads.presentation)
            // connectionsModule() — "Do'stlar" (Connections). chatModule() undan foydalanadi.
            api(projects.dev.feature.connections.presentation)
            api(projects.dev.feature.chat.presentation)
            api(projects.dev.feature.home.presentation)
            api(libs.koin.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(libs.sqldelight.android.driver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
    }
}

android {
    namespace = "dev.core.di"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
