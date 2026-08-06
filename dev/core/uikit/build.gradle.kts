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
        it.binaries.framework { baseName = "uikit" }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.dev.core.common)
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            api(compose.components.resources)
            // DIQQAT: `compose.materialIconsExtended` ataylab qo'shilmagan — u ~12 000 ta
            // ikonka klassini dex'ga olib kiradi (~25 MB). Loyihada barcha ikonkalar
            // `AppIcons`/`ScIcons` (o'z `ImageVector`larimiz) orqali beriladi.
            // Foydalanuvchi avatarlari (`ScAvatar`) — serverdagi rasm havolasi bo'yicha.
            implementation(libs.coil.compose)
            // Tanlangan video keshdagi **fayl** bo'lib yuriladi (`media/MediaFiles`) —
            // xotiraga o'qilmaydi. Yo'lni yaratish/o'chirish shu kutubxona orqali.
            api(libs.kotlinx.io.core)
        }

        androidMain.dependencies {
            // Galereyadan rasm tanlash (`media/ImagePicker`) — ActivityResultContracts.PickVisualMedia.
            // Xarita uchun joylashuv ruxsatini so'rash (`map/UserLocation`) ham shu API'da.
            implementation(libs.androidx.activity.compose)
            // ContextCompat.checkSelfPermission — `map/UserLocation`.
            implementation(libs.androidx.core.ktx)
            // Video pleyer (`media/VideoPlayer`) — ExoPlayer + tayyor `PlayerView`.
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.ui)
            // Video siqish (`media/VideoCompressor`) — yuborishdan oldin 1080p H.264 ga.
            implementation(libs.androidx.media3.transformer)
            implementation(libs.androidx.media3.effect)
            // Ilova ichidagi kamera ekrani (`media/CameraScreen`) — hikoya qo'yishda.
            // `camera-view` `PreviewView` + `LifecycleCameraController` ni beradi,
            // `camera-camera2` — haqiqiy qurilma implementatsiyasi, `camera-video` —
            // yozish (`Recorder`).
            implementation(libs.androidx.camera.view)
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.video)
            // `LocalLifecycleOwner` — ekran fonga ketganda videoni to'xtatish uchun.
            // Compose'dan tranzitiv kelishiga tayanmaymiz: bu API lifecycle 2.8+ da,
            // `androidx.lifecycle.compose` paketiga ko'chgan.
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
    }
}

android {
    namespace = "dev.core.uikit"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// `Res` klassini public qilamiz — boshqa modullar (shared) `listings.json` ni o'qiy oladi.
compose.resources {
    publicResClass = true
    packageOfResClass = "dev.core.uikit.generated.resources"
}
