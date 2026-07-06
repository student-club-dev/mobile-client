package dev.feature.auth.di

import dev.core.domain.repository.AuthRepository
import dev.feature.auth.data.FirebaseAuthRepository
import dev.feature.auth.presentation.AuthViewModel
import dev.feature.auth.presentation.flow.AuthFlowViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Email ro'yxatдан o'tish 6 xonali kod (Cloud Function) bilanmi?
 * - `false` → native (deploy TALAB QILMAYDI): akkaunt darrov yaratiladi.
 * - `true`  → Cloud Function (`firebase deploy` + Blaze kerak): kod tasdiqлангач yaratiladi.
 * Deploy qilганdан keyin shu yerни `true` qiling.
 */
private const val USE_EMAIL_CODE = false

val authFeatureModule = module {
    // Backendsiz Firebase (GitLive) — email/parol, ro'yxat, reset, Firestore profil.
    single<AuthRepository> { FirebaseAuthRepository() }

    viewModelOf(::AuthViewModel)
    viewModel {
        AuthFlowViewModel(
            loginUseCase = get(),
            registerUseCase = get(),
            sendPasswordResetUseCase = get(),
            requestEmailSignupUseCase = get(),
            confirmEmailSignupUseCase = get(),
            saveProfileUseCase = get(),
            syncExternalUserUseCase = get(),
            useEmailCode = USE_EMAIL_CODE,
        )
    }
}
