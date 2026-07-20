package dev.feature.clubs.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.feature.clubs.domain.model.Club
import dev.feature.clubs.domain.repository.ClubRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ClubsUiState(
    val clubs: List<Club> = emptyList(),
    val joinedCount: Int = 0,
)

class ClubsViewModel(
    private val repository: ClubRepository,
) : ViewModel() {
    val state: StateFlow<ClubsUiState> =
        repository.observeClubs()
            .map { list -> ClubsUiState(clubs = list, joinedCount = list.count { it.joined }) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ClubsUiState())

    fun toggleJoin(club: Club) { viewModelScope.launch { repository.setJoined(club.id, !club.joined) } }
}
