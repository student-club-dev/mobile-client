package dev.feature.chat.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.feature.chat.domain.model.MessageType
import dev.feature.chat.domain.repository.ChatRepository
import dev.feature.connections.presentation.ProfileSection
import dev.feature.stories.presentation.StudentPostsSection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * Talaba profilidagi **to'rtta bo'lim** — «Postlar · Media · Fayllar · Havolalar».
 *
 * Profil varag'ining o'zi umumiy (`connections:presentation`), bo'limlar esa shu yerda
 * quriladi va u **chatdan ham, story lentasidan ham bir xil** ochiladi: ilgari story
 * tomonida faqat «Postlar» bo'lib, ikkita boshqa-boshqa profil taassuroti qolardi.
 *
 * ⚠️ Media/fayl/havolalar **mavjud suhbatdan** o'qiladi va suhbat **yaratilmaydi**: profil
 * ko'rish uchun `POST /v1/conversations` yuborilsa, hech qachon yozishmagan odam bilan
 * bo'sh suhbat paydo bo'lardi. Suhbat bo'lmasa bo'limlar bo'sh ko'rinadi.
 */
@Composable
fun rememberPeerProfileSections(
    studentId: String,
    /** Fayl qatori bosildi — yuklab olish hali yo'q, chaqiruvchi xabar ko'rsatadi. */
    onOpenFile: (ChatFileUi) -> Unit = {},
    vm: PeerMediaViewModel = koinViewModel(),
): List<ProfileSection> {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(studentId) { vm.load(studentId) }

    return listOf(
        ProfileSection("Postlar") { StudentPostsSection(studentId) },
        ProfileSection("Media") {
            if (state.media.isEmpty()) {
                ChatEmptySection(if (state.loading) "Yuklanmoqda…" else "Bu suhbatda hali rasm yo'q")
            } else {
                ChatPhotoGrid(state.media, onOpen = { index -> vm.openViewer(index) })
                // Ko'rgich bo'limning ICHIDA: profil varag'i dialog bo'lib ochilgan va
                // ko'rgichni tashqarida chizsak u varaq ostida qolib ketardi.
                state.viewerIndex?.let { index ->
                    ImageViewerDialog(
                        images = state.media,
                        startIndex = index,
                        onDismiss = vm::closeViewer,
                    )
                }
            }
        },
        ProfileSection("Fayllar") {
            if (state.files.isEmpty()) {
                ChatEmptySection(if (state.loading) "Yuklanmoqda…" else "Bu suhbatda fayl yuborilmagan")
            } else {
                ChatFileList(state.files, onOpen = onOpenFile)
            }
        },
        ProfileSection("Havolalar") {
            if (state.links.isEmpty()) {
                ChatEmptySection(if (state.loading) "Yuklanmoqda…" else "Bu suhbatda havola yuborilmagan")
            } else {
                ChatLinkList(state.links)
            }
        },
    )
}

/** «Fayllar» bo'limidagi bitta qator — ro'yxatga xabarning o'zi kerak emas. */
@Immutable
data class ChatFileUi(val messageId: String, val fileName: String, val sizeBytes: Long)

@Immutable
data class PeerMediaState(
    val media: List<ChatMediaItem> = emptyList(),
    val files: List<ChatFileUi> = emptyList(),
    val links: List<ChatLinkUi> = emptyList(),
    val loading: Boolean = true,
    /** Ochilgan rasm ko'rgichi — `null` bo'lsa yopiq. */
    val viewerIndex: Int? = null,
)

/**
 * Profil bo'limlari uchun suhbat medialari.
 *
 * Chatning o'z [ChatViewModel] idan alohida: u butun suhbat ekranini (yozish, o'qildi
 * kursorlari, yuklashlar) boshqaradi va profil story lentasidan ochilganda umuman
 * yaratilmagan bo'ladi.
 */
class PeerMediaViewModel(private val repository: ChatRepository) : ViewModel() {

    private val _state = MutableStateFlow(PeerMediaState())
    val state: StateFlow<PeerMediaState> = _state.asStateFlow()

    private var loadedId: String? = null

    fun load(studentId: String) {
        if (loadedId == studentId) return
        loadedId = studentId
        _state.value = PeerMediaState(loading = true)
        viewModelScope.launch {
            val conversation = repository.observeConversations().first()
                .firstOrNull { it.other.id == studentId }
            if (conversation == null) {
                // Suhbat yo'q — ko'rsatadigan media ham yo'q. Yangisini OCHMAYMIZ.
                _state.value = PeerMediaState(loading = false)
                return@launch
            }
            // Kesh bo'sh bo'lsa (suhbat hech ochilmagan) tarixning oxirgi sahifasi keladi.
            launch { repository.loadLatest(conversation.id) }
            repository.observeMessages(conversation.id).collect { messages ->
                val alive = messages.filter { it.deletedAt == null }
                _state.value = PeerMediaState(
                    // Yangidan eskiga — profildagi to'r shunday tartibda.
                    media = alive.asReversed()
                        .filter { it.type in MEDIA_TYPES && it.attachment != null }
                        .map { message ->
                            val attachment = message.attachment!!
                            ChatMediaItem(
                                messageId = message.id,
                                url = attachment.previewUrl.takeIf { it.isNotBlank() },
                                localBytes = null,
                                aspectRatio = attachment.aspectRatio,
                                fullUrl = attachment.url.takeIf { it.isNotBlank() },
                                video = message.type != MessageType.IMAGE,
                                durationMs = if (message.type == MessageType.VIDEO) attachment.durationMs else 0,
                            )
                        },
                    files = alive.asReversed()
                        .filter { it.type == MessageType.FILE && it.attachment != null }
                        .map { message ->
                            ChatFileUi(
                                messageId = message.id,
                                fileName = message.attachment?.fileName ?: "Fayl",
                                sizeBytes = message.attachment?.sizeBytes ?: 0,
                            )
                        },
                    links = alive.asReversed()
                        .filter { it.type == MessageType.TEXT }
                        .flatMap { message -> message.body.extractLinks().map { message.id to it } }
                        .distinctBy { (_, url) -> url }
                        .map { (id, url) -> ChatLinkUi(messageId = id, url = url, host = url.hostOf()) },
                    loading = false,
                )
            }
        }
    }

    /** To'rdagi rasm bosildi — ko'rgich profil varag'ining ichida ochiladi. */
    fun openViewer(index: Int) {
        _state.value = _state.value.copy(viewerIndex = index)
    }

    fun closeViewer() {
        _state.value = _state.value.copy(viewerIndex = null)
    }

    private companion object {
        /** To'rga tushadigan turlar — ovoz va fayl bu yerda emas. */
        val MEDIA_TYPES = setOf(MessageType.IMAGE, MessageType.VIDEO, MessageType.GIF)
    }
}
