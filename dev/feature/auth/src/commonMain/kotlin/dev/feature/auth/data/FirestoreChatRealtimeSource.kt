package dev.feature.auth.data

import dev.core.domain.model.Conversation
import dev.core.domain.model.ConversationType
import dev.core.domain.model.Message
import dev.core.domain.repository.ChatRealtimeSource
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/**
 * [ChatRealtimeSource] ning Firestore (GitLive) implementatsiyasi (B7).
 *
 * Struktura: `conversations/{id}` hujjatlari + `conversations/{id}/messages/{msgId}` subkolleksiyasi.
 * `.snapshots` — real-time Flow (Firestore o'zi offline cache beradi → real-time + offline-first).
 *
 * [enabled] `false` bo'lsa hech qachon chaqirilmaydi (ChatRepository local DB'dan ishlaydi).
 */
class FirestoreChatRealtimeSource(
    override val enabled: Boolean,
) : ChatRealtimeSource {

    private val db get() = Firebase.firestore
    private val currentUid: String? get() = Firebase.auth.currentUser?.uid

    override fun conversations(): Flow<List<Conversation>> {
        if (!enabled) return flowOf(emptyList())
        val uid = currentUid
        return db.collection(CONVERSATIONS).snapshots.map { snap ->
            snap.documents
                .mapNotNull { runCatching { it.data(FsConversation.serializer()) }.getOrNull() }
                // A'zolik ma'lumoti bo'lsa — faqat foydalanuvchi suhbatlari.
                .filter { it.members.isEmpty() || uid == null || it.members.contains(uid) }
                .sortedByDescending { it.unreadCount }
                .map { it.toDomain() }
        }
    }

    override fun messages(conversationId: String): Flow<List<Message>> {
        if (!enabled) return flowOf(emptyList())
        val uid = currentUid
        return db.collection(CONVERSATIONS).document(conversationId).collection(MESSAGES).snapshots.map { snap ->
            snap.documents
                .mapNotNull { runCatching { it.data(FsMessage.serializer()) }.getOrNull() }
                .sortedBy { it.createdAt }
                .map { it.toDomain(uid) }
        }
    }

    override suspend fun send(conversationId: String, text: String, time: String, createdAt: Long) {
        if (!enabled) return
        val uid = currentUid ?: return
        val id = "$conversationId-$createdAt"
        db.collection(CONVERSATIONS).document(conversationId).collection(MESSAGES).document(id)
            .set(FsMessage.serializer(), FsMessage(id = id, senderId = uid, text = text, time = time, createdAt = createdAt))
        // Suhbat ustki ma'lumotini yangilaymiz (merge — faqat shu maydonlar).
        db.collection(CONVERSATIONS).document(conversationId)
            .set(FsConversationPreview.serializer(), FsConversationPreview(lastMessage = text, lastTime = time), merge = true)
    }

    override suspend fun markRead(conversationId: String) {
        if (!enabled) return
        db.collection(CONVERSATIONS).document(conversationId)
            .set(FsConversationUnread.serializer(), FsConversationUnread(unreadCount = 0), merge = true)
    }

    private companion object {
        const val CONVERSATIONS = "conversations"
        const val MESSAGES = "messages"
    }
}

// --- Firestore hujjat DTO'lari ---
@Serializable
private data class FsConversation(
    val id: String = "",
    val peerName: String = "",
    val peerInitial: String = "",
    val type: String = "PEER",
    val online: Boolean = false,
    val lastMessage: String = "",
    val lastTime: String = "",
    val unreadCount: Int = 0,
    val members: List<String> = emptyList(),
)

@Serializable
private data class FsMessage(
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val time: String = "",
    val createdAt: Long = 0,
)

/** Suhbat ustki ma'lumotini yangilash uchun qisman DTO (merge). */
@Serializable
private data class FsConversationPreview(val lastMessage: String, val lastTime: String)

@Serializable
private data class FsConversationUnread(val unreadCount: Int)

private fun FsConversation.toDomain(): Conversation = Conversation(
    id = id,
    peerName = peerName,
    peerInitial = peerInitial.ifBlank { peerName.take(1).uppercase() },
    type = runCatching { ConversationType.valueOf(type) }.getOrDefault(ConversationType.PEER),
    online = online,
    lastMessage = lastMessage,
    lastTime = lastTime,
    unreadCount = unreadCount,
)

private fun FsMessage.toDomain(currentUid: String?): Message = Message(
    id = id,
    conversationId = "",
    text = text,
    outgoing = senderId.isNotBlank() && senderId == currentUid,
    time = time,
    createdAt = createdAt,
)
