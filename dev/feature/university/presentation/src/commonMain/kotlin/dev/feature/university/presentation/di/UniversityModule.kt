package dev.feature.university.presentation.di

import dev.core.network.createPublicHttpClient
import dev.feature.university.data.remote.KtorUniversityRemoteDataSource
import dev.feature.university.data.remote.UniversityRemoteDataSource
import dev.feature.university.data.repository.UniversityRepositoryImpl
import dev.feature.university.domain.repository.UniversityRepository
import dev.feature.university.presentation.MyUniversityViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

fun universityModule(useRemoteApi: Boolean) = module {
    single<UniversityRemoteDataSource> { KtorUniversityRemoteDataSource(get()) }
    // Oxirgi argument — OTM ro'yxati uchun klient. `prof-emis.edu.uz` uchinchi tomon xizmati,
    // shuning uchun **tokensiz** public klient: ilovaning umumiy klienti har so'rovga
    // `Authorization: Bearer` qo'shadi va sessiya tokeni begona serverga ketib qolardi.
    single<UniversityRepository> {
        UniversityRepositoryImpl(get(), get(), get(), useRemoteApi, createPublicHttpClient())
    }
    viewModelOf(::MyUniversityViewModel)
}
