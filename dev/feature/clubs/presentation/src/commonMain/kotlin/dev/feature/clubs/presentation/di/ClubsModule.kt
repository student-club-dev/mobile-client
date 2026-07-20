package dev.feature.clubs.presentation.di

import dev.feature.clubs.data.repository.ClubRepositoryImpl
import dev.feature.clubs.domain.repository.ClubRepository
import dev.feature.clubs.presentation.ClubsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

fun clubsModule() = module {
    single<ClubRepository> { ClubRepositoryImpl(get(), get(), get()) }
    viewModelOf(::ClubsViewModel)
}
