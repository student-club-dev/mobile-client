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
            api(projects.dev.feature.students.domain)
            implementation(projects.dev.feature.students.presentation)
            implementation(projects.dev.feature.notifications.domain)
            implementation(projects.dev.feature.notifications.presentation)
            implementation(projects.dev.feature.clubs.domain)
            implementation(projects.dev.feature.clubs.presentation)
            implementation(projects.dev.feature.settings.domain)
            implementation(projects.dev.feature.settings.presentation)
            api(projects.dev.feature.university.domain)
            implementation(projects.dev.feature.university.presentation)
            // E'lonlar feature'i — StudentShell PostAdScreen'ni ochadi.
            implementation(projects.dev.feature.ads.presentation)
            // Chat feature'i — FirestoreChatRealtimeSource chat.domain'ni implement qiladi;
            // StudentShell ChatScreen'ni ochadi.
            api(projects.dev.feature.chat.domain)
            implementation(projects.dev.feature.chat.presentation)
            // Home agregator ekrani — StudentShell HomeScreen'ni ochadi.
            implementation(projects.dev.feature.home.presentation)
            // Biznesmen ekranlari alohida modulda (sof UI) — auth uni ishlatadi.
            implementation(projects.dev.feature.business)

            // Local sessiya keshi (offline + avtomatik kirish)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)

            implementation(libs.androidx.navigation.compose)
            implementation(projects.dev.core.navigation)
            implementation(libs.kotlinx.datetime)
            implementation(libs.ktor.client.core)
            // JWT payload'ini o'qish (JwtClaims) uchun
            implementation(libs.kotlinx.serialization.json)

            // GitLive Firebase — faqat CHAT real-time (Firestore) uchun.
            // Autentifikatsiya Firebase'da EMAS: u backend tokenlariga tayanadi.
            implementation(libs.gitlive.firebase.auth)
            implementation(libs.gitlive.firebase.firestore)
        }

        androidMain.dependencies {
            // GitLive Firestore Android tomonda Firebase SDK'sini talab qiladi (chat).
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.auth)
            // Google Sign-In — Credential Manager + Google ID (backendga ID token beradi)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.playServicesAuth)
            implementation(libs.googleid)
            implementation(libs.androidx.activity.compose)
            // Biometrik login (F1) — Face ID / barmoq izi
            implementation(libs.androidx.biometric)
            implementation(libs.androidx.fragment)
        }
    }
}
