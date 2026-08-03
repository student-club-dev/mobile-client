// Story domeni — muallif qisqa profili (`StudentSummary`) `connections` domenidan keladi,
// shuning uchun uni `api` bilan eksport qilamiz.
plugins { id("sc.kmp-library") }
kotlin { sourceSets { commonMain.dependencies {
    api(projects.dev.feature.connections.domain)
} } }
