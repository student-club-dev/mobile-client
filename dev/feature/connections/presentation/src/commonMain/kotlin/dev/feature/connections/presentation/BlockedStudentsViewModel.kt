package dev.feature.connections.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.core.common.Resource
import dev.feature.connections.domain.model.BlockedStudent
import dev.feature.connections.domain.repository.ConnectionsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BlockedUiState(
    val items: List<BlockedStudent> = emptyList(),

    /** Birinchi sahifa yuklanmoqda — ekran skeletsiz, oddiy matn bilan kutadi. */
    val loading: Boolean = false,

    /** Keyingi sahifa yuklanmoqda — ro'yxat ostidagi indikator. */
    val loadingMore: Boolean = false,

    /**
     * Ro'yxat **umuman** ochilmadi (birinchi sahifa xatosi) — to'liq ekranli xato + "Qayta
     * urinish". Keyingi sahifa xatosi bu yerga tushmaydi: unda ro'yxat allaqachon bor,
     * shuning uchun faqat [message] ko'rsatiladi.
     */
    val error: String? = null,

    val hasNext: Boolean = false,

    /** Serverdagi umumiy son — sarlavha ostidagi "N ta talaba". */
    val total: Int = 0,

    /** Blokdan chiqarish davom etayotgan talabalar — tugma o'chirilgan holatda turadi. */
    val busyIds: Set<String> = emptySet(),

    /** Bir martalik xabar (tasdiq yoki xato) — ko'rsatilgach [BlockedStudentsViewModel.messageShown]. */
    val message: String? = null,
) {
    val isEmpty: Boolean get() = items.isEmpty() && !loading && error == null
}

/**
 * **Bloklanganlar** ro'yxati — `GET /v1/blocks` (handoff: `api-changes.md` §4b).
 *
 * Ro'yxatda faqat FOYDALANUVCHI bloklaganlari bo'ladi; sizni kim bloklagani serverda ataylab
 * berilmaydi. Presence maydonlari maskalangan, shuning uchun ekranda "onlayn"/"oxirgi faollik"
 * umuman chizilmaydi.
 *
 * Kesh yo'q — ekran kamdan-kam ochiladi, local nusxa esa boshqa qurilmada blok yechilgach
 * darrov eskirardi. Sahifa **1 dan** boshlanadi.
 */
class BlockedStudentsViewModel(
    private val repository: ConnectionsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BlockedUiState())
    val state: StateFlow<BlockedUiState> = _state.asStateFlow()

    /** Keyingi so'raladigan sahifa. Birinchi yuklashda 1 ga qaytariladi. */
    private var nextPage = FIRST_PAGE

    /** Bir vaqtda bitta sahifa so'ralsin — scroll tez bo'lsa `loadMore` ketma-ket chaqiriladi. */
    private var loadJob: Job? = null

    init {
        refresh()
    }

    /** Ro'yxatni boshidan qayta o'qiydi (ekran ochilishi, "Qayta urinish", blok yechilgandan keyin). */
    fun refresh() {
        loadJob?.cancel()
        nextPage = FIRST_PAGE
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val res = repository.blocked(page = FIRST_PAGE)) {
                is Resource.Success -> {
                    nextPage = FIRST_PAGE + 1
                    _state.update {
                        it.copy(
                            items = res.data.items,
                            hasNext = res.data.hasNext,
                            total = res.data.total,
                            loading = false,
                            error = null,
                        )
                    }
                }
                is Resource.Error -> _state.update { it.copy(loading = false, error = res.message) }
                Resource.Loading -> Unit
            }
        }
    }

    /**
     * Keyingi sahifa. Ekran buni ro'yxat oxiriga yaqinlashganda chaqiradi, shuning uchun
     * takroriy chaqiruvlar shu yerda filtrlanadi — `hasNext` yo'q yoki yuklash ketayotgan
     * bo'lsa hech narsa qilinmaydi.
     */
    fun loadMore() {
        val s = _state.value
        if (!s.hasNext || s.loading || s.loadingMore) return
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            val page = nextPage
            when (val res = repository.blocked(page = page)) {
                is Resource.Success -> {
                    nextPage = page + 1
                    _state.update {
                        // Blok yechilgan qatorlar tufayli sahifalar surilib, bitta talaba ikki
                        // marta kelishi mumkin — id bo'yicha filtrlaymiz (LazyColumn key'i unikal
                        // bo'lishi shart).
                        val known = it.items.mapTo(mutableSetOf()) { b -> b.student.id }
                        it.copy(
                            items = it.items + res.data.items.filter { b -> b.student.id !in known },
                            hasNext = res.data.hasNext,
                            total = res.data.total,
                            loadingMore = false,
                        )
                    }
                }
                // Ro'yxat allaqachon ko'rinib turibdi — uni xato ekraniga almashtirmaymiz.
                is Resource.Error -> _state.update { it.copy(loadingMore = false, message = res.message) }
                Resource.Loading -> Unit
            }
        }
    }

    /**
     * Blokdan chiqarish. Muvaffaqiyatda qator ro'yxatdan **darhol** olib tashlanadi — serverdan
     * qayta so'rash sahifalash holatini buzardi (`refresh` scroll'ni boshiga tashlaydi).
     *
     * ⚠️ Avvalgi bog'lanish tiklanmaydi — foydalanuvchiga shuni aytamiz.
     */
    fun unblock(blocked: BlockedStudent) {
        val id = blocked.student.id
        if (id in _state.value.busyIds) return
        viewModelScope.launch {
            _state.update { it.copy(busyIds = it.busyIds + id) }
            try {
                when (val res = repository.unblock(id)) {
                    is Resource.Success -> _state.update {
                        it.copy(
                            items = it.items.filterNot { b -> b.student.id == id },
                            total = (it.total - 1).coerceAtLeast(0),
                            message = "${blocked.student.displayName} blokdan chiqarildi",
                        )
                    }
                    is Resource.Error -> _state.update { it.copy(message = res.message) }
                    Resource.Loading -> Unit
                }
            } finally {
                _state.update { it.copy(busyIds = it.busyIds - id) }
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    private companion object {
        /** ⚠️ Bu bo'limda sahifalash **1 dan** boshlanadi (feed'dagi 0 emas). */
        const val FIRST_PAGE = 1
    }
}
