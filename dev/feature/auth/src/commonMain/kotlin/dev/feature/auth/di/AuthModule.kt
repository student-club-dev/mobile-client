package dev.feature.auth.di

import dev.core.domain.repository.AuthRepository
import dev.core.network.NetworkConfig
import dev.core.network.generated.api.AuthStudentApi
import dev.feature.auth.data.ApiAuthRepository
import dev.feature.auth.presentation.flow.AuthFlowViewModel
import dev.feature.auth.presentation.main.DiscountsViewModel
import io.ktor.client.HttpClient
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val authFeatureModule = module {
    // Generatsiya qilingan auth klienti — ilovaning umumiy Ktor klienti bilan
    // (Bearer token + avtomatik refresh o'sha klientda o'rnatilgan).
    single { AuthStudentApi(baseUrl = get<NetworkConfig>().baseUrl, httpClient = get<HttpClient>()) }

    // Sessiya: backend tokenlari (TokenStore) + local kesh (SQLDelight) + profil.
    single<AuthRepository> {
        ApiAuthRepository(
            api = get(),
            tokenStore = get(),
            database = get(),
            httpClient = get(),
            connectivity = get(),
            profileRepository = get(),
            // Push tokenini sessiyaga bog'lash (`notificationsModule` da bog'langan).
            pushRegistrar = get(),
        )
    }

    viewModel {
        AuthFlowViewModel(
            loginUseCase = get(),
            loginWithGoogleUseCase = get(),
            registerUseCase = get(),
            completeRegistrationUseCase = get(),
            cancelRegistrationUseCase = get(),
            requestPhoneOtpUseCase = get(),
            verifyPhoneOtpUseCase = get(),
            forgotPasswordUseCase = get(),
            resetPasswordUseCase = get(),
            observeCurrentUserUseCase = get(),
            saveProfileUseCase = get(),
            settingsRepository = get(),
            universityRepository = get(),
        )
    }
    viewModelOf(::DiscountsViewModel)
}
