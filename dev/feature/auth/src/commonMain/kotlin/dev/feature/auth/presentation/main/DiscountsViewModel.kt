package dev.feature.auth.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.core.common.Resource
import dev.core.domain.model.CatalogRules
import dev.core.domain.model.DiscountCategory
import dev.core.domain.model.DiscountGroup
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
import kotlinx.coroutines.flow.first
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
    /**
     * Katalog guruhi ([DiscountGroup.key]) — bosh ekrandagi bo'lim ("Ovqatlar", "Sport").
     * Home'dagi "Barchasi" tugmasi aynan shu filtr bilan ekranni ochadi.
     */
    val groupKey: String? = null,
    /**
     * Bo'lim ichidagi turlar — guruh ilovada ikkiga bo'lingan holat ("Savdo" / "Xizmatlar",
     * qarang [CatalogRules]). Bo'sh — butun guruh. So'rovlar baribir [groupKey] bilan ketadi,
     * bu esa keshdagi e'lonlarni bo'lim doirasiga qisqartiradi.
     */
    val typeKeys: Set<String> = emptySet(),
    /**
     * Tanlangan biznes turlari — **bir nechtasi bo'lishi mumkin** ("Fast food" VA "Somsa").
     *
     * Ilgari bitta qiymat (`categoryId: String?`) edi: foydalanuvchi ikkita turni birga
     * ko'rmoqchi bo'lsa iloji yo'q edi va har tanlov avvalgisini o'chirardi (bug hisoboti
     * #29). Bo'sh — tur filtri qo'llanmaydi.
     */
    val categoryIds: Set<String> = emptySet(),
    val subcategories: Set<String> = emptySet(),
    val gender: String? = null,
    val sort: OfferSort = OfferSort.RELEVANCE,
) {
    /** Yagona tanlangan tur (sarlavha va sxema uchun); bir nechtasi bo'lsa `null`. */
    val singleCategoryId: String? get() = categoryIds.singleOrNull()

    // [typeKeys] sanalmaydi: u alohida filtr emas, ochiq bo'limning o'zi.
    val activeCount: Int
        get() = listOf(
            discountFilter != DiscountFilter.ALL,
            groupKey != null,
            categoryIds.isNotEmpty(),
            subcategories.isNotEmpty(),
            gender != null,
            sort != OfferSort.RELEVANCE,
        ).count { it }
}

/**
 * Katalogdagi bitta BO'LIM — ekranda ko'rinadigan yakuniy bo'linish.
 *
 * "Somsa", "Fast food", "Milliy taomlar" alohida katak bo'lib turgani foydalanuvchini
 * ko'mib tashlardi (27 ta tur); endi ular bitta "Ovqatlanish" katagiga yig'iladi va
 * ichkarida — feed tepasidagi chiplarda — toraytiriladi.
 *
 * Odatda bo'lim = serverning guruhi, lekin bitta guruh ikkiga bo'linishi mumkin
 * ("Savdo va xizmat" → "Savdo" + "Xizmatlar", qarang [CatalogRules]). Shu holda [partial]
 * `true` bo'ladi va feed guruh ichida yana shu bo'limning turlari bo'yicha qisqaradi.
 */
data class CatalogSection(
    /** Ro'yxat kaliti: "FOOD" yoki bo'lingan holda "SHOPPING:SERVICES". */
    val key: String,
    val name: String,
    val emoji: String,
    val accent: Long,
    /** Server guruhi — barcha so'rovlar SHU kalit bilan ketadi. */
    val groupKey: String,
    /** Bo'limdagi turlar — e'lonlari ko'pi birinchi. */
    val types: List<DiscountCategory> = emptyList(),
    /** Bo'lim guruhning faqat bir qismimi (guruh ilovada bo'lingan). */
    val partial: Boolean = false,
) {
    /** Bo'limdagi e'lonlar soni — turlarning sonlari yig'indisi (serverdan). */
    val offerCount: Int get() = types.sumOf { it.offerCount }

    /** Feed filtriga tushadigan tur kalitlari (faqat [partial] bo'limlar uchun kerak). */
    val typeKeys: Set<String> get() = types.map { it.id }.toSet()

    /** Kartadagi ikkinchi qator: "Milliy taomlar · Fast food · Somsa". */
    val typesPreview: String get() = types.joinToString(" · ") { it.name }
}

/**
 * Ekranga kirilganda ko'rinadigan KATALOG — backend guruhlari (`POST /v1/catalog/groups`)
 * bo'yicha BIRLASHTIRILGAN biznes turlari (`POST /v1/catalog/types`, hozircha 27 ta).
 * Bo'lim bosilgach uning barcha turlari bitta feed'da ochiladi.
 *
 * Ro'yxat ilovada qat'iy yozilmagan: yangi tur/guruh qo'shilsa yoki nomi o'zgarsa
 * ekran o'zgarishsiz ishlayveradi ([CatalogRules] dagi tuzatishlardan tashqari).
 */
data class CatalogUiState(
    /** Bo'limlar — server tartibida (Ovqatlanish, Sport, Ta'lim...). */
    val sections: List<CatalogSection> = emptyList(),
    /**
     * Guruhi noma'lum turlar (eski kesh yoki server hali guruhga bog'lamagan) — bo'limlardan
     * keyin alohida katak bo'lib chiziladi, ya'ni yo'qolib ketmaydi.
     */
    val looseTypes: List<DiscountCategory> = emptyList(),
    /** Keshdagi e'lonlar soni — "Barchasi" kartasida ko'rsatiladi. */
    val totalOffers: Int = 0,
    /** Katalog hali kelmagan — bo'limlar o'rniga skelet chiziladi. */
    val loading: Boolean = false,
)

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
    /**
     * Birinchi yuklanish davom etyapti va ko'rsatadigan e'lon hali yo'q — feed o'rniga
     * skelet (shimmer) chiziladi. Keshda e'lon bo'lsa `false`: eski ro'yxat ustiga skelet
     * qo'yilmaydi, yangilanish jimgina bo'ladi.
     */
    val loading: Boolean = false,
    /**
     * Ochiq katalog bo'limi — sarlavha shu bo'lim nomi bilan chiziladi ("🍽 Ovqatlanish").
     * `null` — bo'lim filtri yo'q, sarlavha "Barcha takliflar".
     */
    val section: CatalogSection? = null,
    /**
     * Ochiq biznes turi — feed tepasidagi chipdan tanlangani ("🥟 Somsa"). Sarlavhada
     * [group] dan USTUN turadi: tur aniqroq.
     */
    val type: DiscountCategory? = null,
    /**
     * Ochiq bo'limdagi turlar — feed tepasidagi chiplar shu ro'yxatdan chiziladi
     * ("Hammasi · Milliy taomlar · Fast food · Somsa"). Bo'lim ochilmagan bo'lsa — bo'sh.
     */
    val sectionTypes: List<DiscountCategory> = emptyList(),
    /** Qo'llangan bo'lim (kategoriya) tanlovi — xaritadagi chiplar shuni yoqib turadi. */
    val subcategories: Set<String> = emptySet(),
)

/**
 * Filter ekrani holati (qoralama tanlovlar + dinamik variantlar + jonli natija soni).
 *
 * Variantlar ikki manbadan keladi: server sxemasi (`POST /v1/catalog/filter-schema` — sonlar
 * bilan, faqat haqiqatda uchraydigan qiymatlar) va u yetib kelmaganda — keshdagi e'lonlardan
 * hisoblangan zaxira ro'yxat.
 */
data class FilterDraftState(
    /** Katalog bo'limlari — katalog ekranidagi kataklar bilan bir xil ro'yxat. */
    val sections: List<CatalogSection> = emptyList(),
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

    private val groupsFlow = discountRepository.observeGroups()

    /** Birinchi `refresh()` tugaguncha `true` — feed skeletini shu boshqaradi. */
    private val refreshing = MutableStateFlow(true)

    /**
     * "Tepadan tortib yangilash" ketyapti.
     *
     * [refreshing] dan ALOHIDA oqim: u birinchi yuklanishning skeletini boshqaradi va
     * `catalogState` ichida boshqa manbalar bilan qo'shilgan. Bu esa faqat indikator
     * uchun va ekranga to'g'ridan-to'g'ri beriladi.
     */
    private val _pullRefreshing = MutableStateFlow(false)
    val pullRefreshing: StateFlow<Boolean> = _pullRefreshing

    /**
     * Ekran pastga tortildi — katalog va e'lonlar feed'i serverdan qayta o'qiladi.
     *
     * `discountRepository.refresh()` katalogni ham, feed'ni ham yangilaydi. Ochiq bo'lim
     * bo'lsa uning TO'LIQ ro'yxati ham qayta tortiladi (`refreshGroup`): umumiy feed har
     * guruhdan atigi bir nechtasini oladi va usiz bo'limda e'lonlar kamayib qolardi.
     */
    fun refresh() {
        if (_pullRefreshing.value) return
        viewModelScope.launch {
            _pullRefreshing.value = true
            try {
                runCatching { discountRepository.refresh() }
                applied.value.groupKey?.let { key ->
                    runCatching { discountRepository.refreshGroup(key) }
                }
            } finally {
                _pullRefreshing.value = false
                refreshing.value = false
            }
        }
    }

    private val categoriesFlow = discountRepository.observeCategories()

    // Guruhlar + biznes turlari + yuklanish bayrog'i bitta oqimga yig'iladi: `state` va
    // `filterState` allaqachon beshta manbadan quriladi, oltinchisi uchun typed `combine`
    // overload'i qolmaydi.
    private val catalog = combine(
        groupsFlow, categoriesFlow, refreshing,
    ) { groups, categories, loading -> Triple(groups, categories, loading) }

    /**
     * Ekranga kirilganda katalog (bo'limlar) ko'rinadi; bo'lim tanlangach e'lonlar ro'yxatiga
     * o'tiladi. Home'dagi "Barchasi" tugmasi katalogni chetlab o'tadi ([openGroup]).
     */
    private val _catalogOpen = MutableStateFlow(true)
    val catalogOpen: StateFlow<Boolean> = _catalogOpen

    val catalogState: StateFlow<CatalogUiState> = combine(
        catalog, offersFlow,
    ) { (groups, types, loading), offers ->
        CatalogUiState(
            sections = sectionsOf(groups, types),
            // Guruhi noma'lum turlar (eski kesh) — bo'limlardan keyin alohida.
            looseTypes = types
                .filterNot { CatalogRules.isHidden(it.id) }
                .filter { t -> t.groupKey.isBlank() || groups.none { it.key == t.groupKey } }
                .sortedBy { it.name },
            totalOffers = offers.count { !CatalogRules.isHidden(it.categoryId) },
            loading = loading && types.isEmpty(),
        )
    }
        .catch { emit(CatalogUiState()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CatalogUiState())

    val state: StateFlow<DiscountsUiState> = combine(
        offersFlow, query, applied, savedIdsFlow, catalog,
    ) { offers, q, f, savedIds, (groups, types, loading) ->
        val filtered = filter(offers, f, q)
        val section = sectionsOf(groups, types).find(f)
        DiscountsUiState(
            offers = filtered,
            query = q,
            savedIds = savedIds,
            totalCount = filtered.size,
            activeFilterCount = f.activeCount,
            section = section,
            type = f.singleCategoryId?.let { id -> types.firstOrNull { it.id == id } },
            // Chiplar faqat bo'lim ochiq bo'lganda: "hamma turlar" ro'yxati juda uzun.
            sectionTypes = section?.types.orEmpty(),
            subcategories = f.subcategories,
            loading = loading && offers.isEmpty(),
        )
    }
        .catch { emit(DiscountsUiState()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DiscountsUiState())

    val filterState: StateFlow<FilterDraftState> = combine(
        offersFlow, catalog, draft, query, schema,
    ) { offers, (groups, allCategories, _), d, q, sc ->
        val sections = sectionsOf(groups, allCategories)
        // Bo'lim tanlangan bo'lsa biznes turlari ham shu bo'lim doirasida ko'rsatiladi
        // ("Ovqatlanish" ichida "Game Club", "Xizmatlar" ichida "Kiyim" chiqib qolmasin).
        val categories = sections.find(d)?.types
            ?: allCategories.filterNot { CatalogRules.isHidden(it.id) }
        // Zaxira ro'yxat (sxema kelmagan holat) ham ochiq bo'lim doirasida bo'lsin —
        // aks holda ovqat ekranida keshdagi sport bo'limlari chiqib qolardi.
        val inGroup = filterSection(offers, d)
        val inCategory = if (d.categoryIds.isEmpty()) inGroup
        else inGroup.filter { it.categoryId in d.categoryIds }
        // Bo'limlar: sxema kelgan bo'lsa serverdan (sonlari bilan), aks holda keshdan.
        val schemaSubs = sc?.categories.orEmpty()
            .filter { d.categoryIds.isEmpty() || it.typeKey in d.categoryIds }
        val availableSubs = if (schemaSubs.isNotEmpty()) {
            schemaSubs.sortedByDescending { it.count }.map { it.label }
        } else {
            inCategory.map { it.subcategory }.filter { it.isNotBlank() }.distinct().sorted()
        }
        FilterDraftState(
            sections = sections,
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

    /**
     * Umumiy feed yangilanishi. Guruh yangilanishi undan KEYIN bo'lishi shart: `refresh()`
     * keshdagi e'lonlarni tozalab qayta yozadi (har guruhdan 12 ta), shuning uchun oldin
     * tortilgan to'liq bo'lim ro'yxatini ustidan yozib yuborardi — bo'limda e'lonlar kam
     * ko'rinishining sababi aynan shu edi.
     */
    private val feedRefresh = viewModelScope.launch {
        // Xato bo'lsa ham bayroq tushadi — aks holda skelet abadiy aylanib qolardi
        // (repository keshni o'zi saqlaydi, feed bo'sh bo'lsa "topilmadi" ko'rinadi).
        runCatching { discountRepository.refresh() }
        refreshing.value = false
    }

    init {
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
                applied.value = applied.value.copy(categoryIds = setOfNotNull(s.typeKey), subcategories = emptySet())
            }
            SuggestionKind.CATEGORY -> {
                query.value = ""
                applied.value = applied.value.copy(categoryIds = setOfNotNull(s.typeKey), subcategories = setOf(s.label))
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

    private fun filter(offers: List<DiscountOffer>, f: FilterValues, q: String): List<DiscountOffer> =
        filterSection(offers, f)
        .let { list -> if (f.categoryIds.isEmpty()) list else list.filter { it.categoryId in f.categoryIds } }
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
    fun onQuery(q: String) {
        query.value = q
        // Katalogda turib qidirilsa natijalar ko'rinmay qolardi — e'lonlar ro'yxatiga o'tamiz.
        if (q.isNotBlank()) _catalogOpen.value = false
    }

    // -----------------------------------------------------------------------
    // Katalog (bo'limlar) ↔ e'lonlar ro'yxati
    // -----------------------------------------------------------------------

    /**
     * Katalogdan BO'LIM tanlandi — bo'limning barcha turlari bitta feed'da ochiladi
     * ("Ovqatlanish" → Milliy taomlar + Fast food + Somsa birga). Tur esa feed tepasidagi
     * chiplar orqali toraytiriladi ([selectType]).
     *
     * Guruh alohida to'liq tortiladi ([DiscountRepository.refreshGroup]): umumiy feed har
     * guruhdan atigi bir nechtasini oladi, aks holda bo'limda 2-3 ta e'lon ko'rinib qolardi.
     */
    fun openSection(section: CatalogSection) {
        val values = FilterValues(
            groupKey = section.groupKey,
            // Bo'lingan guruhda faqat shu bo'limning turlari ko'rinadi ("Xizmatlar" ichida
            // kiyim chiqmasin); server so'rovi baribir butun guruh bilan ketadi.
            typeKeys = if (section.partial) section.typeKeys else emptySet(),
        )
        applied.value = values
        draft.value = values
        _catalogOpen.value = false
        viewModelScope.launch {
            // `join()` — umumiy yangilanish keshni tozalab qayta yozadi; undan oldin
            // tortsak natija o'chib ketardi (qarang [openGroup]).
            feedRefresh.join()
            runCatching { discountRepository.refreshGroup(section.groupKey) }
        }
    }

    /**
     * Feed tepasidagi tur chipi bosildi — bo'lim ichida tur bo'yicha toraytiriladi
     * (`null` — "Hammasi", ya'ni butun bo'lim). Bo'lim filtri saqlanadi.
     */
    fun selectType(id: String?) {
        applied.value = applied.value.copy(categoryIds = setOfNotNull(id), subcategories = emptySet(), gender = null)
        draft.value = applied.value
    }

    /**
     * Katalogdan tur tanlandi — feed shu turga qisqaradi. Endi faqat guruhi noma'lum
     * turlar uchun ishlatiladi (qarang [CatalogUiState.looseTypes]).
     *
     * Turning GURUHI ham qo'llanadi: umumiy feed har guruhdan atigi bir nechtasini tortadi,
     * shuning uchun guruh alohida to'liq tortiladi ([DiscountRepository.refreshGroup]) —
     * aks holda tur ichida 2-3 ta e'lon ko'rinib qolardi.
     */
    fun openType(type: DiscountCategory) {
        val groupKey = type.groupKey.takeIf { it.isNotBlank() }
        val values = FilterValues(groupKey = groupKey, categoryIds = setOf(type.id))
        applied.value = values
        draft.value = values
        _catalogOpen.value = false
        if (groupKey == null) return
        viewModelScope.launch {
            // `join()` — umumiy yangilanish keshni tozalab qayta yozadi; undan oldin
            // tortsak natija o'chib ketardi (qarang [openGroup]).
            feedRefresh.join()
            runCatching { discountRepository.refreshGroup(groupKey) }
        }
    }

    /** "Barchasi" — turlarsiz to'liq feed. */
    fun openAllOffers() {
        applied.value = FilterValues()
        draft.value = FilterValues()
        _catalogOpen.value = false
    }

    /** E'lonlar ro'yxatidan katalogga qaytish — filtrlar va qidiruv tozalanadi. */
    fun backToCatalog() {
        applied.value = FilterValues()
        draft.value = FilterValues()
        query.value = ""
        _catalogOpen.value = true
    }

    /**
     * Ekran konkret bo'lim bilan ochildi (Home'dagi "Ovqatlar → Barchasi").
     *
     * Guruh QO'LLANGAN filtrga darrov yoziladi — foydalanuvchi Filter'ni ochmasa ham feed
     * shu bo'limga qisqaradi; qoralamaga ham qo'yiladi, aks holda Filter ochilganda
     * bo'lim tanlanmagandek ko'rinardi. Bo'sh kalit — filtrsiz ochish.
     */
    fun openGroup(key: String?) {
        val groupKey = key?.takeIf { it.isNotBlank() } ?: return
        // Butun guruh ochiladi — ilovadagi bo'linish (Savdo/Xizmatlar) qo'llanmaydi.
        applied.value = applied.value.copy(groupKey = groupKey, typeKeys = emptySet())
        draft.value = draft.value.copy(groupKey = groupKey, typeKeys = emptySet())
        // Bo'lim bilan kelindi — katalog oralig'i o'tkazib yuboriladi.
        _catalogOpen.value = false
        // Bosh ekran uchun har guruhdan faqat bir nechtasi tortilgan edi — bo'lim ekranida
        // esa hammasi kerak (ro'yxat ham, xarita ham shu keshdan ishlaydi).
        // `join()` — umumiy yangilanish tugagach: aks holda u keshni tozalab, shu yerda
        // tortilgan to'liq ro'yxatni o'chirib yuborardi.
        viewModelScope.launch {
            feedRefresh.join()
            runCatching { discountRepository.refreshGroup(groupKey) }
        }
    }

    // --- Filter ekrani (qoralama) ---
    /** Filter ekrani ochilganda qoralamani qo'llangan holat bilan tenglaymiz va sxemani tortamiz. */
    fun openFilter() {
        draft.value = applied.value
        loadSchema(applied.value)
    }

    /**
     * Bo'lim (kategoriya) variantlari kerak bo'lganda — masalan xarita ochilganda —
     * sxemani bir marta tortadi. Filter ekrani ochilmagan bo'lsa sxema hali `null` bo'ladi
     * va xaritada chiplar chiqmasdi.
     */
    fun ensureSchema() {
        if (schema.value == null) loadSchema(applied.value)
    }

    /**
     * Bitta bo'limni (masalan "Lavash / Shaurma") DARHOL qo'llaydi — xaritadagi chiplar
     * shu orqali ishlaydi. `null` — tanlovni bekor qiladi.
     *
     * Qoralamaga ham yoziladi: keyin Filter ochilsa tanlov joyida turadi.
     */
    fun selectSubcategory(label: String?) {
        val value = label?.let { setOf(it) } ?: emptySet()
        applied.value = applied.value.copy(subcategories = value)
        draft.value = draft.value.copy(subcategories = value)
    }

    /**
     * Filtr variantlarini serverdan oladi (`POST /v1/catalog/filter-schema`).
     *
     * Doira quyidagicha toraytiriladi:
     * - biznes turi tanlangan bo'lsa — faqat o'sha tur;
     * - guruh ilovada bo'lingan bo'lsa ("Xizmatlar") — faqat shu bo'limning turlari;
     * - tanlanmagan, lekin katalog bo'limi ochiq bo'lsa ("Ovqatlanish") — SHU bo'limdagi
     *   turlar. Busiz javob butun katalogdan kelardi va ovqat ekranida "Darvozabon maktabi"
     *   kabi sport kategoriyalari chiqib qolardi;
     * - hech biri yo'q bo'lsa — butun katalog.
     *
     * Xato bo'lsa sxema `null` qoladi va ekran keshdan ishlaydi.
     */
    private fun loadSchema(f: FilterValues) = viewModelScope.launch {
        val typeKeys = when {
            f.categoryIds.isNotEmpty() -> f.categoryIds.toList()
            f.typeKeys.isNotEmpty() -> f.typeKeys.toList()
            f.groupKey != null -> discountRepository.observeCategories().first()
                .filter { it.groupKey == f.groupKey && !CatalogRules.isHidden(it.id) }
                .map { it.id }
            else -> emptyList()
        }
        val res = discountRepository.getFilterSchema(typeKeys)
        schema.value = (res as? Resource.Success)?.data?.takeIf { it.total > 0 || it.types.isNotEmpty() }
    }

    fun onDraftDiscountFilter(f: DiscountFilter) { draft.value = draft.value.copy(discountFilter = f) }

    /**
     * Katalog bo'limi o'zgardi. Tur bo'limga bog'liq bo'lgani uchun tanlangan biznes turi
     * va undan keyingi tanlovlar tozalanadi. `null` — bo'limsiz (butun katalog).
     */
    fun onDraftSection(section: CatalogSection?) {
        draft.value = draft.value.copy(
            groupKey = section?.groupKey,
            typeKeys = section?.takeIf { it.partial }?.typeKeys ?: emptySet(),
            categoryIds = emptySet(), subcategories = emptySet(), gender = null,
        )
        loadSchema(draft.value)
    }

/**
     * Biznes turini qo'shadi/olib tashlaydi — **ko'p tanlov**. `null` — hammasini bekor
     * qilish ("Barchasi").
     *
     * Tur o'zgarganda unga bog'liq sub-kategoriya va jins tanlovlari tozalanadi: ular
     * boshqa turda umuman mavjud bo'lmasligi mumkin.
     */
    fun toggleDraftCategory(id: String?) {
        draft.value = draft.value.let { current ->
            val next = when {
                id == null -> emptySet()
                id in current.categoryIds -> current.categoryIds - id
                else -> current.categoryIds + id
            }
            current.copy(categoryIds = next, subcategories = emptySet(), gender = null)
        }
        // bo'limlar va sonlar yangi turga moslanadi (tur bo'sh bo'lsa — ochiq bo'lim doirasida)
        loadSchema(draft.value)
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

/**
 * Server guruhlari va turlaridan ekrandagi BO'LIMLAR ro'yxati.
 *
 * Ikki tuzatish qo'llanadi ([CatalogRules]): yashirin turlar (ijara) tashlanadi va xizmat
 * turlari o'z bo'limiga ajratiladi ("Savdo va xizmat" → "Savdo" + "Xizmatlar"). Guruhda
 * faqat savdo yoki faqat xizmat bo'lsa bo'linish bo'lmaydi — bitta bo'lim qoladi.
 */
private fun sectionsOf(
    groups: List<DiscountGroup>,
    types: List<DiscountCategory>,
): List<CatalogSection> {
    val byGroup = types.filterNot { CatalogRules.isHidden(it.id) }.groupBy { it.groupKey }
    return groups.flatMap { g ->
        val list = byGroup[g.key].orEmpty().sortedByDescending { it.offerCount }
        val (services, goods) = list.partition { CatalogRules.isService(it.id) }
        when {
            // Turi qolmagan guruh (masalan butunlay ijaradan iborat) — katakcha chizilmaydi.
            list.isEmpty() -> emptyList()
            services.isEmpty() || goods.isEmpty() ->
                listOf(CatalogSection(g.key, g.name, g.emoji, g.accent, g.key, list))
            else -> listOf(
                CatalogSection(
                    key = g.key, name = CatalogRules.goodsName(g.name), emoji = g.emoji,
                    accent = g.accent, groupKey = g.key, types = goods, partial = true,
                ),
                CatalogSection(
                    key = g.key + CatalogRules.SERVICES_SUFFIX, name = CatalogRules.servicesName,
                    emoji = CatalogRules.SERVICES_EMOJI, accent = g.accent, groupKey = g.key,
                    types = services, partial = true,
                ),
            )
        }
    }
}

/**
 * Filtr qiymatlariga mos bo'lim. Avval aniq mos kelgani (bo'lingan bo'limda turlar to'plami
 * ham bir xil), keyin — butun guruh. `null` — bo'lim ochilmagan.
 */
private fun List<CatalogSection>.find(f: FilterValues): CatalogSection? {
    val key = f.groupKey ?: return null
    return firstOrNull { it.groupKey == key && it.typeKeys == f.typeKeys }
        ?: firstOrNull { it.groupKey == key && !it.partial }
}

/**
 * E'lonlarni ochiq BO'LIM doirasiga qisqartiradi: guruh + (guruh bo'lingan bo'lsa) o'sha
 * bo'limning turlari. Yashirin turlar (ijara) har doim tashlanadi — eski keshda qolgan
 * qatorlar ham ko'rinib qolmasin.
 */
private fun filterSection(offers: List<DiscountOffer>, f: FilterValues): List<DiscountOffer> = offers
    .filterNot { CatalogRules.isHidden(it.categoryId) }
    .let { list -> if (f.groupKey == null) list else list.filter { it.groupKey == f.groupKey } }
    .let { list -> if (f.typeKeys.isEmpty()) list else list.filter { it.categoryId in f.typeKeys } }
