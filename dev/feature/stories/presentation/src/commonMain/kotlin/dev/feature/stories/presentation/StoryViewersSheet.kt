package dev.feature.stories.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.core.common.Resource
import dev.core.uikit.components.ScAvatar
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScText
import dev.core.uikit.theme.Sc
import dev.feature.connections.domain.model.StudentSummary
import dev.feature.stories.domain.repository.StoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * «Kim ko'rgan» ro'yxati — hikoya ko'ruvchisining ustiga chiqadigan varaq.
 *
 * `ModalBottomSheet` **ataylab ishlatilmadi**: ro'yxat to'liq ekranli `Dialog` ichida
 * ochiladi, varaq esa o'ziga alohida oyna yasaydi — u story oynasining orqasiga tushib,
 * tegishlar noto'g'ri oynaga borardi. Bu yerda esa u o'sha oynadagi oddiy qatlam.
 *
 * Arxivdagi post uchun ham ishlaydi: ko'rishlar soni muzlagan bo'lsa ham ro'yxat ochiladi
 * (`STORY_ARCHIVE_BACKEND.md` §2.3).
 */
@Composable
internal fun StoryViewersSheet(
    storyId: String,
    onClose: () -> Unit,
    vm: StoryViewersViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(storyId) { vm.load(storyId) }

    val listState = rememberLazyListState()
    // Oxiriga yaqinlashganda keyingi sahifa — «Yana» tugmasi ro'yxatning ichida g'alati
    // ko'rinardi va bu yerda barmoq baribir pastga suriladi.
    LaunchedEffect(listState, state.items.size, state.hasNext) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (state.hasNext && lastVisible >= state.items.size - VIEWERS_PREFETCH) {
                    vm.loadMore()
                }
            }
    }

    Box(Modifier.fillMaxSize()) {
        // Fon — bosilsa yopiladi. `indication` yo'q: bu qorong'i parda, tugma emas.
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                ),
        )

        Column(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(SHEET_HEIGHT)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Sc.Card)
                // Varaqning ustiga tegish pardaga o'tmasin — aks holda ro'yxatning bo'sh
                // joyiga bosish uni yopardi.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .navigationBarsPadding(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 16.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScText(
                    // Umumiy son serverdan keladi: ro'yxat sahifalanadi, ya'ni yuklangan
                    // qatorlar soni «nechta ko'rgan» degan savolga javob bermaydi.
                    if (state.total > 0) "${state.total} ta ko'rish" else "Ko'rganlar",
                    16f,
                    FontWeight.ExtraBold,
                    Sc.Ink,
                    Modifier.weight(1f),
                    maxLines = 1,
                )
                Icon(
                    ScIcons.Close,
                    "Yopish",
                    tint = Sc.Muted,
                    modifier = Modifier.size(20.dp).clickable(onClick = onClose),
                )
            }

            when {
                state.loading && state.items.isEmpty() -> SheetNote("Yuklanmoqda…")

                state.message != null -> SheetNote(state.message.orEmpty())

                state.items.isEmpty() -> SheetNote(
                    "Hozircha hech kim ko'rmagan. Post bog'langanlaringizga ko'rinadi.",
                )

                else -> LazyColumn(Modifier.fillMaxWidth(), state = listState) {
                    items(state.items, key = { it.id }) { student -> ViewerRow(student) }
                    if (state.hasNext) {
                        item { SheetNote("Yuklanmoqda…") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewerRow(student: StudentSummary) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScAvatar(name = student.displayName, size = 40.dp, avatarUrl = student.avatarUrl)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ScText(student.displayName, 14f, FontWeight.Bold, Sc.Ink, maxLines = 1)
            // Username bo'lmasa qator bir qatorli qoladi — bo'sh matn joy egallamasin.
            student.username?.let {
                ScText("@$it", 12f, FontWeight.Medium, Sc.MutedLight, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SheetNote(text: String) {
    Box(
        Modifier.fillMaxWidth().height(90.dp).padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        ScText(text, 13f, FontWeight.Medium, Sc.MutedLight, maxLines = 3)
    }
}

@Immutable
data class StoryViewersState(
    val items: List<StudentSummary> = emptyList(),
    /** Serverdagi umumiy son — yuklangan qatorlardan ko'p bo'lishi mumkin. */
    val total: Int = 0,
    val hasNext: Boolean = false,
    val loading: Boolean = false,
    val message: String? = null,
)

/**
 * `GET /v1/stories/{id}/views` — **faqat o'z** postim uchun.
 *
 * Ro'yxat hikoya ko'ruvchisidan chaqiriladi va shu sababli alohida ViewModel'da: postlar
 * ro'yxatining holati bilan bir joyda tursa, ko'ruvchi yopilganda ham xotirada qolardi.
 */
class StoryViewersViewModel(
    private val repository: StoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StoryViewersState())
    val state: StateFlow<StoryViewersState> = _state.asStateFlow()

    private var storyId: String? = null

    /** Varaq ochilganda. Bir xil post qayta so'ralmaydi — varaq yopilib ochilsa ham. */
    fun load(id: String) {
        if (storyId == id && (_state.value.items.isNotEmpty() || _state.value.loading)) return
        storyId = id
        _state.value = StoryViewersState(loading = true)
        viewModelScope.launch {
            when (val result = repository.viewers(id, page = FIRST_PAGE)) {
                is Resource.Success -> _state.value = StoryViewersState(
                    items = result.data.items,
                    total = result.data.total,
                    hasNext = result.data.hasNext,
                )
                is Resource.Error -> _state.value = StoryViewersState(message = result.message)
                else -> _state.update { it.copy(loading = false) }
            }
        }
    }

    /**
     * Keyingi sahifa. Sahifa raqami yuklangan qatorlardan hisoblanadi — [MyPostsViewModel]
     * dagi arxiv bilan bir xil sabab: alohida hisoblagich ro'yxatdan ajralib ketardi.
     */
    fun loadMore() {
        val id = storyId ?: return
        val current = _state.value
        if (current.loading || !current.hasNext) return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val nextPage = current.items.size / StoryRepository.DEFAULT_VIEWERS_PAGE + 1
            when (val result = repository.viewers(id, page = nextPage)) {
                is Resource.Success -> _state.update {
                    it.copy(
                        items = (it.items + result.data.items).distinctBy { student -> student.id },
                        total = result.data.total,
                        hasNext = result.data.hasNext,
                        loading = false,
                    )
                }
                is Resource.Error -> _state.update { it.copy(loading = false, message = result.message) }
                else -> _state.update { it.copy(loading = false) }
            }
        }
    }

    private companion object {
        const val FIRST_PAGE = 1
    }
}

/** Ekran balandligining ulushi — orqada hikoyaning bir qismi ko'rinib tursin. */
private const val SHEET_HEIGHT = 0.62f

/** Oxirigacha shuncha qator qolganda keyingi sahifa so'raladi. */
private const val VIEWERS_PREFETCH = 5
