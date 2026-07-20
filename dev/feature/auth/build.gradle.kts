// Auth — birlashgan feature moduli (UI + oqim). `sc.module-ui` Compose/Koin/Lifecycle/
// designsystem/common'ni beradi; serialization plugin qo'shimcha yoqiladi. Qolgan bog'liqliklar
// (navigatsiya, sessiya keshi, Firebase, platforma auth) shu yerda.
plugins {
    id("sc.module-ui")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.dev.core.domain)
            implementation(projects.dev.core.database)

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
            implementation(libs.kotlinx.datetime)

            // GitLive Firebase — email/parol, parolni tiklash, Firestore profil (backendsiz)
            implementation(libs.gitlive.firebase.auth)
            implementation(libs.gitlive.firebase.firestore)
            implementation(libs.gitlive.firebase.functions)
        }

        androidMain.dependencies {
            // Firebase Auth (Google credential exchange + Phone OTP)
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.auth)
            // Google Sign-In orqali ID token olish uchun Credential Manager
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.playServicesAuth)
            implementation(libs.googleid)
            // Task<T>.await() uchun
            implementation(libs.kotlinx.coroutines.playServices)
            implementation(libs.androidx.activity.compose)
            // Telegram login — Custom Tabs (web oqim)
            implementation(libs.androidx.browser)
            // Biometrik login (F1) — Face ID / barmoq izi
            implementation(libs.androidx.biometric)
            implementation(libs.androidx.fragment)
        }
    }
}
