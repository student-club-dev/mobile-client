// Bog'lanishlar data qatlami — generatsiya qilingan `ConnectionsApi` ustidagi repository.
// Local kesh yo'q (holat ikki tomonlama va tez o'zgaradi), shuning uchun database ishlatilmaydi.
plugins { id("sc.module-data") }
kotlin { sourceSets { commonMain.dependencies { api(projects.dev.feature.connections.domain) } } }
