package dev.feature.chat.data.remote

import dev.core.common.Resource
import dev.core.common.map
import dev.core.common.network.NetworkConnectivity
import dev.core.network.generated.api.ChatApi
import dev.core.network.generated.model.ConversationPageDto
import dev.core.network.generated.model.MarkReadDto
import dev.core.network.generated.model.MessageDto
import dev.core.network.generated.model.MessageListDto
import dev.core.network.generated.model.OpenDirectDto
import dev.core.network.generated.model.SendMessageDto
import dev.core.network.response.safeCall

/**
 * Chat REST qatlami — generatsiya qilingan [ChatApi] ustida (`chat.md` §2–§6).
 *
 * WS ishlaganda ham REST kerak: tarix (`?before=`), qayta ulanishda yetishib olish
 * (`?after=`), ro'yxat va **xabar yuborishning zaxira yo'li**.
 */
class ChatRemoteDataSource(
    private val api: ChatApi,
    private val connectivity: NetworkConnectivity,
) {

    /** `GET /v1/conversations` — sahifa **1** dan boshlanadi. */
    suspend fun conversations(page: Int = 1, size: Int = CONVERSATIONS_PAGE_SIZE): Resource<ConversationPageDto> =
        safeCall(connectivity) { api.conversationsList(page = page, size = size).body() }

    /** `POST /v1/conversations` — idempotent: mavjud suhbat bo'lsa o'sha qaytadi. */
    suspend fun openDirect(studentId: String): Resource<String> =
        safeCall(connectivity) { api.callOpen(OpenDirectDto(studentId = studentId)).body() }
            .map { it.id }

    /**
     * Kursorli tarix. Ikkalasi berilsa **`after` ustun** (`before` e'tiborsiz qoladi) —
     * shuning uchun chaqiruvchi faqat bittasini uzatsin.
     */
    suspend fun messages(
        conversationId: String,
        before: Int? = null,
        after: Int? = null,
        size: Int = MESSAGES_PAGE_SIZE,
    ): Resource<MessageListDto> = safeCall(connectivity) {
        api.history(id = conversationId, before = before, after = after, size = size).body()
    }

    /** Zaxira yo'l bilan yuborish. [clientMsgId] takror yuborishni idempotent qiladi (C6). */
    suspend fun send(conversationId: String, body: String, clientMsgId: String): Resource<MessageDto> =
        safeCall(connectivity) {
            api.conversationsSend(
                id = conversationId,
                sendMessageDto = SendMessageDto(body = body, clientMsgId = clientMsgId),
            ).body()
        }

    /** O'qildi kursori — WS ishlamaganda shu ishlatiladi. */
    suspend fun markRead(conversationId: String, seq: Int): Resource<Unit> =
        safeCall(connectivity) { api.read(conversationId, MarkReadDto(seq = seq)).body() }

    companion object {
        const val CONVERSATIONS_PAGE_SIZE = 50
        const val MESSAGES_PAGE_SIZE = 30
    }
}
