package dev.feature.chat.data.realtime

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

// ---------------------------------------------------------------------------
// WS payload'lari — Swagger'da YO'Q (OpenAPI HTTP ni tasvirlaydi, hodisalarni emas).
// Yagona manba: `handoff/chat.md`.
// ---------------------------------------------------------------------------

/** `message:new` — yangi xabar (jo'natuvchining o'ziga ham keladi!). */
@Serializable
data class WsMessageNew(
    val conversationId: String,
    val message: WsMessage,
)

/**
 * WS ichidagi xabar — REST'dagi `MessageDto` bilan **aynan bir xil shakl**
 * (`handoff/chat.md`: "message:new → message to'liq MessageDto").
 *
 * ⚠️ [clientMsgId] **faqat jo'natuvchining o'z qurilmalariga** to'ldiriladi; qabul qiluvchiga
 * `null` keladi. Optimistik nusxani aynan shu maydon bo'yicha topamiz.
 */
@Serializable
data class WsMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val seq: Int,
    val type: String = "TEXT",
    val body: String? = null,
    val clientMsgId: String? = null,
    /** O'chirilgan xabar (soft delete) — tarixda tombstone bo'lib qoladi. */
    val deletedAt: String? = null,
    /** Bir martada yuborilgan rasmlarni bog'laydi — endi SERVER qaytaradi. */
    val albumId: String? = null,
    val attachment: WsAttachment? = null,
    val sticker: WsSticker? = null,
    val createdAt: String,
)

/**
 * `MessageDto.attachment`. Barcha maydonlar ixtiyoriy qilingan: spec ularni majburiy
 * (`nullable`) deb belgilaydi, lekin bitta yetishmayotgan maydon butun xabarni pars
 * qilinmaydigan qilib qo'ymasligi kerak — u holda suhbat ekranda yo'qolardi.
 */
@Serializable
data class WsAttachment(
    val id: String,
    val kind: String = "IMAGE",
    val status: String = "READY",
    val url: String = "",
    val thumbUrl: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long = 0,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Int? = null,
    val waveform: List<Int> = emptyList(),
    val fileName: String? = null,
    val blurHash: String? = null,
    val isAnimated: Boolean = false,
    val provider: String? = null,
    val externalId: String? = null,
)

/** `MessageDto.sticker`. */
@Serializable
data class WsSticker(
    val id: String,
    val packId: String = "",
    val emoji: String = "",
    val url: String? = null,
    val width: Int = 0,
    val height: Int = 0,
)

/**
 * `message:deleted` — **ikkala** a'zoga ketadi (`handoff/api-changes.md` §4b).
 *
 * Xabar tarixdan yo'qolmaydi: `seq` joyida qoladi, faqat tanasi bo'shatiladi va u
 * o'qilmaganlar sanog'idan chiqadi.
 */
@Serializable
data class WsMessageDeleted(
    val conversationId: String,
    val messageId: String,
    val seq: Int = 0,
)

/**
 * `media:ready` — video transkodlash tugadi.
 *
 * Xabar `attachment.status = "PROCESSING"` bilan yuborilgan bo'lsa, shu hodisada
 * biriktirma almashtiriladi. Xatolik bo'lsa `status = "FAILED"` keladi — hech qachon
 * jim `READY` emas.
 */
@Serializable
data class WsMediaReady(
    val mediaId: String,
    val conversationId: String,
    val messageId: String,
    val attachment: WsAttachment? = null,
)

/** `message:read` / `message:delivered` (server → klient — `byStudentId` BOR). */
@Serializable
data class WsCursor(
    val conversationId: String,
    val seq: Int,
    val byStudentId: String? = null,
)

/** `typing`. */
@Serializable
data class WsTyping(
    val conversationId: String,
    val studentId: String,
    val isTyping: Boolean,
)

/** `presence:update`. `online = true` bo'lganda `lastSeenAt` — `null`. */
@Serializable
data class WsPresence(
    val studentId: String,
    val online: Boolean,
    val lastSeenAt: String? = null,
)

/**
 * `message:send` ning **ack** javobi.
 *
 * ⚠️ WS xatolari `BaseResponse` konvertida kelmaydi va HTTP statusi yo'q — faqat
 * `status: "error"` va `{ code, message }`. `code` REST'dagi bilan bir xil to'plam:
 * `CONVERSATION_NOT_FOUND`, `NOT_CONNECTED`, `MESSAGE_EMPTY`, `UNAUTHORIZED`,
 * `TOKEN_EXPIRED`, `VALIDATION_ERROR`, `RATE_LIMITED`, `INTERNAL_ERROR`.
 */
@Serializable
data class WsSendAck(
    val clientMsgId: String? = null,
    val id: String? = null,
    val seq: Int? = null,
    val createdAt: String? = null,
    val status: String? = null,
    val error: WsError? = null,
) {
    val isSent: Boolean get() = status == "sent" && id != null && seq != null
}

/**
 * `message:read` / `message:delivered` ning ack'i (2026-07-29 dan bor, §17.8).
 *
 * `{ conversationId, seq, status: "ok" }`. Ack kelmasa kursor yo'lda yo'qolgan bo'lishi
 * mumkin — chaqiruvchi REST zaxirasiga tushadi.
 */
@Serializable
data class WsCursorAck(
    val conversationId: String? = null,
    val seq: Int? = null,
    val status: String? = null,
    val error: WsError? = null,
) {
    val isOk: Boolean get() = status == "ok"
}

@Serializable
data class WsError(
    val code: String? = null,
    @SerialName("message") val text: String? = null,
)

/**
 * Chat WebSocket kanali — [SocketIoClient] ustidagi **tiplangan** qatlam.
 *
 * Bu klass protokolni bilmaydi (u `SocketIoClient` da), faqat `handoff/chat.md` dagi hodisa
 * nomlari va payload'larni biladi.
 *
 * ⚠️ `typing:*` **ataylab ack qaytarmaydi** — ular efemer, yo'qolsa zarari yo'q.
 * `message:read`/`message:delivered` esa endi ack qaytaradi, shuning uchun ular zaxira
 * REST yo'liga tushishi kerakligini aniq bila oladi.
 */
class ChatSocket(private val socket: SocketIoClient) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val connected: StateFlow<Boolean> get() = socket.connected

    fun start() = socket.start()
    fun stop() = socket.stop()

    val newMessages: Flow<WsMessageNew> = socket.eventsOf(EVENT_MESSAGE_NEW)
    val deletedMessages: Flow<WsMessageDeleted> = socket.eventsOf(EVENT_MESSAGE_DELETED)
    val mediaReady: Flow<WsMediaReady> = socket.eventsOf(EVENT_MEDIA_READY)
    val readCursors: Flow<WsCursor> = socket.eventsOf(EVENT_MESSAGE_READ)
    val deliveredCursors: Flow<WsCursor> = socket.eventsOf(EVENT_MESSAGE_DELIVERED)
    val typing: Flow<WsTyping> = socket.eventsOf(EVENT_TYPING)
    val presence: Flow<WsPresence> = socket.eventsOf(EVENT_PRESENCE)

    /**
     * Xabar yuboradi va ack kutadi. `null` — WS ulanmagan yoki ack kelmadi; chaqiruvchi
     * REST zaxirasiga o'tsin.
     *
     * Maydonlarning hammasi ixtiyoriy va **berilmagani umuman yuborilmaydi**: [type] siz
     * server xabarni `TEXT` deb oladi, ya'ni eski klient hech narsa o'zgartirmasdan
     * ishlayveradi (`handoff/chat.md`, "type berilmasa TEXT"). Qaysi maydon qaysi tur
     * bilan ketishi — o'sha hujjatdagi jadval; tekshiruv chaqiruvchida (`SendPayload`).
     *
     * [gif] — qidiruvdan olingan GIF havolasi (`GifRefDto` shaklida). Xom `JsonObject`
     * sifatida uzatiladi: GIF paneli alohida moduldadir va bu klass unga bog'lanmasin.
     */
    suspend fun send(
        conversationId: String,
        clientMsgId: String,
        body: String? = null,
        type: String? = null,
        mediaId: String? = null,
        stickerId: String? = null,
        albumId: String? = null,
        gif: JsonObject? = null,
    ): WsSendAck? {
        val ack = socket.emitWithAck(
            EVENT_MESSAGE_SEND,
            buildJsonObject {
                put("conversationId", JsonPrimitive(conversationId))
                put("clientMsgId", JsonPrimitive(clientMsgId))
                type?.let { put("type", JsonPrimitive(it)) }
                body?.let { put("body", JsonPrimitive(it)) }
                mediaId?.let { put("mediaId", JsonPrimitive(it)) }
                stickerId?.let { put("stickerId", JsonPrimitive(it)) }
                albumId?.let { put("albumId", JsonPrimitive(it)) }
                gif?.let { put("gif", it) }
            },
        ) ?: return null
        return runCatching { json.decodeFromJsonElement(WsSendAck.serializer(), ack) }.getOrNull()
    }

    /** Kursorni suradi va ack kutadi. `false` — yo'lda yo'qoldi, REST bilan qayta yuboring. */
    suspend fun markRead(conversationId: String, seq: Int): Boolean =
        emitCursor(EVENT_MESSAGE_READ, conversationId, seq)

    suspend fun markDelivered(conversationId: String, seq: Int): Boolean =
        emitCursor(EVENT_MESSAGE_DELIVERED, conversationId, seq)

    suspend fun setTyping(conversationId: String, typing: Boolean) {
        socket.emit(
            if (typing) EVENT_TYPING_START else EVENT_TYPING_STOP,
            buildJsonObject { put("conversationId", JsonPrimitive(conversationId)) },
        )
    }

    private suspend fun emitCursor(event: String, conversationId: String, seq: Int): Boolean {
        // Klient → server yo'nalishida `byStudentId` YO'Q (nomi bir xil, payload boshqa).
        val ack = socket.emitWithAck(
            event,
            buildJsonObject {
                put("conversationId", JsonPrimitive(conversationId))
                put("seq", JsonPrimitive(seq))
            },
        ) ?: return false
        return runCatching { json.decodeFromJsonElement(WsCursorAck.serializer(), ack) }
            .getOrNull()?.isOk == true
    }

    private inline fun <reified T : Any> SocketIoClient.eventsOf(name: String): Flow<T> =
        events.mapNotNull { event ->
            if (event.name != name) return@mapNotNull null
            val data = event.data ?: return@mapNotNull null
            runCatching { json.decodeFromJsonElement<T>(data) }.getOrNull()
        }

    companion object {
        /** Socket.IO namespace — URL yo'li emas. */
        const val NAMESPACE = "/chat"

        // Server → klient
        const val EVENT_MESSAGE_NEW = "message:new"
        const val EVENT_MESSAGE_DELETED = "message:deleted"
        const val EVENT_MEDIA_READY = "media:ready"
        const val EVENT_PRESENCE = "presence:update"
        const val EVENT_TYPING = "typing"

        // Ikki yo'nalishda ham bir xil nom, LEKIN har xil payload.
        const val EVENT_MESSAGE_READ = "message:read"
        const val EVENT_MESSAGE_DELIVERED = "message:delivered"

        // Klient → server
        const val EVENT_MESSAGE_SEND = "message:send"
        const val EVENT_TYPING_START = "typing:start"
        const val EVENT_TYPING_STOP = "typing:stop"
    }
}
