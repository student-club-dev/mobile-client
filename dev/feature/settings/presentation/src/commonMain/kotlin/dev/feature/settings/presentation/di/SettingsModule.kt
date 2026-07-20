package dev.feature.settings.presentation.di

import dev.feature.settings.data.repository.SettingsRepositoryImpl
import dev.feature.settings.domain.repository.SettingsRepository
import dev.feature.settings.presentation.SettingsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

fun settingsModule() = module {
    single<SettingsRepository> { SettingsRepositoryImpl(get(), get()) }
    viewModelOf(::SettingsViewModel)
}
