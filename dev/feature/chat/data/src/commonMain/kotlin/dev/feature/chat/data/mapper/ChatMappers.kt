package dev.feature.chat.data.mapper

import dev.core.database.sql.ConversationEntity
import dev.core.database.sql.MessageEntity
import dev.core.network.generated.model.MessageDto
import dev.feature.chat.data.realtime.WsMessage
import dev.feature.chat.domain.model.Conversation
import dev.feature.chat.domain.model.ConversationItem
import dev.feature.chat.domain.model.ConversationType
import dev.feature.chat.domain.model.Message
import dev.feature.chat.domain.model.MessageStatus
import dev.feature.chat.domain.model.MessageType
import dev.feature.connections.domain.model.StudentSummary
import kotlinx.datetime.Instant

// --- DB → domen -------------------------------------------------------------------------

internal fun ConversationEntity.toDomain(): ConversationItem = ConversationItem(
    conversation = Conversation(
        id = id,
        type = parseEnum(type, ConversationType.DIRECT),
        lastMessageAt = lastMessageAt?.let(Instant::fromEpochMilliseconds),
    ),
    other = StudentSummary(
        id = otherId,
        username = otherUsername,
        fullName = otherFullName,
        avatarUrl = otherAvatarUrl,
        online = otherOnline != 0L,
        lastSeenAt = otherLastSeenAt?.let(Instant::fromEpochMilliseconds),
    ),
    // Ro'yxat qatorida to'liq xabar kerak emas — matn va kim yozgani yetarli.
    lastMessage = lastMessageBody?.let { text ->
        Message(
            id = "$id-last",
            conversationId = id,
            senderId = lastMessageSenderId.orEmpty(),
            seq = 0,
            body = text,
            createdAt = Instant.fromEpochMilliseconds(lastMessageAt ?: 0L),
        )
    },
    unreadCount = unreadCount.toInt(),
    otherReadSeq = otherReadSeq.toInt(),
    archived = archived != 0L,
)

internal fun MessageEntity.toDomain(): Message = Message(
    id = id,
    conversationId = conversationId,
    senderId = senderId,
    seq = seq.toInt(),
    body = body,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    type = parseEnum(type, MessageType.TEXT),
    status = parseEnum(status, MessageStatus.SENT),
    clientMsgId = clientMsgId,
)

// --- Server javoblari → kesh ustunlari ---------------------------------------------------

/** Serverdan kelgan xabarning keshga yoziladigan ko'rinishi. */
internal data class MessageRow(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val seq: Long,
    val type: String,
    val body: String,
    val createdAt: Long,
    val clientMsgId: String?,
    val status: String = MessageStatus.SENT.name,
)

internal fun MessageDto.toRow(clientMsgId: String? = null): MessageRow = MessageRow(
    id = id,
    conversationId = conversationId,
    senderId = senderId,
    seq = seq.toLong(),
    type = type.name,
    // v1 da faqat TEXT yoziladi, lekin sxemada `body` nullable — bo'sh matn bilan qoplaymiz.
    body = body.orEmpty(),
    createdAt = createdAt.toEpochMilliseconds(),
    clientMsgId = clientMsgId,
)

internal fun WsMessage.toRow(): MessageRow = MessageRow(
    id = id,
    conversationId = conversationId,
    senderId = senderId,
    seq = seq.toLong(),
    type = type,
    body = body.orEmpty(),
    createdAt = parseInstant(createdAt),
    clientMsgId = null,
)

/**
 * ISO-8601 → epoch ms. Server doim to'g'ri sana yuboradi, lekin parse xatosi butun suhbatni
 * yiqitmasin — zaxira `0` (xabar yo'qolmaydi, faqat vaqti noto'g'ri ko'rinadi).
 */
internal fun parseInstant(value: String?): Long =
    value?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() } ?: 0L

internal inline fun <reified T : Enum<T>> parseEnum(name: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
