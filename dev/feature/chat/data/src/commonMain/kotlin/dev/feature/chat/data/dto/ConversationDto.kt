package dev.feature.chat.data.dto

import kotlinx.serialization.Serializable

/**
 * Chat suhbat DTO'si (suhbatlar ro'yxati; xabarlar real-time/alohida). Real API spek kelganda
 * maydon nomlarini serverникiga moslang (@SerialName), oqim o'zgarmaydi.
 */
@Serializable
data class ConversationDto(
    val id: String,
    val peerName: String,
    val peerInitial: String = "",
    val type: String = "PEER",
    val online: Boolean = false,
    val lastMessage: String = "",
    val lastTime: String = "",
    val unreadCount: Int = 0,
)
