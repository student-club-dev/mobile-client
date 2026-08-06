// Qo'ng'iroq data qatlami — `/calls` Socket.IO kanali, uchta REST endpoint va WebRTC
// media qatlamining platformaga xos amalga oshirilishi.
//
// Local kesh YO'Q: qo'ng'iroq tarixi chat lentasidagi `CALL` xabarlarda allaqachon bor
// (`handoff/09-CALLS-REST.md` §4), `GET /v1/calls` esa faqat alohida ekran uchun.
plugins { id("sc.module-data") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.dev.feature.calls.domain)
            // Chaqiruvchining qisqa profilini o'girish uchun.
            implementation(projects.dev.feature.connections.data)
            implementation(libs.napier)
        }
        androidMain.dependencies {
            // Haqiqiy WebRTC — `org.webrtc` API'si (Google'ning rasmiy paketi Maven'da
            // yo'q, bu esa o'sha kodning saqlanadigan nashri).
            implementation(libs.webrtc.android)
            implementation(libs.kotlinx.coroutines.android)
            // Qo'ng'iroq bildirishnomalari (`NotificationCompat`) — old plan xizmati va
            // kiruvchi qo'ng'iroqning to'liq ekranli oynasi.
            implementation(libs.androidx.core.ktx)
            // `CallActionReceiver` — bildirishnomadagi "Javob berish"/"Rad etish". Tizim
            // qabul qiluvchini O'ZI yaratadi, ya'ni unga bog'liqlikni konstruktor orqali
            // berib bo'lmaydi: jonli `CallController` Koin'dan olinadi.
            implementation(libs.koin.core)
        }
    }
}
