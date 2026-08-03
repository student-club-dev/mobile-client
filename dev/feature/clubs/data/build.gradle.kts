plugins { id("sc.module-data") }
kotlin { sourceSets { commonMain.dependencies {
    api(projects.dev.feature.clubs.domain)
    // Koin moduli (`clubsModule`) shu yerda: klublarda presentation moduli yo'q — ro'yxat
    // ham, qo'shilish ham "Xabarlar" ekranining "Klublar" papkasida.
    api(libs.koin.core)
} } }
