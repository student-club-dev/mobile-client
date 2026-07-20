package dev.feature.listings.presentation.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.feature.listings.domain.model.Listing
import dev.feature.listings.domain.model.ListingFilters
import dev.feature.listings.domain.model.ListingKind
import dev.feature.listings.domain.model.filterBy
import dev.feature.listings.domain.usecase.ObserveListingsByKindUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * Talabaga ko'rinadigan ro'yxat: Ijara / Xizmatlar / Ish e'lonlari.
 *
 * Uchala bo'lim bitta ekranda va bitta ViewModel'da, chunki ular bir xil ishlaydi:
 * tur tanlanadi → qidiriladi → filtrlanadi. Farqi faqat qaysi filtrlar ko'rinishida
 * ([ListingFilters]), va u domenda hal qilingan.
 */
class ListingsBrowseViewModel(
    private val observeByKind: ObserveListingsByKindUseCase,
) : ViewModel() {

    private val kind = MutableStateFlow(ListingKind.JOB)
    private val query = MutableStateFlow("")

    /** Ro'yxatga qo'llangan filtrlar. */
    private val applied = MutableStateFlow(ListingFilters())

    /** Filtr oynasida tahrirlanayotgan nusxa — "Qo'llash" bosilmaguncha ro'yxatga ta'sir qilmaydi. */
    private val draft = MutableStateFlow(ListingFilters())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val listings = kind
        // Tur almashganda oldingi kuzatuv bekor qilinadi — aks holda ikkala oqim ham
        // ochiq qolib, ro'yxat goh unisi goh bunisi bilan yangilanardi.
        .flatMapLatest { observeByKind(it) }
        .catch { emit(emptyList()) }

    val state: StateFlow<ListingsBrowseUiState> =
        combine(kind, listings, query, applied) { kind, all, query, filters ->
            ListingsBrowseUiState(
                kind = kind,
                listings = all.filterBy(filters, query),
                totalCount = all.size,
                query = query,
                activeFilterCount = filters.activeCount(kind),
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            ListingsBrowseUiState(),
        )

    /** Filtr oynasining holati — jonli "nechta e'lon topiladi" hisobi bilan. */
    val filterState: StateFlow<ListingFilterUiState> =
        combine(kind, listings, query, draft) { kind, all, query, draft ->
            ListingFilterUiState(
                kind = kind,
                draft = draft,
                previewCount = all.filterBy(draft, query).size,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            ListingFilterUiState(),
        )

    fun selectKind(next: ListingKind) {
        if (kind.value == next) return
        kind.value = next
        // Boshqa turning filtrlari ko'rinmas holda ishlab ketmasligi kerak.
        applied.update { it.resetForKind() }
        draft.value = applied.value
    }

    fun onQuery(value: String) {
        query.value = value
    }

    /** Filtr oynasi ochildi — tahrirlash mavjud holatdan boshlanadi. */
    fun openFilter() {
        draft.value = applied.value
    }

    fun updateDraft(transform: (ListingFilters) -> ListingFilters) = draft.update(transform)

    /** Faqat joriy turning filtrlarini tozalaydi (narx chegarasi ham tushadi). */
    fun resetDraft() {
        draft.value = ListingFilters()
    }

    fun applyFilters() {
        applied.value = draft.value
    }
}

data class ListingsBrowseUiState(
    val kind: ListingKind = ListingKind.JOB,
    val listings: List<Listing> = emptyList(),
    /** Filtrlashdan oldingi jami son — "hech narsa topilmadi" holatini ajratish uchun. */
    val totalCount: Int = 0,
    val query: String = "",
    val activeFilterCount: Int = 0,
) {
    /** Ro'yxat bo'sh, lekin bo'limda e'lon bor — demak filtr yoki qidiruv aybdor. */
    val isFilteredEmpty: Boolean get() = listings.isEmpty() && totalCount > 0

    /** Bo'limning o'zi bo'sh — hali hech kim e'lon joylamagan. */
    val isSectionEmpty: Boolean get() = listings.isEmpty() && totalCount == 0
}

data class ListingFilterUiState(
    val kind: ListingKind = ListingKind.JOB,
    val draft: ListingFilters = ListingFilters(),
    val previewCount: Int = 0,
)
