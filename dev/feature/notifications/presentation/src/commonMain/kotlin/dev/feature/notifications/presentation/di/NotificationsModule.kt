package dev.feature.notifications.presentation.di

import dev.core.common.push.PushRegistrar
import dev.core.network.NetworkConfig
import dev.core.network.generated.api.NotificationsApi
import dev.feature.notifications.data.push.PushRepositoryImpl
import dev.feature.notifications.data.push.platformPushTokenSource
import dev.feature.notifications.data.remote.ApiNotificationRemoteDataSource
import dev.feature.notifications.data.remote.NotificationRemoteDataSource
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
 * Bildirishnomalar: ilova ichidagi ro'yxat (`GET /v1/notifications` + local kesh) **va**
 * oflayn push (`POST /v1/devices` — `03-WEBSOCKET.md` §10).
 *
 * [useRemoteApi] — ro'yxat serverdan olinadimi (`NOTIFICATIONS_REMOTE_ENABLED`). `false`
 * bo'lganda tarmoqqa birorta so'rov ketmaydi va ekran faqat keshdan ishlaydi; push
 * ro'yxatdan o'tish bunga BOG'LIQ EMAS — `/v1/devices` allaqachon serverda bor.
 *
 * [PushRepositoryImpl] bir vaqtda [PushRepository] va [PushRegistrar] — ikkinchisini auth
 * qatlami sessiya ochilganda/yopilganda chaqiradi (auth push haqida hech nima bilmaydi,
 * faqat `:dev:core:common` dagi interfeysni ko'radi).
 */
fun notificationsModule(useRemoteApi: Boolean) = module {
    single { NotificationsApi(baseUrl = get<NetworkConfig>().baseUrl, httpClient = get<HttpClient>()) }

    single<NotificationRemoteDataSource> { ApiNotificationRemoteDataSource(get(), get()) }
    single<NotificationRepository> { NotificationRepositoryImpl(get(), get(), get(), useRemoteApi) }
    // Android: FCM, iOS: APNs ko'prigi.
    single<PushTokenSource> { platformPushTokenSource() }
    // Bitta nusxa, ikkita interfeys: `PushRepository` (feature ichida) va `PushRegistrar`
    // (auth qatlami sessiya signalini beradi). Ikki alohida `single` bo'lsa token
    // yangilanishini kuzatuvchi ikki marta ishga tushardi.
    single { PushRepositoryImpl(get(), get(), get(), get()) } binds
        arrayOf(PushRepository::class, PushRegistrar::class)

    viewModelOf(::NotificationsViewModel)
}
