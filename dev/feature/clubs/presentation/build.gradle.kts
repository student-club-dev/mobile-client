plugins { id("sc.module-ui") }
kotlin { sourceSets { commonMain.dependencies {
    api(projects.dev.feature.clubs.domain)
    implementation(projects.dev.feature.clubs.data)
    implementation(projects.dev.core.database)
    implementation(projects.dev.core.network)
} } }
