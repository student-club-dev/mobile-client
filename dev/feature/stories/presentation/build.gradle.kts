plugins { id("sc.module-ui") }
kotlin { sourceSets { commonMain.dependencies {
    api(projects.dev.feature.stories.domain)
    implementation(projects.dev.feature.stories.data)
    // Koin moduli `StoriesApi` ni quradi.
    implementation(projects.dev.core.network)
    // Arxivdagi postning sanasi ("12 iyul") — `Instant` → local sana.
    implementation(libs.kotlinx.datetime)
} } }
