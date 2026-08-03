package dev.feature.calls.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.core.common.Resource
import dev.feature.calls.domain.model.Call
import dev.feature.calls.domain.model.CallPage
import dev.feature.calls.domain.repository.CallRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Qo'ng'iroqlar tarixi ekranining holati. */
data class CallHistoryState(
    val calls: List<Call> = emptyList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val error: String? = null,
)

/**
 * `GET /v1/calls` — alohida «Qo'ng'iroqlar» ekrani.
 *
 * ⚠️ Bu ekran **majburiy emas**: qo'ng'iroq chat lentasining o'zida `CALL` xabar bo'lib
 * ko'rinadi (`handoff/09-CALLS-REST.md` §4). Alohida ro'yxat faqat «hammasi bir joyda»
 * ko'rinishini xohlaganda kerak.
 */
class CallHistoryViewModel(private val repository: CallRepository) : ViewModel() {

    private val _state = MutableStateFlow(CallHistoryState())
    val state: StateFlow<CallHistoryState> = _state.asStateFlow()

    private var page = 1

    init {
        refresh()
    }

    fun refresh() {
        page = 1
        _state.update { it.copy(loading = true, error = null) }
        load(replace = true)
    }

    /** Ro'yxat oxiriga yetganda chaqiriladi. */
    fun loadMore() {
        val current = _state.value
        if (!current.hasMore || current.loadingMore || current.loading) return
        page += 1
        _state.update { it.copy(loadingMore = true) }
        load(replace = false)
    }

    private fun load(replace: Boolean) = viewModelScope.launch {
        when (val result = repository.history(page = page, size = CallPage.DEFAULT_PAGE_SIZE)) {
            is Resource.Success -> _state.update { current ->
                current.copy(
                    // `distinctBy` — sahifalash paytida yangi qo'ng'iroq qo'shilsa qatorlar
                    // siljib, bittasi ikki sahifada ham chiqib qolishi mumkin.
                    calls = if (replace) result.data.items else (current.calls + result.data.items)
                        .distinctBy(Call::id),
                    loading = false,
                    loadingMore = false,
                    hasMore = result.data.hasNext,
                    error = null,
                )
            }

            is Resource.Error -> _state.update {
                // Sahifa yuklanmasa raqamni qaytaramiz, aks holda keyingi urinish o'sha
                // sahifani o'tkazib yuborardi.
                if (!replace) page -= 1
                it.copy(loading = false, loadingMore = false, error = result.message)
            }

            Resource.Loading -> Unit
        }
    }
}
