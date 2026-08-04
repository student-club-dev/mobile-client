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

    /**
     * Loyihaning **o'z** debug kaliti (`androidApp/debug.keystore`, repoda — `.gitignore`
     * dagi `!debug.keystore` istisnosi).
     *
     * Standart `~/.android/debug.keystore` har bir mashinada boshqacha SHA-1 beradi, ya'ni
     * har bir ishlab chiquvchi uchun Google Cloud'da alohida Android OAuth client kerak
     * bo'lardi. Repodagi umumiy kalit bilan SHA-1 hamma joyda bitta:
     *   DC:18:55:73:19:58:73:89:F7:89:D1:79:3E:E6:16:4A:B9:39:63:30
     */
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }

        /**
         * Release kaliti — `local.properties` dan (repoga tushmaydi):
         *   RELEASE_STORE_FILE=/Users/.../studentclub-release.jks
         *   RELEASE_STORE_PASSWORD=...
         *   RELEASE_KEY_ALIAS=...
         *   RELEASE_KEY_PASSWORD=...
         *
         * Kalit sozlanmagan bo'lsa release build DEBUG kaliti bilan imzolanadi — shunda
         * `Build → Build APK(s)` release variantda ham darhol o'rnatiladigan APK beradi.
         * Play Store debug sertifikatini QABUL QILMAYDI, ya'ni sozlashni unutib qolib
         * ketish xavfi yo'q: yuklashda aniq xato chiqadi.
         */
        create("release") {
            val storePath = secret("RELEASE_STORE_FILE")
            if (storePath.isNotBlank() && file(storePath).exists()) {
                storeFile = file(storePath)
                storePassword = secret("RELEASE_STORE_PASSWORD")
                keyAlias = secret("RELEASE_KEY_ALIAS")
                keyPassword = secret("RELEASE_KEY_PASSWORD")
            } else {
                logger.lifecycle(
                    "⚠️  RELEASE_STORE_FILE sozlanmagan — release build debug kaliti bilan " +
                        "imzolanadi (sinov uchun yaroqli, Play Store uchun emas)."
                )
                storeFile = file("debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // R8 — o'lchamning eng katta leveri: ishlatilmagan kod olib tashlanadi va
            // qolgani qisqartiriladi. Qoidalar `proguard-rules.pro` da (reflection/JNI
            // ishlatadigan joylar: WebRTC, serialization, enum'lar).
            isMinifyEnabled = true
            // Kod qisqargandan keyin unga bog'lanmagan resurslarni ham olib tashlaydi.
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    /**
     * ABI bo'yicha bo'lish — o'lchamning ikkinchi katta leveri.
     *
     * WebRTC (`libjingle_peerconnection_so.so`) har bir ABI uchun 6–12 MB joy oladi va
     * yagona ("universal") APK'da to'rttasi ham yotadi (~40 MB). Qurilmaga esa faqat
     * BITTASI kerak.
     *
     * Play Store'ga **AAB** yuborilsa bu avtomatik bo'ladi va bu blok umuman ta'sir
     * qilmaydi (`bundleRelease` o'zi ABI/til/DPI bo'yicha bo'ladi). Bu yerdagi sozlama —
     * APK'ni to'g'ridan-to'g'ri tarqatish uchun (Telegram, sayt va h.k.).
     *
     * `isUniversalApk = true` — hammasi bir joyda turgan zaxira nusxa ham chiqadi:
     * ABI'si noma'lum qurilmaga shuni berish mumkin.
     */
    splits {
        abi {
            isEnable = true
            reset()
            // Haqiqiy qurilmalar: arm64 — deyarli hammasi, armeabi-v7a — eski 32-bitliklar.
            // x86_64 — emulyator (release'ni emulyatorda sinash uchun kerak).
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
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

    // Firebase — google-services.json orqali sozlanadi. Auth uchun EMAS (u backendda):
    // faqat **push** (FCM) uchun — `StudentClubMessagingService` va qurilma tokeni.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    // Push tokenini bog'lash (`PushTokenBridge`) shu modulda.
    implementation(projects.dev.feature.notifications.data)

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
    // Tooling/Preview faqat Android Studio uchun — release'da kerak emas (bir necha MB dex).
    debugImplementation(compose.uiTooling)
    debugImplementation(compose.preview)
}
