package dev.feature.chat.presentation.di

import dev.core.common.auth.TokenStore
import dev.core.network.NetworkConfig
import dev.core.network.createWebSocketClient
import dev.core.network.refreshSession
import dev.core.network.generated.api.ChatApi
import dev.core.network.media.apiOrigin
import dev.core.network.ws.SocketIoClient
import dev.feature.chat.data.realtime.ChatSocket
import dev.feature.chat.data.remote.ChatRemoteDataSource
import dev.feature.chat.data.remote.GifRemoteDataSource
import dev.feature.chat.data.remote.StickerRemoteDataSource
import dev.feature.chat.data.remote.StickerSearchRemoteDataSource
import dev.feature.chat.data.repository.ChatRepositoryImpl
import dev.feature.chat.data.repository.GifRepositoryImpl
import dev.feature.chat.data.repository.StickerRepositoryImpl
import dev.feature.chat.domain.repository.ChatRepository
import dev.feature.chat.domain.repository.GifRepository
import dev.feature.chat.domain.repository.StickerRepository
import dev.feature.chat.presentation.ChatViewModel
import dev.feature.chat.presentation.PeerMediaViewModel
import dev.feature.chat.presentation.gif.GifPanelViewModel
import dev.feature.chat.presentation.gif.StickerPanelViewModel
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Chat feature'ining barcha qatlamlari (domain / data / presentation) — to'liq backendda:
 * REST `/v1/conversations…` + WebSocket `/chat` (handoff `03-WEBSOCKET.md`).
 *
 * Bayroq YO'Q: local demo suhbatlar olib tashlangan, ma'lumot faqat serverdan keladi
 * (SQLDelight — offline o'qish uchun kesh).
 */
fun chatModule() = module {

    single { ChatApi(baseUrl = get<NetworkConfig>().baseUrl, httpClient = get<HttpClient>()) }
    // `MediaUploader` — biriktirma yuklash uchun: generatsiya qilingan `chatUpload` multipart
    // qismiga `filename` qo'ymaydi va NestJS uni fayl deb qabul qilmaydi.
    single { ChatRemoteDataSource(api = get(), connectivity = get(), media = get()) }

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
                // Tokenni TO'G'RIDAN-TO'G'RI yangilaymiz (`auth/student/refresh`) — ilgari
                // bu yerda `/v1/conversations` so'ralib, uning 401 i orqali yangilanardi.
                // Soket qayta-qayta uzilganda o'sha so'rov ham qayta-qayta ketardi.
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

    single { ChatSocket(get()) }

    single<ChatRepository> {
        ChatRepositoryImpl(
            db = get(),
            dispatchers = get(),
            remote = get(),
            socket = get(),
            tokenStore = get(),
            // Biriktirma havolalari serverdan NISBIY keladi — video/ovoz pleyeri uchun
            // ular to'liq bo'lishi shart (qarang `MediaUrl`).
            apiOrigin = get<NetworkConfig>().apiOrigin,
        )
    }

    // GIF va stiker — chat pipeline'idan MUSTAQIL qatlam: ular `ChatRepository` ni
    // kengaytirmaydi, chunki yagona umumiy nuqta — yuborish (`sendGif`/`sendSticker`), qolgani
    // (qidiruv, katalog, kesh) chatning offline-first keshiga umuman tegmaydi.
    single { GifRemoteDataSource(api = get(), connectivity = get()) }
    single { StickerRemoteDataSource(api = get(), connectivity = get()) }
    single { StickerSearchRemoteDataSource(api = get(), connectivity = get()) }
    single<GifRepository> { GifRepositoryImpl(remote = get()) }
    single<StickerRepository> { StickerRepositoryImpl(remote = get(), search = get()) }

    viewModelOf(::ChatViewModel)
    // Talaba profilidagi «Media / Fayllar / Havolalar» bo'limlari (chatdan ham, story
    // lentasidan ham bir xil ochiladi).
    viewModelOf(::PeerMediaViewModel)
    viewModelOf(::GifPanelViewModel)
    viewModelOf(::StickerPanelViewModel)
}

private const val WS_CLIENT = "chatWsClient"
private const val WS_SCOPE = "chatWsScope"
