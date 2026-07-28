package dev.feature.chat.domain.model

import dev.feature.connections.domain.model.StudentSummary
import kotlinx.datetime.Instant

/** v1 da faqat `DIRECT` yaratiladi; `GROUP` — keyingi bosqich. */
enum class ConversationType { DIRECT, GROUP }

/** v1 da faqat `TEXT` yoziladi; qolganlari kelajak uchun. */
enum class MessageType { TEXT, IMAGE, FILE, VOICE, SYSTEM }

/** Xabarning **local** yuborilish holati (serverda bunday maydon yo'q). */
enum class MessageStatus {
    /** Ekranда ko'rsatildi, lekin server hali tasdiqlamadi. */
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
)
