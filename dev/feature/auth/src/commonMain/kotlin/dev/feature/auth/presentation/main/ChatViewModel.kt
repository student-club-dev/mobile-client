package dev.feature.auth.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.core.domain.model.Conversation
import dev.core.domain.model.Message
import dev.core.domain.repository.ChatRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Chat (1x ro'yxat + 1y suhbat) holati. */
data class ChatUiState(
    val conversations: List<Conversation> = emptyList(),
    val selected: Conversation? = null,
    val messages: List<Message> = emptyList(),
    val draft: String = "",
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val chatRepository: ChatRepository,
) : ViewModel() {

    private val selectedId = MutableStateFlow<String?>(null)
    private val draft = MutableStateFlow("")

    private val messagesFlow = selectedId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else chatRepository.observeMessages(id)
    }

    val state: StateFlow<ChatUiState> = combine(
        chatRepository.observeConversations(),
        selectedId,
        messagesFlow,
        draft,
    ) { conversations, id, messages, d ->
        ChatUiState(
            conversations = conversations,
            selected = conversations.firstOrNull { it.id == id },
            messages = messages,
            draft = d,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatUiState())

    fun open(conversation: Conversation) {
        selectedId.value = conversation.id
        viewModelScope.launch { chatRepository.markRead(conversation.id) }
    }

    fun close() {
        selectedId.value = null
    }

    fun onDraft(v: String) {
        draft.value = v
    }

    fun send() {
        val id = selectedId.value ?: return
        val text = draft.value.trim()
        if (text.isEmpty()) return
        val nextAt = (state.value.messages.maxOfOrNull { it.createdAt } ?: 0L) + 1000
        draft.value = ""
        viewModelScope.launch { chatRepository.send(id, text, time = "hozir", createdAt = nextAt) }
    }
}
