package dev.feature.chat.presentation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.core.common.Resource
import dev.core.common.auth.TokenStore
import dev.feature.chat.domain.model.ConversationItem
import dev.feature.chat.domain.model.Message
import dev.feature.chat.domain.model.MessageStatus
import dev.feature.chat.domain.model.MessageType
import dev.feature.chat.domain.model.OutgoingImage
import dev.feature.chat.domain.model.Sticker
import dev.feature.chat.domain.repository.ChatRepository
import dev.feature.connections.domain.model.ReportReason
import dev.feature.connections.domain.repository.ConnectionsRepository
import dev.feature.university.domain.repository.UniversityRepository
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

/**
 * Albomdagi (yoki yakka) bitta rasm.
 *
 * `@Immutable` — Compose `ByteArray` ni beqaror deb biladi va busiz HAR BIR xabar pufagi
 * ota qayta chizilganda o'zi ham qayta chizilardi (uzun suhbatda bu sezilarli sekinlik).
 * Va'da bajariladi: baytlar massivi yaratilgandan keyin O'ZGARTIRILMAYDI.
 */
@Immutable
data class ChatImageUi(
    /** Qaysi xabarga tegishli — bosilganda/qayta yuborilganda kerak. */
    val messageId: String,
    /** Serverdagi havola. Hali yuklanmagan bo'lsa `null`. */
    val url: String?,
    /** Yuklanayotgan paytdagi local nusxa — havola paydo bo'lguncha shu ko'rsatiladi. */
    val localBytes: ByteArray?,
    val aspectRatio: Float?,
) {
    val loading: Boolean get() = url == null
}

/** Profil ekranidagi «Havolalar» bo'limi uchun bitta havola. */
@Immutable
data class ChatLinkUi(
    val messageId: String,
    val url: String,
    /** Ro'yxatda sarlavha o'rnida — `studentclub.uz` kabi. */
    val host: String,
)

/** Ekranda ko'rsatiladigan xabar — domen modeli + tayyor yorliqlar. */
@Immutable
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
    val type: MessageType = MessageType.TEXT,
    /**
     * Rasm(lar). Bir martada yuborilganlari **bitta** qatorga yig'iladi va to'r bo'lib
     * chiziladi — shuning uchun ro'yxatda ular yakka xabar sifatida ko'rinmaydi.
     */
    val images: List<ChatImageUi> = emptyList(),
    /** `STICKER` da — katta chiziladigan emoji. */
    val sticker: String? = null,
    /** Albomdagi barcha xabar id'lari — qayta yuborish hammasiga tegishli. */
    val messageIds: List<String> = listOf(id),
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
    /**
     * `universityId` → universitet nomi (local katalog). Backend qisqa profilda faqat
     * id'ni qaytaradi (katalogi yo'q), nomni o'zimiz topamiz.
     */
    val universityNames: Map<String, String> = emptyMap(),
) {
    /**
     * Suhbatdagi barcha rasmlar — profil ekranidagi «Umumiy media» to'ri, yangidan eskiga.
     * Alohida so'rov kerak emas: [messages] allaqachon butun keshni qamraydi.
     */
    val photos: List<ChatImageUi>
        get() = messages.asReversed()
            .filter { it.type == MessageType.IMAGE }
            .flatMap { it.images.asReversed() }
            // Hali yuklanmagani (havolasiz) to'rda ko'rsatilmaydi.
            .filter { it.url != null }

    /**
     * Matnli xabarlardagi havolalar — «Havolalar» bo'limi, yangidan eskiga.
     *
     * Rasm xabarining tanasi ham havola (backend tipli xabarni bermaydi), lekin u
     * `IMAGE` deb ajratilgani uchun bu yerga tushmaydi.
     */
    val links: List<ChatLinkUi>
        get() = messages.asReversed()
            .filter { it.type == MessageType.TEXT }
            .flatMap { message -> message.text.extractLinks().map { message.id to it } }
            .distinctBy { (_, url) -> url }
            .map { (id, url) -> ChatLinkUi(messageId = id, url = url, host = url.hostOf()) }

    /** Suhbatdoshning universiteti — katalogda topilmasa `null` (xom `emis-142` ko'rsatilmaydi). */
    val peerUniversity: String?
        get() = selected?.other?.universityId?.let { universityNames[it] }
}

/** Matndagi `http(s)://…` bo'laklari. Tinish belgilari havolaga yopishib qolmasin. */
private fun String.extractLinks(): List<String> = split(' ', '\n', '\t')
    .map { it.trim().trimEnd('.', ',', ')', ']', '!', '?', ';', ':') }
    .filter { it.startsWith("https://") || it.startsWith("http://") }
    .filter { it.length > MIN_LINK_LENGTH }

/** `https://studentclub.uz/a/b?x=1` → `studentclub.uz`. */
private fun String.hostOf(): String = substringAfter("://")
    .substringBefore('/')
    .substringBefore('?')
    .removePrefix("www.")
    .ifBlank { this }

/** `https://` ning o'zi havola emas. */
private const val MIN_LINK_LENGTH = 11

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
    universityRepository: UniversityRepository,
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

    /** Universitet nomlari — profil ekranida ko'rsatiladi. */
    private val universityNames = MutableStateFlow<Map<String, String>>(emptyMap())

    init {
        chatRepository.connectRealtime()
        viewModelScope.launch { chatRepository.refreshConversations() }
        viewModelScope.launch {
            universityRepository.observeUniversities()
                .catch { /* katalog bo'lmasa profil universitetsiz ko'rinadi */ }
                .collect { list -> universityNames.value = list.associate { it.id to it.name } }
        }
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
        combine(
            draft,
            typingFlow,
            chatRepository.observeRealtimeConnected(),
            extra,
            combine(chatRepository.observeLocalImages(), universityNames) { local, unis ->
                local to unis
            },
        ) { d, typing, rt, e, (local, unis) -> Rest(d, typing, rt, e, local, unis) },
    ) { conversations, archived, id, messages, rest ->
        val selected = (conversations + archived).firstOrNull { it.id == id }
        ChatUiState(
            conversations = conversations,
            archivedConversations = archived,
            selected = selected,
            messages = messages.toUi(
                otherReadSeq = selected?.otherReadSeq ?: 0,
                otherDeliveredSeq = selected?.otherDeliveredSeq ?: 0,
                localImages = rest.localImages,
            ),
            draft = rest.draft,
            peerTyping = rest.typing,
            realtime = rest.realtime,
            loadingOlder = rest.extra.loadingOlder,
            hasMoreHistory = rest.extra.hasMoreHistory,
            message = rest.extra.message,
            universityNames = rest.universityNames,
        )
    }
        // Manbalardan biri istisno tashlasa kolektor o'lib, state abadiy muzlab qolardi.
        .catch { emit(ChatUiState()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatUiState())

    private data class Rest(
        val draft: String,
        val typing: Boolean,
        val realtime: Boolean,
        val extra: ExtraState,
        val localImages: Map<String, ByteArray>,
        val universityNames: Map<String, String>,
    )

    /**
     * Domen xabarlari → ekran modeli: kim yozgani, soat, sana ajratgichi, o'qilganligi.
     *
     * Ketma-ket kelgan rasmlar avval **albomga** yig'iladi (qarang [groupAlbums]), shuning
     * uchun natijadagi qatorlar soni xabarlar sonidan kam bo'lishi mumkin.
     */
    private fun List<Message>.toUi(
        otherReadSeq: Int,
        otherDeliveredSeq: Int,
        localImages: Map<String, ByteArray>,
    ): List<ChatMessageUi> {
        val groups = groupAlbums()
        return groups.mapIndexed { index, group ->
            // Guruh sarlavhasi — birinchi xabar; vaqt va belgichalar — oxirgisiniki.
            val head = group.first()
            val tail = group.last()
            val previous = groups.getOrNull(index - 1)?.last()
            ChatMessageUi(
                id = head.id,
                text = head.body,
                outgoing = head.senderId == myId,
                time = ChatFormat.time(tail.createdAt),
                // Albomda bittasi ham yuborilmagan bo'lsa — butun to'r shu holatda ko'rinadi.
                status = group.combinedStatus(),
                // `seq = 0` — hali yuborilmagan xabar, o'qilgan bo'lishi mumkin emas.
                read = tail.seq > 0 && tail.seq <= otherReadSeq,
                // O'qilgan xabar, ta'rifi bo'yicha, yetkazilgan ham — kursorlar alohida
                // kelgani uchun (delivered kechikishi mumkin) buni ochiq yozamiz.
                delivered = tail.seq > 0 && (tail.seq <= otherDeliveredSeq || tail.seq <= otherReadSeq),
                dayLabel = if (previous == null || !ChatFormat.sameDay(previous.createdAt, head.createdAt)) {
                    ChatFormat.dayLabel(head.createdAt)
                } else {
                    null
                },
                type = head.type,
                images = if (head.type == MessageType.IMAGE) {
                    group.map { m ->
                        ChatImageUi(
                            messageId = m.id,
                            url = m.attachment?.url?.takeIf { it.isNotBlank() },
                            localBytes = localImages[m.id],
                            aspectRatio = m.attachment?.aspectRatio,
                        )
                    }
                } else {
                    emptyList()
                },
                sticker = head.body.takeIf { head.type == MessageType.STICKER },
                messageIds = group.map { it.id },
            )
        }
    }

    /**
     * Ketma-ket rasmlarni albomga yig'adi.
     *
     * `albumId` faqat **yuboruvchi** qurilmada bo'ladi — server uni qaytarmaydi
     * (`CHAT_MEDIA_AND_CALLS_BACKEND.md` §3). Shuning uchun qabul qiluvchi tomonda albom
     * qo'shni xabarlardan taxmin qilinadi: bir odam, ketma-ket, [ALBUM_WINDOW_SECONDS]
     * ichida yuborgan rasmlar bitta to'r hisoblanadi.
     */
    private fun List<Message>.groupAlbums(): List<List<Message>> {
        val groups = mutableListOf<MutableList<Message>>()
        forEach { message ->
            val current = groups.lastOrNull()
            val previous = current?.last()
            val joins = previous != null &&
                current.size < MAX_ALBUM_SIZE &&
                message.type == MessageType.IMAGE &&
                previous.type == MessageType.IMAGE &&
                message.senderId == previous.senderId &&
                sameAlbum(previous, message)
            if (joins) current.add(message) else groups.add(mutableListOf(message))
        }
        return groups
    }

    private fun sameAlbum(a: Message, b: Message): Boolean = when {
        // Kamida bittasida kalit bor — u holda faqat kalit hal qiladi.
        a.albumId != null || b.albumId != null -> a.albumId == b.albumId
        else -> (b.createdAt - a.createdAt).inWholeSeconds <= ALBUM_WINDOW_SECONDS
    }

    /** Albomning umumiy holati: yiqilgani bo'lsa `FAILED`, yuborilayotgani bo'lsa `SENDING`. */
    private fun List<Message>.combinedStatus(): MessageStatus = when {
        any { it.status == MessageStatus.FAILED } -> MessageStatus.FAILED
        any { it.status == MessageStatus.SENDING } -> MessageStatus.SENDING
        else -> MessageStatus.SENT
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

    /**
     * Tanlangan rasmlarni yuboradi — bir martada bir nechtasi.
     *
     * Yuklash **fon rejimida** ketadi: ekranda rasmlar darhol paydo bo'ladi va yuklanish
     * tugagach havolaga almashadi, ya'ni foydalanuvchi kutib turmaydi va boshqa xabar
     * yozishi mumkin.
     */
    fun sendImages(images: List<OutgoingImage>) {
        val id = selectedId.value ?: return
        if (images.isEmpty()) return
        stopTyping()
        viewModelScope.launch {
            when (val res = chatRepository.sendImages(id, images)) {
                // Yiqilganlari ro'yxatda `FAILED` bo'lib qoladi — qayta urinish mumkin.
                is Resource.Error -> extra.update { it.copy(message = res.message) }
                else -> Unit
            }
        }
    }

    fun sendSticker(sticker: Sticker) {
        val id = selectedId.value ?: return
        stopTyping()
        viewModelScope.launch {
            when (val res = chatRepository.sendSticker(id, sticker)) {
                is Resource.Error -> extra.update { it.copy(message = res.message) }
                else -> Unit
            }
        }
    }

    /**
     * Yuborilmagan xabarni qayta yuborish (o'sha `clientMsgId` bilan — idempotent).
     *
     * Albomda bir nechta xabar bo'lishi mumkin — faqat **yiqilganlari** qayta yuboriladi,
     * muvaffaqiyatlisiga tegilmaydi.
     */
    fun retry(messageIds: List<String>) = viewModelScope.launch {
        messageIds.forEach { id ->
            when (val res = chatRepository.retry(id)) {
                is Resource.Error -> extra.update { it.copy(message = res.message) }
                else -> Unit
            }
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

    /** Hali tayyor bo'lmagan amal uchun bir martalik xabar («tez orada»). */
    fun showMessage(text: String) = extra.update { it.copy(message = text) }

    fun messageShown() = extra.update { it.copy(message = null) }

    override fun onCleared() {
        chatRepository.disconnectRealtime()
        super.onCleared()
    }

    private companion object {
        const val TYPING_IDLE_MS = 3_000L

        /** Bir albomdagi rasmlar chegarasi — `ChatRepositoryImpl` dagi bilan bir xil. */
        const val MAX_ALBUM_SIZE = 10

        /**
         * Qabul qiluvchi tomonda albomni taxmin qilish oynasi. Server `albumId` ni
         * qaytarmagani uchun ketma-ket kelgan rasmlar shu oraliqda bitta to'r hisoblanadi.
         */
        const val ALBUM_WINDOW_SECONDS = 60L
    }
}
