// Auth — birlashgan feature moduli (UI + oqim). `sc.module-ui` Compose/Koin/Lifecycle/
// uikit/common'ni beradi; serialization plugin qo'shimcha yoqiladi. Qolgan bog'liqliklar
// (navigatsiya, sessiya keshi, backend klienti, platforma auth) shu yerda.
plugins {
    id("sc.module-ui")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.dev.core.domain)
            implementation(projects.dev.core.database)
            // Backend auth (`/v1/auth/student/*`) — generatsiya qilingan klient `:dev:core:network`
            // orqali keladi (u `:dev:api-client` ni `api(...)` bilan eksport qiladi).
            implementation(projects.dev.core.network)

            // Ro'yxatdan o'tish oqimi profilni saqlaydi; MainShell Profil/e'lon ekranlarini ochadi.
            api(projects.dev.feature.profile.domain)
            implementation(projects.dev.feature.profile.presentation)
            implementation(projects.dev.feature.listings.presentation)
            // Ishlar feature'i — Home domendan Job o'qiydi, StudentShell JobsScreen'ni ochadi.
            api(projects.dev.feature.jobs.domain)
            implementation(projects.dev.feature.jobs.presentation)
            // "Do'stlar" (Connections) ekrani — StudentShell uni ochadi.
            implementation(projects.dev.feature.connections.presentation)
            implementation(projects.dev.feature.notifications.domain)
            implementation(projects.dev.feature.notifications.presentation)
            implementation(projects.dev.feature.settings.domain)
            implementation(projects.dev.feature.settings.presentation)
            api(projects.dev.feature.university.domain)
            implementation(projects.dev.feature.university.presentation)
            // E'lonlar feature'i — StudentShell PostAdScreen'ni ochadi.
            implementation(projects.dev.feature.ads.presentation)
            // Chat feature'i — StudentShell ChatScreen'ni ochadi.
            implementation(projects.dev.feature.chat.presentation)
            // Home agregator ekrani — StudentShell HomeScreen'ni ochadi.
            implementation(projects.dev.feature.home.presentation)

            // Local sessiya keshi (offline + avtomatik kirish)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)

            // "Siz uchun" feed kartalaridagi e'lon rasmlari (`DiscountOffer.imageUrl`).
            implementation(libs.coil.compose)

            implementation(libs.androidx.navigation.compose)
            implementation(projects.dev.core.navigation)
            implementation(libs.kotlinx.datetime)
            implementation(libs.ktor.client.core)
            // JWT payload'ini o'qish (JwtClaims) uchun
            implementation(libs.kotlinx.serialization.json)
        }

        androidMain.dependencies {
            // Google Sign-In — Credential Manager + Google ID (backendga ID token beradi)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.playServicesAuth)
            implementation(libs.googleid)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.fragment)
        }
    }
}
