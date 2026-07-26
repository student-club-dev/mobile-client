import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.google.services)
}

/**
 * Maxfiy sozlamalar — `local.properties` dan (u `.gitignore` da, repoga tushmaydi).
 * CI'da fayl bo'lmaydi, shuning uchun muhit o'zgaruvchisiga tushamiz.
 *
 * Namuna kalitlar `local.properties.example` da.
 */
fun secret(key: String): String {
    val file = rootProject.file("local.properties")
    val fromFile = if (file.exists()) {
        Properties().apply { file.inputStream().use(::load) }.getProperty(key)
    } else {
        null
    }
    return fromFile ?: System.getenv(key) ?: ""
}

android {
    namespace = "uz.studentclub.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "uz.studentclub.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        // Google Sign-In — Google Cloud'dagi **Web** (server) OAuth client ID.
        // `GoogleSignIn.android.kt` uni `google_web_client_id` resursi orqali o'qiydi va
        // olingan ID token backendga (`POST /v1/auth/student/oauth/google`) yuboriladi.
        // Bo'sh bo'lsa kirish tugmasi aniq xato beradi (jimgina ishlamay qolmaydi).
        resValue("string", "google_web_client_id", secret("GOOGLE_WEB_CLIENT_ID"))
    }

    buildFeatures { compose = true }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(projects.dev.shared)
    implementation(projects.dev.core.di)
    implementation(projects.dev.core.uikit)
    // OkHttpInterceptors (Chucker'ni ro'yxatga qo'shish uchun) shu modulda.
    implementation(projects.dev.core.network)

    // Chucker — Debug HTTP inspektori (bildirishnoma + alohida ekran).
    // release'da no-op: hech narsa qilmaydi, kod o'zgarmaydi.
    debugImplementation(libs.chucker)
    releaseImplementation(libs.chucker.noop)

    // Firebase (Google + Phone auth) — google-services.json orqali sozlanadi
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)

    implementation(libs.androidx.core.ktx)
    // Android 12+ tizim splash ekrani (`installSplashScreen()` MainActivity'da).
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    // MainActivity FragmentActivity bo'lishi uchun (biometrik BiometricPrompt talab qiladi)
    implementation(libs.androidx.fragment)
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    // Compose (android) — Compose MP artifacts work on android target
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(compose.uiTooling)
    implementation(compose.preview)
}
