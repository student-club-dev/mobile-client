package dev.feature.chat.data.mapper

import dev.core.database.sql.ConversationEntity
import dev.core.database.sql.MessageEntity
import dev.core.network.generated.model.MessageDto
import dev.feature.chat.data.realtime.WsMessage
import dev.feature.chat.domain.model.Attachment
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
    // Ro'yxat qatorida to'liq xabar kerak emas — matn, turi va kim yozgani yetarli.
    // Turi shu yerda ham aniqlanadi, aks holda ro'yxatda rasm o'rniga uzun havola ko'rinardi.
    lastMessage = lastMessageBody?.let { text ->
        Message(
            id = "$id-last",
            conversationId = id,
            senderId = lastMessageSenderId.orEmpty(),
            seq = 0,
            body = text,
            createdAt = Instant.fromEpochMilliseconds(lastMessageAt ?: 0L),
            type = MediaContent.detect(text),
        )
    },
    unreadCount = unreadCount.toInt(),
    otherReadSeq = otherReadSeq.toInt(),
    otherDeliveredSeq = otherDeliveredSeq.toInt(),
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
    attachment = attachmentUrl?.let { url ->
        Attachment(
            url = url,
            thumbUrl = attachmentThumbUrl,
            width = attachmentWidth.toInt(),
            height = attachmentHeight.toInt(),
        )
    },
    albumId = albumId,
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
    val attachmentUrl: String? = null,
    val attachmentThumbUrl: String? = null,
    val attachmentWidth: Long = 0,
    val attachmentHeight: Long = 0,
    val albumId: String? = null,
)

// v1 da server faqat TEXT yozadi, lekin sxemada `body` nullable — bo'sh matn bilan qoplaymiz.
internal fun MessageDto.toRow(clientMsgId: String? = null): MessageRow = messageRow(
    id = id,
    conversationId = conversationId,
    senderId = senderId,
    seq = seq.toLong(),
    serverType = type.name,
    body = body.orEmpty(),
    createdAt = createdAt.toEpochMilliseconds(),
    clientMsgId = clientMsgId,
)

internal fun WsMessage.toRow(): MessageRow = messageRow(
    id = id,
    conversationId = conversationId,
    senderId = senderId,
    seq = seq.toLong(),
    serverType = type,
    body = body.orEmpty(),
    createdAt = parseInstant(createdAt),
    clientMsgId = null,
)

/**
 * Serverdan kelgan xabardan kesh qatorini quradi va **turini aniqlaydi**.
 *
 * Server `TEXT` dan boshqa tur bergan bo'lsa — unga ishonamiz (backend tipli xabarni
 * qo'shgani). Aks holda tana bo'yicha taxmin qilamiz: rasm havolasi → `IMAGE`, yakka
 * emoji → `STICKER` (qarang [MediaContent]).
 */
private fun messageRow(
    id: String,
    conversationId: String,
    senderId: String,
    seq: Long,
    serverType: String,
    body: String,
    createdAt: Long,
    clientMsgId: String?,
): MessageRow {
    val fromServer = parseEnum(serverType, MessageType.TEXT)
    val type = if (fromServer != MessageType.TEXT) fromServer else MediaContent.detect(body)
    return MessageRow(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        seq = seq,
        type = type.name,
        body = body,
        createdAt = createdAt,
        clientMsgId = clientMsgId,
        // Rasmda tananing o'zi havola — biriktirma ham shu. Server o'lcham qaytarmaydi,
        // shuning uchun kengligi/balandligi `0` bo'lib qoladi va UI kvadratga tushadi.
        attachmentUrl = if (type == MessageType.IMAGE) MediaContent.imageUrlOrNull(body) else null,
    )
}

/**
 * ISO-8601 → epoch ms. Server doim to'g'ri sana yuboradi, lekin parse xatosi butun suhbatni
 * yiqitmasin — zaxira `0` (xabar yo'qolmaydi, faqat vaqti noto'g'ri ko'rinadi).
 */
internal fun parseInstant(value: String?): Long =
    value?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() } ?: 0L

internal inline fun <reified T : Enum<T>> parseEnum(name: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
