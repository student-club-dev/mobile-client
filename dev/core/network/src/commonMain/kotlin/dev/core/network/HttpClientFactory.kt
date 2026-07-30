package dev.core.network

import dev.core.common.auth.AuthTokens
import dev.core.common.auth.TokenStore
import dev.core.common.deviceName
import dev.core.common.platformName
import dev.core.network.response.EnvelopeUnwrapPlugin
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.authProvider
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Tarmoq sozlamalari.
 *
 * [baseUrl] oxirida **slash bilan** `/v1/` turadi — Ktor nisbiy yo'llarni shunga nisbatan hal
 * qiladi va generatsiya qilingan klient ham shu bazaga yo'lni qo'shadi.
 *
 * [refreshPath] — token yangilash endpoint'i (bazaga nisbatan). ElonUz'da talaba va biznes uchun
 * alohida: `auth/student/refresh` / `auth/business/refresh`. Bu ilova — talaba ilovasi.
 */
data class NetworkConfig(
    val baseUrl: String,
    val refreshPath: String = "auth/student/refresh",
    val enableLogging: Boolean = true,
)

val appJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    explicitNulls = false
}

/**
 * Har ikkala platforma uchun yagona, sozlangan Ktor klienti.
 *
 * Sessiya to'liq **backend tokenlariga** tayanadi ([TokenStore]):
 * - har so'rovga `Authorization: Bearer <accessToken>` qo'shiladi;
 * - 401 (`TOKEN_EXPIRED`) kelganda `refreshToken` bilan yangi juftlik olinadi va so'rov
 *   avtomatik takrorlanadi;
 * - refresh ham rad etilsa — sessiya tozalanadi (foydalanuvchi qayta kirishi kerak).
 */
fun createHttpClient(
    config: NetworkConfig,
    tokenStore: TokenStore,
): HttpClient = platformHttpClient {
    expectSuccess = true

    install(HttpTimeout) {
        requestTimeoutMillis = REQUEST_TIMEOUT_MS
        connectTimeoutMillis = CONNECT_TIMEOUT_MS
        socketTimeoutMillis = SOCKET_TIMEOUT_MS
    }

    // BaseResponse konvertini shaffof ochadi — ContentNegotiation'DAN OLDIN o'rnatiladi,
    // shunda raw JSON'ni birinchi bo'lib shu ushlaydi (aks holda konvert bo'sh DTO'ga aylanadi).
    install(EnvelopeUnwrapPlugin)

    install(ContentNegotiation) { json(appJson) }

    if (config.enableLogging) {
        install(Logging) { level = LogLevel.HEADERS }
    }

    install(Auth) {
        bearer {
            loadTokens {
                tokenStore.tokens()?.let { BearerTokens(it.accessToken, it.refreshToken) }
            }
            // 401 kelganda chaqiriladi. `markAsRefreshTokenRequest` bo'lmasa yangilash
            // so'rovining o'zi ham 401'ga tushib cheksiz sikl hosil qilardi.
            refreshTokens {
                val current = tokenStore.tokens()?.refreshToken ?: return@refreshTokens null
                val renewed = runCatching {
                    client.post(config.baseUrl + config.refreshPath) {
                        markAsRefreshTokenRequest()
                        contentType(ContentType.Application.Json)
                        setBody(RefreshRequest(refreshToken = current))
                    }.body<TokensResponse>()
                }.getOrNull()

                if (renewed == null) {
                    // Refresh token yaroqsiz/muddati o'tgan — sessiyani tozalaymiz, aks holda
                    // ilova har so'rovda yaroqsiz token bilan urinaverardi.
                    tokenStore.clear()
                    null
                } else {
                    tokenStore.save(AuthTokens(renewed.accessToken, renewed.refreshToken))
                    BearerTokens(renewed.accessToken, renewed.refreshToken)
                }
            }
        }
    }

    install(DefaultRequest)
    defaultRequest {
        url(config.baseUrl)
        contentType(ContentType.Application.Json)
    }
}

/**
 * Ktor `Auth` plagini tokenni **xotirada keshlaydi** — kirish/chiqishdan keyin keshni majburan
 * tozalash kerak, aks holda klient eski (yoki umuman yo'q) token bilan ishlayveradi.
 */
fun HttpClient.resetAuthTokenCache() {
    authProvider<BearerAuthProvider>()?.clearToken()
}

/** Token yangilash so'rovi/javobi — generatsiya qilingan modelga bog'lanmaslik uchun local. */
@Serializable
private data class RefreshRequest(
    val refreshToken: String,
    val deviceName: String = dev.core.common.deviceName,
    val platform: String = platformName,
)

@Serializable
private data class TokensResponse(
    val accessToken: String,
    val refreshToken: String,
)

/**
 * **WebSocket klienti** — chat real-time kanali uchun (`{HOST}/socket.io/`, Socket.IO).
 *
 * Nega alohida klient (ilovaning umumiy klienti EMAS):
 * - [EnvelopeUnwrapPlugin] har javob tanasini `BaseResponse` deb o'qishga urinadi — WS
 *   handshake javobi JSON emas, ya'ni plagin uni buzardi;
 * - `expectSuccess = true` handshake'ning `101 Switching Protocols` javobini xato deb bilardi;
 * - `Auth` plagini (Bearer + refresh) WS uchun keraksiz — token handshake so'roviga qo'lda
 *   qo'shiladi (qarang `SocketIoClient`), chunki Socket.IO uni CONNECT paketida ham kutadi.
 *
 * `pingIntervalMillis` **berilmaydi**: Engine.IO o'zining matnli `2`/`3` ping-pong'ini yuritadi
 * (qarang `SocketIoClient`), ustiga protokol darajasidagi ping qo'shish ortiqcha.
 */
fun createWebSocketClient(): HttpClient = platformHttpClient(debugInterceptors = false) {
    install(WebSockets)
    install(HttpTimeout) {
        // WS sessiyasi uzoq yashaydi — so'rov/soket chegaralari qo'yilmaydi, faqat ulanish.
        connectTimeoutMillis = CONNECT_TIMEOUT_MS
    }
}

/**
 * **Rasmlar uchun klient** (Coil) — ilovaning umumiy klientidan ALOHIDA.
 *
 * Avval Coil umumiy klientdan foydalanardi va bu ko'p rasmli ekranlarda (talabalar
 * ro'yxati, chatdagi albom, profil to'ri) qismning yuklanmasligiga olib kelardi:
 *
 * - **`requestTimeoutMillis = 15 s`** butun so'rovga, shu jumladan **navbatda turgan**
 *   vaqtga ham tegishli. OkHttp bitta xostga bir vaqtda 5 ta so'rov yuboradi, qolgani
 *   navbatda kutadi — 20 ta rasm so'ralganda oxirgilari yuklanishga ulgurmay chegaraga
 *   urilardi va **jimgina yiqilardi**.
 * - Debug qurilishida har rasm **Chucker** orqali o'tardi: u javob tanasini o'z bazasiga
 *   nusxalaydi, ya'ni har bir rasm ikki marta xotiraga yozilardi.
 * - `expectSuccess`, envelope va `Auth` (refresh) plaginlari rasm uchun keraksiz — ular
 *   faqat qo'shimcha yiqilish yo'llarini ochardi.
 *
 * Bu yerda: **Chucker yo'q**, so'rov chegarasi kengroq (navbat o'ldirmaydi), plaginlar yo'q.
 *
 * ⚠️ Sessiya tokeni oddiy sarlavha bilan qo'shiladi va bu endi **majburiy**: chat
 * biriktirmalari `GET /v1/media/{id}/raw` orqali beriladi, u esa suhbat a'zoligini
 * tekshiradi — tokensiz so'rov `404` oladi (`handoff/api-changes.md` §4c). Sarlavha har
 * so'rovda qaytadan o'qiladi, ya'ni kirish/chiqishdan keyin ham to'g'ri qoladi.
 *
 * ⚠️ ...lekin **faqat o'z serverimizga** ([NetworkConfig.baseUrl] xosti). Bu klient endi
 * begona hostlardan ham rasm oladi — KLIPY GIF'lari (`static.klipy.com`) va Fluent Emoji
 * stikerlari (`cdn.jsdelivr.net`). Sarlavha shartsiz qo'yilganda sessiya tokeni o'sha
 * xizmatlarning jurnallariga tushardi; bu [createPublicHttpClient] da aytilgan qoidaning
 * aynan buzilishi.
 *
 * `Auth` plagini (refresh) ataylab QO'YILMAGAN: o'nlab rasm bir vaqtda 401 olsa, u o'nlab
 * refresh so'rovini boshlab yuborardi. Buning narxi — access token muddati o'tgan lahzada
 * rasmlar bir marta yuklanmay qolishi mumkin; keyingi so'rov (ilovaning umumiy klienti
 * tokenni yangilagach) ishlaydi.
 */
fun createImageHttpClient(tokenStore: TokenStore, config: NetworkConfig): HttpClient {
    val apiHost = Url(config.baseUrl).host
    return platformHttpClient(debugInterceptors = false) {
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            socketTimeoutMillis = IMAGE_SOCKET_TIMEOUT_MS
            requestTimeoutMillis = IMAGE_REQUEST_TIMEOUT_MS
        }
        install(ownHostBearer(apiHost, tokenStore))
    }
}

/**
 * `Authorization` ni **faqat** [apiHost] ga qo'yadi.
 *
 * Nega `defaultRequest` emas: uning bloki so'rovning o'z `url` i bilan emas, bo'sh
 * standart quruvchi bilan ishlaydi — u yerdan xostni bilib bo'lmaydi. `onRequest` esa
 * yuborilayotgan haqiqiy so'rovni beradi.
 */
private fun ownHostBearer(apiHost: String, tokenStore: TokenStore) =
    createClientPlugin("ImageAuthOwnHostOnly") {
        onRequest { request, _ ->
            if (request.url.host.equals(apiHost, ignoreCase = true)) {
                tokenStore.tokens()?.accessToken?.let {
                    request.header(HttpHeaders.Authorization, "Bearer $it")
                }
            }
        }
    }

/**
 * Tashqi (uchinchi tomon) xizmatlar uchun klient — masalan OpenStreetMap Nominatim.
 *
 * Ilovaning umumiy klientidan farqi: **Bearer token qo'shmaydi** va bazaviy manzili yo'q.
 * Sessiya tokenini begona serverga yuborish mumkin emas, shuning uchun alohida klient.
 */
fun createPublicHttpClient(): HttpClient = platformHttpClient {
    expectSuccess = true
    install(HttpTimeout) {
        requestTimeoutMillis = REQUEST_TIMEOUT_MS
        connectTimeoutMillis = CONNECT_TIMEOUT_MS
        socketTimeoutMillis = SOCKET_TIMEOUT_MS
    }
    install(ContentNegotiation) { json(appJson) }
}

/**
 * Tarmoq kutish chegaralari.
 *
 * Ilgari umuman belgilanmagan edi va engine standartlari ishlardi — Android/OkHttp'da ~10 s,
 * iOS/Darwin'da esa **60 s**. Server javob bermasa foydalanuvchi shuncha vaqt kutardi; bu ayniqsa
 * xaritadan joy tanlashda seziladi, chunki u yerda zaxira geokoder birinchisi tugagachgina
 * boshlanadi.
 */
private const val CONNECT_TIMEOUT_MS = 8_000L
private const val REQUEST_TIMEOUT_MS = 15_000L
private const val SOCKET_TIMEOUT_MS = 15_000L

/**
 * Rasm so'rovlari uchun kengroq chegaralar. Sabab — OkHttp bitta xostga bir vaqtda atigi
 * **5 ta** so'rov yuboradi: ro'yxatda 20 ta rasm bo'lsa, oxirgisi navbatda ~4 tsikl kutadi.
 * Umumiy klientdagi 15 soniya shu kutishni ham o'z ichiga olgani uchun ular yiqilardi.
 */
private const val IMAGE_REQUEST_TIMEOUT_MS = 60_000L
private const val IMAGE_SOCKET_TIMEOUT_MS = 30_000L

/** Platformaga xos HTTP engine (Android: OkHttp, iOS: Darwin). */
/**
 * Platforma engine'i bilan klient quradi.
 *
 * [debugInterceptors] — Android'dagi debug HTTP inspektorlari ([OkHttpInterceptors], masalan
 * Chucker) qo'shilsinmi. **WebSocket klienti uchun `false`**: ular application-level
 * interceptor bo'lgani uchun `101 Switching Protocols` javobining tanasini almashtiradi va
 * upgrade buziladi. Ustiga, HTTP inspektorining WS kanalida ishi ham yo'q.
 */
expect fun platformHttpClient(
    debugInterceptors: Boolean = true,
    config: HttpClientConfig<*>.() -> Unit,
): HttpClient
