package dev.feature.ads.presentation.di

import dev.feature.ads.data.remote.AdRemoteDataSource
import dev.feature.ads.data.remote.KtorAdRemoteDataSource
import dev.feature.ads.data.repository.AdRepositoryImpl
import dev.feature.ads.domain.repository.AdRepository
import dev.feature.ads.presentation.PostAdViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * E'lonlar feature'ining barcha qatlamlarini bog'laydi (domain / data / presentation).
 * [useRemoteApi] — masofaviy sinxronlash bayrog'i (`CoreModules.REMOTE_SYNC_ENABLED`).
 */
fun adsModule(useRemoteApi: Boolean) = module {
    single<AdRemoteDataSource> { KtorAdRemoteDataSource(get()) }
    single<AdRepository> { AdRepositoryImpl(get(), get(), get(), useRemoteApi) }
    viewModelOf(::PostAdViewModel)
}
