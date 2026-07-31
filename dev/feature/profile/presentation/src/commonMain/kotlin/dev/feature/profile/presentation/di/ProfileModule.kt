package dev.feature.profile.presentation.di

import dev.core.network.NetworkConfig
import dev.core.network.generated.api.ProfileApi
import dev.feature.profile.data.remote.ApiProfileRemoteDataSource
import dev.feature.profile.data.remote.ProfileRemoteDataSource
import dev.feature.profile.data.repository.ProfileRepositoryImpl
import dev.feature.profile.domain.repository.ProfileRepository
import dev.feature.profile.domain.usecase.AddProfilePhotoUseCase
import dev.feature.profile.domain.usecase.DeleteProfilePhotoUseCase
import dev.feature.profile.domain.usecase.HasProfileUseCase
import dev.feature.profile.domain.usecase.ObserveProfileUseCase
import dev.feature.profile.domain.usecase.ProfilePhotosUseCase
import dev.feature.profile.domain.usecase.RefreshProfileUseCase
import dev.feature.profile.domain.usecase.SetMainProfilePhotoUseCase
import dev.feature.profile.domain.usecase.SaveProfileUseCase
import dev.feature.profile.domain.usecase.UploadAvatarUseCase
import dev.feature.profile.presentation.ProfileViewModel
import io.ktor.client.HttpClient
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Profil feature'ining barcha qatlamlarini bog'laydi (domain / data / presentation).
 *
 * Masofaviy manba — real backend: `GET/PUT /v1/profile/me` (OpenAPI'dan generatsiya qilingan
 * [ProfileApi]). Sessiya uid'i (JWT `sub`) `TokenStore` dan keladi — auth backendda, shuning
 * uchun profil qatlami Firebase'ga umuman bog'lanmaydi.
 */
fun profileModule() = module {

    // Generatsiya qilingan klientga ilovaning umumiy Ktor klienti uzatiladi —
    // shunda sessiya tokeni (Bearer) har so'rovga avtomatik qo'shiladi.
    single { ProfileApi(baseUrl = get<NetworkConfig>().baseUrl, httpClient = get<HttpClient>()) }

    single<ProfileRemoteDataSource> { ApiProfileRemoteDataSource(get(), get()) }

    single<ProfileRepository> { ProfileRepositoryImpl(get(), get(), get(), get()) }

    factory { ObserveProfileUseCase(get()) }
    factory { SaveProfileUseCase(get()) }
    factory { HasProfileUseCase(get()) }
    factory { RefreshProfileUseCase(get()) }
    factory { UploadAvatarUseCase(get()) }
    factory { ProfilePhotosUseCase(get()) }
    factory { AddProfilePhotoUseCase(get()) }
    factory { SetMainProfilePhotoUseCase(get()) }
    factory { DeleteProfilePhotoUseCase(get()) }

    viewModelOf(::ProfileViewModel)
}
