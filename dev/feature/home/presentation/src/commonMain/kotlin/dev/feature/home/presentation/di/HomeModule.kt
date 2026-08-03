package dev.feature.home.presentation.di

import dev.feature.home.presentation.HomeViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Home feature'i — sof agregator. Faqat [HomeViewModel] ni bog'laydi; kerakli repository'lar
 * boshqa feature modullari (listings/connections/notifications/university/profile) va core'da
 * allaqachon ta'minlangan, Koin ularni runtime'da ulaydi.
 */
fun homeModule() = module {
    viewModelOf(::HomeViewModel)
}
