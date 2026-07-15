package dev.feature.discounts.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.core.domain.usecase.ObserveCurrentUserUseCase
import dev.feature.discounts.domain.model.Listing
import dev.feature.discounts.domain.usecase.DeleteListingUseCase
import dev.feature.discounts.domain.usecase.ObserveMyListingsUseCase
import dev.feature.discounts.domain.usecase.ToggleListingPausedUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MyListingsUiState(
    val listings: List<Listing> = emptyList(),
    val loading: Boolean = true,
)

/** "Mening e'lonlarim" — biznes egasi yuklagan chegirmalar (barcha statuslar). */
class MyListingsViewModel(
    observeCurrentUser: ObserveCurrentUserUseCase,
    observeMyListings: ObserveMyListingsUseCase,
    private val togglePaused: ToggleListingPausedUseCase,
    private val deleteListing: DeleteListingUseCase,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<MyListingsUiState> = observeCurrentUser()
        // Foydalanuvchi almashsa (chiqib qayta kirsa) ro'yxat ham almashadi.
        .flatMapLatest { user -> observeMyListings((user?.id ?: 0L).toString()) }
        .map { MyListingsUiState(listings = it, loading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MyListingsUiState())

    fun togglePaused(listing: Listing) {
        viewModelScope.launch { togglePaused.invoke(listing) }
    }

    fun delete(listing: Listing) {
        viewModelScope.launch { deleteListing(listing.id) }
    }
}
