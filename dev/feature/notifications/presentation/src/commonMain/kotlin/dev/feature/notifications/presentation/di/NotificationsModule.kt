package dev.feature.notifications.presentation.di

import dev.core.common.push.PushRegistrar
import dev.core.network.NetworkConfig
import dev.core.network.generated.api.NotificationsApi
import dev.feature.notifications.data.push.PushRepositoryImpl
import dev.feature.notifications.data.push.platformPushTokenSource
import dev.feature.notifications.data.repository.NotificationRepositoryImpl
import dev.feature.notifications.domain.push.PushRepository
import dev.feature.notifications.domain.push.PushTokenSource
import dev.feature.notifications.domain.repository.NotificationRepository
import dev.feature.notifications.presentation.NotificationsViewModel
import io.ktor.client.HttpClient
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.binds
import org.koin.dsl.module

/**
 * Bildirishnomalar: ilova ichidagi ro'yxat (local DB) **va** oflayn push
 * (`POST /v1/devices` — `chat.md` §10).
 *
 * [PushRepositoryImpl] bir vaqtda [PushRepository] va [PushRegistrar] — ikkinchisini auth
 * qatlami sessiya ochilganda/yopilganda chaqiradi (auth push haqida hech nima bilmaydi,
 * faqat `:dev:core:common` dagi interfeysni ko'radi).
 */
fun notificationsModule() = module {
    single<NotificationRepository> { NotificationRepositoryImpl(get(), get()) }

    single { NotificationsApi(baseUrl = get<NetworkConfig>().baseUrl, httpClient = get<HttpClient>()) }
    // Android: FCM, iOS: APNs ko'prigi.
    single<PushTokenSource> { platformPushTokenSource() }
    // Bitta nusxa, ikkita interfeys: `PushRepository` (feature ichida) va `PushRegistrar`
    // (auth qatlami sessiya signalini beradi). Ikki alohida `single` bo'lsa token
    // yangilanishini kuzatuvchi ikki marta ishga tushardi.
    single { PushRepositoryImpl(get(), get(), get(), get()) } binds
        arrayOf(PushRepository::class, PushRegistrar::class)

    viewModelOf(::NotificationsViewModel)
}
