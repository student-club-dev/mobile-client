package dev.feature.chat.data.realtime

import dev.core.network.ws.SocketIoClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement

// ---------------------------------------------------------------------------
// WS payload'lari — Swagger'da YO'Q. Yagona manba: handoff `chat.md` §7–§9.
// ---------------------------------------------------------------------------

/** `message:new` — yangi xabar (jo'natuvchining o'ziga ham keladi!). */
@Serializable
data class WsMessageNew(
    val conversationId: String,
    val message: WsMessage,
)

/** WS ichidagi xabar — REST'dagi `MessageDto` bilan bir xil shakl. */
@Serializable
data class WsMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val seq: Int,
    val type: String = "TEXT",
    val body: String? = null,
    val createdAt: String,
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
 * `CONVERSATION_NOT_FOUND`, `NOT_CONNECTED`, `MESSAGE_EMPTY`, `UNAUTHORIZED`, `INTERNAL_ERROR`.
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

@Serializable
data class WsError(
    val code: String? = null,
    @SerialName("message") val text: String? = null,
)

/**
 * Chat WebSocket kanali — [SocketIoClient] ustidagi **tiplangan** qatlam.
 *
 * Bu klass protokolni bilmaydi (u `SocketIoClient` da), faqat `chat.md` §8 dagi hodisa
 * nomlari va payload'larni biladi.
 *
 * ⚠️ `message:read`, `message:delivered`, `typing:*` — **ack qaytarmaydi va xato bermaydi**
 * ("eng yaxshi harakat"). Muhim ish faqat `message:send` va REST orqali bo'ladi.
 */
class ChatSocket(private val socket: SocketIoClient) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val connected: StateFlow<Boolean> get() = socket.connected

    fun start() = socket.start()
    fun stop() = socket.stop()

    val newMessages: Flow<WsMessageNew> = socket.eventsOf(EVENT_MESSAGE_NEW)
    val readCursors: Flow<WsCursor> = socket.eventsOf(EVENT_MESSAGE_READ)
    val deliveredCursors: Flow<WsCursor> = socket.eventsOf(EVENT_MESSAGE_DELIVERED)
    val typing: Flow<WsTyping> = socket.eventsOf(EVENT_TYPING)
    val presence: Flow<WsPresence> = socket.eventsOf(EVENT_PRESENCE)

    /**
     * Xabar yuboradi va ack kutadi. `null` — WS ulanmagan yoki ack kelmadi; chaqiruvchi
     * REST zaxirasiga o'tsin.
     */
    suspend fun send(conversationId: String, clientMsgId: String, body: String): WsSendAck? {
        val ack = socket.emitWithAck(
            EVENT_MESSAGE_SEND,
            buildJsonObject {
                put("conversationId", JsonPrimitive(conversationId))
                put("clientMsgId", JsonPrimitive(clientMsgId))
                put("body", JsonPrimitive(body))
            },
        ) ?: return null
        return runCatching { json.decodeFromJsonElement(WsSendAck.serializer(), ack) }.getOrNull()
    }

    suspend fun markRead(conversationId: String, seq: Int) =
        emitCursor(EVENT_MESSAGE_READ, conversationId, seq)

    suspend fun markDelivered(conversationId: String, seq: Int) =
        emitCursor(EVENT_MESSAGE_DELIVERED, conversationId, seq)

    suspend fun setTyping(conversationId: String, typing: Boolean) {
        socket.emit(
            if (typing) EVENT_TYPING_START else EVENT_TYPING_STOP,
            buildJsonObject { put("conversationId", JsonPrimitive(conversationId)) },
        )
    }

    private suspend fun emitCursor(event: String, conversationId: String, seq: Int) {
        // Klient → server yo'nalishida `byStudentId` YO'Q (nomi bir xil, payload boshqa).
        socket.emit(
            event,
            buildJsonObject {
                put("conversationId", JsonPrimitive(conversationId))
                put("seq", JsonPrimitive(seq))
            },
        )
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
