// Ishlar feature'ining data qatlami — `sc.module-data` (database, network, ktor, serialization).
plugins {
    id("sc.module-data")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.dev.feature.jobs.domain)
        }
    }
}
