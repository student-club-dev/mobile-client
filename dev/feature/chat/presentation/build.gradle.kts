// Chat presentation qatlami.
plugins { id("sc.module-ui") }
kotlin { sourceSets { commonMain.dependencies {
    api(projects.dev.feature.chat.domain)
    implementation(projects.dev.feature.chat.data)
    // Bloklash / shikoyat / bog'lanishni uzish — chat ichidan chaqiriladi.
    implementation(projects.dev.feature.connections.data)
    // Koin moduli `ChatApi`, WS klienti va `TokenStore` ni quradi.
    implementation(projects.dev.core.database)
    implementation(projects.dev.core.network)
    // Vaqt yorliqlari (ChatFormat) va domen modellaridagi `Instant`.
    implementation(libs.kotlinx.datetime)
    // Chatdagi rasmlar (`AsyncImage`) — yuklangan media havolasi bo'yicha ko'rsatiladi.
    implementation(libs.coil.compose)
} } }
