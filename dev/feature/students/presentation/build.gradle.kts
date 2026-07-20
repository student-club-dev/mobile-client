// Studentlar presentation qatlami.
plugins { id("sc.module-ui") }
kotlin { sourceSets { commonMain.dependencies {
    api(projects.dev.feature.students.domain)
    implementation(projects.dev.feature.students.data)
    // "Mening universitetim bo'yicha" saralash profildan universitetni oladi.
    implementation(projects.dev.feature.profile.domain)
    implementation(projects.dev.core.database)
    implementation(projects.dev.core.network)
} } }
