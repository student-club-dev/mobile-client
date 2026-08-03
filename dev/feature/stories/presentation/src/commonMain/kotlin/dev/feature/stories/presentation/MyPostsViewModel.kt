package dev.feature.stories.presentation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.core.common.Resource
import dev.core.common.auth.TokenStore
import dev.feature.connections.domain.model.StudentSummary
import dev.feature.stories.domain.model.Story
import dev.feature.stories.domain.model.StoryGroup
import dev.feature.stories.domain.repository.StoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Profildagi «Postlar» va «Arxivlangan postlar» bo'limlarining holati.
 *
 * Ikkala ro'yxat ham **bir vaqtda** yuklanadi: ular bitta tanlagichning ikki tomoni va
 * foydalanuvchi ular orasida bemalol yuradi — har o'tishda qayta so'rov yuborilsa bo'lim
 * har safar bo'sh holatdan boshlanardi.
 */
@Immutable
data class MyPostsState(
    /** Faol postlar — 24 soati tugamaganlari (`GET /v1/stories/mine`), yangidan eskiga. */
    val posts: List<Story> = emptyList(),
    /** Arxiv — muddati tugab, faqat menga ko'rinadigan postlar. */
    val archived: List<Story> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    /** Arxivning davomi bormi — «Yana» tugmasi shunga qarab chiziladi. */
    val hasMoreArchived: Boolean = false,
    val message: String? = null,
)

/**
 * Profil postlari — o'z lavhalarim, ikki ro'yxatda.
 *
 * Lenta ([StoriesViewModel]) dan **ataylab ajratilgan**: u bosh ekranda yashaydi va
 * boshqalarning lavhalari bilan birga aylanadi, bu yerda esa faqat meniki va arxiv kerak.
 * Bitta ViewModel'ga qo'shilsa profil ochilganda butun lenta ham qayta yuklanardi.
 */
class MyPostsViewModel(
    private val repository: StoryRepository,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val _state = MutableStateFlow(MyPostsState())
    val state: StateFlow<MyPostsState> = _state.asStateFlow()

    private val _viewer = MutableStateFlow(StoryViewerState())
    val viewer: StateFlow<StoryViewerState> = _viewer.asStateFlow()

    /** Story medialari token bilan so'raladi (`handoff/07-STORIES.md` §11.2). */
    fun mediaHeaders(): Map<String, String> =
        tokenStore.tokens()?.accessToken?.let { mapOf("Authorization" to "Bearer $it") }.orEmpty()

    init {
        refresh()
    }

    fun refresh() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            // Telefondagi «StudentClub» papkasi BIR marta o'qiladi va ikkala ro'yxatga ham
            // bog'lanadi: o'z postingni ko'rish uchun serverdan qayta yuklab olish shart
            // emas, arxivda esa media serverda umuman bo'lmasligi mumkin.
            val local = localMediaUrls()
            val mine = repository.mine()
            val archive = repository.archive(page = FIRST_PAGE)
            _state.update { current ->
                current.copy(
                    // Faol postlar serverdan **eskidan yangiga** keladi (ko'rish tartibi),
                    // to'rda esa yangisi tepada turishi kerak.
                    posts = (mine as? Resource.Success)?.data
                        ?.sortedByDescending { it.createdAt }
                        ?.map { it.withLocalMedia(local) }
                        ?: current.posts,
                    archived = (archive as? Resource.Success)?.data?.items
                        ?.map { it.withLocalMedia(local) }
                        ?: current.archived,
                    hasMoreArchived = (archive as? Resource.Success)?.data?.hasNext ?: false,
                    loading = false,
                )
            }
        }
    }

    /**
     * Arxivning keyingi sahifasi.
     *
     * Sahifa raqami ro'yxat uzunligidan hisoblanadi: oraliqda post o'chirilsa raqamni alohida
     * saqlash bilan ular bir-biridan ajralib ketardi.
     */
    fun loadMoreArchived() {
        val current = _state.value
        if (current.loadingMore || !current.hasMoreArchived) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val nextPage = current.archived.size / StoryRepository.DEFAULT_ARCHIVE_PAGE + 1
            when (val result = repository.archive(page = nextPage)) {
                is Resource.Success -> {
                    val local = localMediaUrls()
                    val items = result.data.items.map { it.withLocalMedia(local) }
                    _state.update {
                        it.copy(
                            // Takror kelgan qatorlar tashlab yuboriladi — sahifa chegarasida
                            // yangi post qo'shilsa server oynani suradi.
                            archived = (it.archived + items).distinctBy { story -> story.id },
                            hasMoreArchived = result.data.hasNext,
                            loadingMore = false,
                        )
                    }
                }
                is Resource.Error -> _state.update {
                    it.copy(loadingMore = false, message = result.message)
                }
                else -> _state.update { it.copy(loadingMore = false) }
            }
        }
    }

    /**
     * Postni ochadi — ko'ruvchi lenta bilan bir xil ([StoryViewerDialog]).
     *
     * Guruh shu yerda yig'iladi: muallif — o'zim, ism va avatar tashqaridan (profil
     * ekranidan) keladi, chunki story moduli profil modulini bilmaydi.
     */
    fun open(stories: List<Story>, index: Int, authorName: String, authorAvatarUrl: String?) {
        val story = stories.getOrNull(index) ?: return
        _viewer.value = StoryViewerState(
            group = StoryGroup(
                author = StudentSummary(
                    id = story.authorId,
                    fullName = authorName,
                    avatarUrl = authorAvatarUrl,
                ),
                stories = stories,
                // O'z postim — halqa yonmaydi.
                hasUnseen = false,
                lastCreatedAt = story.createdAt,
            ),
            index = index,
        )
    }

    /** Oxirgi postdan keyin ko'ruvchi yopiladi — lentadagidek keyingi guruhga o'tilmaydi. */
    fun next() {
        val current = _viewer.value
        val group = current.group ?: return
        if (current.index + 1 < group.stories.size) {
            _viewer.value = current.copy(index = current.index + 1)
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

    /**
     * Postni o'chiradi — **ikkala** ro'yxatdan ham.
     *
     * Qator serverdan javob kelishidan oldin olib tashlanadi: o'chirish tugmasi
     * tasdiqlash oynasi ortida va u yiqilishi kamdan-kam. Xato bo'lsa ro'yxat qayta
     * o'qiladi ([refresh]) va post o'z joyiga qaytadi.
     */
    fun delete(storyId: String) {
        val open = _viewer.value
        // Ko'ruvchi ochiq bo'lsa yopiladi: o'chirilgan postni ko'rsatib turishning ma'nosi yo'q.
        if (open.story?.id == storyId) close()
        _state.update {
            it.copy(
                posts = it.posts.filterNot { story -> story.id == storyId },
                archived = it.archived.filterNot { story -> story.id == storyId },
            )
        }
        viewModelScope.launch {
            when (val result = repository.delete(storyId)) {
                is Resource.Error -> {
                    _state.update { it.copy(message = result.message) }
                    refresh()
                }
                else -> Unit
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    private companion object {
        const val FIRST_PAGE = 1
    }
}
