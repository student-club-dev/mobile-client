package dev.feature.chat.presentation.gif

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.core.common.Resource
import dev.feature.chat.domain.model.GifErrorKind
import dev.feature.chat.domain.model.GifProvider
import dev.feature.chat.domain.model.StickerCatalog
import dev.feature.chat.domain.model.StickerPack
import dev.feature.chat.domain.model.StickerSearchItem
import dev.feature.chat.domain.model.gifErrorKind
import dev.feature.chat.domain.repository.StickerRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Stiker panelining holati — katalog **va** qidiruv. */
@Immutable
data class StickerPanelState(
    val packs: List<StickerPack> = StickerCatalog.packs,
    /**
     * Katalog serverdan keldimi. `false` — ilovaga kiritilgan Fluent Emoji zaxirasi
     * ko'rsatilyapti (backendda stiker **tasvirlari** yo'q, `PENDING_ACTIONS.md` §6).
     */
    val fromServer: Boolean = false,
    val loading: Boolean = true,

    // --- Qidiruv (KLIPY) ---------------------------------------------------------------
    val query: String = "",
    val results: List<StickerSearchItem> = emptyList(),
    /** Birinchi sahifa yuklanyapti — to'r o'rnida indikator. */
    val searching: Boolean = false,
    /** Keyingi sahifa yuklanyapti — to'r oxirida indikator. */
    val loadingMore: Boolean = false,
    val error: GifErrorKind? = null,
    /** Atribut belgisi shu maydondan tanlanadi (`handoff/06-STICKER-SEARCH.md` §3). */
    val provider: GifProvider = GifProvider.KLIPY,
    /** Keyingi sahifa kursori — **shaffof**. `null` — oxiri. */
    val nextCursor: String? = null,
    /**
     * Qidiruv shu deploymentda umuman bormi.
     *
     * `503` kelganda `false` ga tushadi va qidiruv maydoni **butunlay yashiriladi**
     * (`handoff/06-STICKER-SEARCH.md` §5): bu vaqtinchalik nosozlik emas — provayder kaliti
     * sozlanmagan, ya'ni qayta urinish ham, xato matnini ko'rsatish ham keraksiz. Katalog
     * hech narsaga bog'liq emas, panel o'z ishini davom ettiradi.
     */
    val searchAvailable: Boolean = true,
) {
    /** Qidiruv rejimimi — bo'sh so'rovda katalog ko'rsatiladi (trending EMAS). */
    val searchMode: Boolean get() = query.isNotBlank()
    val hasMore: Boolean get() = nextCursor != null
    val emptyResults: Boolean get() = results.isEmpty() && !searching && error == null
}

/**
 * Stiker paneli — ikkita manba bitta ekranda.
 *
 * 1. **Katalog** (`GET /v1/stickers/packs`, zaxira — Fluent Emoji): so'rov bo'sh bo'lganda.
 *    Repository darajasida `ETag` bilan keshlanadi, xato holati yo'q.
 * 2. **Qidiruv** (`GET /v1/stickers/search`, KLIPY): foydalanuvchi yozganda. Bu yerda xato
 *    holati bor va u ko'rsatiladi — bo'sh to'r sababsiz qolmasin.
 *
 * ⚠️ Bo'sh so'rov ataylab **trending so'ramaydi** (GIF panelidan farqi). Sabab — kvota:
 * test kaliti soatiga 100 ta so'rov beradi va uni endi GIF bilan stiker bo'lishadi
 * (`handoff/06-STICKER-SEARCH.md` §3). Panelni har ochilishida so'rov yuborilsa kvota
 * foydalanuvchi hech narsa qidirmasdan tugardi; katalog esa bepul va allaqachon keshda.
 */
class StickerPanelViewModel(
    private val stickerRepository: StickerRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StickerPanelState())
    val state: StateFlow<StickerPanelState> = _state.asStateFlow()

    /** Qidiruv (debounce) va sahifalash bir-birini bekor qilmasligi uchun ikki alohida job. */
    private var searchJob: Job? = null
    private var pageJob: Job? = null

    init {
        viewModelScope.launch {
            val catalog = stickerRepository.catalog()
            _state.update {
                it.copy(
                    packs = catalog.packs,
                    fromServer = catalog.fromServer,
                    loading = false,
                )
            }
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        pageJob?.cancel()
        if (query.isBlank()) {
            // Katalogga qaytdik — eski natijalar qolib ketmasin.
            _state.update {
                it.copy(results = emptyList(), searching = false, loadingMore = false, error = null, nextCursor = null)
            }
            return
        }
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            load(query = query, reset = true)
        }
    }

    /** Xatodan keyin "Qayta urinish". */
    fun retry() {
        val query = _state.value.query
        if (query.isBlank()) return
        searchJob?.cancel()
        load(query = query, reset = true)
    }

    /** To'r oxiriga yetganda — keyingi sahifa. */
    fun loadMore() {
        val current = _state.value
        if (!current.searchMode) return
        if (current.searching || current.loadingMore || !current.hasMore || current.error != null) return
        load(query = current.query, reset = false)
    }

    /**
     * Foydalanuvchi stiker tanladi.
     *
     * `POST /v1/stickers/{id}/share` shu yerdan chaqiriladi — kontraktda shunday. U "eng
     * yaxshi harakat": javobi kutilmaydi va yuborishni to'sib qo'ymaydi.
     */
    fun onPicked(item: StickerSearchItem) {
        val query = _state.value.query
        viewModelScope.launch { stickerRepository.share(item, query) }
    }

    private fun load(query: String, reset: Boolean) {
        pageJob?.cancel()
        val cursor = if (reset) null else _state.value.nextCursor
        _state.update {
            if (reset) {
                it.copy(results = emptyList(), searching = true, error = null, nextCursor = null)
            } else {
                it.copy(loadingMore = true)
            }
        }
        pageJob = viewModelScope.launch {
            when (val res = stickerRepository.search(query = query, cursor = cursor)) {
                is Resource.Success -> _state.update { state ->
                    val page = res.data
                    state.copy(
                        // `distinctBy` — sahifalashda takror element kelishi mumkin (kursor
                        // shaffof), takroriy `key` esa LazyGrid'ni istisno bilan yiqitadi.
                        results = (if (reset) page.items else state.results + page.items).distinctBy { it.id },
                        searching = false,
                        loadingMore = false,
                        error = null,
                        provider = page.provider,
                        nextCursor = page.next,
                    )
                }

                is Resource.Error -> {
                    val kind = res.gifErrorKind ?: GifErrorKind.UNKNOWN
                    // Kalit sozlanmagan — qidiruvni umuman yashiramiz va katalogga qaytamiz.
                    val configured = kind != GifErrorKind.PROVIDER_NOT_CONFIGURED
                    _state.update {
                        it.copy(
                            searching = false,
                            loadingMore = false,
                            // Xato turi noma'lum bo'lsa ham panel bo'sh ekran ko'rsatmasin.
                            error = if (configured) kind else null,
                            searchAvailable = configured,
                            query = if (configured) it.query else "",
                            results = if (configured) it.results else emptyList(),
                        )
                    }
                }

                Resource.Loading -> Unit
            }
        }
    }

    private companion object {
        /**
         * 350 ms — GIF paneli bilan bir xil. Kichikroq qiymat har harfda so'rov yuboradi va
         * global kvotani daqiqalarda yeb qo'yadi.
         */
        const val DEBOUNCE_MS = 350L
    }
}
