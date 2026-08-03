// Home — sof agregator presentation moduli. O'z domeni/data'si yo'q; boshqa feature'lar
// domenlaridan (listings, connections, notifications, university, profile) va core:domain
// (discounts) repository/model'larini o'qib, bosh ekranni quradi.
plugins { id("sc.module-ui") }
kotlin { sourceSets { commonMain.dependencies {
    // Ish e'lonlari endi Listing modeli (feature:listings), eski feature:jobs emas.
    implementation(projects.dev.feature.listings.domain)
    // "Bog'lanishlarim" bo'limi — haqiqiy `GET /v1/connections`. Eski feature:students
    // (seed'dan yuradigan soxta ro'yxat) bu yerda ishlatilmaydi.
    implementation(projects.dev.feature.connections.domain)
    implementation(projects.dev.feature.notifications.domain)
    implementation(projects.dev.feature.university.domain)
    implementation(projects.dev.feature.profile.domain)
    // Story lentasi (bosh ekranning eng tepasidagi qator).
    implementation(projects.dev.feature.stories.presentation)
    // Lavha muallifining profili — umumiy varaq (connections) + uning chatdan keladigan
    // bo'limlari (media/fayl/havola). Bosh ekran ikkalasini birlashtiradigan yagona joy.
    implementation(projects.dev.feature.connections.presentation)
    implementation(projects.dev.feature.chat.presentation)
    // DiscountRepository/DiscountCategory/DiscountOffer + ObserveCurrentUserUseCase.
    implementation(projects.dev.core.domain)
    // Yordam e'lonlari kartochkasidagi muddat yorlig'i ("Ertaga 12:00").
    implementation(libs.kotlinx.datetime)
    // Sarlavhadagi profil rasmi (avatarUrl) — Coil ilovaning umumiy Ktor klientidan yuklaydi.
    implementation(libs.coil.compose)
} } }
