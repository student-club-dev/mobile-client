// Chat presentation qatlami.
plugins { id("sc.module-ui") }
kotlin { sourceSets { commonMain.dependencies {
    api(projects.dev.feature.chat.domain)
    implementation(projects.dev.feature.chat.data)
    // Bloklash / shikoyat / bog'lanishni uzish — chat ichidan chaqiriladi.
    implementation(projects.dev.feature.connections.data)
    // Suhbatdosh profili — UMUMIY varaq (story lentasidan ham shu ochiladi).
    implementation(projects.dev.feature.connections.presentation)
    // O'sha profildagi «Postlar» bo'limi — suhbatdoshning faol lavhalari.
    implementation(projects.dev.feature.stories.presentation)
    // Suhbatdosh profilidagi universitet nomi: backend faqat `universityId` beradi
    // (katalogi yo'q), nomni local katalogdan topamiz.
    implementation(projects.dev.feature.university.domain)
    // Klublar lentasi — suhbatlar ro'yxatining tepasida (avval bosh ekranda edi).
    implementation(projects.dev.feature.clubs.domain)
    // Koin moduli `ChatApi`, WS klienti va `TokenStore` ni quradi.
    implementation(projects.dev.core.database)
    implementation(projects.dev.core.network)
    // Vaqt yorliqlari (ChatFormat) va domen modellaridagi `Instant`.
    implementation(libs.kotlinx.datetime)
    // Chatdagi rasmlar (`AsyncImage`) — yuklangan media havolasi bo'yicha ko'rsatiladi.
    implementation(libs.coil.compose)
} } }
