plugins { id("sc.module-ui") }
kotlin { sourceSets { commonMain.dependencies {
    api(projects.dev.feature.chat.domain)
    implementation(projects.dev.feature.chat.data)
    // Koin moduli `ChatRepositoryImpl(database, ...)` va `KtorChatRemoteDataSource(httpClient)` ni quradi.
    implementation(projects.dev.core.database)
    implementation(projects.dev.core.network)
    // send() uchun Clock/vaqt yorlig'i.
    implementation(libs.kotlinx.datetime)
} } }
