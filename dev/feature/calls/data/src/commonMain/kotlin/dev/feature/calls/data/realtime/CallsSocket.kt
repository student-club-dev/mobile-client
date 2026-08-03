package dev.feature.calls.data.realtime

import dev.core.network.ws.SocketIoClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement

// ---------------------------------------------------------------------------------------
// `/calls` — signalizatsiya protokoli. Swagger'da YO'Q (OpenAPI HTTP ni tasvirlaydi,
// WebSocket hodisalarini emas). Yagona manba: `handoff/09-CALLS-PROTOCOL.md`.
//
// `/chat` dan ATAYLAB alohida namespace: SDP hech qachon chat socket'iga tushmaydi.
// ---------------------------------------------------------------------------------------

/**
 * Har bir klient → server hodisasining **ack** i.
 *
 * Muvaffaqiyatda maydonlar hodisaga qarab keladi; xatoda esa **doim aynan bitta shakl**:
 * `{ status: "error", error: { code, message } }`. Bu `/chat` dagi bilan bir xil naqsh va
 * REST'ning `BaseResponse` konvertidan farq qiladi.
 *
 * ⚠️ **`status: "ok"` — «bajarildi» degani emas.** Tugatuvchi hodisa joriy holatga mos
 * kelmasa server uni jim qabul qiladi va hech narsa qilmaydi (yo'qolgan ack tufayli
 * takrorlangan `call:end` xato bermasligi kerak). Haqiqiy yopilish `call:ended` /
 * `call:declined` / `call:canceled` bilan keladi — ack bilan emas.
 */
@Serializable
data class CallAck(
    val status: String? = null,
    val callId: String? = null,
    /** `call:invite` da — jiringlash muddati; `call:auth` da — tokenning yangi `exp` i. */
    val expiresAt: String? = null,
    val relayOnly: Boolean? = null,
    val error: CallAckError? = null,
) {
    val isOk: Boolean get() = status == "ok"
}

@Serializable
data class CallAckError(
    val code: String? = null,
    @SerialName("message") val text: String? = null,
)

/** `call:incoming` — chaqirilganning **barcha** qurilmalariga. */
@Serializable
data class WsCallIncoming(
    val callId: String,
    val conversationId: String,
    val caller: WsCaller,
    val media: String = "AUDIO",
    val sdp: String,
    val relayOnly: Boolean = true,
    val expiresAt: String? = null,
)

/**
 * Chaqiruvchining qisqa surati.
 *
 * [fullName] `null` emas, lekin ism ham familiya ham bo'sh bo'lsa **bo'sh satr** bo'lishi
 * mumkin — o'shanda [username] ga tushiladi.
 */
@Serializable
data class WsCaller(
    val id: String,
    val fullName: String = "",
    val username: String? = null,
    val avatarUrl: String? = null,
)

/** `call:accepted` — chaquvchiga; ichida chaqirilganning answer SDP'si. */
@Serializable
data class WsCallAccepted(
    val callId: String,
    val sdp: String,
    val relayOnly: Boolean = true,
)

/** `call:ringing` — chaqirilganning telefoni jiringlay boshladi. */
@Serializable
data class WsCallRinging(val callId: String)

/** `call:taken` — **o'zingizning boshqa** qurilmangiz javob berdi yoki rad etdi. */
@Serializable
data class WsCallTaken(val callId: String)

/** `call:declined` — chaqirilgan rad etdi (chaquvchiga). */
@Serializable
data class WsCallDeclined(
    val callId: String,
    val reason: String = "DECLINED",
)

/** `call:canceled` — chaquvchi bekor qildi (chaqirilganga). */
@Serializable
data class WsCallCanceled(val callId: String)

/**
 * `call:ended` — **ikkala** ishtirokchiga.
 *
 * [durationMs] doimo son (javob berilmaganda `0`), [endedBy] esa **nullable**: taymer
 * yopgan qo'ng'iroqda va glare'da hech kim tugatmagan.
 */
@Serializable
data class WsCallEnded(
    val callId: String,
    val reason: String = "HANGUP",
    val durationMs: Int = 0,
    val endedBy: String? = null,
)

/** `call:ice` — o'zgarishsiz uzatiladigan nomzod. */
@Serializable
data class WsCallIce(
    val callId: String,
    val candidate: WsIceCandidate,
)

/**
 * ICE nomzodi — **aynan uchta kalit**.
 *
 * ⚠️ Server validatsiyasi `whitelist` + `forbidNonWhitelisted` rejimida: e'lon qilinmagan
 * har qanday kalit `VALIDATION_ERROR` beradi. `RTCIceCandidate.toJSON()` ko'p platformada
 * `usernameFragment` ni ham qo'shadi — u **olib tashlanadi** (qarang [CallsSocket.sendIce]).
 *
 * [sdpMid] `null` bo'lishi ham **qabul qilinmaydi**: nomzodlarni tugatish markeri umuman
 * yuborilmaydi, WebRTC uni o'zi hal qiladi.
 */
@Serializable
data class WsIceCandidate(
    val candidate: String,
    val sdpMid: String,
    val sdpMLineIndex: Int,
)

/** `call:renegotiate` — o'zgarishsiz uzatiladigan SDP. */
@Serializable
data class WsCallRenegotiate(
    val callId: String,
    val sdp: String,
)

/** `call:media-state` — suhbatdoshning mikrofon/kamera holati. */
@Serializable
data class WsCallMediaState(
    val callId: String,
    val audioEnabled: Boolean = true,
    val videoEnabled: Boolean = false,
)

/**
 * `/calls` kanali — [SocketIoClient] ustidagi **tiplangan** qatlam.
 *
 * Bu klass protokolning o'zini bilmaydi (u `SocketIoClient` da) — faqat 17 ta hodisa nomi,
 * payload shakli va **payload cheklarini** biladi (`handoff/09-CALLS-PROTOCOL.md` §7).
 *
 * Cheklar shu yerda tekshiriladi, chunki **WS xato ack'i qaysi maydon buzilganini
 * aytmaydi**: `message` doimo umumiy «Ma'lumotlar noto'g'ri». Ya'ni payloadni oldindan
 * to'g'ri yig'ishdan boshqa iloj yo'q.
 */
class CallsSocket(private val socket: SocketIoClient) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val connected: StateFlow<Boolean> get() = socket.connected

    fun start() = socket.start()
    fun stop() = socket.stop()

    // --- Server → klient (ack yo'q — bular bildirishnoma) --------------------------------

    val incoming: Flow<WsCallIncoming> = eventsOf(EVENT_INCOMING)
    val accepted: Flow<WsCallAccepted> = eventsOf(EVENT_ACCEPTED)
    val ringing: Flow<WsCallRinging> = eventsOf(EVENT_RINGING)
    val taken: Flow<WsCallTaken> = eventsOf(EVENT_TAKEN)
    val declined: Flow<WsCallDeclined> = eventsOf(EVENT_DECLINED)
    val canceled: Flow<WsCallCanceled> = eventsOf(EVENT_CANCELED)
    val ended: Flow<WsCallEnded> = eventsOf(EVENT_ENDED)
    val ice: Flow<WsCallIce> = eventsOf(EVENT_ICE)
    val renegotiate: Flow<WsCallRenegotiate> = eventsOf(EVENT_RENEGOTIATE)
    val mediaState: Flow<WsCallMediaState> = eventsOf(EVENT_MEDIA_STATE)

    // --- Klient → server (holat o'zgartiruvchi) ------------------------------------------

    /**
     * Qo'ng'iroq boshlaydi.
     *
     * ⚠️ **`conversationId` YUBORILMAYDI** — server uni (chaquvchi, chaqirilgan) juftligidan
     * o'zi topadi. Yuborilsa payload validatsiyadan o'tmaydi, ya'ni eski payloadni
     * «zararsiz ortiqcha maydon» deb qoldirib bo'lmaydi
     * (`handoff/09-CALLS-DEVIATIONS.md` §4). Sabab xavfsizlik: qiymat klientdan kelsa
     * hujumchi begona suhbatga `CALL` xabar in'ektsiya qila olardi.
     */
    suspend fun invite(calleeId: String, media: String, sdp: String): CallAck? = emit(
        EVENT_INVITE,
        buildJsonObject {
            put("calleeId", JsonPrimitive(calleeId))
            put("media", JsonPrimitive(media))
            put("sdp", JsonPrimitive(sdp))
        },
    )

    suspend fun accept(callId: String, sdp: String): CallAck? = emit(
        EVENT_ACCEPT,
        buildJsonObject {
            put("callId", JsonPrimitive(callId))
            put("sdp", JsonPrimitive(sdp))
        },
    )

    /**
     * ICE `connected` bo'lganda **va har qayta ulanishdan keyin** yuboriladi.
     *
     * Ikkinchi holat ixtiyoriy emas: socket uzilganda server 20 soniyalik oyna quradi va
     * uni **faqat o'sha talabadan, o'sha qo'ng'iroq uchun kelgan `call:*` freym** bekor
     * qiladi. Qayta ulanishning o'zi yetarli emas — yangi socket hech qanday `callId`
     * olib kelmaydi va server uni nimaga bog'lashni bilmaydi. Natijada media oqib turgan
     * **sog'lom** qo'ng'iroq 20 soniyadan keyin `FAILED` bilan yopilardi
     * (`handoff/09-CALLS-PREREQUISITES.md` §2).
     *
     * Idempotent: qo'ng'iroq allaqachon `ACTIVE` bo'lsa holat yozuvi no-op bo'ladi va
     * faqat mavjudlik belgisi yangilanadi.
     */
    suspend fun connected(callId: String): CallAck? = emit(EVENT_CONNECTED, callIdPayload(callId))

    suspend fun decline(callId: String, reason: String): CallAck? = emit(
        EVENT_DECLINE,
        buildJsonObject {
            put("callId", JsonPrimitive(callId))
            put("reason", JsonPrimitive(reason))
        },
    )

    suspend fun cancel(callId: String): CallAck? = emit(EVENT_CANCEL, callIdPayload(callId))

    suspend fun end(callId: String): CallAck? = emit(EVENT_END, callIdPayload(callId))

    /**
     * Socket'ning saqlangan token `exp` ini yangilaydi — **socket uzilmaydi, qo'ng'iroq
     * to'xtamaydi**.
     *
     * ⚠️ Boshqa talabaning tokenini yubormang: bu yangilash emas, sessiya almashtirish deb
     * qaraladi va socket **uziladi**.
     */
    suspend fun auth(token: String): CallAck? = emit(
        EVENT_AUTH,
        buildJsonObject { put("token", JsonPrimitive(token)) },
    )

    // --- Klient → server (o'zgarishsiz uzatiladiganlar) ----------------------------------

    /**
     * «Telefonim jiringlayapti» — **chaqirilgan** yuboradi, server chaquvchiga uzatadi.
     *
     * Spec'da bu faqat S → K edi; endi K → S → K (`handoff/09-CALLS-DEVIATIONS.md` §5).
     * Yuborilmasa chaquvchi «jiringlayapti» holatini **hech qachon ko'rmaydi**.
     */
    suspend fun sendRinging(callId: String): CallAck? = emit(EVENT_RINGING, callIdPayload(callId))

    /**
     * Bitta ICE nomzodi.
     *
     * Faqat uchta kalit yuboriladi — `usernameFragment` va boshqalari **atayin tashlanadi**
     * (`forbidNonWhitelisted`). `sdpMid` bo'sh bo'lsa nomzod umuman yuborilmaydi: bu
     * nomzodlarni tugatish markeri va uni WebRTC o'zi hal qiladi.
     *
     * ⚠️ Nomzodlar **trickle** qilinadi — kelgan sari birma-bir. Yig'ilgan to'plamni bir
     * yo'la yuborish socket bucket'ini (30 token, sekundiga 15) ~2 soniyada bo'shatadi va
     * `RATE_LIMITED` keltiradi (`…PROTOCOL.md` §10).
     */
    suspend fun sendIce(callId: String, candidate: String, sdpMid: String?, sdpMLineIndex: Int): CallAck? {
        val payload = icePayloadOrNull(callId, candidate, sdpMid, sdpMLineIndex) ?: return null
        return emit(EVENT_ICE, payload)
    }

    suspend fun sendRenegotiate(callId: String, sdp: String): CallAck? = emit(
        EVENT_RENEGOTIATE,
        buildJsonObject {
            put("callId", JsonPrimitive(callId))
            put("sdp", JsonPrimitive(sdp))
        },
    )

    suspend fun sendMediaState(callId: String, audioEnabled: Boolean, videoEnabled: Boolean): CallAck? = emit(
        EVENT_MEDIA_STATE,
        buildJsonObject {
            put("callId", JsonPrimitive(callId))
            put("audioEnabled", JsonPrimitive(audioEnabled))
            put("videoEnabled", JsonPrimitive(videoEnabled))
        },
    )

    // --- Ichki ---------------------------------------------------------------------------

    private fun callIdPayload(callId: String): JsonObject =
        buildJsonObject { put("callId", JsonPrimitive(callId)) }

    private suspend fun emit(event: String, payload: JsonObject): CallAck? {
        val ack = socket.emitWithAck(event, payload) ?: return null
        return runCatching { json.decodeFromJsonElement(CallAck.serializer(), ack) }.getOrNull()
    }

    private inline fun <reified T : Any> eventsOf(name: String): Flow<T> =
        socket.events.mapNotNull { event ->
            if (event.name != name) return@mapNotNull null
            val data = event.data ?: return@mapNotNull null
            runCatching { json.decodeFromJsonElement<T>(data) }.getOrNull()
        }

    companion object {
        /** Socket.IO namespace — URL yo'li emas. */
        const val NAMESPACE = "/calls"

        /** SDP uzunligi chegarasi (`invite`, `accept`, `renegotiate`). */
        const val MAX_SDP = 65_536

        /** Nomzod qatorining chegarasi. */
        const val MAX_CANDIDATE = 512

        /** `sdpMLineIndex` chegarasi. */
        const val MAX_MLINE_INDEX = 64

        // Klient → server
        const val EVENT_INVITE = "call:invite"
        const val EVENT_ACCEPT = "call:accept"
        const val EVENT_CONNECTED = "call:connected"
        const val EVENT_DECLINE = "call:decline"
        const val EVENT_CANCEL = "call:cancel"
        const val EVENT_END = "call:end"
        const val EVENT_AUTH = "call:auth"

        // Server → klient
        const val EVENT_INCOMING = "call:incoming"
        const val EVENT_ACCEPTED = "call:accepted"
        const val EVENT_TAKEN = "call:taken"
        const val EVENT_DECLINED = "call:declined"
        const val EVENT_CANCELED = "call:canceled"
        const val EVENT_ENDED = "call:ended"

        // Ikki yo'nalishda ham bir xil nom (payload ham bir xil).
        const val EVENT_RINGING = "call:ringing"
        const val EVENT_ICE = "call:ice"
        const val EVENT_RENEGOTIATE = "call:renegotiate"
        const val EVENT_MEDIA_STATE = "call:media-state"

        /**
         * `call:ice` payload'i yoki `null` — nomzod chegaradan o'tmasa.
         *
         * Chaqiruvchidan ayrilgan, chunki cheklarni **tarmoqqa chiqmasdan** sinash kerak:
         * WS xato ack'i qaysi maydon buzilganini aytmaydi, ya'ni xato ishlab chiqarishda
         * faqat «qo'ng'iroq ulanmayapti» bo'lib ko'rinardi.
         *
         * ⚠️ Obyektda **aynan uchta kalit** bo'ladi: server `forbidNonWhitelisted` rejimida
         * va `RTCIceCandidate.toJSON()` qo'shadigan `usernameFragment` `VALIDATION_ERROR`
         * keltirardi.
         */
        internal fun icePayloadOrNull(
            callId: String,
            candidate: String,
            sdpMid: String?,
            sdpMLineIndex: Int,
        ): JsonObject? {
            // Bo'sh `sdpMid` — nomzodlarni tugatish markeri; uni WebRTC o'zi hal qiladi.
            if (sdpMid.isNullOrEmpty() || sdpMid.length > MAX_SDP_MID) return null
            if (candidate.isEmpty() || candidate.length > MAX_CANDIDATE) return null
            if (sdpMLineIndex !in 0..MAX_MLINE_INDEX) return null
            return buildJsonObject {
                put("callId", JsonPrimitive(callId))
                put(
                    "candidate",
                    buildJsonObject {
                        put("candidate", JsonPrimitive(candidate))
                        put("sdpMid", JsonPrimitive(sdpMid))
                        put("sdpMLineIndex", JsonPrimitive(sdpMLineIndex))
                    },
                )
            }
        }

        /** `candidate.sdpMid` uzunligi chegarasi. */
        const val MAX_SDP_MID = 32
    }
}
