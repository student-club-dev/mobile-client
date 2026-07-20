package dev.feature.chat.data.mapper

import dev.core.database.sql.ConversationEntity
import dev.core.database.sql.MessageEntity
import dev.feature.chat.domain.model.Conversation
import dev.feature.chat.domain.model.ConversationType
import dev.feature.chat.domain.model.Message

internal fun ConversationEntity.toDomain(): Conversation = Conversation(
    id = id,
    peerName = peerName,
    peerInitial = peerInitial,
    type = parseEnum(type, ConversationType.PEER),
    online = online.toBool(),
    lastMessage = lastMessage,
    lastTime = lastTime,
    unreadCount = unreadCount.toInt(),
    archived = archived != 0L,
)

internal fun MessageEntity.toDomain(): Message = Message(
    id = id,
    conversationId = conversationId,
    text = body,
    outgoing = outgoing.toBool(),
    time = time,
    createdAt = createdAt,
)

// --- DB <-> domen yordamchilari (core:data'dagi internal helper'lar bilan bir xil) ---
internal fun Boolean.toDb(): Long = if (this) 1L else 0L
internal fun Long.toBool(): Boolean = this != 0L
internal inline fun <reified T : Enum<T>> parseEnum(name: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
