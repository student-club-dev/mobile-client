package dev.feature.chat.presentation.gif

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.core.common.Resource
import dev.feature.chat.domain.model.GifErrorKind
import dev.feature.chat.domain.model.GifItem
import dev.feature.chat.domain.model.GifProvider
import dev.feature.chat.domain.model.gifErrorKind
import dev.feature.chat.domain.repository.GifRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** GIF panelining holati. */
@Immutable
data class GifPanelState(
    val query: String = "",
    val items: List<GifItem> = emptyList(),
    /** Birinchi sahifa yuklanyapti — to'r o'rnida indikator. */
    val loading: Boolean = false,
    /** Keyingi sahifa yuklanyapti — to'r oxirida indikator. */
    val loadingMore: Boolean = false,
    val error: GifErrorKind? = null,
    /**
     * Atribut belgisi shu maydondan tanlanadi va **doim ko'rsatiladi** — hatto natija
     * kelmagan yoki xato bo'lgan holatda ham (shartnomaviy talab, `gif.md`).
     */
    val provider: GifProvider = GifProvider.KLIPY,
    /**
     * Keyingi sahifa kursori — **shaffof**: ichiga qaralmaydi, o'zgartirilmaydi, faqat
     * serverga qaytariladi. `null` — oxiri.
     */
    val nextCursor: String? = null,
) {
    val hasMore: Boolean get() = nextCursor != null
    val empty: Boolean get() = items.isEmpty() && !loading && error == null
}

/**
 * GIF qidiruv panelining ViewModel'i.
 *
 * Ikkita narsani ataylab qattiq ushlaydi (`gif.md`, "test kaliti — soatiga 100 ta so'rov,
 * GLOBAL"):
 *
 * 1. **Debounce** — har bosilgan harf so'rov yubormaydi ([DEBOUNCE_MS]).
 * 2. **Kesh** — repository darajasida; bir xil so'z/sahifa qayta so'ralmaydi.
 *
 * Qidiruv bo'sh bo'lsa server trending qaytaradi — bu alohida endpoint emas, shuning uchun
 * panel ochilishi ham oddiy qidiruv chaqiruvi.
 */
class GifPanelViewModel(
    private val gifRepository: GifRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(GifPanelState(loading = true))
    val state: StateFlow<GifPanelState> = _state.asStateFlow()

    /** Qidiruv (debounce) va sahifalash bir-birini bekor qilmasligi uchun ikki alohida job. */
    private var searchJob: Job? = null
    private var pageJob: Job? = null

    init {
        load(query = "", reset = true)
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            load(query = query, reset = true)
        }
    }

    /** Xatodan keyin "Qayta urinish". */
    fun retry() {
        searchJob?.cancel()
        load(query = _state.value.query, reset = true)
    }

    /** To'r oxiriga yetganda — keyingi sahifa. */
    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || !current.hasMore || current.error != null) return
        load(query = current.query, reset = false)
    }

    /**
     * Foydalanuvchi GIF tanladi.
     *
     * `POST /v1/gifs/{id}/share` shu yerdan chaqiriladi — kontraktda shunday. U "eng yaxshi
     * harakat": javobi kutilmaydi va yuborishni to'sib qo'ymaydi.
     */
    fun onPicked(gif: GifItem) {
        val query = _state.value.query
        viewModelScope.launch { gifRepository.share(gif, query) }
    }

    private fun load(query: String, reset: Boolean) {
        pageJob?.cancel()
        val cursor = if (reset) null else _state.value.nextCursor
        _state.update {
            if (reset) {
                it.copy(query = query, items = emptyList(), loading = true, error = null, nextCursor = null)
            } else {
                it.copy(loadingMore = true)
            }
        }
        pageJob = viewModelScope.launch {
            when (val res = gifRepository.search(query = query, cursor = cursor)) {
                is Resource.Success -> _state.update { state ->
                    val page = res.data
                    state.copy(
                        // `distinctBy` — sahifalashda takror element kelishi mumkin (kursor
                        // shaffof, provayder kafolat bermaydi), takroriy `key` esa LazyGrid'ni
                        // istisno bilan yiqitadi.
                        items = (if (reset) page.items else state.items + page.items).distinctBy { it.id },
                        loading = false,
                        loadingMore = false,
                        error = null,
                        provider = page.provider,
                        nextCursor = page.next,
                    )
                }

                is Resource.Error -> _state.update {
                    it.copy(
                        loading = false,
                        loadingMore = false,
                        // Xato turi noma'lum bo'lsa ham panel bo'sh ekran ko'rsatmasin.
                        error = res.gifErrorKind ?: GifErrorKind.UNKNOWN,
                    )
                }

                Resource.Loading -> Unit
            }
        }
    }

    private companion object {
        /**
         * 350 ms — odam yozayotganda tabiiy pauza. Kichikroq qiymat har harfda so'rov
         * yuboradi va global kvotani daqiqalarda yeb qo'yadi.
         */
        const val DEBOUNCE_MS = 350L
    }
}
