package dev.feature.chat.presentation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.core.common.Resource
import dev.core.common.auth.TokenStore
import dev.feature.chat.domain.model.ConversationItem
import dev.feature.chat.domain.model.EmojiText
import dev.feature.chat.domain.model.FluentEmoji
import dev.feature.chat.domain.model.GifItem
import dev.feature.chat.domain.model.Message
import dev.feature.chat.domain.model.MessageStatus
import dev.feature.chat.domain.model.MessageType
import dev.feature.chat.domain.model.OutgoingImage
import dev.feature.chat.domain.model.OutgoingVideo
import dev.feature.chat.domain.model.Sticker
import dev.feature.chat.domain.model.StickerSearchItem
import dev.feature.chat.domain.model.UploadState
import dev.feature.chat.domain.repository.ChatRepository
import dev.feature.clubs.domain.model.Club
import dev.feature.clubs.domain.repository.ClubRepository
import dev.feature.connections.domain.model.ReportReason
import dev.feature.connections.domain.repository.ConnectionsRepository
import dev.feature.university.domain.repository.UniversityRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Albomdagi (yoki yakka) bitta media — **rasm ham, video ham**.
 *
 * Telegramdagidek: bir martada yuborilgan rasm va videolar **bitta mozaikada** turadi va
 * ikkalasi ham bir xil katakda chiziladi (videoda ustiga o'ynatish belgisi va davomiyligi
 * qo'shiladi). Shuning uchun ular alohida tur emas — bitta model, [video] bayrog'i bilan.
 *
 * `@Immutable` — Compose `ByteArray` ni beqaror deb biladi va busiz HAR BIR xabar pufagi
 * ota qayta chizilganda o'zi ham qayta chizilardi (uzun suhbatda bu sezilarli sekinlik).
 * Va'da bajariladi: baytlar massivi yaratilgandan keyin O'ZGARTIRILMAYDI.
 */
@Immutable
data class ChatMediaItem(
    /** Qaysi xabarga tegishli — bosilganda/qayta yuborilganda kerak. */
    val messageId: String,
    /**
     * Ro'yxatda ko'rsatiladigan havola — server bergan **kichik nusxa** (`?variant=thumb`),
     * bo'lmasa to'lig'i. Hali yuklanmagan bo'lsa `null`.
     */
    val url: String?,
    /** Yuklanayotgan paytdagi local nusxa — havola paydo bo'lguncha shu ko'rsatiladi. */
    val localBytes: ByteArray?,
    val aspectRatio: Float?,
    /**
     * To'liq o'lchamdagi havola — rasm ochilganda. Ro'yxatdagi thumb 320px, uni butun
     * ekranga cho'zsak xira ko'rinardi. Berilmasa [url] ning o'zi ishlatiladi.
     */
    val fullUrl: String? = url,
    /**
     * Yuklash foizi (`0f..1f`) — **faqat hozir ketayotgan** rasmda. Yuklanmayotgan
     * (yoki allaqachon yuklangan) rasmda `null`.
     *
     * Albomdagi har rasm o'z foizini ko'rsatadi: ular ketma-ket yuklanadi, ya'ni bir
     * vaqtda faqat bittasida halqa aylanadi — Telegramdagidek.
     */
    val uploadProgress: Float? = null,
    /** Yuklanmoqda: havola hali yo'q, foiz esa hali kelmagan bo'lishi mumkin. */
    val uploading: Boolean = false,
    /** Video — katakda o'ynatish belgisi va davomiyligi ko'rsatiladi, bosilsa pleyer ochiladi. */
    val video: Boolean = false,
    /** Video davomiyligi. Rasmda `0`. */
    val durationMs: Int = 0,
    /** Server videoni hali transkod qilmoqda — poster bor, o'zi hali yo'q. */
    val processing: Boolean = false,
    /**
     * Xabar yuborilmadi.
     *
     * Kerak, chunki fayl **yuklanib bo'lgan**, lekin `message:send` yiqilgan bo'lishi mumkin:
     * o'shanda biriktirma `PROCESSING` holatida qoladi va katak bir vaqtda ham
     * «Tayyorlanmoqda…», ham «yuborilmadi» deb turardi — ikkita qarama-qarshi yorliq.
     */
    val failed: Boolean = false,
) {
    val loading: Boolean get() = url == null
}

/**
 * Rasm bo'lmagan biriktirma — fayl, ovozli xabar yoki video.
 *
 * Rasmlar ([ChatMediaItem]) dan alohida: ular albomga yig'iladi va to'r bo'lib chiziladi,
 * bular esa har doim **yakka** va har biri o'z pufagida ko'rinadi.
 */
@Immutable
data class ChatAttachmentUi(
    /** Serverdagi medianing to'liq havolasi — **token bilan** so'raladi. */
    val url: String,
    val thumbUrl: String? = null,
    /** `FILE` da asl nom; bo'lmasa umumiy "Fayl". */
    val fileName: String? = null,
    val sizeBytes: Long = 0,
    val durationMs: Int = 0,
    /** Ovozli xabarning 48 nuqtali to'lqini (`0..100`). Boshqa turlarda bo'sh. */
    val waveform: List<Int> = emptyList(),
    /** Video hali transkodlanmoqda — poster bor, o'zi hali yo'q. */
    val processing: Boolean = false,
    val aspectRatio: Float? = null,
)

/**
 * Ketayotgan biriktirma — pufak o'rnida foiz halqasi chiziladi.
 *
 * Nom va hajm **yuklovchidan** keladi, keshdan emas: fayl/video/ovozning biriktirmasi
 * javob bilan birga keladi, ya'ni yuklash davomida qatorda u yo'q.
 */
@Immutable
data class ChatUploadUi(
    /** `0f..1f`; `null` — hajm noma'lum, halqa aylanma bo'lib chiziladi. */
    val progress: Float?,
    val fileName: String?,
    val sizeBytes: Long,
)

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
    val images: List<ChatMediaItem> = emptyList(),
    /**
     * Katta chiziladigan emoji — `STICKER` da stikerning emojisi, `TEXT` da esa faqat
     * emojidan iborat qisqa xabar (Telegram/WhatsApp qoidasi, qarang [EmojiText]).
     */
    val sticker: String? = null,
    /**
     * Server katalogidagi stikerning tasviri (shaffof fonli WebP). `null` bo'lsa [sticker]
     * emojisi katta qilib chiziladi — zaxira katalogda rasm yo'q.
     */
    val stickerUrl: String? = null,
    /**
     * Xabar o'chirilgan — o'rniga tombstone chiziladi. Qator tarixda **qoladi**: `seq` —
     * tarix va o'qildi kursorlarining o'qi (`handoff/02-API-CHANGES.md` §4b).
     */
    val deleted: Boolean = false,
    /** O'z xabarimizni o'chirish mumkin (`DELETE /v1/messages/{id}`). */
    val canDelete: Boolean = false,
    /** Albomdagi barcha xabar id'lari — qayta yuborish hammasiga tegishli. */
    val messageIds: List<String> = listOf(id),
    /** `FILE` / `VOICE` / `VIDEO` biriktirmasi. Qolgan turlarda `null`. */
    val attachment: ChatAttachmentUi? = null,
    /**
     * Fayl hozir ketmoqda — `null` bo'lmasa pufak o'rniga foiz ko'rsatiladi.
     *
     * Rasm albomida ishlatilmaydi: u yerda har katak o'z foizini chizadi
     * ([ChatMediaItem.uploadProgress]).
     */
    val upload: ChatUploadUi? = null,
)

/**
 * Xabarlar ekranidagi papka — Telegramdagi papkalar kabi, bir vaqtda faqat bittasi tanlangan.
 *
 * Hozircha ikkitasi: yakka suhbatlar va klublar. **Guruhlar keyin qo'shiladi** — u backendda
 * hali yo'q, shuning uchun bu yerda ham bo'sh papka bo'lib turmaydi: yangi qiymat qo'shilishi
 * bilan tepa qatorda o'zi paydo bo'ladi ([ChatFolder.entries] bo'yicha chiziladi).
 */
enum class ChatFolder(val label: String) {
    PERSONAL("Shaxsiy"),
    CLUBS("Klublar"),
}

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
     * Barcha suhbatlardagi o'qilmagan xabarlar soni — pastki panel badge'i uchun
     * (`GET /v1/conversations/unread-count`). O'chirilgan xabarlar hisobga olinmaydi.
     * Busiz butun ro'yxatni yuklab, qo'lda qo'shish kerak bo'lardi.
     */
    val unreadTotal: Int = 0,
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
    val photos: List<ChatMediaItem>
        get() = messages.asReversed()
            .filter { it.type == MessageType.IMAGE && !it.deleted }
            .flatMap { it.images.asReversed() }
            // Hali yuklanmagani (havolasiz) to'rda ko'rsatilmaydi.
            .filter { it.url != null }

    /**
     * Suhbatdagi barcha fayllar — profil ekranidagi «Fayllar» bo'limi, yangidan eskiga.
     * Ovoz va video bu yerga kirmaydi: ular alohida turlar va ro'yxatda boshqacha ko'rinadi.
     */
    val files: List<ChatMessageUi>
        get() = messages.asReversed()
            .filter { it.type == MessageType.FILE && !it.deleted && it.attachment != null }

    /** Matnli xabarlardagi havolalar — «Havolalar» bo'limi, yangidan eskiga. */
    val links: List<ChatLinkUi>
        get() = messages.asReversed()
            .filter { it.type == MessageType.TEXT && !it.deleted }
            .flatMap { message -> message.text.extractLinks().map { message.id to it } }
            .distinctBy { (_, url) -> url }
            .map { (id, url) -> ChatLinkUi(messageId = id, url = url, host = url.hostOf()) }

    /** Suhbatdoshning universiteti — katalogda topilmasa `null` (xom `emis-142` ko'rsatilmaydi). */
    val peerUniversity: String?
        get() = selected?.other?.universityId?.let { universityNames[it] }
}

/**
 * Mozaikada (albom to'rida) chiziladigan turlar — rasm, GIF va **video**.
 *
 * Telegramdagidek: bir martada tanlangan rasm va videolar bitta to'r bo'lib ko'rinadi va
 * kataklari bir xil — farqi faqat videoda o'ynatish belgisi va davomiyligi bo'lishida.
 *
 * ⚠️ GIF katakda **statik kadr**: `url` — ovozsiz MP4, uni ro'yxatda o'ynatish o'nlab
 * dekoderni bir vaqtda ochardi. Bosilganda pleyerda to'liq ochiladi.
 */
private val MEDIA_GRID = setOf(MessageType.IMAGE, MessageType.GIF, MessageType.VIDEO)

/**
 * Albomga qo'shiladigan turlar — rasm va video.
 *
 * GIF **kirmaydi**: server uni ovozsiz MP4 ga o'giradi va u odatda yakka, o'z nisbatida
 * yuboriladi; to'r katagiga kesib qo'yilsa GIF'ning ma'nosi yo'qolardi.
 */
private val ALBUM_LIKE = setOf(MessageType.IMAGE, MessageType.VIDEO)

/** O'z pufagida chiziladigan biriktirma turlari — mozaikaga yig'ilmaydi. */
private val ATTACHMENT_LIKE = setOf(MessageType.FILE, MessageType.VOICE)

/** Matndagi `http(s)://…` bo'laklari. Tinish belgilari havolaga yopishib qolmasin. */
internal fun String.extractLinks(): List<String> = split(' ', '\n', '\t')
    .map { it.trim().trimEnd('.', ',', ')', ']', '!', '?', ';', ':') }
    .filter { it.startsWith("https://") || it.startsWith("http://") }
    .filter { it.length > MIN_LINK_LENGTH }

/** `https://studentclub.uz/a/b?x=1` → `studentclub.uz`. */
internal fun String.hostOf(): String = substringAfter("://")
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
    private val clubRepository: ClubRepository,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val myId: String? = tokenStore.userId()

    /**
     * Klublar — "Klublar" papkasining ro'yxati.
     *
     * Alohida klublar ekrani YO'Q: qo'shilish/chiqish ham shu yerda bo'ladi, chunki klub —
     * jamoaviy suhbat va uning o'rni xabarlar ichida.
     *
     * [state] ga qo'shilmaydi: suhbat oqimi bilan hech qanday aloqasi yo'q va uni o'sha
     * combine'ga tiqish faqat qatlamni chuqurlashtirardi. Xato bo'lsa ro'yxat bo'sh qoladi.
     */
    val clubs: StateFlow<List<Club>> = clubRepository.observeClubs()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Klubga qo'shilish / undan chiqish (hozircha local). */
    fun toggleJoin(club: Club) {
        viewModelScope.launch {
            clubRepository.setJoined(club.id, !club.joined)
            showMessage(if (club.joined) "Klubdan chiqdingiz" else "Klubga qo'shildingiz")
        }
    }

    private val _folder = MutableStateFlow(ChatFolder.PERSONAL)

    /**
     * Tanlangan papka. [state] dan tashqarida — u ro'yxat ham, ochilgan suhbat ham bo'lgan
     * katta combine, papka esa unga umuman ta'sir qilmaydi (faqat qaysi ro'yxat chizilishi).
     *
     * ViewModel'da, ekranda emas: suhbat ochilib yopilganda ro'yxat kompozitsiyadan chiqadi
     * va `remember` tanlovni unutardi — foydalanuvchi har safar "Shaxsiy" ga qaytib tushardi.
     */
    val folder: StateFlow<ChatFolder> = _folder.asStateFlow()

    fun selectFolder(value: ChatFolder) {
        _folder.value = value
    }

    private val selectedId = MutableStateFlow<String?>(null)

    /**
     * Suhbat tanlanganmi — `state.selected` dan farqli, DARHOL to'g'ri javob beradi.
     *
     * `state.selected` suhbat local keshdagi ro'yxatda paydo bo'lgandan keyingina to'ladi,
     * ya'ni ochish tugagan payt u hali `null` bo'lishi mumkin. Ekran shu farqqa qarab
     * "ochilmoqda" va "ochib bo'lmadi" holatlarini ajratadi.
     */
    val hasOpenConversation: Boolean get() = selectedId.value != null

    private val draft = MutableStateFlow("")
    private val extra = MutableStateFlow(ExtraState())

    /** State'ga qo'shiladigan, oqimga bog'liq bo'lmagan qismlar. */
    private data class ExtraState(
        val loadingOlder: Boolean = false,
        val hasMoreHistory: Boolean = true,
        val message: String? = null,
        val unreadTotal: Int = 0,
    )

    /** "Yozmoqda" ni to'xtatuvchi taymer — 3 soniya jimlikdan keyin `typing:stop`. */
    private var typingStopJob: Job? = null
    private var typingActive = false

    /** Universitet nomlari — profil ekranida ko'rsatiladi. */
    private val universityNames = MutableStateFlow<Map<String, String>>(emptyMap())

    init {
        chatRepository.connectRealtime()
        viewModelScope.launch {
            chatRepository.refreshConversations()
            refreshUnreadTotal()
        }
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
            combine(
                chatRepository.observeLocalImages(),
                universityNames,
                chatRepository.observeUploads(),
            ) { local, unis, uploads -> Triple(local, unis, uploads) },
        ) { d, typing, rt, e, (local, unis, uploads) -> Rest(d, typing, rt, e, local, unis, uploads) },
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
                uploads = rest.uploads,
            ),
            draft = rest.draft,
            peerTyping = rest.typing,
            realtime = rest.realtime,
            loadingOlder = rest.extra.loadingOlder,
            hasMoreHistory = rest.extra.hasMoreHistory,
            message = rest.extra.message,
            unreadTotal = rest.extra.unreadTotal,
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
        val uploads: Map<String, UploadState>,
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
        uploads: Map<String, UploadState>,
    ): List<ChatMessageUi> {
        val groups = groupAlbums()
        return groups.mapIndexed { index, group ->
            // Guruh sarlavhasi — birinchi xabar; vaqt va belgichalar — oxirgisiniki.
            val head = group.first()
            val tail = group.last()
            val previous = groups.getOrNull(index - 1)?.last()
            val outgoing = head.senderId == myId
            ChatMessageUi(
                id = head.id,
                text = if (head.deleted) DELETED_TEXT else head.body,
                outgoing = outgoing,
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
                // O'chirilgan media ham oddiy tombstone bo'lib chiziladi — havolasi yo'q.
                type = if (head.deleted) MessageType.TEXT else head.type,
                // ⚠️ `GIF` ham shu ro'yxatga tushadi: u ham bitta tasvirli biriktirma va
                // xuddi rasm kabi chiziladi. Busiz GIF xabari hech qaysi pufakka tushmay,
                // ekranda BO'SH pufak bo'lib qolardi (tanasi taqiqlangan).
                images = if (head.type in MEDIA_GRID && !head.deleted) {
                    group.map { m ->
                        val upload = uploads[m.id]
                        ChatMediaItem(
                            messageId = m.id,
                            // Biriktirma URL'i himoyalangan (`/v1/media/{id}/raw`) — rasm
                            // yuklovchi tokenli klientdan foydalanadi (`createImageHttpClient`).
                            url = m.attachment?.previewUrl?.takeIf { it.isNotBlank() },
                            localBytes = localImages[m.id],
                            aspectRatio = m.attachment?.aspectRatio,
                            fullUrl = m.attachment?.url?.takeIf { it.isNotBlank() },
                            uploadProgress = upload?.progress,
                            uploading = upload != null,
                            // GIF ham pleyerda ochiladi (u ovozsiz MP4), lekin katakda
                            // davomiylik ko'rsatilmaydi — u yerda soniya sanashning ma'nosi yo'q.
                            video = m.type == MessageType.VIDEO || m.type == MessageType.GIF,
                            durationMs = if (m.type == MessageType.VIDEO) {
                                m.attachment?.durationMs ?: 0
                            } else {
                                0
                            },
                            processing = m.attachment?.processing == true,
                            failed = m.status == MessageStatus.FAILED,
                        )
                    }
                } else {
                    emptyList()
                },
                // Rasm bo'lmagan biriktirmalar — har biri o'z pufagida.
                attachment = head.attachment
                    ?.takeIf { head.type in ATTACHMENT_LIKE && !head.deleted }
                    ?.let { media ->
                        ChatAttachmentUi(
                            url = media.url,
                            thumbUrl = media.thumbUrl,
                            fileName = media.fileName,
                            sizeBytes = media.sizeBytes,
                            durationMs = media.durationMs,
                            waveform = media.waveform,
                            processing = media.processing,
                            aspectRatio = media.aspectRatio,
                        )
                    },
                sticker = head.bigEmojiOrNull(),
                // Server tasviri bo'lmasa — Fluent 3D. Bu **qo'lda yozilgan** emojiga ham
                // tegishli: yakka emoji baribir katta chizilardi, endi u tizim glifi emas,
                // stiker ko'rinishida chiqadi. Rasm kelmasa chizuvchi emojiga qaytadi
                // (`StickerImage`), ya'ni bu xavfsiz yaxshilanish.
                stickerUrl = head.sticker?.url?.takeIf { it.isNotBlank() && !head.deleted }
                    ?: head.bigEmojiOrNull()?.let { FluentEmoji.urlFor(it) },
                deleted = head.deleted,
                // Faqat o'z xabarimizni, faqat serverga yetib borganini va faqat bir marta.
                canDelete = outgoing && !head.deleted && head.seq > 0,
                messageIds = group.map { it.id },
                // Rasm to'ri o'z foizini katak-katak chizadi — bu yerda faqat yakka
                // biriktirmalar (fayl/video/ovoz) qoladi.
                upload = uploads[head.id]
                    ?.takeIf { head.type in ATTACHMENT_LIKE }
                    ?.let { ChatUploadUi(it.progress, it.fileName, it.sizeBytes) },
            )
        }
    }

    /**
     * `TEXT` xabarning tanasi faqat emojidan iborat bo'lsa — u katta chiziladi, `STICKER`
     * bo'lsa esa stikerning emojisi olinadi.
     *
     * Qo'lda yozilgan emoji endi **hech qachon** `STICKER` bo'lib qaytmaydi: yangi
     * kontraktda stiker `stickerId` bilan ketadi va tana taqiqlangan (`handoff/03-WEBSOCKET.md`).
     * Katta chizish shuning uchun sof ko'rsatish qaroriga aylandi.
     */
    private fun Message.bigEmojiOrNull(): String? = when {
        deleted -> null
        type == MessageType.STICKER -> sticker?.emoji?.takeIf { it.isNotBlank() } ?: body
        type == MessageType.TEXT && EmojiText.isLoneEmoji(body) -> body.trim()
        else -> null
    }

    /**
     * Ketma-ket rasmlarni albomga yig'adi.
     *
     * `albumId` ni **server qaytaradi** (`MessageDto.albumId`, 2026-07-29 dan), shuning
     * uchun to'r ikkala tomonda ham bir xil chiziladi. Vaqt oynasi ([ALBUM_WINDOW_SECONDS])
     * faqat **zaxira**: keshda kalitsiz eski qatorlar qolgan bo'lishi mumkin.
     */
    private fun List<Message>.groupAlbums(): List<List<Message>> {
        val groups = mutableListOf<MutableList<Message>>()
        forEach { message ->
            val current = groups.lastOrNull()
            val previous = current?.last()
            val joins = previous != null &&
                current.size < MAX_ALBUM_SIZE &&
                // Rasm va video ARALASH albomga tushadi (Telegramdagidek) — ular bitta
                // tanlovda yuborilgan bo'lsa `albumId` ham bitta bo'ladi.
                message.type in ALBUM_LIKE &&
                previous.type in ALBUM_LIKE &&
                // Tombstone alohida qator bo'lib qoladi — u to'rga qo'shilmaydi.
                !message.deleted && !previous.deleted &&
                message.senderId == previous.senderId &&
                sameAlbum(previous, message)
            if (joins) current.add(message) else groups.add(mutableListOf(message))
        }
        return groups
    }

    private fun sameAlbum(a: Message, b: Message): Boolean = when {
        // Kamida bittasida kalit bor — u holda FAQAT kalit hal qiladi.
        a.albumId != null || b.albumId != null -> a.albumId == b.albumId
        // Kalitsiz zaxira — faqat RASMLAR uchun (eski kesh qatorlari). Videoni vaqt
        // oynasiga qarab qo'shsak, ketma-ket yuborilgan mustaqil ikki fayl ham bitta
        // to'rga tushib qolardi: albom kalitini endi server beradi, taxminga hojat yo'q.
        a.type == MessageType.IMAGE && b.type == MessageType.IMAGE ->
            (b.createdAt - a.createdAt).inWholeSeconds <= ALBUM_WINDOW_SECONDS
        else -> false
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
     * Bog'langan talaba bilan suhbatni ochadi ("Xabar" tugmasi).
     *
     * **Avval kesh, keyin tarmoq.** Shu odam bilan suhbat local bazada bo'lsa u DARHOL
     * ochiladi va korutina shu yerda tugaydi — ekran tarmoqni umuman kutmaydi. Server
     * bilan tasdiqlash (`POST /v1/conversations` **idempotent**, ustiga suhbatni
     * yangilash) fonda davom etadi: u ikkita so'rov, ya'ni sekin aloqada bir necha
     * soniya — ilgari ekran o'sha vaqt davomida qotib turardi.
     *
     * Kesh bo'sh bo'lsagina (birinchi marta yozilyapti) server javobi kutiladi: suhbat
     * id'sini faqat u beradi.
     */
    fun openWithStudent(studentId: String) = viewModelScope.launch {
        val cached = chatRepository.cachedDirectId(studentId)
        if (cached != null) {
            openById(cached)
            confirmDirect(studentId)
            return@launch
        }
        when (val res = chatRepository.openDirect(studentId)) {
            is Resource.Success -> openById(res.data)
            // `403 NOT_CONNECTED` — avval bog'lanish kerak.
            is Resource.Error -> extra.update { it.copy(message = res.message) }
            Resource.Loading -> Unit
        }
    }

    /**
     * Keshdan ochilgan suhbatni server bilan tasdiqlaydi (suhbatdoshning holati, oxirgi
     * xabari va o'qilmaganlar sanog'i yangilanadi).
     *
     * MUHIM: `viewModelScope` da, [openWithStudent] ning ICHIDA `launch` bilan emas — bola
     * korutina bo'lsa `Job.join()` uni ham kutardi va ekran yana tarmoqqa bog'lanib qolardi.
     * Xato bo'lsa (tarmoq yo'q) keshdagi yozishma baribir ochiq qoladi.
     */
    private fun confirmDirect(studentId: String) {
        viewModelScope.launch { chatRepository.openDirect(studentId) }
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

    /**
     * Yozishma chizishga tayyor bo'lguncha kutadi (`state.selected` to'lguncha) — `true`
     * qaytsa ekran uni ko'rsatishi mumkin.
     *
     * To'g'ridan-to'g'ri ochish uchun: suhbat id'si bor, lekin uning qatori keshdagi
     * ro'yxatga tushmaguncha yozishma chizilmaydi. Kutish [OPEN_THREAD_TIMEOUT_MS] bilan
     * chegaralangan — suhbat umuman topilmasa (eski push) ekran bo'sh qolib ketmasin.
     */
    suspend fun awaitThread(): Boolean =
        withTimeoutOrNull(OPEN_THREAD_TIMEOUT_MS) { state.first { it.selected != null } } != null

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
        viewModelScope.launch {
            chatRepository.markRead(id)
            refreshUnreadTotal()
        }
    }

    /**
     * Badge hisoblagichi. Xato bo'lsa **jim** o'tiladi: eski qiymat noto'g'ri ko'rsatishdan
     * ko'ra, badge uchun xato xabari chiqarish bezovta qilardi.
     */
    private suspend fun refreshUnreadTotal() {
        val res = chatRepository.unreadCount()
        if (res is Resource.Success) extra.update { it.copy(unreadTotal = res.data.total) }
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
        // 3 soniya jimlikdan keyin — `typing:stop` (03-WEBSOCKET.md §11).
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
    fun sendImages(images: List<OutgoingImage>, caption: String? = null) {
        val id = selectedId.value ?: return
        if (images.isEmpty()) return
        stopTyping()
        viewModelScope.launch {
            when (val res = chatRepository.sendImages(id, images, caption)) {
                // Yiqilganlari ro'yxatda `FAILED` bo'lib qoladi — qayta urinish mumkin.
                is Resource.Error -> extra.update { it.copy(message = res.message) }
                else -> Unit
            }
        }
    }

    /** Hujjat yuborish (`kind = FILE`). */
    fun sendFile(bytes: ByteArray, fileName: String) {
        val id = selectedId.value ?: return
        stopTyping()
        viewModelScope.launch {
            when (val res = chatRepository.sendFile(id, bytes, fileName)) {
                is Resource.Error -> extra.update { it.copy(message = res.message) }
                else -> Unit
            }
        }
    }

    /**
     * Video yuborish (`kind = VIDEO`). Server kerak bo'lsa transkod qiladi.
     *
     * Fayl keshdan oqim bilan yuklanadi va yuborilgach o'chiriladi — qarang [OutgoingVideo].
     */
    fun sendVideo(video: OutgoingVideo) {
        val id = selectedId.value ?: return
        stopTyping()
        viewModelScope.launch {
            when (val res = chatRepository.sendVideo(id, video)) {
                is Resource.Error -> extra.update { it.copy(message = res.message) }
                else -> Unit
            }
        }
    }

    /**
     * Ketayotgan yuborishni to'xtatadi — pufakdagi `×`.
     *
     * Videoda siqish ham, yuklash ham uziladi va optimistik qator yo'qoladi. Xato
     * ko'rsatilmaydi: bekor qilish foydalanuvchining o'z qarori, unga "bekor qilindi"
     * deb aytish ortiqcha shovqin bo'lardi.
     */
    fun cancelUpload(messageId: String) {
        viewModelScope.launch { chatRepository.cancelSend(messageId) }
    }

    /** Ovozli xabar (`kind = VOICE`). To'lqin va davomiylikni server hisoblaydi. */
    fun sendVoice(bytes: ByteArray, fileName: String) {
        val id = selectedId.value ?: return
        stopTyping()
        viewModelScope.launch {
            when (val res = chatRepository.sendVoice(id, bytes, fileName)) {
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
     * Qidiruvdan tanlangan **provayder stikeri** (KLIPY). Katalogdagi stikerdan farqi —
     * `stickerId` emas, `sticker` obyekti bilan ketadi (`handoff/06-STICKER-SEARCH.md` §2).
     */
    fun sendStickerRef(item: StickerSearchItem) {
        val id = selectedId.value ?: return
        stopTyping()
        viewModelScope.launch {
            when (val res = chatRepository.sendStickerRef(id, item.toRef())) {
                is Resource.Error -> extra.update { it.copy(message = res.message) }
                else -> Unit
            }
        }
    }

    /**
     * Qidiruvdan tanlangan GIF. Fayl yuklanmaydi — provayder havolasi serverga
     * **o'zgartirilmasdan** qaytariladi (`handoff/04-GIF-INTEGRATION.md`).
     */
    fun sendGif(gif: GifItem) {
        val id = selectedId.value ?: return
        stopTyping()
        viewModelScope.launch {
            when (val res = chatRepository.sendGif(id, gif.toRef())) {
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

    /**
     * Belgilangan xabarlarni o'chirish — Telegram'dagi ikki qamrov bilan.
     *
     * - [forEveryone] = `true` — **soft delete**: qator tarixda tombstone bo'lib qoladi,
     *   ikkala a'zoga WS `message:deleted` ketadi (`handoff/02-API-CHANGES.md` §4b).
     *   Faqat o'z xabaringga qo'llanadi.
     * - [forEveryone] = `false` — xabar **faqat shu qurilmada** yashiriladi.
     *
     * Albomda bir nechta xabar bor — ro'yxatga ularning hammasi tushadi, chunki ekranda
     * ular bitta to'r.
     */
    fun deleteMessages(messageIds: List<String>, forEveryone: Boolean) = viewModelScope.launch {
        when (val res = chatRepository.deleteMessages(messageIds, forEveryone)) {
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

    /**
     * Media so'rovlari uchun sarlavhalar.
     *
     * Chat biriktirmalari `GET /v1/media/{id}/raw` orqali beriladi va u **suhbat a'zoligini
     * tekshiradi** — tokensiz so'rov `404` oladi. Rasm yuklovchi (Coil) buni o'zi qo'yadi,
     * ovoz/video pleyerlari esa alohida — ular shu xaritani oladi.
     */
    fun mediaHeaders(): Map<String, String> =
        tokenStore.tokens()?.accessToken?.let { mapOf("Authorization" to "Bearer $it") }.orEmpty()

    /** Hali tayyor bo'lmagan amal uchun bir martalik xabar («tez orada»). */
    fun showMessage(text: String) = extra.update { it.copy(message = text) }

    fun messageShown() = extra.update { it.copy(message = null) }

    override fun onCleared() {
        chatRepository.disconnectRealtime()
        super.onCleared()
    }

    private companion object {
        const val TYPING_IDLE_MS = 3_000L

        /** To'g'ridan-to'g'ri ochilgan suhbatni kutish chegarasi ([awaitThread]). */
        const val OPEN_THREAD_TIMEOUT_MS = 5_000L

        /** Bir albomdagi rasmlar chegarasi — `ChatRepositoryImpl` dagi bilan bir xil. */
        const val MAX_ALBUM_SIZE = 10

        /**
         * Albomni taxmin qilish oynasi — **faqat zaxira**: keshda `albumId` siz eski
         * qatorlar qolgan bo'lishi mumkin. Yangi xabarlarda kalit serverdan keladi.
         */
        const val ALBUM_WINDOW_SECONDS = 60L

        /** Tombstone matni — xabar tarixda qoladi, lekin tanasi yo'q. */
        const val DELETED_TEXT = "Xabar o'chirildi"
    }
}
