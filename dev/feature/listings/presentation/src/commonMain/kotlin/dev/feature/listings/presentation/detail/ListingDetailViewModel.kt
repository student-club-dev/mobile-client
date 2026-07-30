package dev.feature.listings.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.feature.listings.domain.model.Listing
import dev.feature.listings.domain.usecase.GetListingUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * E'lonni to'liq ko'rish ekranining ViewModel'i.
 *
 * Ekran faqat o'qish uchun — shuning uchun holat ham juda sodda: e'lonning o'zi va yuklash
 * bosqichi. Tahrirlash oqimi butunlay boshqa ekranda ([dev.feature.listings.presentation.PostListingViewModel]).
 */
class ListingDetailViewModel(
    private val getListing: GetListingUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ListingDetailUiState())
    val state: StateFlow<ListingDetailUiState> = _state.asStateFlow()

    /** Qaysi e'lon yuklangani — bir xil id bilan qayta yuklanmasligi uchun. */
    private var loadedId: String? = null

    /**
     * E'lonni yuklaydi.
     *
     * Ekran `LaunchedEffect` ichida chaqiradi va u konfiguratsiya o'zgarganda yoki qayta
     * kompozitsiyada yana ishga tushishi mumkin — shu sabab bir xil id ikkinchi marta
     * yuklanmaydi, aks holda tayyor kontent yana "Yuklanmoqda" holatiga qaytib ketardi.
     */
    fun load(listingId: String) {
        if (loadedId == listingId) return
        loadedId = listingId

        viewModelScope.launch {
            _state.update { it.copy(loading = true, notFound = false) }
            val listing = getListing(listingId)
            _state.update {
                it.copy(
                    listing = listing,
                    loading = false,
                    // `null` — e'lon o'chirilgan yoki havola eskirgan.
                    notFound = listing == null,
                )
            }
        }
    }
}

data class ListingDetailUiState(
    val listing: Listing? = null,
    val loading: Boolean = true,
    val notFound: Boolean = false,
)
