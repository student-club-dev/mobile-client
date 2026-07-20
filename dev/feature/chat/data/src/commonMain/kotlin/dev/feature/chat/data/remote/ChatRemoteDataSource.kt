package dev.feature.chat.data.remote

import dev.core.common.Resource
import dev.feature.chat.data.dto.ConversationDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

interface ChatRemoteDataSource { suspend fun fetchConversations(): Resource<List<ConversationDto>> }

class KtorChatRemoteDataSource(private val client: HttpClient) : ChatRemoteDataSource {
    override suspend fun fetchConversations(): Resource<List<ConversationDto>> = try {
        Resource.Success(client.get("conversations").body())
    } catch (e: Exception) {
        Resource.Error(e.message ?: "Suhbatlarni yuklab bo'lmadi", e)
    }
}
