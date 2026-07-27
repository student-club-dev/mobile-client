// Home — sof agregator presentation moduli. O'z domeni/data'si yo'q; boshqa feature'lar
// domenlaridan (listings, students, clubs, notifications, university, profile) va core:domain
// (discounts) repository/model'larini o'qib, bosh ekranни quradi.
plugins { id("sc.module-ui") }
kotlin { sourceSets { commonMain.dependencies {
    // Ish e'lonlari endi Listing modeli (feature:listings), eski feature:jobs emas.
    implementation(projects.dev.feature.listings.domain)
    implementation(projects.dev.feature.students.domain)
    implementation(projects.dev.feature.clubs.domain)
    implementation(projects.dev.feature.notifications.domain)
    implementation(projects.dev.feature.university.domain)
    implementation(projects.dev.feature.profile.domain)
    // DiscountRepository/DiscountCategory/DiscountOffer + ObserveCurrentUserUseCase.
    implementation(projects.dev.core.domain)
    // Yordam e'lonlari kartochkasidagi muddat yorlig'i ("Ertaga 12:00").
    implementation(libs.kotlinx.datetime)
    // Sarlavhadagi profil rasmi (avatarUrl) — Coil ilovaning umumiy Ktor klientidan yuklaydi.
    implementation(libs.coil.compose)
} } }
