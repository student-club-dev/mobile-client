package dev.core.di

import dev.feature.auth.di.authFeatureModule
import dev.feature.listings.presentation.di.listingsModule
import dev.feature.jobs.presentation.di.jobsModule
import dev.feature.students.presentation.di.studentsModule
import dev.feature.notifications.presentation.di.notificationsModule
import dev.feature.clubs.presentation.di.clubsModule
import dev.feature.settings.presentation.di.settingsModule
import dev.feature.university.presentation.di.universityModule
import dev.feature.ads.presentation.di.adsModule
import dev.feature.chat.presentation.di.chatModule
import dev.feature.home.presentation.di.homeModule
import dev.feature.profile.presentation.di.profileModule
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
    listingsModule(REMOTE_SYNC_ENABLED) +
    jobsModule(REMOTE_SYNC_ENABLED) +
    studentsModule(REMOTE_SYNC_ENABLED) +
    notificationsModule() +
    clubsModule() +
    settingsModule() +
    universityModule(REMOTE_SYNC_ENABLED) +
    adsModule(REMOTE_SYNC_ENABLED) +
    chatModule(REMOTE_SYNC_ENABLED) +
    homeModule()

/** Umumiy Koin start (androidApp shu yerga androidContext qo'shadi). */
fun initKoin(appDeclaration: KoinAppDeclaration = {}): KoinApplication =
    startKoin {
        appDeclaration()
        modules(appModules())
    }
