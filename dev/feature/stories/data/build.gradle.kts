// Story data qatlami — generatsiya qilingan `StoriesApi` + `MediaUploader` (yuklash).
// Local kesh yo'q: hikoya 24 soat yashaydi va istalgan payt muddati o'tadi.
plugins { id("sc.module-data") }
kotlin { sourceSets { commonMain.dependencies {
    api(projects.dev.feature.stories.domain)
    // Muallif qisqa profilini o'girish uchun `connections` mapperlaridan foydalanamiz.
    implementation(projects.dev.feature.connections.data)
} } }
