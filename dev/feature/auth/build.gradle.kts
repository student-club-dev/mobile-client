plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework { baseName = "auth" }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.dev.core.domain)
            implementation(projects.dev.core.designsystem)
            implementation(projects.dev.core.common)
            implementation(projects.dev.core.database)

            // Ro'yxatdan o'tish oqimi profilni saqlaydi (SaveProfile/HasProfile),
            // MainShell esa Profil ekranlarini ochadi.
            // Eslatma: MainShell keyinchalik alohida `feature:main` moduliga ko'chirilsa,
            // bu yerdagi presentation bog'liqligi yo'qoladi.
            api(projects.dev.feature.profile.domain)
            implementation(projects.dev.feature.profile.presentation)

            // Local sessiya keshi (offline + avtomatik kirish)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)

            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)

            // GitLive Firebase — email/parol, parolni tiklash, Firestore profil (backendsiz)
            implementation(libs.gitlive.firebase.auth)
            implementation(libs.gitlive.firebase.firestore)
            implementation(libs.gitlive.firebase.functions)
        }

        androidMain.dependencies {
            // Firebase Auth (Google credential exchange + Phone OTP)
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.auth)
            // Google Sign-In orqali ID token olish uchun Credential Manager
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.playServicesAuth)
            implementation(libs.googleid)
            // Task<T>.await() uchun
            implementation(libs.kotlinx.coroutines.playServices)
            implementation(libs.androidx.activity.compose)
            // Telegram login — Custom Tabs (web oqim)
            implementation(libs.androidx.browser)
            // Biometrik login (F1) — Face ID / barmoq izi
            implementation(libs.androidx.biometric)
            implementation(libs.androidx.fragment)
        }
    }
}

android {
    namespace = "dev.feature.auth"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
