package dev.feature.chat.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.core.common.Resource
import dev.core.common.auth.TokenStore
import dev.feature.chat.domain.model.ConversationItem
import dev.feature.chat.domain.model.Message
import dev.feature.chat.domain.model.MessageStatus
import dev.feature.chat.domain.repository.ChatRepository
import dev.feature.connections.domain.model.ReportReason
import dev.feature.connections.domain.repository.ConnectionsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ekranda ko'rsatiladigan xabar — domen modeli + tayyor yorliqlar. */
data class ChatMessageUi(
    val id: String,
    val text: String,
    val outgoing: Boolean,
    val time: String,
    val status: MessageStatus,
    /** Suhbatdosh o'qiganmi — chiquvchi xabarda ikki belgicha YORQIN yonadi. */
    val read: Boolean,
    /** Suhbatdoshning qurilmasiga yetib borganmi — ikki belgicha (xira). */
    val delivered: Boolean,
    /** `null` bo'lmasa — bu xabardan oldin sana ajratgichi chiziladi. */
    val dayLabel: String? = null,
)

data class ChatUiState(
    val conversations: List<ConversationItem> = emptyList(),
    val archivedConversations: List<ConversationItem> = emptyList(),
    val selected: ConversationItem? = null,
    val messages: List<ChatMessageUi> = emptyList(),
    val draft: String = "",
    /** Suhbatdosh yozmoqda (WS `typing`). */
    val peerTyping: Boolean = false,
    /** WebSocket ulanganmi — sarlavhada holat ko'rsatiladi. */
    val realtime: Boolean = false,
    val loadingOlder: Boolean = false,
    val hasMoreHistory: Boolean = true,
    /** Bir martalik xabar (xato / tasdiq). */
    val message: String? = null,
)

/**
 * Chat ekranining holati — suhbatlar ro'yxati va ochilgan suhbat bitta ViewModel'da
 * (ekran ham shunday: `selected == null` bo'lsa ro'yxat, aks holda yozishma).
 *
 * Ma'lumot **faqat local keshdan** o'qiladi; REST/WS uni fon rejimida to'ldiradi
 * (`ChatRepositoryImpl`). Shu sabab bu yerda "yuklanmoqda" holati deyarli yo'q.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val connectionsRepository: ConnectionsRepository,
    tokenStore: TokenStore,
) : ViewModel() {

    private val myId: String? = tokenStore.userId()

    private val selectedId = MutableStateFlow<String?>(null)
    private val draft = MutableStateFlow("")
    private val extra = MutableStateFlow(ExtraState())

    /** State'ga qo'shiladigan, oqimga bog'liq bo'lmagan qismlar. */
    private data class ExtraState(
        val loadingOlder: Boolean = false,
        val hasMoreHistory: Boolean = true,
        val message: String? = null,
    )

    /** "Yozmoqda" ni to'xtatuvchi taymer — 3 soniya jimlikdan keyin `typing:stop`. */
    private var typingStopJob: Job? = null
    private var typingActive = false

    init {
        chatRepository.connectRealtime()
        viewModelScope.launch { chatRepository.refreshConversations() }
    }

    // MUHIM: `messagesFlow` combine'ning majburiy a'zosi. `onStart` darhol bo'sh ro'yxat
    // beradi, aks holda birinchi snapshot kelmaguncha butun state emit qilinmay, bosilgan
    // suhbat ochilmay qolardi. `catch` esa oqim xatosida state'ning muzlab qolishini oldini oladi.
    private val messagesFlow = selectedId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else {
            chatRepository.observeMessages(id).onStart { emit(emptyList()) }.catch { emit(emptyList()) }
        }
    }

    private val typingFlow = selectedId.flatMapLatest { id ->
        if (id == null) flowOf(false) else chatRepository.observeTyping(id).catch { emit(false) }
    }

    val state: StateFlow<ChatUiState> = combine(
        chatRepository.observeConversations(),
        chatRepository.observeArchivedConversations(),
        selectedId,
        messagesFlow,
        combine(draft, typingFlow, chatRepository.observeRealtimeConnected(), extra) { d, typing, rt, e ->
            Quad(d, typing, rt, e)
        },
    ) { conversations, archived, id, messages, rest ->
        val selected = (conversations + archived).firstOrNull { it.id == id }
        ChatUiState(
            conversations = conversations,
            archivedConversations = archived,
            selected = selected,
            messages = messages.toUi(selected?.otherReadSeq ?: 0, selected?.otherDeliveredSeq ?: 0),
            draft = rest.draft,
            peerTyping = rest.typing,
            realtime = rest.realtime,
            loadingOlder = rest.extra.loadingOlder,
            hasMoreHistory = rest.extra.hasMoreHistory,
            message = rest.extra.message,
        )
    }
        // Manbalardan biri istisno tashlasa kolektor o'lib, state abadiy muzlab qolardi.
        .catch { emit(ChatUiState()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatUiState())

    private data class Quad(
        val draft: String,
        val typing: Boolean,
        val realtime: Boolean,
        val extra: ExtraState,
    )

    /** Domen xabarlari → ekran modeli: kim yozgani, soat, sana ajratgichi, o'qilganligi. */
    private fun List<Message>.toUi(otherReadSeq: Int, otherDeliveredSeq: Int): List<ChatMessageUi> =
        mapIndexed { index, m ->
            val previous = getOrNull(index - 1)
            ChatMessageUi(
                id = m.id,
                text = m.body,
                outgoing = m.senderId == myId,
                time = ChatFormat.time(m.createdAt),
                status = m.status,
                // `seq = 0` — hali yuborilmagan xabar, o'qilgan bo'lishi mumkin emas.
                read = m.seq > 0 && m.seq <= otherReadSeq,
                // O'qilgan xabar, ta'rifi bo'yicha, yetkazilgan ham — kursorlar alohida
                // kelgani uchun (delivered kechikishi mumkin) buni ochiq yozamiz.
                delivered = m.seq > 0 && (m.seq <= otherDeliveredSeq || m.seq <= otherReadSeq),
                dayLabel = if (previous == null || !ChatFormat.sameDay(previous.createdAt, m.createdAt)) {
                    ChatFormat.dayLabel(m.createdAt)
                } else {
                    null
                },
            )
        }

    // --- Suhbat ochish -------------------------------------------------------------------

    fun open(conversation: ConversationItem) = openById(conversation.id)

    /**
     * Suhbatni **id bo'yicha** ochadi — push bosilganda (`data.conversationId`).
     * Suhbat keshda bo'lmasligi mumkin (ilova sovuq ishga tushdi), shuning uchun avval
     * ro'yxat yangilanadi: sarlavhadagi ism/avatar shundan keladi.
     */
    fun openConversation(conversationId: String) {
        openById(conversationId)
        viewModelScope.launch {
            if (state.value.selected == null) chatRepository.refreshConversations()
        }
    }

    /**
     * Bog'langan talaba bilan suhbatni ochadi — `POST /v1/conversations` **idempotent**,
     * shuning uchun "suhbat bormi?" deb tekshirilmaydi.
     */
    fun openWithStudent(studentId: String) = viewModelScope.launch {
        when (val res = chatRepository.openDirect(studentId)) {
            is Resource.Success -> openById(res.data)
            // `403 NOT_CONNECTED` — avval bog'lanish kerak.
            is Resource.Error -> extra.update { it.copy(message = res.message) }
            Resource.Loading -> Unit
        }
    }

    private fun openById(conversationId: String) {
        selectedId.value = conversationId
        extra.update { it.copy(hasMoreHistory = true) }
        viewModelScope.launch {
            // Oxirgi xabarlar + qayta ulanishda uzilib qolganlari (`?after=`).
            chatRepository.loadLatest(conversationId)
            chatRepository.markDelivered(conversationId)
            chatRepository.markRead(conversationId)
        }
    }

    fun close() {
        stopTyping()
        selectedId.value = null
    }

    /** Yuqoriga aylantirilganda eski xabarlarni yuklaydi. */
    fun loadOlder() {
        val id = selectedId.value ?: return
        if (extra.value.loadingOlder || !extra.value.hasMoreHistory) return
        viewModelScope.launch {
            extra.update { it.copy(loadingOlder = true) }
            val res = chatRepository.loadOlder(id)
            extra.update {
                it.copy(
                    loadingOlder = false,
                    hasMoreHistory = (res as? Resource.Success)?.data ?: it.hasMoreHistory,
                )
            }
        }
    }

    /** Ekranga qaytganda o'qilgan deb belgilaydi. */
    fun markRead() {
        val id = selectedId.value ?: return
        viewModelScope.launch { chatRepository.markRead(id) }
    }

    // --- Yozish --------------------------------------------------------------------------

    fun onDraft(value: String) {
        draft.value = value
        val id = selectedId.value ?: return
        if (value.isBlank()) {
            stopTyping()
            return
        }
        if (!typingActive) {
            typingActive = true
            viewModelScope.launch { chatRepository.setTyping(id, true) }
        }
        // 3 soniya jimlikdan keyin — `typing:stop` (chat.md §11).
        typingStopJob?.cancel()
        typingStopJob = viewModelScope.launch {
            delay(TYPING_IDLE_MS)
            stopTyping()
        }
    }

    fun send() {
        val id = selectedId.value ?: return
        val text = draft.value.trim()
        if (text.isEmpty()) return
        draft.value = ""
        stopTyping()
        viewModelScope.launch {
            when (val res = chatRepository.send(id, text)) {
                // Xato bo'lsa xabar ro'yxatda `FAILED` bo'lib qoladi — qayta urinish mumkin.
                is Resource.Error -> extra.update { it.copy(message = res.message) }
                else -> Unit
            }
        }
    }

    /** Yuborilmagan xabarni qayta yuborish (o'sha `clientMsgId` bilan — idempotent). */
    fun retry(messageId: String) = viewModelScope.launch {
        when (val res = chatRepository.retry(messageId)) {
            is Resource.Error -> extra.update { it.copy(message = res.message) }
            else -> Unit
        }
    }

    private fun stopTyping() {
        typingStopJob?.cancel()
        typingStopJob = null
        if (!typingActive) return
        typingActive = false
        val id = selectedId.value ?: return
        viewModelScope.launch { chatRepository.setTyping(id, false) }
    }

    // --- Suhbat ustidagi amallar ---------------------------------------------------------

    /** Arxivlash — **faqat local** (backendda endpoint yo'q). */
    fun setArchived(conversationId: String, archived: Boolean) = viewModelScope.launch {
        chatRepository.setArchived(conversationId, archived)
    }

    /** Bog'lanishni uzish — suhbat qoladi (tarix o'qiladi), lekin yozib bo'lmaydi. */
    fun disconnect(studentId: String) = viewModelScope.launch {
        when (val res = connectionsRepository.disconnect(studentId)) {
            is Resource.Success -> extra.update { it.copy(message = "Bog'lanish uzildi") }
            is Resource.Error -> extra.update { it.copy(message = res.message) }
            Resource.Loading -> Unit
        }
    }

    fun block(studentId: String) = viewModelScope.launch {
        when (val res = connectionsRepository.block(studentId)) {
            is Resource.Success -> {
                extra.update { it.copy(message = "Bloklandi") }
                selectedId.value = null
                chatRepository.refreshConversations()
            }
            is Resource.Error -> extra.update { it.copy(message = res.message) }
            Resource.Loading -> Unit
        }
    }

    fun reportStudent(studentId: String, reason: ReportReason, note: String?) = viewModelScope.launch {
        report(connectionsRepository.report(reason, targetStudentId = studentId, note = note))
    }

    /**
     * Xabar ustidan shikoyat. ⚠️ Server `messageId` mavjudligini tekshirmaydi — shuning
     * uchun faqat HAQIQIY (yuborilgan) xabarning id'si yuboriladi.
     */
    fun reportMessage(messageId: String, reason: ReportReason, note: String?) = viewModelScope.launch {
        report(connectionsRepository.report(reason, messageId = messageId, note = note))
    }

    private fun report(result: Resource<Unit>) {
        val text = when (result) {
            is Resource.Success -> "Shikoyatingiz qabul qilindi"
            is Resource.Error -> result.message
            Resource.Loading -> return
        }
        extra.update { it.copy(message = text) }
    }

    fun messageShown() = extra.update { it.copy(message = null) }

    override fun onCleared() {
        chatRepository.disconnectRealtime()
        super.onCleared()
    }

    private companion object {
        const val TYPING_IDLE_MS = 3_000L
    }
}
