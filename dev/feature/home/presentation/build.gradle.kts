// Home — sof agregator presentation moduli. O'z domeni/data'si yo'q; boshqa feature'lar
// domenlaridan (jobs, students, clubs, notifications, university, profile) va core:domain
// (discounts) repository/model'larini o'qib, bosh ekranни quradi.
plugins { id("sc.module-ui") }
kotlin { sourceSets { commonMain.dependencies {
    implementation(projects.dev.feature.jobs.domain)
    implementation(projects.dev.feature.students.domain)
    implementation(projects.dev.feature.clubs.domain)
    implementation(projects.dev.feature.notifications.domain)
    implementation(projects.dev.feature.university.domain)
    implementation(projects.dev.feature.profile.domain)
    // DiscountRepository/DiscountCategory/DiscountOffer + ObserveCurrentUserUseCase.
    implementation(projects.dev.core.domain)
} } }
