package dev.core.di

import dev.feature.auth.di.authFeatureModule
import dev.feature.profile.presentation.di.profileModule
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

/**
 * Ilovaning barcha Koin modullari. Har yangi feature o'z modulini shu yerga qo'shadi.
 *
 * `profileModule` masofaviy manbani [REMOTE_SYNC_ENABLED] ga qarab tanlaydi:
 * REST `/v1/profile/me` (backend bor) yoki Firestore `users/{uid}` (backendsiz).
 */
fun appModules() = coreModules() + authFeatureModule + profileModule(REMOTE_SYNC_ENABLED)

/** Umumiy Koin start (androidApp shu yerga androidContext qo'shadi). */
fun initKoin(appDeclaration: KoinAppDeclaration = {}): KoinApplication =
    startKoin {
        appDeclaration()
        modules(appModules())
    }
