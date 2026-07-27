package dev.feature.chat.presentation.di

import dev.core.common.auth.TokenStore
import dev.core.network.NetworkConfig
import dev.core.network.createWebSocketClient
import dev.core.network.generated.api.ChatApi
import dev.core.network.ws.SocketIoClient
import dev.feature.chat.data.realtime.ChatSocket
import dev.feature.chat.data.remote.ChatRemoteDataSource
import dev.feature.chat.data.repository.ChatRepositoryImpl
import dev.feature.chat.domain.repository.ChatRepository
import dev.feature.chat.presentation.ChatViewModel
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Chat feature'ining barcha qatlamlari (domain / data / presentation) — to'liq backendda:
 * REST `/v1/conversations…` + WebSocket `/chat` (handoff `chat.md`).
 *
 * Bayroq YO'Q: local demo suhbatlar olib tashlangan, ma'lumot faqat serverdan keladi
 * (SQLDelight — offline o'qish uchun kesh).
 */
fun chatModule() = module {

    single { ChatApi(baseUrl = get<NetworkConfig>().baseUrl, httpClient = get<HttpClient>()) }
    single { ChatRemoteDataSource(get(), get()) }

    // WS uchun ALOHIDA klient — ilovaning umumiy klienti (envelope + expectSuccess + Auth)
    // handshake'ni buzardi; qarang `createWebSocketClient`.
    single(named(WS_CLIENT)) { createWebSocketClient() }

    // Soket ViewModel'dan uzun yashaydi (tab almashganda uzilmasin) — o'z qamrovi bilan.
    single(named(WS_SCOPE)) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    single {
        SocketIoClient(
            client = get(named(WS_CLIENT)),
            // Handshake origin'ga boradi (`{HOST}/socket.io/`), `/v1` prefiksisiz.
            endpoint = get<NetworkConfig>().baseUrl.substringBefore("/v1"),
            namespace = ChatSocket.NAMESPACE,
            tokenProvider = { refresh ->
                // Token muddati o'tgan bo'lsa uni yangilashning eng ishonchli yo'li — arzon
                // avtorizatsiyali REST so'rovi: Ktor `Auth` plagini 401 da o'zi refresh
                // qiladi va yangi juftlikni `TokenStore` ga yozadi.
                if (refresh) runCatching { get<ChatRemoteDataSource>().conversations(page = 1, size = 1) }
                get<TokenStore>().tokens()?.accessToken
            },
            scope = get(named(WS_SCOPE)),
        )
    }

    single { ChatSocket(get()) }

    single<ChatRepository> {
        ChatRepositoryImpl(
            db = get(),
            dispatchers = get(),
            remote = get(),
            socket = get(),
            tokenStore = get(),
        )
    }

    viewModelOf(::ChatViewModel)
}

private const val WS_CLIENT = "chatWsClient"
private const val WS_SCOPE = "chatWsScope"
