plugins { id("sc.module-ui") }
kotlin { sourceSets { commonMain.dependencies {
    api(projects.dev.feature.notifications.domain)
    implementation(projects.dev.feature.notifications.data)
    implementation(projects.dev.core.database)
} } }
