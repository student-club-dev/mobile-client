package dev.feature.auth.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.core.domain.model.DiscountCategory
import dev.core.domain.model.DiscountOffer
import dev.core.domain.repository.DiscountRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

/** "Siz uchun" feed holati (qo'llangan filtrlar bilan). */
data class DiscountsUiState(
    val offers: List<DiscountOffer> = emptyList(),
    val query: String = "",
    val savedIds: Set<String> = emptySet(),
    val totalCount: Int = 0,
    val activeFilterCount: Int = 0,
)

/** Filter ekrani holati (qoralama tanlovlar + dinamik variantlar + jonli natija soni). */
data class FilterDraftState(
    val categories: List<DiscountCategory> = emptyList(),
    val draft: FilterValues = FilterValues(),
    val availableSubcategories: List<String> = emptyList(),
    val genderApplicable: Boolean = false,
    val previewCount: Int = 0,
)

class DiscountsViewModel(
    private val discountRepository: DiscountRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    // Qo'llangan filtrlar — feed'ni boshqaradi.
    private val applied = MutableStateFlow(FilterValues())
    // Qoralama filtrlar — Filter ekranida tahrirlanadi, "Qo'llash" bosilganda [applied] ga ko'chadi.
    private val draft = MutableStateFlow(FilterValues())

    private val offersFlow = discountRepository.observeAllOffers()
    private val savedIdsFlow = discountRepository.observeSaved().map { list -> list.map { it.id }.toSet() }

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
        offersFlow, discountRepository.observeCategories(), draft, query,
    ) { offers, categories, d, q ->
        val inCategory = if (d.categoryId == null) offers else offers.filter { it.categoryId == d.categoryId }
        val availableSubs = inCategory.map { it.subcategory }.filter { it.isNotBlank() }.distinct().sorted()
        FilterDraftState(
            categories = categories,
            draft = d,
            availableSubcategories = availableSubs,
            genderApplicable = inCategory.any { it.gender.isNotBlank() },
            previewCount = filter(offers, d, q).size,
        )
    }
        .catch { emit(FilterDraftState()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FilterDraftState())

    init {
        viewModelScope.launch { discountRepository.refresh() }
    }

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
    /** Filter ekrani ochilganda qoralamani qo'llangan holat bilan tenglaymiz. */
    fun openFilter() { draft.value = applied.value }

    fun onDraftDiscountFilter(f: DiscountFilter) { draft.value = draft.value.copy(discountFilter = f) }

    /** Biznes turi o'zgarsa — unga bog'liq sub-kategoriya va jins tanlovlari tozalanadi. */
    fun onDraftCategory(id: String?) {
        draft.value = draft.value.copy(categoryId = id, subcategories = emptySet(), gender = null)
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

    fun toggleSaved(offer: DiscountOffer, currentlySaved: Boolean) {
        viewModelScope.launch { discountRepository.setSaved(offer.id, !currentlySaved) }
    }
}
