package dev.feature.auth.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.core.domain.model.Ad
import dev.core.domain.model.AdType
import dev.core.domain.repository.AdRepository
import dev.core.domain.usecase.ObserveCurrentUserUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Elon berish (1s tur → 1v forma) holati. */
data class PostAdUiState(
    val type: AdType? = null,
    val title: String = "",
    val category: String = "",
    val price: String = "",
    val description: String = "",
    val posted: Boolean = false,
) {
    val canSubmit: Boolean get() = title.isNotBlank() && category.isNotBlank()
}

class PostAdViewModel(
    observeCurrentUserUseCase: ObserveCurrentUserUseCase,
    private val adRepository: AdRepository,
) : ViewModel() {

    private val user = observeCurrentUserUseCase().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _state = MutableStateFlow(PostAdUiState())
    val state: StateFlow<PostAdUiState> = _state.asStateFlow()

    fun selectType(type: AdType) = _state.update { it.copy(type = type) }
    fun backToTypes() = _state.update { it.copy(type = null) }
    fun onTitle(v: String) = _state.update { it.copy(title = v) }
    fun onCategory(v: String) = _state.update { it.copy(category = v) }
    fun onPrice(v: String) = _state.update { it.copy(price = v) }
    fun onDescription(v: String) = _state.update { it.copy(description = v) }

    fun submit() {
        val s = _state.value
        if (!s.canSubmit || s.type == null) return
        val ownerId = (user.value?.id ?: 0L).toString()
        val id = "ad-$ownerId-${s.title.hashCode()}"
        viewModelScope.launch {
            adRepository.post(
                Ad(
                    id = id,
                    type = s.type,
                    title = s.title,
                    category = s.category,
                    price = s.price,
                    description = s.description,
                    images = emptyList(),
                    ownerId = ownerId,
                    createdAgo = "hozir",
                ),
            )
            _state.update { it.copy(posted = true) }
        }
    }
}
