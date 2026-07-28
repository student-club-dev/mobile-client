// Bog'lanishlar ("Do'stlar") ekrani — qidiruv / so'rovlar / bog'langanlar + blok va shikoyat.
plugins { id("sc.module-ui") }
kotlin { sourceSets { commonMain.dependencies {
    api(projects.dev.feature.connections.domain)
    implementation(projects.dev.feature.connections.data)
    // "Universitetim" filtri va qatordagi monogramma: profildagi `universityId` +
    // local universitet katalogi (backend qisqa profilda universitet NOMINI qaytarmaydi).
    implementation(projects.dev.feature.profile.domain)
    implementation(projects.dev.feature.university.domain)
    // Koin moduli `ConnectionsRepositoryImpl(ConnectionsApi, ...)` ni quradi.
    implementation(projects.dev.core.network)
} } }
