package dev.feature.jobs.presentation.di

import dev.feature.jobs.data.remote.JobRemoteDataSource
import dev.feature.jobs.data.remote.KtorJobRemoteDataSource
import dev.feature.jobs.data.repository.JobRepositoryImpl
import dev.feature.jobs.domain.repository.JobRepository
import dev.feature.jobs.presentation.JobsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Ishlar feature'ining barcha qatlamlarini bog'laydi (domain / data / presentation).
 * [useRemoteApi] — masofaviy sinxronlash bayrog'i (`CoreModules.REMOTE_SYNC_ENABLED`).
 */
fun jobsModule(useRemoteApi: Boolean) = module {
    single<JobRemoteDataSource> { KtorJobRemoteDataSource(get()) }
    single<JobRepository> { JobRepositoryImpl(get(), get(), get(), useRemoteApi) }
    viewModelOf(::JobsViewModel)
}
