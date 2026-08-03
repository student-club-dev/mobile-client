plugins { id("sc.module-ui") }
kotlin { sourceSets { commonMain.dependencies {
    api(projects.dev.feature.notifications.domain)
    implementation(projects.dev.feature.notifications.data)
    implementation(projects.dev.core.database)
    // Koin moduli `NotificationsApi` (push qurilma tokeni) ni quradi.
    implementation(projects.dev.core.network)
} } }
