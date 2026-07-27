package dev.feature.connections.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.core.common.Resource
import dev.feature.connections.domain.model.ConnectedStudent
import dev.feature.connections.domain.model.ConnectionRequest
import dev.feature.connections.domain.model.ConnectionStatus
import dev.feature.connections.domain.model.ConnectionView
import dev.feature.connections.domain.model.ReportReason
import dev.feature.connections.domain.model.RequestDirection
import dev.feature.connections.domain.model.SearchedStudent
import dev.feature.connections.domain.model.StudentSummary
import dev.feature.connections.domain.repository.ConnectionsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** "Do'stlar" ekranining uch bo'limi. */
enum class ConnectionsTab { SEARCH, REQUESTS, CONNECTED }

/** So'rovlar bo'limidagi ikki ro'yxat. */
enum class RequestsTab { INCOMING, OUTGOING }

data class ConnectionsUiState(
    val tab: ConnectionsTab = ConnectionsTab.CONNECTED,
    val requestsTab: RequestsTab = RequestsTab.INCOMING,

    val query: String = "",
    val searching: Boolean = false,
    val results: List<SearchedStudent> = emptyList(),
    /** Qidiruv bajarildi-yu, natija bo'sh — "hech nima topilmadi" ni faqat shunda ko'rsatamiz. */
    val searched: Boolean = false,

    val incoming: List<ConnectionRequest> = emptyList(),
    val outgoing: List<ConnectionRequest> = emptyList(),
    /** Kiruvchi so'rovlar soni — tab yonidagi badge. */
    val incomingTotal: Int = 0,

    val connections: List<ConnectedStudent> = emptyList(),
    val loading: Boolean = false,

    /** Amal bajarilayotgan talabalar (tugma o'chirilgan holatda turadi). */
    val busyIds: Set<String> = emptySet(),

    /** Bir martalik xabar (xato yoki tasdiq) — ko'rsatilgach [messageShown] chaqiriladi. */
    val message: String? = null,
) {
    val requests: List<ConnectionRequest>
        get() = if (requestsTab == RequestsTab.INCOMING) incoming else outgoing
}

/**
 * "Do'stlar" ekrani — `Connections` bo'limining to'liq oqimi (handoff: `connections.md` §14).
 *
 * Ro'yxatlar **keshlanmaydi**: bog'lanish holati ikki tomonlama o'zgaradi, shuning uchun har
 * ochilishda va har amaldan keyin serverdan qayta o'qiladi.
 */
class ConnectionsViewModel(
    private val repository: ConnectionsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ConnectionsUiState())
    val state: StateFlow<ConnectionsUiState> = _state.asStateFlow()

    /** Qidiruv debounce'i — har harfda so'rov ketmasligi uchun (~300 ms). */
    private var searchJob: Job? = null

    init {
        refreshAll()
    }

    fun refreshAll() {
        loadConnections()
        loadRequests()
    }

    fun selectTab(tab: ConnectionsTab) {
        _state.update { it.copy(tab = tab) }
        when (tab) {
            ConnectionsTab.REQUESTS -> loadRequests()
            ConnectionsTab.CONNECTED -> loadConnections()
            ConnectionsTab.SEARCH -> Unit
        }
    }

    fun selectRequestsTab(tab: RequestsTab) {
        _state.update { it.copy(requestsTab = tab) }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    // --- Qidiruv -------------------------------------------------------------------------

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
        searchJob?.cancel()
        if (value.isBlank()) {
            _state.update { it.copy(results = emptyList(), searching = false, searched = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _state.update { it.copy(searching = true) }
            when (val res = repository.search(value)) {
                is Resource.Success -> _state.update {
                    it.copy(results = res.data.items, searching = false, searched = true)
                }
                is Resource.Error -> _state.update {
                    it.copy(searching = false, message = res.message)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun clearQuery() = onQueryChange("")

    // --- Amallar -------------------------------------------------------------------------

    /**
     * So'rov yuborish. Javobdagi `status` ni tekshiramiz: `ACCEPTED` bo'lsa avtomatik
     * bog'lanish sodir bo'lgan (C1) — foydalanuvchiga darhol "Xabar yozish" ko'rinadi.
     */
    fun connect(student: StudentSummary) = runBusy(student.id) {
        when (val res = repository.sendRequest(student.id)) {
            is Resource.Success -> {
                val connected = res.data.status == ConnectionStatus.ACCEPTED
                updateSearchStatus(
                    student.id,
                    if (connected) ConnectionView.CONNECTED else ConnectionView.PENDING_OUT,
                )
                _state.update {
                    it.copy(
                        message = if (connected) {
                            "${student.displayName} bilan bog'landingiz"
                        } else {
                            "So'rov yuborildi"
                        },
                    )
                }
                loadRequests()
                if (connected) loadConnections()
            }
            // 429 — rad etilgandan keyingi sovish muddati yoki daqiqalik limit. Server qolgan
            // vaqtni bermaydi, shuning uchun taymer YO'Q — faqat xabar.
            is Resource.Error -> _state.update { it.copy(message = res.message) }
            Resource.Loading -> Unit
        }
    }

    fun accept(request: ConnectionRequest) = runBusy(request.student.id) {
        when (val res = repository.accept(request.connectionId)) {
            is Resource.Success -> {
                _state.update { it.copy(message = "${request.student.displayName} bilan bog'landingiz") }
                updateSearchStatus(request.student.id, ConnectionView.CONNECTED)
                loadRequests()
                loadConnections()
            }
            is Resource.Error -> {
                // `404` uchala holatni bildiradi (so'rov yo'q / sizga emas / javob berilgan) —
                // ro'yxatni qayta yuklab, foydalanuvchiga haqiqiy holatni ko'rsatamiz.
                _state.update { it.copy(message = res.message) }
                loadRequests()
            }
            Resource.Loading -> Unit
        }
    }

    fun decline(request: ConnectionRequest) = runBusy(request.student.id) {
        when (val res = repository.decline(request.connectionId)) {
            is Resource.Success -> {
                _state.update { it.copy(message = "So'rov rad etildi") }
                loadRequests()
            }
            is Resource.Error -> {
                _state.update { it.copy(message = res.message) }
                loadRequests()
            }
            Resource.Loading -> Unit
        }
    }

    fun disconnect(student: StudentSummary) = runBusy(student.id) {
        when (val res = repository.disconnect(student.id)) {
            is Resource.Success -> {
                _state.update { it.copy(message = "Bog'lanish uzildi") }
                updateSearchStatus(student.id, ConnectionView.NONE)
                loadConnections()
            }
            is Resource.Error -> _state.update { it.copy(message = res.message) }
            Resource.Loading -> Unit
        }
    }

    fun block(student: StudentSummary) = runBusy(student.id) {
        when (val res = repository.block(student.id)) {
            is Resource.Success -> {
                _state.update {
                    it.copy(
                        message = "${student.displayName} bloklandi",
                        // Bloklangan odam qidiruv natijasidan ham yo'qoladi.
                        results = it.results.filterNot { r -> r.student.id == student.id },
                    )
                }
                // Blok bog'lanish/so'rovni server tomonda o'chiradi — ro'yxatlarni yangilaymiz.
                refreshAll()
            }
            is Resource.Error -> _state.update { it.copy(message = res.message) }
            Resource.Loading -> Unit
        }
    }

    fun report(student: StudentSummary, reason: ReportReason, note: String?) = runBusy(student.id) {
        when (val res = repository.report(reason, targetStudentId = student.id, note = note)) {
            // Takroriy shikoyat eskisiga birlashadi — foydalanuvchi uchun farqi yo'q.
            is Resource.Success -> _state.update { it.copy(message = "Shikoyatingiz qabul qilindi") }
            is Resource.Error -> _state.update { it.copy(message = res.message) }
            Resource.Loading -> Unit
        }
    }

    // --- Yuklash -------------------------------------------------------------------------

    private fun loadConnections() = viewModelScope.launch {
        _state.update { it.copy(loading = true) }
        when (val res = repository.connections()) {
            is Resource.Success -> _state.update { it.copy(connections = res.data.items, loading = false) }
            is Resource.Error -> _state.update { it.copy(loading = false, message = res.message) }
            Resource.Loading -> Unit
        }
    }

    private fun loadRequests() = viewModelScope.launch {
        val incoming = repository.requests(RequestDirection.INCOMING)
        val outgoing = repository.requests(RequestDirection.OUTGOING)
        _state.update { s ->
            s.copy(
                incoming = (incoming as? Resource.Success)?.data?.items ?: s.incoming,
                incomingTotal = (incoming as? Resource.Success)?.data?.total ?: s.incomingTotal,
                outgoing = (outgoing as? Resource.Success)?.data?.items ?: s.outgoing,
            )
        }
    }

    /** Qidiruv ro'yxatidagi tugmani darhol yangilaydi (server javobini kutmasdan qayta so'ramaymiz). */
    private fun updateSearchStatus(studentId: String, status: ConnectionView) {
        _state.update { s ->
            s.copy(
                results = s.results.map { r ->
                    if (r.student.id == studentId) r.copy(connectionStatus = status) else r
                },
            )
        }
    }

    /** Amal davomida tugmani o'chirib turadi — ikki marta bosish takroriy so'rov yubormasin. */
    private fun runBusy(studentId: String, block: suspend () -> Unit) {
        if (studentId in _state.value.busyIds) return
        viewModelScope.launch {
            _state.update { it.copy(busyIds = it.busyIds + studentId) }
            try {
                block()
            } finally {
                _state.update { it.copy(busyIds = it.busyIds - studentId) }
            }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
