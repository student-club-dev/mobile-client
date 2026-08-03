package dev.feature.listings.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.core.common.Resource
import dev.core.common.error.AppException
import dev.feature.listings.domain.model.Listing
import dev.feature.listings.domain.usecase.FetchListingUseCase
import dev.feature.listings.domain.usecase.GetListingUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * E'lonni to'liq ko'rish ekranining ViewModel'i.
 *
 * Ikki manba ketma-ket ishlatiladi va bu ataylab: avval **keshdagi** nusxa ko'rsatiladi
 * (ro'yxatdan bosilgan e'lon darrov ochiladi, bo'sh ekran ko'rinmaydi), so'ng serverdan
 * to'liq varianti keladi. Farqi kichik emas: `contactPhone` **faqat** `GET /{id}` javobida
 * bo'ladi (§7.2.0) va `viewsCount` ham aynan shu so'rovda oshadi.
 *
 * Tahrirlash oqimi butunlay boshqa ekranda
 * ([dev.feature.listings.presentation.PostListingViewModel]).
 */
class ListingDetailViewModel(
    private val getCached: GetListingUseCase,
    private val fetchListing: FetchListingUseCase,
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
     * yuklanmaydi, aks holda tayyor kontent yana "Yuklanmoqda" holatiga qaytib ketardi
     * va har ochilishda ko'rishlar soni bekordan-bekor oshardi.
     */
    fun load(listingId: String) {
        if (loadedId == listingId) return
        loadedId = listingId

        viewModelScope.launch {
            val cached = getCached(listingId)
            _state.update { it.copy(listing = cached, loading = true, notFound = false) }

            when (val res = fetchListing(listingId)) {
                is Resource.Success -> _state.update {
                    it.copy(listing = res.data, loading = false, notFound = false, error = null)
                }
                is Resource.Error -> _state.update {
                    // `404` — e'lon o'chirilgan yoki sizga ko'rinmaydi (server ataylab
                    // 403 bermaydi: begona odam e'lon borligini ham bilmasligi kerak).
                    val gone = res.error is AppException.NotFound
                    it.copy(
                        // Server e'lon yo'q desa keshdagi nusxa ham eskirgan — repository
                        // uni o'chirdi, ekranda ham ko'rsatib turish yolg'on bo'lardi.
                        listing = if (gone) null else it.listing,
                        loading = false,
                        notFound = gone,
                        // Keshdagi nusxa bor bo'lsa ekran ochiq qoladi — xato faqat
                        // "telefon raqami ko'rinmayapti" degani, bo'sh ekran emas.
                        error = res.message.takeIf { _ -> !gone && cached == null },
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    /** Xato holatidan qayta urinish. */
    fun retry() {
        val id = loadedId ?: return
        loadedId = null
        load(id)
    }
}

data class ListingDetailUiState(
    val listing: Listing? = null,
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val error: String? = null,
)
