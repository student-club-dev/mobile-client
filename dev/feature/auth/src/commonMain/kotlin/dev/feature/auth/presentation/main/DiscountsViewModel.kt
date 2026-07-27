package dev.feature.auth.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.core.common.Resource
import dev.core.domain.model.DiscountCategory
import dev.core.domain.model.DiscountOffer
import dev.core.domain.model.OfferDetail
import dev.core.domain.model.OfferFilterSchema
import dev.core.domain.model.OfferSuggestion
import dev.core.domain.model.SuggestionKind
import dev.core.domain.model.Region
import dev.core.domain.repository.DiscountRepository
import dev.core.domain.repository.RegionRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Taklif so'ralishidan oldingi eng qisqa so'rov uzunligi va yozish to'xtashini kutish vaqti. */
private const val MIN_SUGGEST_CHARS = 2
private const val SUGGEST_DEBOUNCE_MS = 300L

/** Chegirma holati filtri. */
enum class DiscountFilter { ALL, DISCOUNT, REGULAR }

/** Feed saralashi. */
enum class OfferSort { RELEVANCE, DISCOUNT_DESC, PRICE_ASC, PRICE_DESC }

/** Bir to'plam filtr qiymatlari (ham qo'llangan, ham qoralama uchun ishlatiladi). */
data class FilterValues(
    val discountFilter: DiscountFilter = DiscountFilter.ALL,
    val categoryId: String? = null,
    val subcategories: Set<String> = emptySet(),
    val gender: String? = null,
    val sort: OfferSort = OfferSort.RELEVANCE,
) {
    val activeCount: Int
        get() = listOf(
            discountFilter != DiscountFilter.ALL,
            categoryId != null,
            subcategories.isNotEmpty(),
            gender != null,
            sort != OfferSort.RELEVANCE,
        ).count { it }
}

/** Filter ekranidagi viloyat ro'yxati holati. */
data class RegionPickerState(
    val loading: Boolean = false,
    val error: String? = null,
    val regions: List<Region> = emptyList(),
)

/** "Siz uchun" feed holati (qo'llangan filtrlar bilan). */
data class DiscountsUiState(
    val offers: List<DiscountOffer> = emptyList(),
    val query: String = "",
    val savedIds: Set<String> = emptySet(),
    val totalCount: Int = 0,
    val activeFilterCount: Int = 0,
)

/**
 * Filter ekrani holati (qoralama tanlovlar + dinamik variantlar + jonli natija soni).
 *
 * Variantlar ikki manbadan keladi: server sxemasi (`POST /v1/catalog/filter-schema` — sonlar
 * bilan, faqat haqiqatda uchraydigan qiymatlar) va u yetib kelmaganda — keshdagi e'lonlardan
 * hisoblangan zaxira ro'yxat.
 */
data class FilterDraftState(
    val categories: List<DiscountCategory> = emptyList(),
    val draft: FilterValues = FilterValues(),
    val availableSubcategories: List<String> = emptyList(),
    val genderApplicable: Boolean = false,
    val previewCount: Int = 0,
    /** Serverdagi e'lonlar soni: biznes turi kaliti → soni. */
    val typeCounts: Map<String, Int> = emptyMap(),
    /** Bo'lim (kategoriya) nomi → soni. */
    val subcategoryCounts: Map<String, Int> = emptyMap(),
    /** `ALL` / `DISCOUNT` / `REGULAR` → soni. */
    val kindCounts: Map<String, Int> = emptyMap(),
    val priceRange: LongRange? = null,
    /** Sxemadagi umumiy e'lonlar soni (`null` — sxema hali yuklanmagan). */
    val schemaTotal: Int? = null,
)

/** Tafsilot oynasi (`POST /v1/discounts/detail`). `null` — oyna yopiq. */
data class OfferDetailState(
    val loading: Boolean = false,
    val detail: OfferDetail? = null,
    val error: String? = null,
)

class DiscountsViewModel(
    private val discountRepository: DiscountRepository,
    private val regionRepository: RegionRepository,
) : ViewModel() {

    // --- Joylashuv (viloyat) filtri -------------------------------------------------
    // Bu filtr boshqalaridan FARQ QILADI: u so'rovga (`filter.geo.regionIds`) ketadi, ya'ni
    // tanlangan zahoti feed qayta tortiladi. Shu bois qoralamada emas, darrov qo'llanadi.

    private val _regionPicker = MutableStateFlow(RegionPickerState())
    val regionPicker: StateFlow<RegionPickerState> = _regionPicker

    /** Tanlangan viloyat (`null` — butun O'zbekiston). */
    val selectedRegion: StateFlow<Region?> = regionRepository.observeSelected()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Ro'yxat birinchi ochilishda tortiladi. */
    fun loadRegions() {
        if (_regionPicker.value.regions.isNotEmpty() || _regionPicker.value.loading) return
        _regionPicker.value = _regionPicker.value.copy(loading = true, error = null)
        viewModelScope.launch {
            when (val res = regionRepository.regions()) {
                is Resource.Success -> _regionPicker.value = RegionPickerState(regions = res.data)
                is Resource.Error -> _regionPicker.value = RegionPickerState(error = res.message)
                Resource.Loading -> Unit
            }
        }
    }

    /** Viloyatni saqlaydi va feed'ni yangi geo filtri bilan qayta tortadi. */
    fun selectRegion(region: Region?) {
        viewModelScope.launch {
            regionRepository.select(region)
            discountRepository.refresh()
        }
    }

    private val query = MutableStateFlow("")

    // Qo'llangan filtrlar — feed'ni boshqaradi.
    private val applied = MutableStateFlow(FilterValues())
    // Qoralama filtrlar — Filter ekranida tahrirlanadi, "Qo'llash" bosilganda [applied] ga ko'chadi.
    private val draft = MutableStateFlow(FilterValues())

    private val offersFlow = discountRepository.observeAllOffers()
    private val savedIdsFlow = discountRepository.observeSaved().map { list -> list.map { it.id }.toSet() }

    // Server sxemasi — filtr ekrani ochilganda (va tur o'zgarganda) yangilanadi.
    private val schema = MutableStateFlow<OfferFilterSchema?>(null)

    private val _suggestions = MutableStateFlow<List<OfferSuggestion>>(emptyList())
    /** Qidiruv qatori ostidagi takliflar (bo'sh — hech narsa ko'rsatilmaydi). */
    val suggestions: StateFlow<List<OfferSuggestion>> = _suggestions

    private val _detail = MutableStateFlow<OfferDetailState?>(null)
    /** Ochiq tafsilot oynasi; `null` — yopiq. */
    val detail: StateFlow<OfferDetailState?> = _detail

    val state: StateFlow<DiscountsUiState> = combine(
        offersFlow, query, applied, savedIdsFlow,
    ) { offers, q, f, savedIds ->
        val filtered = filter(offers, f, q)
        DiscountsUiState(
            offers = filtered,
            query = q,
            savedIds = savedIds,
            totalCount = filtered.size,
            activeFilterCount = f.activeCount,
        )
    }
        .catch { emit(DiscountsUiState()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DiscountsUiState())

    val filterState: StateFlow<FilterDraftState> = combine(
        offersFlow, discountRepository.observeCategories(), draft, query, schema,
    ) { offers, categories, d, q, sc ->
        val inCategory = if (d.categoryId == null) offers else offers.filter { it.categoryId == d.categoryId }
        // Bo'limlar: sxema kelgan bo'lsa serverdan (sonlari bilan), aks holda keshdan.
        val schemaSubs = sc?.categories.orEmpty().filter { d.categoryId == null || it.typeKey == d.categoryId }
        val availableSubs = if (schemaSubs.isNotEmpty()) {
            schemaSubs.sortedByDescending { it.count }.map { it.label }
        } else {
            inCategory.map { it.subcategory }.filter { it.isNotBlank() }.distinct().sorted()
        }
        FilterDraftState(
            categories = categories,
            draft = d,
            availableSubcategories = availableSubs,
            genderApplicable = inCategory.any { it.gender.isNotBlank() },
            previewCount = filter(offers, d, q).size,
            typeCounts = sc?.types.orEmpty().associate { it.key to it.count },
            subcategoryCounts = schemaSubs.associate { it.label to it.count },
            kindCounts = sc?.listingKinds.orEmpty(),
            priceRange = sc?.priceRange,
            schemaTotal = sc?.total,
        )
    }
        .catch { emit(FilterDraftState()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FilterDraftState())

    init {
        viewModelScope.launch { discountRepository.refresh() }
        observeQueryForSuggestions()
    }

    // -----------------------------------------------------------------------
    // Qidiruv takliflari (`POST /v1/discounts/suggest`)
    // -----------------------------------------------------------------------
    /**
     * Har harf uchun so'rov yubormaslik uchun qo'lda debounce: [collectLatest] oldingi
     * kutishni bekor qiladi, shuning uchun faqat yozish to'xtaganda so'rov ketadi.
     */
    private fun observeQueryForSuggestions() = viewModelScope.launch {
        query.collectLatest { q ->
            if (q.trim().length < MIN_SUGGEST_CHARS) {
                _suggestions.value = emptyList()
                return@collectLatest
            }
            delay(SUGGEST_DEBOUNCE_MS)
            val res = discountRepository.suggest(q.trim())
            _suggestions.value = (res as? Resource.Success)?.data.orEmpty()
        }
    }

    /** Taklif tanlanganda: turi/bo'limi bo'yicha filtr qo'llanadi yoki e'lon ochiladi. */
    fun onSuggestionPicked(s: OfferSuggestion) {
        _suggestions.value = emptyList()
        when (s.kind) {
            SuggestionKind.TYPE -> {
                query.value = ""
                applied.value = applied.value.copy(categoryId = s.typeKey, subcategories = emptySet())
            }
            SuggestionKind.CATEGORY -> {
                query.value = ""
                applied.value = applied.value.copy(categoryId = s.typeKey, subcategories = setOf(s.label))
            }
            // Biznes nomi local qidiruvda ham topiladi — matnni qatorga qo'yamiz.
            SuggestionKind.BUSINESS -> query.value = s.label
            SuggestionKind.LISTING -> s.listingId?.let(::openOffer)
        }
    }

    // -----------------------------------------------------------------------
    // Tafsilot (`POST /v1/discounts/detail`)
    // -----------------------------------------------------------------------
    fun openOffer(offerId: String) {
        _detail.value = OfferDetailState(loading = true)
        viewModelScope.launch {
            _detail.value = when (val res = discountRepository.getDetail(offerId)) {
                is Resource.Success -> OfferDetailState(detail = res.data)
                is Resource.Error -> OfferDetailState(error = res.message)
                Resource.Loading -> OfferDetailState(loading = true)
            }
        }
    }

    fun closeOffer() { _detail.value = null }

    private fun filter(offers: List<DiscountOffer>, f: FilterValues, q: String): List<DiscountOffer> = offers
        .let { list -> if (f.categoryId == null) list else list.filter { it.categoryId == f.categoryId } }
        .let { list -> if (f.gender == null) list else list.filter { it.gender == f.gender } }
        .let { list -> if (f.subcategories.isEmpty()) list else list.filter { it.subcategory in f.subcategories } }
        .let { list ->
            when (f.discountFilter) {
                DiscountFilter.ALL -> list
                DiscountFilter.DISCOUNT -> list.filter { it.isDiscount }
                DiscountFilter.REGULAR -> list.filter { !it.isDiscount }
            }
        }
        .let { list ->
            if (q.isBlank()) list
            else list.filter {
                it.merchant.contains(q, ignoreCase = true) ||
                    it.title.contains(q, ignoreCase = true) ||
                    it.subcategory.contains(q, ignoreCase = true)
            }
        }
        .let { list ->
            when (f.sort) {
                OfferSort.RELEVANCE -> list.sortedByDescending { it.featured }
                OfferSort.DISCOUNT_DESC -> list.sortedByDescending { it.discountPercent }
                OfferSort.PRICE_ASC -> list.sortedBy { it.finalPrice }
                OfferSort.PRICE_DESC -> list.sortedByDescending { it.finalPrice }
            }
        }

    // Qidiruv — feed'ga darrov ta'sir qiladi (filter ekranidan tashqarida).
    fun onQuery(q: String) { query.value = q }

    // --- Filter ekrani (qoralama) ---
    /** Filter ekrani ochilganda qoralamani qo'llangan holat bilan tenglaymiz va sxemani tortamiz. */
    fun openFilter() {
        draft.value = applied.value
        loadSchema(applied.value.categoryId)
    }

    /**
     * Filtr variantlarini serverdan oladi. Tanlangan biznes turi berilsa — bo'limlar va sonlar
     * shu tur doirasida qaytadi. Xato bo'lsa sxema `null` qoladi va ekran keshdan ishlaydi.
     */
    private fun loadSchema(typeKey: String?) = viewModelScope.launch {
        val res = discountRepository.getFilterSchema(listOfNotNull(typeKey))
        schema.value = (res as? Resource.Success)?.data?.takeIf { it.total > 0 || it.types.isNotEmpty() }
    }

    fun onDraftDiscountFilter(f: DiscountFilter) { draft.value = draft.value.copy(discountFilter = f) }

    /** Biznes turi o'zgarsa — unga bog'liq sub-kategoriya va jins tanlovlari tozalanadi. */
    fun onDraftCategory(id: String?) {
        draft.value = draft.value.copy(categoryId = id, subcategories = emptySet(), gender = null)
        loadSchema(id) // bo'limlar va sonlar yangi turga moslanadi
    }

    fun toggleDraftSubcategory(sub: String) {
        draft.value = draft.value.let {
            it.copy(subcategories = if (sub in it.subcategories) it.subcategories - sub else it.subcategories + sub)
        }
    }

    fun onDraftGender(gender: String?) { draft.value = draft.value.copy(gender = gender) }
    fun onDraftSort(s: OfferSort) { draft.value = draft.value.copy(sort = s) }

    /** "Tozalash" — qoralamani asliga qaytaradi (hali qo'llanmaydi). */
    fun resetDraft() { draft.value = FilterValues() }

    /** "Qo'llash" — qoralama filtrlarni feed'ga qo'llaydi. */
    fun applyFilters() { applied.value = draft.value }

    fun toggleSaved(offer: DiscountOffer, currentlySaved: Boolean) =
        toggleSaved(offer.id, currentlySaved)

    fun toggleSaved(offerId: String, currentlySaved: Boolean) {
        viewModelScope.launch { discountRepository.setSaved(offerId, !currentlySaved) }
    }
}
