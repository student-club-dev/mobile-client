package dev.feature.calls.presentation.di

import dev.core.common.auth.TokenStore
import dev.core.network.NetworkConfig
import dev.core.network.createWebSocketClient
import dev.core.network.refreshSession
import dev.core.network.generated.api.CallsApi
import dev.core.network.ws.SocketIoClient
import dev.feature.calls.data.engine.CallEngineFactory
import dev.feature.calls.data.realtime.CallsSocket
import dev.feature.calls.data.remote.CallsRemoteDataSource
import dev.feature.calls.data.repository.CallRepositoryImpl
import dev.feature.calls.data.session.CallSessionManager
import dev.feature.calls.domain.repository.CallController
import dev.feature.calls.domain.repository.CallRepository
import dev.feature.calls.presentation.CallHistoryViewModel
import dev.feature.calls.presentation.CallViewModel
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Qo'ng'iroq feature'i — REST (`/v1/calls…`) + Socket.IO `/calls`.
 *
 * ⚠️ `/calls` — **chatdan alohida** namespace va alohida `SocketIoClient` nusxasi: SDP
 * hech qachon chat kanaliga tushmaydi (`handoff/09-CALLS-PROTOCOL.md`). Ikkalasi bitta WS
 * klientini bo'lishadi degan taxmin xato bo'lardi — Socket.IO'da har namespace o'z CONNECT
 * paketini talab qiladi va bizning minimal klientimiz bitta nusxada bitta namespace ni
 * boshqaradi.
 *
 * [CallSessionManager] — `single`: bir vaqtda bitta jonli qo'ng'iroq bo'ladi va u ekran
 * yopilganda ham davom etishi kerak.
 *
 * [CallEngineFactory] platforma modulida beriladi (`AndroidCallEngineFactory` /
 * `IosCallEngineFactory`) — u `Context` talab qiladi.
 */
fun callsModule() = module {

    single { CallsApi(baseUrl = get<NetworkConfig>().baseUrl, httpClient = get<HttpClient>()) }
    single { CallsRemoteDataSource(api = get(), connectivity = get()) }
    single<CallRepository> { CallRepositoryImpl(remote = get()) }

    // WS uchun ALOHIDA klient — ilovaning umumiy klienti (envelope + expectSuccess + Auth)
    // handshake'ni buzardi; qarang `createWebSocketClient`.
    single(named(WS_CLIENT)) { createWebSocketClient() }

    // Soket ViewModel'dan uzun yashaydi (ekran yopilsa ham qo'ng'iroq davom etadi).
    single(named(WS_SCOPE)) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    single(named(SOCKET)) {
        SocketIoClient(
            client = get(named(WS_CLIENT)),
            // Handshake origin'ga boradi (`{HOST}/socket.io/`), `/v1` prefiksisiz.
            endpoint = get<NetworkConfig>().baseUrl.substringBefore("/v1"),
            namespace = CallsSocket.NAMESPACE,
            tokenProvider = { refresh ->
                // Tokenni TO'G'RIDAN-TO'G'RI yangilaymiz (`auth/student/refresh`).
                //
                // Ilgari bu yerda "arzon avtorizatsiyali REST so'rovi" — `ice-servers` —
                // yuborilardi va Ktor `Auth` plagini uning 401 ida tokenni yangilardi.
                // Server WS ni qabul qilmaganda soket qayta-qayta uziladi, ya'ni o'sha
                // "arzon so'rov" davriy bo'lib qolardi: trafik jurnalida hech qanday
                // qo'ng'iroq bo'lmasa ham `GET /v1/calls/ice-servers` takrorlanib turardi.
                //
                // ⚠️ Faqat SESSIYA BOR bo'lganda: refresh tokensiz yangilanadigan narsa yo'q.
                val store = get<TokenStore>()
                if (refresh && store.tokens() != null) {
                    runCatching {
                        get<HttpClient>().refreshSession(get<NetworkConfig>(), store)
                    }
                }
                store.tokens()?.accessToken
            },
            scope = get(named(WS_SCOPE)),
        )
    }

    single { CallsSocket(get(named(SOCKET))) }

    single<CallController> {
        CallSessionManager(
            socket = get(),
            engineFactory = get<CallEngineFactory>(),
            repository = get(),
            accessToken = { get<TokenStore>().tokens()?.accessToken },
            presence = get(),
            audio = get(),
            scope = get(named(WS_SCOPE)),
        )
    }

    viewModelOf(::CallViewModel)
    viewModelOf(::CallHistoryViewModel)
}

private const val WS_CLIENT = "callsWsClient"
private const val WS_SCOPE = "callsWsScope"
private const val SOCKET = "callsSocketIo"
