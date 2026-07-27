// Chat domen qatlami.
// `Connections` ga bog'liqlik ATAYLAB: suhbatdosh — o'sha `StudentSummary`, va bog'lanmagan
// talabalar bilan chat umuman ochilmaydi (403 NOT_CONNECTED).
plugins { id("sc.kmp-library") }
kotlin { sourceSets { commonMain.dependencies {
    api(projects.dev.feature.connections.domain)
    api(libs.kotlinx.datetime)
} } }
