package dev.feature.calls.data.repository

import dev.core.common.Resource
import dev.core.common.map
import dev.feature.calls.data.remote.CallsRemoteDataSource
import dev.feature.calls.domain.model.ActiveCall
import dev.feature.calls.domain.model.Call
import dev.feature.calls.domain.model.CallDirection
import dev.feature.calls.domain.model.CallEndReason
import dev.feature.calls.domain.model.CallMedia
import dev.feature.calls.domain.model.CallPage
import dev.feature.calls.domain.model.CallParty
import dev.feature.calls.domain.model.CallStats
import dev.feature.calls.domain.model.CallStatus
import dev.feature.calls.domain.model.IceServer
import dev.feature.calls.domain.model.IceServers
import dev.feature.calls.domain.repository.CallRepository
import dev.core.network.generated.model.ActiveCallDto
import dev.core.network.generated.model.CallDto
import dev.core.network.generated.model.IceServersDto
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

/**
 * [CallRepository] ning amalga oshirilishi.
 *
 * Yagona holat — TURN hisobining keshi. Qolgan ikkala endpoint keshsiz: tarix har safar
 * yangi o'qiladi (u chat lentasidagi `CALL` xabarlar bilan ikkilanadi va offline'da kerak
 * emas), stats esa yozuv amali.
 */
class CallRepositoryImpl(
    private val remote: CallsRemoteDataSource,
    private val clock: Clock = Clock.System,
) : CallRepository {

    private val iceMutex = Mutex()
    private var cachedIce: IceServers? = null

    /** Kesh qachon yaroqsiz bo'lishi — epoch sekundlarda. */
    private var iceExpiresAtSeconds: Long = 0

    /**
     * Hisob qo'ng'iroqqa bog'lanmagan, ya'ni har qo'ng'iroqda qayta so'rash shart emas —
     * va so'rash chegarasi ham bor (daqiqasiga 10 ta, `studentId` bo'yicha).
     *
     * ⚠️ `ttlSeconds` ning ma'nosi provayderga qarab farq qiladi (coturn'da credential'ning
     * haqiqiy umri, Metered'da esa faqat maslahat, chunki u yerda credential muddatsiz).
     * Ikkala holatda ham xatti-harakat bir xil bo'lishi kerak: muddat tugashiga
     * [IceServers.REFRESH_MARGIN_SECONDS] qolganda qayta so'raymiz — Metered'da bu bizga
     * credential'ni almashtirish oynasini beradi.
     */
    override suspend fun iceServers(forceRefresh: Boolean): Resource<IceServers> = iceMutex.withLock {
        val now = clock.now().epochSeconds
        val cached = cachedIce
        if (!forceRefresh && cached != null && now < iceExpiresAtSeconds) {
            return@withLock Resource.Success(cached)
        }
        when (val result = remote.iceServers()) {
            is Resource.Success -> {
                val servers = result.data.toDomain()
                cachedIce = servers
                iceExpiresAtSeconds = now +
                    (servers.ttlSeconds - IceServers.REFRESH_MARGIN_SECONDS).toLong()
                        .coerceAtLeast(MIN_CACHE_SECONDS)
                Resource.Success(servers)
            }
            // Xato keshni TOZALAMAYDI: bir marta olingan hisob muddati tugagunicha yaroqli
            // bo'lib qolaveradi va vaqtinchalik 429/503 jonli qo'ng'iroqni buzmasligi kerak.
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }
    }

    override suspend fun history(page: Int, size: Int): Resource<CallPage> =
        remote.history(page = page, size = size).map { dto ->
            CallPage(
                items = dto.items.map { it.toDomain() },
                page = dto.page,
                size = dto.propertySize,
                total = dto.total,
                hasNext = dto.hasNext,
            )
        }

    /**
     * ⚠️ Kesh YO'Q va bo'lishi ham mumkin emas: bu so'rovning butun ma'nosi — "ayni SHU
     * lahzada qo'ng'iroq bormi". Bir soniyalik eskirgan javob ham telefonni bo'sh joyga
     * jiringlatib qo'yardi.
     *
     * Muddati o'tgan qo'ng'iroq `null` bilan bir xil ma'noda — server buni o'zi ham
     * shunday hisoblaydi, lekin klient soati bilan tekshirish arzon va zararsiz.
     */
    override suspend fun activeCall(): Resource<ActiveCall?> =
        remote.activeCall().map { response ->
            response.call
                ?.takeIf { it.expiresAt > clock.now() }
                ?.toDomain()
        }

    override suspend fun reportStats(callId: String, stats: CallStats): Resource<Unit> =
        remote.reportStats(callId, stats)

    private companion object {
        /** Provayder juda qisqa TTL bersa ham hisob kamida shuncha qayta ishlatiladi. */
        const val MIN_CACHE_SECONDS = 60L
    }
}

private fun IceServersDto.toDomain(): IceServers = IceServers(
    // Ro'yxat **qanday kelsa shundayligicha** uzatiladi: URL soni ham, host nomi ham
    // provayderga qarab o'zgaradi (coturn 3 ta URL, Metered 4 ta) va ularni klientda
    // tekshirish/filtrlash keyingi deploy'da qo'ng'iroqni o'chirib qo'yardi.
    servers = iceServers.map { IceServer(urls = it.urls, username = it.username, credential = it.credential) },
    ttlSeconds = ttlSeconds,
)

private fun ActiveCallDto.toDomain(): ActiveCall = ActiveCall(
    callId = callId,
    conversationId = conversationId,
    // `state`/`media` — `String` (kengayadigan enum): noma'lum qiymat butun javobni
    // yiqitmasligi kerak. Noma'lum holat "jonli emas" deb o'qiladi.
    status = parseEnum(state, CallStatus.ENDED),
    media = parseEnum(media, CallMedia.AUDIO),
    incoming = incoming,
    peerId = peer?.id,
    peerName = peer?.fullName,
    peerAvatarUrl = peer?.avatarUrl,
)

private fun CallDto.toDomain(): Call = Call(
    id = id,
    conversationId = conversationId,
    peerId = peerId,
    direction = parseEnum(direction, CallDirection.INCOMING),
    media = parseEnum(media, CallMedia.AUDIO),
    status = parseEnum(status, CallStatus.ENDED),
    startedAt = startedAt,
    answeredAt = answeredAt,
    endedAt = endedAt,
    durationMs = durationMs,
    endReason = parseEnumOrNull<CallEndReason>(endReason),
    endedBy = parseEnumOrNull<CallParty>(endedBy),
)

/**
 * Sim qiymati → domen enum'i. Noma'lum qiymat [default] ga tushadi.
 *
 * DTO'lar ataylab `String` (`lenientEnums`): kotlinx noma'lum enum qiymatida **butun
 * javobni** yiqitadi, ya'ni server enum'ga bitta qiymat qo'shsa qo'ng'iroqlar tarixi
 * butunlay ochilmay qolardi.
 */
internal inline fun <reified T : Enum<T>> parseEnum(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default

internal inline fun <reified T : Enum<T>> parseEnumOrNull(name: String?): T? =
    enumValues<T>().firstOrNull { it.name == name }
