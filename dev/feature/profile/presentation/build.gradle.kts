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
        it.binaries.framework { baseName = "profilePresentation" }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.dev.feature.profile.domain)
            // Koin moduli implementatsiyalarni bog'laydi (repository + remote manbalar).
            implementation(projects.dev.feature.profile.data)

            // Profil ekranidagi "Mening e'lonlarim / Saqlangan / Arizalar" bo'limlari
            // hamon umumiy domen repository'laridan o'qiydi.
            implementation(projects.dev.core.domain)
            implementation(projects.dev.core.common)
            implementation(projects.dev.core.designsystem)
            implementation(projects.dev.core.network)
            // Koin moduli `ProfileRepositoryImpl(database, ...)` ni quradi.
            implementation(projects.dev.core.database)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)

            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.kotlinx.coroutines.core)

            // Avatarni URL'dan ko'rsatish (Coil 3 — multiplatform).
            implementation(libs.coil.compose)
        }

        androidMain.dependencies {
            // Galereyadan rasm tanlash — ActivityResultContracts.PickVisualMedia.
            implementation(libs.androidx.activity.compose)
        }
    }
}

android {
    namespace = "dev.feature.profile.presentation"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
