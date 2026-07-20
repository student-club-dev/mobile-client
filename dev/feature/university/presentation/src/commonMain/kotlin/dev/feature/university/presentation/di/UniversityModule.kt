package dev.feature.university.presentation.di

import dev.feature.university.data.remote.KtorUniversityRemoteDataSource
import dev.feature.university.data.remote.UniversityRemoteDataSource
import dev.feature.university.data.repository.UniversityRepositoryImpl
import dev.feature.university.domain.repository.UniversityRepository
import dev.feature.university.presentation.MyUniversityViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

fun universityModule(useRemoteApi: Boolean) = module {
    single<UniversityRemoteDataSource> { KtorUniversityRemoteDataSource(get()) }
    single<UniversityRepository> { UniversityRepositoryImpl(get(), get(), get(), useRemoteApi, get()) }
    viewModelOf(::MyUniversityViewModel)
}
