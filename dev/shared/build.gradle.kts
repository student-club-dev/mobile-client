plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "Shared"
            isStatic = true
            export(projects.dev.core.di)
            // Swift tomonidagi social auth bridge (IosSocialAuthBridge/Delegate) ko'rinsin
            export(projects.dev.feature.auth)
            // Push ko'prigi (`IosPushBridge`) — APNs tokeni va bosilgan bildirishnoma
            // Swift'dan shu orqali kiradi. `implementation` bo'lsa Obj-C sarlavhasiga
            // tushmasdi (u tranzitiv emas), shuning uchun alohida `export`.
            export(projects.dev.feature.notifications.data)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.dev.core.di)
            implementation(projects.dev.core.uikit)
            implementation(projects.dev.core.data)
            // Mavzu (ThemeMode) + SettingsRepository — App() ildizida ishlatiladi.
            implementation(projects.dev.feature.settings.domain)
            implementation(projects.dev.feature.university.domain)
            implementation(projects.dev.core.domain)
            api(projects.dev.feature.auth)
            // `export(...)` uchun bog'liqlik `api` bo'lishi shart.
            api(projects.dev.feature.notifications.data)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)

            // Rasm yuklash (avatar) — ilova darajasidagi ImageLoader sozlamasi App.kt'da.
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
            // Rasm havolasi tuzatilganini logga yozish (`MediaUrlMapper`) — buzuq havolani
            // qurilmada tez topish uchun.
            implementation(libs.napier)
            implementation(libs.ktor.client.core)
        }
    }
}

android {
    namespace = "dev.shared"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
