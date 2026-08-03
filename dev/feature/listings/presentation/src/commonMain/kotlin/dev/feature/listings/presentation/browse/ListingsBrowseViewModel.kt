package dev.feature.listings.presentation.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.core.common.Resource
import dev.feature.listings.domain.model.Listing
import dev.feature.listings.domain.model.ListingFilters
import dev.feature.listings.domain.model.ListingGeoFilter
import dev.feature.listings.domain.model.ListingKind
import dev.feature.listings.domain.model.ListingQuery
import dev.feature.listings.domain.model.ListingSort
import dev.feature.listings.domain.usecase.SearchListingsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Talabaga ko'rinadigan ro'yxat: Fanlardan yordam / Ijara / Xizmatlar / Ish e'lonlari.
 *
 * Ro'yxat **serverdan** keladi (`GET /v1/student-listings`) va cheksiz skroll bilan
 * sahifalanadi. Nega klientda filtrlanmaydi: telefonda e'lonlarning faqat ko'rilgan qismi
 * bor, ko'rinish qoidalari (blok, muddat, o'zganing qoralamasi) esa umuman yo'q — local
 * filtr "topilmadi" deganda serverda o'nlab mos e'lon turgan bo'lishi mumkin. Tarmoq
 * yo'qolganda repository keshdan javob beradi, ya'ni ekran baribir bo'sh qolmaydi.
 *
 * To'rtala bo'lim bitta ViewModel'da, chunki ular bir xil ishlaydi: tur tanlanadi →
 * qidiriladi → filtrlanadi. Farqi faqat qaysi filtrlar ko'rinishida ([ListingFilters]).
 */
class ListingsBrowseViewModel(
    private val search: SearchListingsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ListingsBrowseUiState())
    val state: StateFlow<ListingsBrowseUiState> = _state.asStateFlow()

    /** Filtr oynasida tahrirlanayotgan nusxa — "Qo'llash" bosilmaguncha ro'yxatga ta'sir qilmaydi. */
    private val _filterState = MutableStateFlow(ListingFilterUiState())
    val filterState: StateFlow<ListingFilterUiState> = _filterState.asStateFlow()

    /** Foydalanuvchining joylashuvi — [ListingSort.NEAREST] faqat shu bo'lganda ma'noli. */
    private var userLocation: ListingGeoFilter? = null

    private var searchJob: Job? = null
    private var moreJob: Job? = null
    private var previewJob: Job? = null

    /** Birinchi `selectKind` gacha hech narsa so'ralmaydi — ekran qaysi bo'limni ochishini biladi. */
    private var started = false

    // -----------------------------------------------------------------------
    // Kirish nuqtalari
    // -----------------------------------------------------------------------

    fun selectKind(next: ListingKind) {
        if (started && _state.value.kind == next) return
        started = true
        _state.update { current ->
            current.copy(
                kind = next,
                // Boshqa turning filtrlari ko'rinmas holda ishlab ketmasligi kerak.
                filters = current.filters.resetForKind(),
                // Saralash ham turga bog'liq: "muddati yaqin" faqat topshiriqda bor.
                sort = current.sort.validFor(next),
            )
        }
        syncDraft()
        reload()
    }

    fun onQuery(value: String) {
        _state.update { it.copy(query = value) }
        // Har bosilgan harfda so'rov yubormaymiz — foydalanuvchi yozishni to'xtatgach.
        reload(debounceMs = SEARCH_DEBOUNCE_MS)
    }

    /**
     * Ekran joylashuvni aniqlaganda chaqiriladi. Radius **qo'shilmaydi**: koordinata faqat
     * masofa bo'yicha saralash uchun kerak, uni filtrga aylantirish uzoqdagi e'lonlarni
     * jimgina yo'q qilib qo'yardi.
     */
    fun setUserLocation(lat: Double?, lng: Double?) {
        val next = if (lat != null && lng != null) ListingGeoFilter(lat = lat, lng = lng) else null
        if (next == userLocation) return
        userLocation = next
        _state.update { it.copy(canSortByDistance = next != null) }
        // Joylashuv "eng yaqin" saralashiga ta'sir qiladi; qolgan tartiblarda so'rov o'zgarmaydi.
        if (_state.value.sort == ListingSort.NEAREST) reload()
    }

    fun refresh() = reload()

    /** Cheksiz skroll — ro'yxatning oxiriga yetganda. */
    fun loadMore() {
        val current = _state.value
        val cursor = current.nextCursor
        if (!current.hasNext || cursor == null || current.loadingMore || current.loading) return

        moreJob?.cancel()
        moreJob = viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            when (val res = search(current.toQuery(cursor = cursor))) {
                is Resource.Success -> _state.update { s ->
                    s.copy(
                        // Kursor eskirgan bo'lsa server takroriy e'lon berishi mumkin —
                        // id bo'yicha ajratamiz, aks holda LazyColumn bir xil kalitda yiqiladi.
                        listings = (s.listings + res.data.items).distinctBy { it.id },
                        hasNext = res.data.hasNext,
                        nextCursor = res.data.nextCursor,
                        loadingMore = false,
                    )
                }
                // Keyingi sahifa kelmasa bor ro'yxat joyida qoladi — faqat xabar chiqadi.
                is Resource.Error -> _state.update { it.copy(loadingMore = false, error = res.message) }
                Resource.Loading -> Unit
            }
        }
    }

    fun consumeError() = _state.update { it.copy(error = null) }

    // -----------------------------------------------------------------------
    // Filtr oynasi
    // -----------------------------------------------------------------------

    /** Filtr oynasi ochildi — tahrirlash mavjud holatdan boshlanadi. */
    fun openFilter() {
        syncDraft()
        previewCount()
    }

    fun updateDraft(transform: (ListingFilters) -> ListingFilters) {
        _filterState.update { it.copy(draft = transform(it.draft)) }
        previewCount()
    }

    /**
     * Saralash ham qoralamada — filtr bilan birga, bitta "Qo'llash" bosishida ketadi.
     * Alohida darhol qo'llansa ro'yxat filtr oynasi ochiqligida ostidan sakrab yangilanardi.
     */
    fun updateSort(sort: ListingSort) = _filterState.update { it.copy(sort = sort) }

    /** Faqat joriy turning filtrlarini tozalaydi (narx chegarasi va tartib ham tushadi). */
    fun resetDraft() {
        _filterState.update { it.copy(draft = ListingFilters(), sort = ListingSort.NEWEST) }
        previewCount()
    }

    fun applyFilters() {
        val filter = _filterState.value
        _state.update { it.copy(filters = filter.draft, sort = filter.sort) }
        reload()
    }

    private fun syncDraft() {
        val current = _state.value
        _filterState.value = ListingFilterUiState(
            kind = current.kind,
            draft = current.filters,
            sort = current.sort,
            sortOptions = ListingSort.optionsFor(current.kind)
                // "Eng yaqin" joylashuv noma'lum bo'lganda serverda jimgina "yangi
                // e'lonlar" ga tushadi — tanlov sifatida ko'rsatish yolg'on bo'lardi.
                .filter { it != ListingSort.NEAREST || userLocation != null },
            previewCount = current.totalCount,
        )
        // Sonni bu yerda SO'RAMAYMIZ: `syncDraft` tur almashganda ham chaqiriladi va
        // o'shanda filtr oynasi yopiq — hech kim ko'rmaydigan raqam uchun so'rov ketardi.
    }

    /**
     * "Qo'llash · 137" — tanlangan filtr bilan nechta e'lon topilishi.
     *
     * Sahifa raqamli rejimda so'raladi (`page=1&size=1`), chunki `total` **faqat** o'sha
     * rejimda hisoblanadi. Bir dona element so'raymiz: bizga ro'yxat emas, son kerak.
     */
    private fun previewCount() {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            delay(PREVIEW_DEBOUNCE_MS)
            val query = _state.value.toQuery(
                filters = _filterState.value.draft,
                size = 1,
                page = 1,
            )
            val res = search(query)
            if (res is Resource.Success) {
                _filterState.update { it.copy(previewCount = res.data.total ?: res.data.items.size) }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Qidiruv
    // -----------------------------------------------------------------------

    private fun reload(debounceMs: Long = 0) {
        searchJob?.cancel()
        moreJob?.cancel()
        searchJob = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            _state.update { it.copy(loading = true, error = null) }

            when (val res = search(_state.value.toQuery())) {
                is Resource.Success -> _state.update { s ->
                    s.copy(
                        listings = res.data.items,
                        hasNext = res.data.hasNext,
                        nextCursor = res.data.nextCursor,
                        // Kursorli rejimda server `total` bermaydi — o'shanda yuklangani.
                        totalCount = res.data.total ?: res.data.items.size,
                        loading = false,
                        loaded = true,
                    )
                }
                is Resource.Error -> _state.update {
                    it.copy(listings = emptyList(), hasNext = false, nextCursor = null,
                        loading = false, loaded = true, error = res.message)
                }
                Resource.Loading -> Unit
            }
        }
    }

    private fun ListingsBrowseUiState.toQuery(
        filters: ListingFilters = this.filters,
        cursor: String? = null,
        size: Int = ListingQuery.DEFAULT_PAGE_SIZE,
        page: Int? = null,
    ) = ListingQuery(
        kind = kind,
        text = query,
        filters = filters,
        // Koordinata faqat masofa bo'yicha saralashda yuboriladi.
        geo = userLocation.takeIf { sort == ListingSort.NEAREST },
        sort = sort,
        size = size,
        cursor = cursor,
        page = page,
    )

    /** Bo'limda mavjud bo'lmagan tartib (masalan ishda "muddati yaqin") — odatiysiga tushadi. */
    private fun ListingSort.validFor(kind: ListingKind): ListingSort =
        if (this in ListingSort.optionsFor(kind)) this else ListingSort.NEWEST

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 350L
        const val PREVIEW_DEBOUNCE_MS = 350L
    }
}

data class ListingsBrowseUiState(
    val kind: ListingKind = ListingKind.JOB,
    val listings: List<Listing> = emptyList(),
    /** Serverdagi jami son (kursorli rejimda — yuklanganlar soni). */
    val totalCount: Int = 0,
    val query: String = "",
    val filters: ListingFilters = ListingFilters(),
    val sort: ListingSort = ListingSort.NEWEST,
    /** Joylashuv ma'lummi — "eng yaqin" tartibi faqat shunda tanlanadi. */
    val canSortByDistance: Boolean = false,

    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    /** Keyingi sahifa bormi (cheksiz skroll). */
    val hasNext: Boolean = false,
    val nextCursor: String? = null,
    /** Birinchi javob kelganmi — usiz "bo'sh" va "hali yuklanmagan" farq qilmaydi. */
    val loaded: Boolean = false,
    val error: String? = null,
) {
    val activeFilterCount: Int get() = filters.activeCount(kind)

    /** Qidiruv yoki filtr qo'yilganmi — bo'sh ro'yxatning sababi shunda. */
    val isNarrowed: Boolean get() = query.isNotBlank() || activeFilterCount > 0

    /** Ro'yxat bo'sh, lekin sababi filtr yoki qidiruvda. */
    val isFilteredEmpty: Boolean get() = loaded && listings.isEmpty() && isNarrowed

    /** Bo'limning o'zi bo'sh — hali hech kim e'lon joylamagan. */
    val isSectionEmpty: Boolean get() = loaded && listings.isEmpty() && !isNarrowed
}

data class ListingFilterUiState(
    val kind: ListingKind = ListingKind.JOB,
    val draft: ListingFilters = ListingFilters(),
    val sort: ListingSort = ListingSort.NEWEST,
    /** Shu bo'limda ma'noli tartiblar (joylashuv noma'lum bo'lsa "eng yaqin" yo'q). */
    val sortOptions: List<ListingSort> = emptyList(),
    val previewCount: Int = 0,
)
