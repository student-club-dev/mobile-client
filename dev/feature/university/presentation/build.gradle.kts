plugins { id("sc.module-ui") }
kotlin { sourceSets { commonMain.dependencies {
    api(projects.dev.feature.university.domain)
    implementation(projects.dev.feature.university.data)
    // "Universitetimdagi talabalar" — `GET /v1/students` (Connections bo'limi).
    implementation(projects.dev.feature.connections.domain)
    implementation(projects.dev.feature.profile.domain)
    // "Mening universitetim" xarita/e'lon komponentlari + DiscountRepository/DiscountOffer.
    implementation(projects.dev.feature.listings.presentation)
    implementation(projects.dev.core.domain)
    implementation(projects.dev.core.database)
    implementation(projects.dev.core.network)
} } }
