package dev.feature.listings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.core.common.Resource
import dev.core.domain.usecase.ObserveCurrentUserUseCase
import dev.feature.listings.domain.model.Listing
import dev.feature.listings.domain.usecase.DeleteListingUseCase
import dev.feature.listings.domain.usecase.ObserveMyListingsUseCase
import dev.feature.listings.domain.usecase.RefreshMyListingsUseCase
import dev.feature.listings.domain.usecase.ToggleListingPausedUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MyListingsUiState(
    val listings: List<Listing> = emptyList(),
    val loading: Boolean = true,
    /** Serverdan yangilanmoqda — kesh allaqachon ko'rinib turibdi. */
    val refreshing: Boolean = false,
    val message: String? = null,
)

/**
 * "Mening e'lonlarim" — foydalanuvchining barcha e'lonlari, barcha statuslar bilan
 * (qoralama, faol, to'xtatilgan, muddati o'tgan).
 *
 * Ro'yxat **keshdan** ko'rsatiladi va shu bilan birga serverdan yangilanadi
 * (`GET /v1/student-listings/mine`): ekran darrov ochiladi, so'ng boshqa qurilmadagi
 * o'zgarishlar o'rniga tushadi. Status va o'chirish amallari to'g'ridan-to'g'ri serverga
 * boradi — muvaffaqiyat javobi keshni yangilaydi va ro'yxat o'zi qayta chiziladi.
 */
class MyListingsViewModel(
    observeCurrentUser: ObserveCurrentUserUseCase,
    observeMyListings: ObserveMyListingsUseCase,
    private val refreshMyListings: RefreshMyListingsUseCase,
    private val togglePaused: ToggleListingPausedUseCase,
    private val deleteListing: DeleteListingUseCase,
) : ViewModel() {

    private val ownerId = observeCurrentUser()
        .map { (it?.id ?: 0L).toString() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Tarmoq holati — keshdan kelgan ro'yxat bilan qo'shib beriladi. */
    private val sync = MutableStateFlow(SyncState())

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<MyListingsUiState> = ownerId
        .filterNotNull()
        // Foydalanuvchi almashsa (chiqib qayta kirsa) ro'yxat ham almashadi.
        .flatMapLatest { id -> observeMyListings(id) }
        .combine(sync) { listings, sync ->
            MyListingsUiState(
                listings = listings,
                loading = false,
                refreshing = sync.refreshing,
                message = sync.message,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MyListingsUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val id = ownerId.filterNotNull().first()
            sync.update { it.copy(refreshing = true) }
            val res = refreshMyListings(id)
            sync.update {
                // Xato bo'lsa kesh joyida qoladi — ro'yxat yo'qolmaydi, faqat xabar chiqadi.
                it.copy(refreshing = false, message = (res as? Resource.Error)?.message)
            }
        }
    }

    fun togglePaused(listing: Listing) {
        viewModelScope.launch {
            val res = togglePaused.invoke(listing)
            sync.update { it.copy(message = (res as? Resource.Error)?.message) }
        }
    }

    fun delete(listing: Listing) {
        viewModelScope.launch {
            val res = deleteListing(listing.id)
            sync.update { it.copy(message = (res as? Resource.Error)?.message) }
        }
    }

    fun consumeMessage() = sync.update { it.copy(message = null) }

    private data class SyncState(val refreshing: Boolean = false, val message: String? = null)
}
