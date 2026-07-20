package dev.feature.chat.domain.repository

import dev.core.common.Resource
import dev.feature.chat.domain.model.Conversation
import dev.feature.chat.domain.model.Message
import kotlinx.coroutines.flow.Flow

/** Chat — suhbatlar va xabarlar (offline, real-time'ga tayyor tuzilma). */
interface ChatRepository {
    fun observeConversations(): Flow<List<Conversation>>
    fun observeArchivedConversations(): Flow<List<Conversation>>
    fun observeMessages(conversationId: String): Flow<List<Message>>
    suspend fun send(conversationId: String, text: String, time: String, createdAt: Long)
    suspend fun markRead(conversationId: String)

    /** Suhbatdagi bitta xabarni o'chiradi. */
    suspend fun deleteMessage(conversationId: String, messageId: String)

    /** Suhbatdagi barcha xabarlarni o'chiradi (tozalash). */
    suspend fun clearMessages(conversationId: String)

    /** Suhbatni butunlay o'chiradi (suhbat + barcha xabarlari). */
    suspend fun deleteConversation(conversationId: String)

    /** Suhbatni arxivlash / arxivdan chiqarish. */
    suspend fun setArchived(conversationId: String, archived: Boolean)

    /** Backend'dan suhbatlar ro'yxatini sinxronlaydi (offline-first). Xatoda cache saqlanadi. */
    suspend fun refresh(): Resource<Unit>
}
