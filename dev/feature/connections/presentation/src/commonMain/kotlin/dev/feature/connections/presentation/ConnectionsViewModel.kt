package dev.feature.connections.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.core.common.Resource
import dev.feature.connections.domain.model.ConnectedStudent
import dev.feature.connections.domain.model.ConnectionRequest
import dev.feature.connections.domain.model.ConnectionStatus
import dev.feature.connections.domain.model.ConnectionView
import dev.feature.connections.domain.model.Gender
import dev.feature.connections.domain.model.ReportReason
import dev.feature.connections.domain.model.RequestDirection
import dev.feature.connections.domain.model.SearchedStudent
import dev.feature.connections.domain.model.StudentFilter
import dev.feature.connections.domain.model.StudentSort
import dev.feature.connections.domain.model.StudentSummary
import dev.feature.connections.domain.repository.ConnectionsRepository
import dev.feature.profile.domain.usecase.ObserveProfileUseCase
import dev.feature.university.domain.repository.UniversityRepository
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

    /** Qidiruv matni + barcha filtrlar bitta joyda — so'rov aynan shundan quriladi. */
    val filter: StudentFilter = StudentFilter(),
    val searching: Boolean = false,
    val results: List<SearchedStudent> = emptyList(),
    /** So'rov bajarildi-yu, natija bo'sh — "hech kim topilmadi" ni faqat shunda ko'rsatamiz. */
    val searched: Boolean = false,

    /** Profildagi universitet — "Universitetim" filtri shu id bo'yicha ishlaydi. */
    val myUniversityId: String? = null,
    /**
     * `universityId` → monogramma (local katalog). Backend qisqa profilda universitet
     * **nomini** qaytarmaydi (katalogi yo'q), faqat id'ni — nomni o'zimiz topamiz.
     */
    val universityMonograms: Map<String, String> = emptyMap(),

    val incoming: List<ConnectionRequest> = emptyList(),
    val outgoing: List<ConnectionRequest> = emptyList(),
    /** Kiruvchi so'rovlar soni — tab yonidagi badge. */
    val incomingTotal: Int = 0,

    val connections: List<ConnectedStudent> = emptyList(),
    val loading: Boolean = false,
    /** "Tepadan tortib yangilash" ketyapti — ro'yxat joyida, tepada aylanma indikator. */
    val refreshing: Boolean = false,

    /** Amal bajarilayotgan talabalar (tugma o'chirilgan holatda turadi). */
    val busyIds: Set<String> = emptySet(),

    /** Bir martalik xabar (xato yoki tasdiq) — ko'rsatilgach [messageShown] chaqiriladi. */
    val message: String? = null,
) {
    val requests: List<ConnectionRequest>
        get() = if (requestsTab == RequestsTab.INCOMING) incoming else outgoing

    val query: String get() = filter.query.orEmpty()

    /** Matndan tashqari birorta filtr yoqilganmi — "Tozalash" tugmasi shunda ko'rinadi. */
    val hasActiveFilters: Boolean
        get() = filter.copy(query = null).isEmpty.not() || filter.sort != StudentSort.RECENT

    /** Talaba qatorida ko'rsatiladigan universitet monogrammasi (katalogda bo'lmasa `null`). */
    fun universityLabel(student: StudentSummary): String? =
        student.universityId?.let { universityMonograms[it] }
}

/**
 * "Do'stlar" ekrani — `Connections` bo'limining to'liq oqimi (handoff: `connections.md` §14).
 *
 * Ro'yxatlar **keshlanmaydi**: bog'lanish holati ikki tomonlama o'zgaradi, shuning uchun har
 * ochilishda va har amaldan keyin serverdan qayta o'qiladi.
 */
class ConnectionsViewModel(
    private val repository: ConnectionsRepository,
    observeProfile: ObserveProfileUseCase,
    universityRepository: UniversityRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ConnectionsUiState())
    val state: StateFlow<ConnectionsUiState> = _state.asStateFlow()

    /** Qidiruv debounce'i — har harfda so'rov ketmasligi uchun (~300 ms). */
    private var searchJob: Job? = null

    init {
        refreshAll()
        // `GET /v1/students` filtrsiz ham ishlaydi, shuning uchun ro'yxat darrov to'ladi —
        // avvalgidek "avval biror narsa yozing" holatida turmaydi.
        loadStudents(debounce = false)

        viewModelScope.launch {
            observeProfile().collect { profile ->
                _state.update { it.copy(myUniversityId = profile?.universityId) }
            }
        }
        viewModelScope.launch {
            universityRepository.observeUniversities().collect { list ->
                _state.update { s -> s.copy(universityMonograms = list.associate { it.id to it.monogram }) }
            }
        }
    }

    fun refreshAll() {
        loadConnections()
        loadRequests()
    }

    /**
     * "Tepadan tortib yangilash" — ochiq bo'lim serverdan qayta o'qiladi.
     *
     * Barcha uch ro'yxat birdan yangilanadi, chunki ular bir-biriga bog'liq: so'rov qabul
     * qilinsa u so'rovlardan chiqib bog'langanlarga o'tadi va qidiruvdagi tugmasi ham
     * o'zgaradi. Faqat ochiq bo'limni yangilash boshqasini eskirgan holda qoldirardi.
     */
    fun refreshCurrentTab() {
        if (_state.value.refreshing) return
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            try {
                loadConnections().join()
                loadRequests().join()
                reloadStudents()
            } finally {
                _state.update { it.copy(refreshing = false) }
            }
        }
    }

    fun selectTab(tab: ConnectionsTab) {
        _state.update { it.copy(tab = tab) }
        when (tab) {
            ConnectionsTab.REQUESTS -> loadRequests()
            ConnectionsTab.CONNECTED -> loadConnections()
            // Ro'yxat init'da yuklanadi; bo'sh bo'lsa (masalan birinchi so'rov xato bergan)
            // qayta urinamiz.
            ConnectionsTab.SEARCH -> if (_state.value.results.isEmpty()) loadStudents(debounce = false)
        }
    }

    fun selectRequestsTab(tab: RequestsTab) {
        _state.update { it.copy(requestsTab = tab) }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    // --- Qidiruv va filtrlar -------------------------------------------------------------

    fun onQueryChange(value: String) =
        applyFilter(debounce = true) { it.copy(query = value.takeIf { v -> v.isNotBlank() }) }

    fun clearQuery() = onQueryChange("")

    /**
     * "Universitetim" — profildagi `universityId` bo'yicha filtr. Profil to'ldirilmagan
     * bo'lsa tugma umuman ko'rsatilmaydi (ekran [ConnectionsUiState.myUniversityId] ni
     * tekshiradi), shuning uchun bu yerda qo'shimcha shart yo'q.
     */
    fun toggleMyUniversity() {
        val mine = _state.value.myUniversityId ?: return
        applyFilter {
            it.copy(universityIds = if (mine in it.universityIds) emptyList() else listOf(mine))
        }
    }

    fun toggleGender(gender: Gender) = applyFilter {
        it.copy(genders = it.genders.toggle(gender))
    }

    fun toggleCourseYear(year: String) = applyFilter {
        it.copy(courseYears = it.courseYears.toggle(year))
    }

    /** "Yangi odamlar" — hali munosabat yo'q talabalar (`connectionStatus = NONE`). */
    fun toggleOnlyNew() = applyFilter {
        it.copy(connectionStatus = if (it.connectionStatus == ConnectionView.NONE) null else ConnectionView.NONE)
    }

    fun setSort(sort: StudentSort) = applyFilter { it.copy(sort = sort) }

    /** Matnga tegmaydi — foydalanuvchi yozgan so'rov saqlanib qoladi. */
    fun clearFilters() = applyFilter {
        StudentFilter(query = it.query)
    }

    private fun applyFilter(debounce: Boolean = false, change: (StudentFilter) -> StudentFilter) {
        _state.update { it.copy(filter = change(it.filter)) }
        loadStudents(debounce)
    }

    /**
     * Ro'yxatni qayta tortadi. [debounce] — matn yozilayotganda: server IP bo'yicha
     * daqiqasiga 30 so'rovni cheklaydi, har harfga so'rov yuborsak `429` ga tushamiz.
     */
    private fun loadStudents(debounce: Boolean) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MS)
            reloadStudents()
        }
    }

    /**
     * Talabalar ro'yxatini serverdan bir marta o'qiydi.
     *
     * `suspend`: chaqiruvchi uning tugashini KUTA oladi — "tepadan tortish" indikatori
     * hamma so'rov qaytgunicha aylanishi kerak.
     */
    private suspend fun reloadStudents() {
        _state.update { it.copy(searching = true) }
        when (val res = repository.students(_state.value.filter)) {
            is Resource.Success -> _state.update {
                it.copy(results = res.data.items, searching = false, searched = true)
            }
            is Resource.Error -> _state.update {
                it.copy(searching = false, searched = true, message = res.message)
            }
            Resource.Loading -> Unit
        }
    }

    private fun <T> List<T>.toggle(value: T): List<T> =
        if (value in this) this - value else this + value

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
                if (connected) {
                    loadConnections()
                    // Darhol bog'lanish sodir bo'ldi — qidiruv ro'yxatidagi holat ham
                    // serverdagisi bilan tenglashtiriladi.
                    reloadStudents()
                }
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
                // ⚠️ Qidiruv ro'yxati ham SERVERDAN qayta o'qiladi, faqat local
                // `updateSearchStatus` bilan cheklanmasdan: qabul qilingandan keyin
                // serverdagi `connectionStatus` o'zgaradi va "Yangi odamlar" filtri
                // yoqilgan bo'lsa bu odam ro'yxatdan butunlay chiqib ketishi kerak.
                // Ilgari u eski tugmasi bilan osilib qolardi ("update ishlamayapti").
                reloadStudents()
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
