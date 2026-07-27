plugins { id("sc.module-ui") }
kotlin { sourceSets { commonMain.dependencies {
    api(projects.dev.feature.settings.domain)
    implementation(projects.dev.feature.settings.data)
    // Sozlamalar ekrani profil kartasi + chiqish uchun ProfileViewModel'ni ishlatadi.
    implementation(projects.dev.feature.profile.presentation)
    implementation(projects.dev.core.database)
    // Viloyat filtri (feed geo) — RegionRepository core:domain'da.
    implementation(projects.dev.core.domain)
} } }
