plugins { id("sc.module-ui") }
kotlin { sourceSets { commonMain.dependencies {
    api(projects.dev.feature.stories.domain)
    implementation(projects.dev.feature.stories.data)
    // Koin moduli `StoriesApi` ni quradi.
    implementation(projects.dev.core.network)
} } }
