// Ishlar feature'ining presentation qatlami — `sc.module-ui` (Compose, Lifecycle, Koin).
plugins {
    id("sc.module-ui")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.dev.feature.jobs.domain)
            // Koin moduli implementatsiyalarni bog'laydi (repository + remote).
            implementation(projects.dev.feature.jobs.data)
            // Koin moduli `JobRepositoryImpl(database, ...)` va `KtorJobRemoteDataSource(httpClient)` ni quradi.
            implementation(projects.dev.core.database)
            implementation(projects.dev.core.network)
        }
    }
}
