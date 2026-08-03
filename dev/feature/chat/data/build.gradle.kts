// Chat data qatlami — REST (generatsiya qilingan `ChatApi`) + Socket.IO (`dev.core.network.ws`),
// ikkalasi ham SQLDelight keshiga yozadi.
plugins { id("sc.module-data") }
kotlin { sourceSets { commonMain.dependencies {
    api(projects.dev.feature.chat.domain)
} } }
