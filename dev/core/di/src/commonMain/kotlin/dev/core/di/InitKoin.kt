package dev.core.di

import dev.feature.auth.di.authFeatureModule
import dev.feature.listings.presentation.di.listingsModule
import dev.feature.jobs.presentation.di.jobsModule
import dev.feature.notifications.presentation.di.notificationsModule
import dev.feature.clubs.data.di.clubsModule
import dev.feature.settings.presentation.di.settingsModule
import dev.feature.university.presentation.di.universityModule
import dev.feature.ads.presentation.di.adsModule
import dev.feature.calls.presentation.di.callsModule
import dev.feature.calls.presentation.di.callsPlatformModule
import dev.feature.chat.presentation.di.chatModule
import dev.feature.connections.presentation.di.connectionsModule
import dev.feature.home.presentation.di.homeModule
import dev.feature.profile.presentation.di.profileModule
import dev.feature.stories.presentation.di.storiesModule
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

/**
 * Ilovaning barcha Koin modullari. Har yangi feature o'z modulini shu yerga qo'shadi.
 *
 * Har modul masofaviy manbani bayroqqa qarab tanlaydi: profil doim REST'dan
 * (`/v1/profile/me` mavjud), qolganlari esa hozircha local bazadan
 * ([REMOTE_SYNC_ENABLED] — ular uchun endpoint hali yo'q).
 */
fun appModules() = coreModules() +
    authFeatureModule +
    profileModule() +
    // E'lonlar — `/v1/student-listings*` allaqachon bor, shuning uchun o'z bayrog'i bilan.
    listingsModule(STUDENT_LISTINGS_REMOTE_ENABLED) +
    jobsModule(REMOTE_SYNC_ENABLED) +
    // Bildirishnomalar ro'yxati — o'z bayrog'i bilan (`GET /v1/notifications` kutilmoqda).
    notificationsModule(NOTIFICATIONS_REMOTE_ENABLED) +
    clubsModule() +
    settingsModule() +
    universityModule(REMOTE_SYNC_ENABLED) +
    adsModule(REMOTE_SYNC_ENABLED) +
    // Bog'lanishlar va chat — to'liq backendda (bayroqsiz): `Connections` + `Chat` bo'limlari.
    connectionsModule() +
    chatModule() +
    // Qo'ng'iroq — chatning ustida turadi (bog'lanmagan odamga qo'ng'iroq qilib bo'lmaydi).
    // Media qatlami platformaga xos, shuning uchun ikkita modul.
    callsModule() +
    callsPlatformModule() +
    // Story — chat bilan bir xil eshik (faqat bog'langanlar ko'radi).
    storiesModule() +
    homeModule()

/**
 * Umumiy Koin start (androidApp shu yerga androidContext qo'shadi).
 *
 * Bu yerda Napier ham yoqiladi. Busiz ilovaning HAMMA `Napier.*` chaqiruvi jimgina yo'qolardi
 * (Napier antilog o'rnatilmasa hech qayerga yozmaydi) — ya'ni logcat'da diagnostika yo'q edi.
 */
fun initKoin(appDeclaration: KoinAppDeclaration = {}): KoinApplication {
    // `takeLogarithm` — avvalgi antilog'larni tozalaydi, shunda qayta chaqirilsa (test,
    // process restart) har satr ikki marta chiqmaydi.
    Napier.takeLogarithm()
    Napier.base(DebugAntilog())
    return startKoin {
        appDeclaration()
        modules(appModules())
    }
}
