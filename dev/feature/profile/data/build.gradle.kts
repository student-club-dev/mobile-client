// Data qatlami — umumiy infratuzilma `sc.module-data` dan. Profil to'liq backend REST'ida
// (`/v1/profile/me`), sessiya uid'i esa `TokenStore` da — Firebase bu yerda ishlatilmaydi.
plugins {
    id("sc.module-data")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.dev.feature.profile.domain)
        }
    }
}
