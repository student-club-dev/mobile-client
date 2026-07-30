package dev.feature.chat.presentation.gif

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.feature.chat.domain.model.StickerCatalog
import dev.feature.chat.domain.model.StickerPack
import dev.feature.chat.domain.repository.StickerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Stiker panelining holati. */
@Immutable
data class StickerPanelState(
    val packs: List<StickerPack> = StickerCatalog.packs,
    /**
     * Katalog serverdan keldimi. `false` — ilovaga kiritilgan emoji zaxirasi ko'rsatilyapti
     * (backendda stiker **tasvirlari** hali yo'q, `PENDING_ACTIONS.md` §6).
     */
    val fromServer: Boolean = false,
    val loading: Boolean = true,
)

/**
 * Stiker katalogi paneli.
 *
 * Katalog `GET /v1/stickers/packs` dan bitta javobda keladi va repository darajasida
 * `ETag` bilan keshlanadi — ya'ni panelni qayta-qayta ochish yangi so'rov qilmaydi.
 * Xato holati yo'q: server javob bermasa ham emoji zaxirasi ko'rsatiladi.
 */
class StickerPanelViewModel(
    private val stickerRepository: StickerRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StickerPanelState())
    val state: StateFlow<StickerPanelState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val catalog = stickerRepository.catalog()
            _state.update {
                it.copy(
                    packs = catalog.packs,
                    fromServer = catalog.fromServer,
                    loading = false,
                )
            }
        }
    }
}
