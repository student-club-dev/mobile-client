package dev.feature.stories.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.Immutable
import dev.core.common.Resource
import dev.core.common.auth.TokenStore
import dev.feature.stories.domain.model.StoryGroup
import dev.feature.stories.domain.repository.StoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/**
 * **Boshqa talabaning postlari** — uning profilidagi «Postlar» bo'limi.
 *
 * ⚠️ Manba — lenta (`GET /v1/stories/feed`), chunki backendda «falonchining hikoyalari»
 * degan alohida endpoint yo'q. Bu ayni paytda **to'g'ri eshik** ham: lentaga faqat
 * bog'langan odamlarning va faqat muddati o'tmagan hikoyalari tushadi, ya'ni bu yerda
 * ko'rish huquqi qo'shimcha tekshirishsiz o'z-o'zidan hal bo'ladi.
 *
 * Arxiv ko'rinmaydi — u faqat egasiga (`STORY_ARCHIVE_BACKEND.md`).
 */
@Composable
fun StudentPostsSection(
    studentId: String,
    modifier: Modifier = Modifier,
    vm: StudentPostsViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val viewer by vm.viewer.collectAsStateWithLifecycle()
    LaunchedEffect(studentId) { vm.load(studentId) }

    val stories = state.group?.stories.orEmpty()
    // Yuklanish yozuvi YO'Q va bo'sh holat ham yo'q: bo'lim umuman ma'lumoti bor bo'lgandagina
    // qo'shiladi (`rememberPeerProfileSections`). Ya'ni bu yerga faqat to'lgan ro'yxat keladi.
    if (stories.isNotEmpty()) {
        PostGrid(
            stories = stories,
            // Boshqa odamning postida sana ham, ko'rishlar soni ham ko'rsatilmaydi:
            // `viewsCount` unda ATAYLAB `null` (§3), sana esa 24 soatlik hikoyada ortiqcha.
            archived = true,
            onOpen = { index -> vm.open(index) },
            modifier = modifier,
        )
    }

    if (viewer.open) {
        StoryViewerDialog(
            state = viewer,
            mediaHeaders = vm.mediaHeaders(),
            onNext = vm::next,
            onPrevious = vm::previous,
            onClose = vm::close,
            // O'chirish faqat muallifda — bu yerda hech qachon chaqirilmaydi.
            onDelete = {},
        )
    }
}

@Immutable
data class StudentPostsState(
    /** Lentadagi shu muallif guruhi; `null` — faol hikoyasi yo'q. */
    val group: StoryGroup? = null,
    val loading: Boolean = true,
)

/** Profildagi «Postlar» bo'limi uchun — bitta muallifning faol hikoyalari. */
class StudentPostsViewModel(
    private val repository: StoryRepository,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val _state = MutableStateFlow(StudentPostsState())
    val state: StateFlow<StudentPostsState> = _state.asStateFlow()

    private val _viewer = MutableStateFlow(StoryViewerState())
    val viewer: StateFlow<StoryViewerState> = _viewer.asStateFlow()

    private var loadedId: String? = null

    fun mediaHeaders(): Map<String, String> =
        tokenStore.tokens()?.accessToken?.let { mapOf("Authorization" to "Bearer $it") }.orEmpty()

    /** Bir xil talaba uchun qayta so'ralmaydi — bo'lim tab almashganda qayta chiziladi. */
    fun load(studentId: String) {
        if (loadedId == studentId) return
        loadedId = studentId
        _state.value = StudentPostsState(loading = true)
        viewModelScope.launch {
            val feed = repository.feed()
            _state.value = StudentPostsState(
                group = (feed as? Resource.Success)?.data?.firstOrNull { it.author.id == studentId },
                loading = false,
            )
        }
    }

    fun open(index: Int) {
        val group = _state.value.group ?: return
        _viewer.value = StoryViewerState(group = group, index = index)
        markViewed()
    }

    fun next() {
        val current = _viewer.value
        val group = current.group ?: return
        // Oxirgi hikoyadan keyin yopiladi: bu profil ichidagi bo'lim, lentadagidek
        // keyingi odamga o'tib ketish bu yerda kutilmagan bo'lardi.
        if (current.index + 1 < group.stories.size) {
            _viewer.value = current.copy(index = current.index + 1)
            markViewed()
        } else {
            close()
        }
    }

    fun previous() {
        val current = _viewer.value
        if (current.index <= 0) return
        _viewer.value = current.copy(index = current.index - 1)
    }

    fun close() {
        _viewer.value = StoryViewerState()
    }

    /** Ko'rish belgisi — fon amali, xatosi yutiladi ([StoryRepository.markViewed]). */
    private fun markViewed() {
        val story = _viewer.value.story ?: return
        if (story.seen) return
        viewModelScope.launch { repository.markViewed(story.id) }
        _state.update { current ->
            val group = current.group ?: return@update current
            current.copy(
                group = group.copy(
                    stories = group.stories.map { if (it.id == story.id) it.copy(seen = true) else it },
                ),
            )
        }
    }
}
