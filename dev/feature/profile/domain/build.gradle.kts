plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework { baseName = "profileDomain" }
    }

    sourceSets {
        commonMain.dependencies {
            // Faqat `Resource` va coroutines — domen qatlami hech qanday framework'ga bog'lanmaydi.
            api(projects.dev.core.common)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

android {
    namespace = "dev.feature.profile.domain"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
