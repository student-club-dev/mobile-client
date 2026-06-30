package dev.feature.auth.di

import dev.feature.auth.presentation.AuthViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val authFeatureModule = module {
    viewModelOf(::AuthViewModel)
}
