package dev.feature.university.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.core.common.Resource
import dev.core.domain.model.DiscountOffer
import dev.feature.listings.domain.model.Listing
import dev.feature.listings.domain.model.ListingKind
import dev.feature.listings.domain.usecase.ObserveListingsByKindUseCase
import dev.feature.listings.domain.usecase.RefreshListingsUseCase
import dev.feature.connections.domain.model.ConnectionStatus
import dev.feature.connections.domain.model.ConnectionView
import dev.feature.connections.domain.model.SearchedStudent
import dev.feature.connections.domain.model.StudentFilter
import dev.feature.connections.domain.repository.ConnectionsRepository
import dev.feature.university.domain.model.University
import dev.core.domain.repository.DiscountRepository
import dev.feature.university.domain.repository.UniversityRepository
import dev.feature.profile.domain.usecase.ObserveProfileUseCase
import dev.feature.profile.domain.usecase.SaveProfileUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** "Mening universitetim" ekrani holati. */
data class MyUniversityUiState(
    val university: University? = null,       // profildagi universitetim (tanlanmagan bo'lsa null)
    /**
     * Shu universitet talabalari — `GET /v1/students?universityId=<profil>`
     * ([ConnectionsRepository]). Local kesh yo'q: ro'yxat serverdan keladi, shuning uchun
     * bo'sh ro'yxat "bu universitetda hali boshqa talaba yo'q" degani.
     */
    val mates: List<SearchedStudent> = emptyList(),
    /**
     * Shu universitetga bog'langan topshiriq e'lonlari ("Fanlardan yordam") —
     * `Listing.universityId == university.id` (`STUDENT_LISTINGS_BACKEND.md` §7.2.4).
     * Boshqa OTM e'lonlari bu yerga tushmaydi: ular "E'lonlar" ekranida.
     */
    val tasks: List<Listing> = emptyList(),
    // "Siz uchun" listingidagi turlar (ma'lumot ElonUz'dan) — universitet atrofidagi joylar.
    val printShops: List<DiscountOffer> = emptyList(),   // "printerxona"
    val foods: List<DiscountOffer> = emptyList(),        // "ovqat"
    val loading: Boolean = true,
)

/** Universitet tanlash BottomSheet holati (prof-emis.edu.uz ro'yxati). */
data class UniversityPickerState(
    val loading: Boolean = false,
    val error: String? = null,
    val query: String = "",
    val results: List<University> = emptyList(),
)

class MyUniversityViewModel(
    private val observeProfileUseCase: ObserveProfileUseCase,
    private val saveProfileUseCase: SaveProfileUseCase,
    private val universityRepository: UniversityRepository,
    private val connectionsRepository: ConnectionsRepository,
    discountRepository: DiscountRepository,
    observeListingsByKind: ObserveListingsByKindUseCase,
    private val refreshListings: RefreshListingsUseCase,
) : ViewModel() {

    /**
     * Talabalar keshi. [ConnectionsRepository] — `suspend`, oqim bermaydi, shuning uchun
     * javob shu oqimga qo'yiladi va [state] uni boshqa manbalar bilan qo'shadi.
     */
    private val _mates = MutableStateFlow<List<SearchedStudent>>(emptyList())

    init {
        viewModelScope.launch { universityRepository.refresh() }
        // "Fanlardan yordam" bo'limi keshdan o'qiladi — uni serverdagi e'lonlar bilan
        // to'ldiramiz, aks holda ro'yxat faqat eskirgan keshni ko'rsatib turardi.
        viewModelScope.launch { refreshListings(ListingKind.TASK) }
        // Profildagi universitet o'zgarsa (yoki birinchi marta tanlansa) ro'yxat qayta olinadi.
        viewModelScope.launch {
            observeProfileUseCase()
                .map { it?.universityId }
                .distinctUntilChanged()
                .collect { loadMates(it) }
        }
    }

    val state: StateFlow<MyUniversityUiState> = combine(
        observeProfileUseCase(),
        universityRepository.observeUniversities(),
        _mates,
        discountRepository.observeAllOffers(),
        observeListingsByKind(ListingKind.TASK),
    ) { profile, universities, mates, offers, taskListings ->
        val uni = universities.firstOrNull { it.id == profile?.universityId }
        // Katalogdagi printerxona turi — kalit backenddan keladi (`PRINTING`); ovqat esa
        // butun katalog guruhi bo'yicha (`FOOD` — fast-food, milliy taomlar, somsa).
        val printShops = offers.filter { it.categoryId == "PRINTING" }
        val foods = offers.filter { it.groupKey == "FOOD" }
        // Fanlardan yordam — FAQAT shu universitetga bog'langan topshiriqlar. Universitet
        // tanlanmagan bo'lsa ro'yxat bo'sh: "mening universitetim" degan tushuncha yo'q.
        val tasks = uni?.let { u -> taskListings.filter { it.universityId == u.id } }.orEmpty()
        MyUniversityUiState(
            university = uni,
            mates = mates,
            tasks = tasks,
            printShops = printShops,
            foods = foods,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MyUniversityUiState())

    // --- Universitet tanlash (BottomSheet) ---
    private val allUniversities = MutableStateFlow<List<University>>(emptyList())
    private val _picker = MutableStateFlow(UniversityPickerState())
    val picker: StateFlow<UniversityPickerState> = _picker

    /** Sheet ochilganda chaqiriladi — ro'yxat allaqachon yuklangan bo'lsa qayta olmaydi. */
    fun loadUniversities() {
        if (allUniversities.value.isNotEmpty() || _picker.value.loading) return
        _picker.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val res = universityRepository.fetchSelectableUniversities()) {
                is Resource.Success -> {
                    allUniversities.value = res.data
                    _picker.update { it.copy(loading = false, results = filter(res.data, it.query)) }
                }
                is Resource.Error -> _picker.update { it.copy(loading = false, error = res.message) }
                Resource.Loading -> Unit
            }
        }
    }

    fun onUniversityQuery(q: String) {
        _picker.update { it.copy(query = q, results = filter(allUniversities.value, q)) }
    }

    // Qidiruv qisqartmani ham qamraydi (`University.matches`): ro'yxatda "TATU" deb
    // yozgan foydalanuvchi rasmiy nomni ("Muhammad al-Xorazmiy nomidagi…") bilishi shart emas.
    private fun filter(list: List<University>, q: String): List<University> =
        if (q.isBlank()) list.take(200) else list.filter { it.matches(q) }.take(200)

    /** Universitetni tanlash — local DB'ga qo'shadi va profilga bog'laydi. */
    fun selectUniversity(uni: University) {
        viewModelScope.launch {
            universityRepository.addUniversity(uni)
            val profile = observeProfileUseCase().first()
            if (profile != null) saveProfileUseCase(profile.copy(universityId = uni.id))
        }
    }

    /**
     * "Universitetimdagi talabalar" — `GET /v1/students?universityId=…`.
     *
     * `universityId` profildagi **erkin satr** (`emis-142`): serverda universitetlar
     * katalogi yo'q va filtr shu satrni aynan solishtiradi. Universitet tanlanmagan bo'lsa
     * so'rov umuman yuborilmaydi — "mening universitetim" degan tushuncha yo'q.
     */
    private suspend fun loadMates(universityId: String?) {
        if (universityId.isNullOrBlank()) {
            _mates.value = emptyList()
            return
        }
        val res = connectionsRepository.students(
            filter = StudentFilter(universityIds = listOf(universityId)),
            size = MATES_PAGE_SIZE,
        )
        if (res is Resource.Success) _mates.value = res.data.items
    }

    /**
     * Talaba kartasidagi «Bog'lanish» — `POST /v1/connections/requests`.
     *
     * Javobdagi `status = ACCEPTED` — u odam sizga allaqachon so'rov yuborgan ekan, ya'ni
     * bog'lanish darhol sodir bo'ldi. Bog'lanishni bekor qilish bu ekranda yo'q: to'liq
     * oqim (rad etish, blok, shikoyat) "Do'stlar" ekranida.
     */
    fun connect(student: SearchedStudent) {
        if (student.connectionStatus != ConnectionView.NONE) return
        viewModelScope.launch {
            val res = connectionsRepository.sendRequest(student.student.id)
            if (res !is Resource.Success) return@launch
            val status = if (res.data.status == ConnectionStatus.ACCEPTED) {
                ConnectionView.CONNECTED
            } else {
                ConnectionView.PENDING_OUT
            }
            _mates.update { list ->
                list.map { if (it.student.id == student.student.id) it.copy(connectionStatus = status) else it }
            }
        }
    }

    private companion object {
        /** Bir sahifada nechta talaba — ekranda gorizontal ro'yxat va "Barchasi" oynasi. */
        const val MATES_PAGE_SIZE = 30
    }
}
