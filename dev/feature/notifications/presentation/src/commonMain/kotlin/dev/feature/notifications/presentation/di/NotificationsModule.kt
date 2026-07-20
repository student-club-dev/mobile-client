package dev.feature.notifications.presentation.di

import dev.feature.notifications.data.repository.NotificationRepositoryImpl
import dev.feature.notifications.domain.repository.NotificationRepository
import dev.feature.notifications.presentation.NotificationsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

fun notificationsModule() = module {
    single<NotificationRepository> { NotificationRepositoryImpl(get(), get()) }
    viewModelOf(::NotificationsViewModel)
}
