package dev.feature.chat.data.remote

import dev.core.common.Resource
import dev.core.common.error.AppException
import dev.core.common.map
import dev.core.common.network.NetworkConnectivity
import dev.core.network.generated.api.ChatApi
import dev.core.network.generated.model.AttachmentDto
import dev.core.network.generated.model.BulkDeleteResultDto
import dev.core.network.generated.model.ClearHistoryResultDto
import dev.core.network.generated.model.DeleteMessagesDto
import dev.core.network.generated.model.DeleteScopeDto
import dev.core.network.generated.model.QuoteInputDto
import dev.core.network.generated.model.ConversationListItemDto
import dev.core.network.generated.model.ConversationPageDto
import dev.core.network.generated.model.GifRefDto
import dev.core.network.generated.model.StickerRefDto
import dev.core.network.generated.model.MarkDeliveredDto
import dev.core.network.generated.model.MarkReadDto
import dev.core.network.generated.model.MessageDto
import dev.core.network.generated.model.MessageListDto
import dev.core.network.generated.model.OpenDirectDto
import dev.core.network.generated.model.SendMessageDto
import dev.core.network.generated.model.UnreadCountDto
import dev.core.network.media.ChatMediaKind
import dev.core.network.media.MediaQuality
import dev.core.network.media.MediaUploader
import dev.core.network.media.UploadProgress
import dev.core.network.response.safeCall
import dev.feature.chat.data.mapper.SendPayload
import dev.feature.chat.data.mapper.parseEnum
import dev.feature.chat.domain.model.DeleteScope
import dev.feature.chat.domain.model.MessageType
import kotlinx.serialization.json.Json

/**
 * Chat REST qatlami — generatsiya qilingan [ChatApi] ustida (`handoff/03-WEBSOCKET.md`).
 *
 * WS ishlaganda ham REST kerak: tarix (`?before=`), qayta ulanishda yetishib olish
 * (`?after=`), ro'yxat, **xabar yuborishning zaxira yo'li** va kursorlarning zaxirasi.
 */
class ChatRemoteDataSource(
    private val api: ChatApi,
    private val connectivity: NetworkConnectivity,
    private val media: MediaUploader,
) {

    /** `GET /v1/conversations` — sahifa **1** dan boshlanadi. */
    suspend fun conversations(page: Int = 1, size: Int = CONVERSATIONS_PAGE_SIZE): Resource<ConversationPageDto> =
        safeCall(connectivity) { api.conversationsList(page = page, size = size).body() }

    /**
     * `GET /v1/conversations/{id}` — ro'yxatdagi qator bilan **aynan bir xil shakl**.
     *
     * Push bosilganda yoki notanish suhbatdan xabar kelganda butun ro'yxatni qayta
     * yuklamaslik uchun (`handoff/02-API-CHANGES.md` §4b).
     */
    suspend fun conversation(conversationId: String): Resource<ConversationListItemDto> =
        safeCall(connectivity) { api.one(conversationId).body() }

    /** `GET /v1/conversations/unread-count` — tab badge'i uchun ikkala hisoblagich. */
    suspend fun unreadCount(): Resource<UnreadCountDto> =
        safeCall(connectivity) { api.unreadCount().body() }

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
        /**
         * Sitataga sakrash — oyna shu `seq` ning ikkala tomonidan olinadi. `before`/`after`
         * bilan **birga yuborilmaydi** (`422`), shuning uchun chaqiruvchi bittasini tanlaydi.
         */
        around: Int? = null,
        size: Int = MESSAGES_PAGE_SIZE,
    ): Resource<MessageListDto> = safeCall(connectivity) {
        api.history(id = conversationId, before = before, after = after, around = around, size = size).body()
    }

    /**
     * `POST /v1/media/chat-upload` — biriktirmani yuklaydi va `mediaId` qaytaradi.
     *
     * `conversationId` ruxsat tekshiruvi: a'zo bo'lmasangiz yoki bog'lanish uzilgan bo'lsa
     * `403 NOT_CONNECTED`. Natija **bir martalik** — bitta `mediaId` faqat bitta xabarga.
     */
    suspend fun uploadAttachment(
        conversationId: String,
        bytes: ByteArray,
        fileName: String,
        kind: ChatMediaKind,
        /** Video sifati (`AUTO`/`HIGH`/`ORIGINAL`). Faqat videoga ta'sir qiladi. */
        quality: MediaQuality? = null,
        /** Yuborilgan baytlar ulushi (`0f..1f`) — ekrandagi foiz halqasi uchun. */
        onProgress: UploadProgress? = null,
    ): Resource<AttachmentDto> = safeCall(connectivity) {
        media.chatUpload(
            bytes = bytes,
            fileName = fileName,
            kind = kind,
            conversationId = conversationId,
            quality = quality,
            onProgress = onProgress,
        )
    }.withMediaServerMessage()

    /**
     * O'sha yuklash, lekin **diskdagi fayldan** — baytlar xotiraga o'qilmaydi.
     *
     * Video uchun: gigabaytlik `ByteArray` va uning multipart nusxasi birga arzon telefonni
     * xotiradan qoqib tashlardi.
     *
     * ⚠️ ~10 MB dan kattasi **rezyumlanadigan** yo'ldan ketadi (`/v1/media/upload/init` →
     * `part` → `complete`): bir martalik so'rov uzilganda noldan boshlanardi, endi esa
     * server olgan bo'laklar joyida qoladi. Bu [MediaUploader] ichida hal qilinadi —
     * chaqiruvchi uchun hech narsa o'zgarmaydi.
     */
    suspend fun uploadAttachmentFile(
        conversationId: String,
        path: String,
        sizeBytes: Long,
        fileName: String,
        kind: ChatMediaKind,
        quality: MediaQuality? = null,
        onProgress: UploadProgress? = null,
        /** Rezyumlanadigan sessiya ochilganda — bekor qilish uchun id. */
        onSession: ((String) -> Unit)? = null,
    ): Resource<AttachmentDto> = safeCall(connectivity) {
        media.chatUploadFile(
            path = path,
            sizeBytes = sizeBytes,
            fileName = fileName,
            kind = kind,
            conversationId = conversationId,
            quality = quality,
            onProgress = onProgress,
            onSession = onSession,
        )
    }.withMediaServerMessage()

    /**
     * Zaxira yo'l bilan yuborish. [clientMsgId] takror yuborishni idempotent qiladi (C6).
     *
     * `type` ataylab **doim** yuboriladi: `null` qoldirilsa server `TEXT` deb oladi va media
     * xabar oddiy matnga aylanib qolardi.
     */
    internal suspend fun send(
        conversationId: String,
        payload: SendPayload,
        clientMsgId: String,
    ): Resource<MessageDto> = safeCall(connectivity) {
        api.conversationsSend(
            id = conversationId,
            sendMessageDto = SendMessageDto(
                // `SendMessageDto.type` — `String` (spec'ning `lenientEnums` ro'yxati).
                // Domen enum'ining nomi bilan sim formati bir xil, ya'ni o'girish shart emas.
                type = payload.type.name,
                body = payload.wireBody,
                mediaId = payload.mediaId,
                gif = payload.gif?.let { json.decodeFromJsonElement(gifSerializer, it) },
                stickerId = payload.stickerId,
                sticker = payload.sticker?.let { json.decodeFromJsonElement(stickerSerializer, it) },
                albumId = payload.albumId,
                clientMsgId = clientMsgId,
                replyToMessageId = payload.replyToMessageId,
                quote = payload.quote?.let { QuoteInputDto(text = it.text, offset = it.offset) },
            ),
        ).body()
        // Media xabar (stiker/GIF/biriktirma) yuklashsiz ham shu yerdan ketadi — server
        // tomonda yiqilsa foydalanuvchi "umumiy server xatosi" emas, aniq sababni ko'radi.
    }.let { if (payload.type == MessageType.TEXT) it else it.withMediaServerMessage() }

    /**
     * `DELETE /v1/messages/{id}` — soft delete, **idempotent**. Javob — o'chirilgan
     * xabarning o'zi (`deletedAt` to'ldirilgan, `body` bo'sh).
     *
     * `scope` **ochiq yuboriladi**: tushirilsa server uni `EVERYONE` deb oladi va bu
     * chaqiruvchining niyatiga bog'liq bo'lib qolardi.
     */
    suspend fun deleteMessage(
        messageId: String,
        scope: DeleteScope = DeleteScope.EVERYONE,
    ): Resource<MessageDto> =
        safeCall(connectivity) { api.messagesRemove(messageId, scope.dto()).body() }

    /**
     * `POST /v1/messages/delete` — **bitta so'rovda** 1–100 ta xabar (hammasi bitta
     * suhbatdan). Ilgari har biri uchun alohida `DELETE` ketardi.
     *
     * Javobda `deleted` / `skipped` dan tashqari **qayta hisoblangan** `unreadCount` va
     * `lastMessage` ham keladi — ro'yxatni qayta so'ramaymiz.
     */
    suspend fun deleteMessages(ids: List<String>, scope: DeleteScope): Resource<BulkDeleteResultDto> =
        safeCall(connectivity) {
            api.removeMany(DeleteMessagesDto(ids = ids, scope = scope.dto())).body()
        }

    /**
     * `DELETE /v1/conversations/{id}/history` — suhbat ro'yxatda qoladi, tarixi ketadi.
     * Javobdagi `clearedBeforeSeq` — keshdan qayergacha o'chirish kerakligi.
     */
    suspend fun clearHistory(conversationId: String, scope: DeleteScope): Resource<ClearHistoryResultDto> =
        safeCall(connectivity) { api.clearHistory(conversationId, scope.dto()).body() }

    /** `DELETE /v1/conversations/{id}` — javob shakli [clearHistory] bilan bir xil. */
    suspend fun deleteConversation(conversationId: String, scope: DeleteScope): Resource<ClearHistoryResultDto> =
        safeCall(connectivity) { api.deleteConversation(conversationId, scope.dto()).body() }

    /** O'qildi kursori — WS ack kelmaganda shu ishlatiladi. */
    suspend fun markRead(conversationId: String, seq: Int): Resource<Unit> =
        safeCall(connectivity) { api.read(conversationId, MarkReadDto(seq = seq)).body() }

    /**
     * Yetkazildi kursori (§17.6). Busiz uzilgan ulanish jo'natuvchini abadiy **bitta**
     * belgichada qoldirardi — WS'dan boshqa yo'l yo'q edi.
     */
    suspend fun markDelivered(conversationId: String, seq: Int): Resource<Unit> =
        safeCall(connectivity) { api.delivered(conversationId, MarkDeliveredDto(seq = seq)).body() }

    private companion object {
        const val CONVERSATIONS_PAGE_SIZE = 50
        const val MESSAGES_PAGE_SIZE = 30

        /**
         * GIF/stiker qidiruv natijasi xom `JsonObject` sifatida keladi (panellar alohida
         * modulda), REST esa tiplangan DTO kutadi — shu yerda o'giriladi.
         */
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val gifSerializer = GifRefDto.serializer()
        val stickerSerializer = StickerRefDto.serializer()
    }
}

/**
 * Media yo'lidagi **5xx** ni aniqroq matnga almashtiradi.
 *
 * Umumiy "Serverda xatolik" media uchun yordam bermaydi: foydalanuvchi matn yuborsa ishlab
 * turgani uchun aybni o'z rasmidan yoki internetidan qidiradi. 5xx — server tomonning
 * nosozligi (biriktirma xizmati/transkodlash), shuning uchun shuni to'g'ridan-to'g'ri
 * aytamiz. 4xx'ga TEGILMAYDI — u yerda server o'zi aniq sabab beradi (hajm chegarasi,
 * `NOT_CONNECTED`, `MEDIA_ALREADY_USED`) va uni umumlashtirish yo'qotish bo'lardi.
 */
internal fun <T> Resource<T>.withMediaServerMessage(): Resource<T> =
    if (this is Resource.Error && error is AppException.Server) {
        copy(message = MEDIA_SERVER_MESSAGE)
    } else {
        this
    }

/** Domen qamrovi → generatsiya qilingan enum. Ikkisi bir xil, lekin bog'lanish bitta joyda. */
private fun DeleteScope.dto(): DeleteScopeDto = when (this) {
    DeleteScope.ME -> DeleteScopeDto.ME
    DeleteScope.EVERYONE -> DeleteScopeDto.EVERYONE
}

internal const val MEDIA_SERVER_MESSAGE =
    "Biriktirmani yuborib bo'lmadi — serverdagi media xizmati javob bermayapti. Birozdan so'ng qayta urining."
