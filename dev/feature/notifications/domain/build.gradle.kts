// Bildirishnomalar domen qatlami.
// `kotlinx-datetime` — `AppNotification.createdAt` (`Instant`) uchun; vaqt yorlig'i
// tayyor matn emas, har chizishda hisoblanadi (`api` — UI ham o'sha turni ko'radi).
plugins { id("sc.kmp-library") }
kotlin { sourceSets { commonMain.dependencies {
    api(libs.kotlinx.datetime)
} } }
