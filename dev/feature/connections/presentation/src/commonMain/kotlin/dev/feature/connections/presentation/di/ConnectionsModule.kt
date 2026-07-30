package dev.feature.connections.presentation.di

import dev.core.network.NetworkConfig
import dev.core.network.generated.api.ConnectionsApi
import dev.feature.connections.data.repository.ConnectionsRepositoryImpl
import dev.feature.connections.domain.repository.ConnectionsRepository
import dev.feature.connections.presentation.BlockedStudentsViewModel
import dev.feature.connections.presentation.ConnectionsViewModel
import io.ktor.client.HttpClient
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Bog'lanishlar feature'ining barcha qatlamlari (domain / data / presentation).
 *
 * Bayroq YO'Q — bo'lim to'liq backendda ishlaydi (`connections.md`: o'nta endpoint ham tayyor),
 * local seed varianti mavjud emas.
 */
fun connectionsModule() = module {
    // Generatsiya qilingan klientga ilovaning umumiy Ktor klienti uzatiladi — sessiya tokeni
    // (Bearer) har so'rovga avtomatik qo'shiladi va muddati tugasa yangilanadi.
    single { ConnectionsApi(baseUrl = get<NetworkConfig>().baseUrl, httpClient = get<HttpClient>()) }

    single<ConnectionsRepository> { ConnectionsRepositoryImpl(get(), get()) }

    viewModelOf(::ConnectionsViewModel)
    // "Bloklanganlar" ro'yxati — Sozlamalar → Maxfiylik'dan ochiladigan alohida ekran.
    viewModelOf(::BlockedStudentsViewModel)
}
