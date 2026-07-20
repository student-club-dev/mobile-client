package dev.feature.chat.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.core.common.AppDispatchers
import dev.core.common.Resource
import dev.core.database.sql.StudentClubsDatabase
import dev.feature.chat.data.mapper.toDb
import dev.feature.chat.data.mapper.toDomain
import dev.feature.chat.data.remote.ChatRemoteDataSource
import dev.feature.chat.domain.model.Conversation
import dev.feature.chat.domain.model.Message
import dev.feature.chat.domain.repository.ChatRealtimeSource
import dev.feature.chat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ChatRepositoryImpl(
    private val db: StudentClubsDatabase,
    private val dispatchers: AppDispatchers,
    private val remote: ChatRemoteDataSource,
    private val syncEnabled: Boolean,
    // --- B7: real-time manba (Firestore). enabled=false bo'lsa local DB'dan ishlaydi. ---
    private val realtime: ChatRealtimeSource,
) : ChatRepository {
    private val q get() = db.chatQueries

    override fun observeConversations(): Flow<List<Conversation>> =
        if (realtime.enabled) realtime.conversations()
        else q.selectConversations().asFlow().mapToList(dispatchers.io).map { r -> r.map { it.toDomain() } }

    override fun observeArchivedConversations(): Flow<List<Conversation>> =
        if (realtime.enabled) realtime.archivedConversations()
        else q.selectArchivedConversations().asFlow().mapToList(dispatchers.io).map { r -> r.map { it.toDomain() } }

    override fun observeMessages(conversationId: String): Flow<List<Message>> =
        if (realtime.enabled) realtime.messages(conversationId)
        else q.selectMessages(conversationId).asFlow().mapToList(dispatchers.io).map { r -> r.map { it.toDomain() } }

    override suspend fun send(conversationId: String, text: String, time: String, createdAt: Long) {
        if (realtime.enabled) {
            realtime.send(conversationId, text, time, createdAt)
            return
        }
        withContext(dispatchers.io) {
            q.transaction {
                q.insertMessage(
                    id = "$conversationId-$createdAt",
                    conversationId = conversationId,
                    body = text,
                    outgoing = true.toDb(),
                    time = time,
                    createdAt = createdAt,
                )
                q.touchConversation(text, time, 0L, conversationId)
            }
        }
    }

    override suspend fun markRead(conversationId: String) {
        if (realtime.enabled) {
            realtime.markRead(conversationId)
            return
        }
        withContext(dispatchers.io) { q.markRead(conversationId) }
    }

    override suspend fun deleteMessage(conversationId: String, messageId: String) {
        if (realtime.enabled) {
            realtime.deleteMessage(conversationId, messageId)
            return
        }
        withContext(dispatchers.io) { q.deleteMessage(messageId) }
    }

    override suspend fun clearMessages(conversationId: String) {
        if (realtime.enabled) {
            realtime.clearMessages(conversationId)
            return
        }
        withContext(dispatchers.io) { q.clearConversationMessages(conversationId) }
    }

    override suspend fun deleteConversation(conversationId: String) {
        if (realtime.enabled) {
            realtime.deleteConversation(conversationId)
            return
        }
        withContext(dispatchers.io) {
            q.transaction {
                q.clearConversationMessages(conversationId)
                q.deleteConversation(conversationId)
            }
        }
    }

    override suspend fun setArchived(conversationId: String, archived: Boolean) {
        if (realtime.enabled) {
            realtime.setArchived(conversationId, archived)
            return
        }
        withContext(dispatchers.io) { q.setArchived(if (archived) 1L else 0L, conversationId) }
    }

    override suspend fun refresh(): Resource<Unit> {
        if (!syncEnabled) return Resource.Success(Unit)
        return when (val res = remote.fetchConversations()) {
            is Resource.Success -> {
                withContext(dispatchers.io) {
                    q.transaction {
                        q.clearConversations()
                        res.data.forEach { c ->
                            q.upsertConversation(
                                c.id, c.peerName, c.peerInitial, c.type, c.online.toDb(),
                                c.lastMessage, c.lastTime, c.unreadCount.toLong(),
                            )
                        }
                    }
                }
                Resource.Success(Unit)
            }
            is Resource.Error -> res
            Resource.Loading -> Resource.Success(Unit)
        }
    }
}
