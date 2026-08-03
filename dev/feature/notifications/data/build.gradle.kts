// Bildirishnomalar data qatlami — local ro'yxat (SQLDelight) + **push** (`/v1/devices`).
plugins { id("sc.module-data") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.dev.feature.notifications.domain)
        }
        androidMain.dependencies {
            // FCM tokeni. Versiya BOM'dan; `google-services.json` androidApp'da.
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.messaging)
            // `Task.await()` — FirebaseMessaging.getToken() ni suspend qilish uchun.
            implementation(libs.kotlinx.coroutines.playServices)
        }
    }
}
