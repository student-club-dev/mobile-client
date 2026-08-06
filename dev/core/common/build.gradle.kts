plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework { baseName = "common" }
    }

    sourceSets {
        commonMain.dependencies {
            // `api` — `AppMessageBus.messages` (SharedFlow) modul chegarasidan tashqarida,
            // UI qatlamida o'qiladi.
            api(libs.kotlinx.coroutines.core)
            api(libs.napier)
        }
        // Telefon/summa qoliplari (`format/Formats.kt`) — butun ilova shularga tayanadi.
        commonTest.dependencies {
            implementation("org.jetbrains.kotlin:kotlin-test")
        }
    }
}

android {
    namespace = "dev.core.common"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
