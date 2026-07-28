package dev.feature.chat.domain.model

import dev.feature.connections.domain.model.StudentSummary
import kotlinx.datetime.Instant

/** v1 da faqat `DIRECT` yaratiladi; `GROUP` — keyingi bosqich. */
enum class ConversationType { DIRECT, GROUP }

/**
 * Xabar turi.
 *
 * ⚠️ Backend hozir **faqat `TEXT` yaratadi** — `SendMessageDto` da `type` maydoni yo'q
 * (qarang: `CHAT_MEDIA_AND_CALLS_BACKEND.md` §0). Shuning uchun `IMAGE` va `STICKER`
 * turlari **klient tomonida** aniqlanadi: rasm xabarining tanasi — rasm havolasi,
 * stikerniki — yakka emoji. Ikkalasi ham eski klientlarda o'qiladigan holda ko'rinadi
 * (havola / emoji), backend tipli xabarni qo'shganda esa mapper serverning `type` ini
 * afzal ko'radi va bu evristika o'zi ishlamay qoladi.
 */
enum class MessageType { TEXT, IMAGE, GIF, VIDEO, VOICE, FILE, STICKER, CALL, SYSTEM }

/** Xabarning **local** yuborilish holati (serverda bunday maydon yo'q). */
enum class MessageStatus {
    /** Ekranda ko'rsatildi, lekin server hali tasdiqlamadi. */
    SENDING,

    /** Server qabul qildi — `seq` va `id` haqiqiy. */
    SENT,

    /** Yuborib bo'lmadi — foydalanuvchi qayta urinishi mumkin (o'sha `clientMsgId` bilan). */
    FAILED,
}

/** Suhbatning o'zi. */
data class Conversation(
    val id: String,
    val type: ConversationType = ConversationType.DIRECT,
    /** Oxirgi xabar vaqti. Yangi (bo'sh) suhbatda `null`. */
    val lastMessageAt: Instant? = null,
)

/**
 * Suhbatlar ro'yxatidagi bitta qator.
 *
 * [other].`online` / `lastSeenAt` — Redis'dan jonli o'qiladi va WS `presence:update` bilan
 * yangilanadi. Suhbatdosh `lastSeenVisibility = NOBODY` qo'ygan bo'lsa server ikkalasini
 * ham yashiradi (`false` / `null`).
 */
data class ConversationItem(
    val conversation: Conversation,
    val other: StudentSummary,
    val lastMessage: Message? = null,
    val unreadCount: Int = 0,
    /**
     * Suhbatdosh **o'qigan** eng yuqori `seq` — chiquvchi xabar shundan past yoki teng
     * bo'lsa "o'qildi" (✓✓ yorqin). Ikki manba: WS `message:read` va ro'yxat javobidagi
     * `peerReadSeq` — ya'ni ilova qayta ochilganda holat tiklanadi.
     */
    val otherReadSeq: Int = 0,
    /**
     * Suhbatdoshning **qurilmasi olgan** eng yuqori `seq` (WS `message:delivered` /
     * `peerDeliveredSeq`). Shundan past `seq` — "yetkazildi" (✓✓ xira).
     */
    val otherDeliveredSeq: Int = 0,
    /** Faqat local bayroq — backendda arxivlash endpointi yo'q. */
    val archived: Boolean = false,
) {
    val id: String get() = conversation.id
}

/**
 * Suhbat ichidagi bitta xabar.
 *
 * [seq] — suhbat ichidagi tartib o'qi: 1 dan boshlanadi, bo'shliq qoldirmaydi. **Tartiblash,
 * tarix kursori (`before`), qayta ulanishda yetishib olish (`after`) va o'qildi belgisi —
 * hammasi shu bo'yicha**, `createdAt` bo'yicha emas. Hali yuborilmagan xabarda `0`.
 */
data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val seq: Int,
    val body: String,
    val createdAt: Instant,
    val type: MessageType = MessageType.TEXT,
    val status: MessageStatus = MessageStatus.SENT,
    val clientMsgId: String? = null,
    /** `IMAGE`/`GIF`/`VIDEO`/`VOICE`/`FILE` da to'ladi; `TEXT` da doim `null`. */
    val attachment: Attachment? = null,
    /**
     * Bir martada yuborilgan rasmlarni bog'laydi. Backend bu maydonni hali qaytarmaydi,
     * shuning uchun hozir **klient o'zi qo'yadi** va u faqat shu qurilmada saqlanadi —
     * boshqa tomonda albom qo'shni xabarlardan taxmin qilinadi.
     */
    val albumId: String? = null,
)

/**
 * Media biriktirmasi.
 *
 * [width]/[height] `0` bo'lishi mumkin — backend hozir rasm o'lchamini qaytarmaydi
 * (`MediaUploadResponseDto` da faqat `url` bor). Shunda UI kvadrat nisbatga tushadi.
 */
data class Attachment(
    val url: String,
    val thumbUrl: String? = null,
    val mimeType: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val sizeBytes: Long = 0,
    val durationMs: Int = 0,
) {
    /** Ko'rsatish uchun eng arzon havola — ro'yxatda kichigi, ochilganda to'lig'i. */
    val previewUrl: String get() = thumbUrl ?: url

    /** Nisbat ma'lum bo'lmasa `null` — chaqiruvchi o'zining odatiy nisbatini qo'yadi. */
    val aspectRatio: Float? get() = if (width > 0 && height > 0) width.toFloat() / height else null
}

/** Yuborish uchun tayyorlangan rasm — domen qatlami platformadagi tanlagichga bog'lanmasin. */
class OutgoingImage(
    val bytes: ByteArray,
    val fileName: String,
)
