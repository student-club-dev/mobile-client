package dev.feature.auth.di

import dev.core.domain.repository.AuthRepository
import dev.feature.auth.data.FirebaseAuthRepository
import dev.feature.auth.presentation.flow.AuthFlowViewModel
import dev.feature.auth.presentation.main.ChatViewModel
import dev.feature.auth.presentation.main.DiscountsViewModel
import dev.feature.auth.presentation.main.HomeViewModel
import dev.feature.auth.presentation.main.JobsViewModel
import dev.feature.auth.presentation.main.PostAdViewModel
import dev.feature.auth.presentation.main.ProfileViewModel
import dev.feature.auth.presentation.main.StudentsViewModel
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
    // StudentClubsDatabase (SQLDelight) local sessiya keshi uchun uzatiladi.
    single<AuthRepository> { FirebaseAuthRepository(get()) }

    viewModel {
        AuthFlowViewModel(
            loginUseCase = get(),
            registerUseCase = get(),
            sendPasswordResetUseCase = get(),
            requestEmailSignupUseCase = get(),
            confirmEmailSignupUseCase = get(),
            saveProfileUseCase = get(),
            syncExternalUserUseCase = get(),
            hasProfileUseCase = get(),
            observeCurrentUserUseCase = get(),
            useEmailCode = USE_EMAIL_CODE,
        )
    }
    viewModelOf(::HomeViewModel)
    viewModelOf(::JobsViewModel)
    viewModelOf(::StudentsViewModel)
    viewModelOf(::DiscountsViewModel)
    viewModelOf(::PostAdViewModel)
    viewModelOf(::ProfileViewModel)
    viewModelOf(::ChatViewModel)
}
