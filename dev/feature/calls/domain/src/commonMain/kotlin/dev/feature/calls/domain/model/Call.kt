package dev.feature.calls.domain.model

import kotlinx.datetime.Instant

/**
 * Qo'ng'iroq turi. `VIDEO` da ham foydalanuvchi kamerani o'chirib qo'yishi mumkin —
 * bu maydon **boshlang'ich niyat**, joriy holat emas (joriy holat: [CallSession.remoteVideo]).
 */
enum class CallMedia { AUDIO, VIDEO }

/**
 * Qo'ng'iroqning holati — **sakkiz** qiymat (`handoff/09-CALLS-PROTOCOL.md` §5).
 *
 * ```
 * call:invite ──► RINGING ── call:accept ──► CONNECTING ── call:connected ──► ACTIVE
 * ```
 *
 * [CONNECTING] alohida holat sifatida bor, chunki «javob berildi» ≠ «media oqyapti»:
 * ICE hali kelishishi kerak va serverning 30 soniyalik taymeri aynan shu oynani o'lchaydi.
 * `ACTIVE` ga o'tkazadigan narsa — klientning **o'z** `call:connected` i.
 */
enum class CallStatus {
    RINGING, CONNECTING, ACTIVE, ENDED, MISSED, DECLINED, FAILED, CANCELED;

    /** Terminal holatdan chiqish yo'li yo'q — UI shunda yopiladi. */
    val isTerminal: Boolean get() = this !in setOf(RINGING, CONNECTING, ACTIVE)

    /** Jonli qo'ng'iroq — ekran ochiq turishi va media qatlami tirik bo'lishi kerak. */
    val isLive: Boolean get() = !isTerminal
}

/**
 * Qo'ng'iroq nima uchun yopilgani.
 *
 * ⚠️ [UNAUTHORIZED] enum'da bor, lekin **1-bosqichda hech qachon chiqmaydi** — uni
 * yozadigan yo'l serverda yo'q (`handoff/09-CALLS-DEVIATIONS.md` §15). Shunga qaramay
 * qayta ishlanadi: enum to'liq bo'lsin.
 */
enum class CallEndReason { HANGUP, TIMEOUT, DECLINED, BUSY, FAILED, CANCELED, UNAUTHORIZED }

/** Yo'nalish — **o'qiyotgan odamga nisbatan** hisoblanadi (server hisoblab beradi). */
enum class CallDirection { INCOMING, OUTGOING }

/**
 * Qo'ng'iroqni kim tugatgani.
 *
 * `null` — **haqiqiy holat**: taymer yopgan (jiringlash tugadi, ulanmadi, 4 soat, uzilish)
 * va glare'da hech kim tugatmagan qo'ng'iroqda hech kim.
 */
enum class CallParty { CALLER, CALLEE }

/**
 * Rad etish sababi — `call:decline` payload'idagi yagona ikki qiymat.
 *
 * [BUSY] ni klient **o'zi** yuboradi: allaqachon boshqa qo'ng'iroqda bo'lganda kiruvchi
 * taklifni shu sabab bilan rad etadi. Serverning `CALL_BUSY` xatosi bilan aralashtirmang —
 * u `call:invite` ning **ack** ida keladi va hech qanday qo'ng'iroq yaratilmagan bo'ladi.
 */
enum class CallDeclineReason { DECLINED, BUSY }

/**
 * Tugagan qo'ng'iroqning tarixdagi yozuvi — `GET /v1/calls`.
 *
 * ⚠️ `callerId`/`calleeId` **yo'q**: server ularning o'rniga [peerId] + [direction] beradi,
 * ya'ni ro'yxatni chizishda o'z id'ingiz bilan solishtirish kerak emas
 * (`handoff/09-CALLS-DEVIATIONS.md` §11).
 */
data class Call(
    /** **uuid v4**, 36 belgi — talaba id'lari (cuid) bilan bir xil tipda tekshirmang. */
    val id: String,
    val conversationId: String,
    /** Suhbatdosh — hech qachon o'qiyotgan odamning o'zi emas. */
    val peerId: String,
    val direction: CallDirection,
    val media: CallMedia,
    /** `RINGING`/`CONNECTING`/`ACTIVE` ham uchraydi — o'sha paytda jonli qo'ng'iroq. */
    val status: CallStatus,
    val startedAt: Instant,
    /** Javob berilmagan qo'ng'iroqda `null`. */
    val answeredAt: Instant? = null,
    val endedAt: Instant? = null,
    /** **Nullable emas** — javob berilmaganda `0` (`…DEVIATIONS.md` §10). */
    val durationMs: Int = 0,
    val endReason: CallEndReason? = null,
    /** Taymer yopgan qo'ng'iroqda `null`. */
    val endedBy: CallParty? = null,
)

/** `GET /v1/calls` sahifasi — loyihaning standart sahifalash konverti. */
data class CallPage(
    val items: List<Call> = emptyList(),
    val page: Int = 1,
    val size: Int = DEFAULT_PAGE_SIZE,
    val total: Int = 0,
    val hasNext: Boolean = false,
) {
    companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}

/**
 * TURN/STUN hisobi — `GET /v1/calls/ice-servers`.
 *
 * ⚠️ **Hech narsani qotirmang**: backend ikkita TURN provayderini qo'llaydi va ular har xil
 * shakl qaytaradi (coturn — 3 ta URL, Metered — 4 ta, boshqa hostlar va portlar).
 * Ro'yxat qanday kelsa shundayligicha `RTCConfiguration` ga uzatiladi
 * (`handoff/09-CALLS-REST.md` §1, «Ro'yxatning shakli»).
 *
 * [ttlSeconds] ning ma'nosi ham provayderga qarab farq qiladi (coturn'da — haqiqiy muddat,
 * Metered'da — «shundan keyin qayta so'ra» maslahati). Ikkalasida ham xatti-harakat bir xil:
 * muddat tugashiga [REFRESH_MARGIN_SECONDS] qolganda qayta so'raymiz.
 */
data class IceServers(
    val servers: List<IceServer> = emptyList(),
    val ttlSeconds: Int = DEFAULT_TTL_SECONDS,
) {
    /**
     * Ro'yxatda **haqiqiy TURN** bormi.
     *
     * ⚠️ Bu tekshiruvsiz `relayOnly` halokatli: `iceTransportPolicy = RELAY` da klient
     * FAQAT relay nomzod yig'adi va TURN bo'lmasa nomzodlar umuman bo'lmaydi — qo'ng'iroq
     * "Ulanmoqda" da turib, 30 soniyadan keyin `FAILED` bo'ladi. STUN (`stun:`) bu yerda
     * hisobga olinmaydi: u srflx beradi, relay emas.
     */
    val hasTurn: Boolean
        get() = servers.any { server ->
            server.urls.any { it.startsWith("turn:") || it.startsWith("turns:") }
        }

    companion object {
        const val DEFAULT_TTL_SECONDS = 3600

        /** Muddat tugashiga shuncha qolganda hisob yangilanadi — 5 daqiqa. */
        const val REFRESH_MARGIN_SECONDS = 300
    }
}

/**
 * Bitta ICE serveri.
 *
 * [username]/[credential] **STUN yozuvida umuman yo'q** (`optional`, `null` emas) —
 * shuning uchun ikkalasi ham nullable.
 */
data class IceServer(
    val urls: List<String>,
    val username: String? = null,
    val credential: String? = null,
)

/**
 * Tanlangan nomzod juftligining turi — `POST /v1/calls/{id}/stats` dagi `candidateType`.
 *
 * ⚠️ **Yig'ilgan nomzodlardan emas, TANLANGAN juftlikdan** olinadi: relay nomzodlar deyarli
 * har qo'ng'iroqda yig'iladi, lekin ko'pincha ishlatilmaydi — «relay yig'ildimi?» deb
 * qarasak, deyarli har qo'ng'iroq `RELAY` chiqadi va raqam butunlay yaroqsiz bo'ladi
 * (`handoff/09-CALLS-REST.md` §3).
 */
enum class CandidateType { HOST, SRFLX, RELAY }

/**
 * Qo'ng'iroq tugagach yuboriladigan o'lchov (`POST /v1/calls/{id}/stats`).
 *
 * Bu **ixtiyoriy telemetriya emas**: TURN tarmoq kengligi byudjeti aynan shu raqamlar
 * bilan hal qilinadi va yuborilmasa backendda hech qanday ma'lumot qolmaydi.
 *
 * ⚠️ [bytesSent]/[bytesReceived] — `Long`: uzun video qo'ng'iroq Int32 ning ~2 GB
 * chegarasidan oshadi.
 */
data class CallStats(
    val candidateType: CandidateType,
    val rttMs: Int? = null,
    val jitterMs: Int? = null,
    val packetsLost: Int? = null,
    val packetsReceived: Int? = null,
    val bytesSent: Long? = null,
    val bytesReceived: Long? = null,
)

/**
 * Serverdagi **jonli** qo'ng'iroqning qisqa tavsifi — `GET /v1/calls/active`
 * (`04-CALLS_RESPONSE.md` §4).
 *
 * [CallSession] dan farqi: bu **media emas, faqat fakt**. Unda SDP taklifi yo'q, ya'ni
 * bundan qo'ng'iroqqa javob berib bo'lmaydi — javob berish uchun kerak bo'lgan offer
 * faqat WebSocket'dagi `call:incoming` bilan keladi. Shuning uchun bu model ikki narsaga
 * ishlatiladi: jiringlashni **to'xtatish** (server "bunday qo'ng'iroq yo'q" desa) va
 * qaysi qo'ng'iroq jonli ekanini tekshirish.
 */
data class ActiveCall(
    val callId: String,
    val conversationId: String,
    val status: CallStatus,
    val media: CallMedia,
    /** `true` — chaquvchi qarshi tomon, ya'ni javob berish kerak bo'lgan qo'ng'iroq. */
    val incoming: Boolean,
    val peerId: String? = null,
    val peerName: String? = null,
    val peerAvatarUrl: String? = null,
)
