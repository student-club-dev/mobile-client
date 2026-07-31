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
            implementation(compose.materialIconsExtended)
            // Foydalanuvchi avatarlari (`ScAvatar`) — serverdagi rasm havolasi bo'yicha.
            implementation(libs.coil.compose)
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
