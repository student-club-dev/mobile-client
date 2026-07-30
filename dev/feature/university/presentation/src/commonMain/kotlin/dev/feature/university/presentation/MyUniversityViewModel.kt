package dev.feature.university.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.core.common.Resource
import dev.core.domain.model.DiscountOffer
import dev.feature.students.domain.model.FriendStatus
import dev.feature.students.domain.model.Student
import dev.feature.university.domain.model.University
import dev.core.domain.repository.DiscountRepository
import dev.feature.students.domain.repository.StudentRepository
import dev.feature.university.domain.repository.UniversityRepository
import dev.feature.profile.domain.usecase.ObserveProfileUseCase
import dev.feature.profile.domain.usecase.SaveProfileUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** "Mening universitetim" ekrani holati. */
data class MyUniversityUiState(
    val university: University? = null,       // profildagi universitetim (tanlanmagan bo'lsa null)
    val mates: List<Student> = emptyList(),   // shu universitet talabalari
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
    private val studentRepository: StudentRepository,
    discountRepository: DiscountRepository,
) : ViewModel() {

    init {
        viewModelScope.launch { universityRepository.refresh() }
        viewModelScope.launch { studentRepository.refresh() }
    }

    val state: StateFlow<MyUniversityUiState> = combine(
        observeProfileUseCase(),
        universityRepository.observeUniversities(),
        studentRepository.observeStudents(),
        discountRepository.observeAllOffers(),
    ) { profile, universities, students, offers ->
        val uni = universities.firstOrNull { it.id == profile?.universityId }
        // Aynan shu universitet talabalari; topilmasa (prof-emis id local seed'ga mos kelmaydi)
        // do'stlashish uchun barcha talabalarni ko'rsatamiz.
        val mates = if (uni != null) {
            students.filter { it.universityId == uni.id }.ifEmpty { students }
        } else emptyList()
        // Backend kaliti (`PRINTING`) va local seed kaliti (`printerxona`) — ikkalasi ham;
        // ovqat esa butun katalog guruhi bo'yicha (`FOOD` — fast-food, milliy taomlar, somsa).
        val printShops = offers.filter { it.categoryId == "PRINTING" || it.categoryId == "printerxona" }
        val foods = offers.filter { it.groupKey == "FOOD" }
        MyUniversityUiState(university = uni, mates = mates, printShops = printShops, foods = foods, loading = false)
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

    private fun filter(list: List<University>, q: String): List<University> =
        if (q.isBlank()) list.take(200)
        else list.filter { it.name.contains(q, ignoreCase = true) || it.city.contains(q, ignoreCase = true) }.take(200)

    /** Universitetni tanlash — local DB'ga qo'shadi va profilga bog'laydi. */
    fun selectUniversity(uni: University) {
        viewModelScope.launch {
            universityRepository.addUniversity(uni)
            val profile = observeProfileUseCase().first()
            if (profile != null) saveProfileUseCase(profile.copy(universityId = uni.id))
        }
    }

    /** "+Do'st" ↔ "Kutilmoqda". */
    fun toggleFriend(student: Student) {
        val next = if (student.friendStatus == FriendStatus.NONE) FriendStatus.PENDING else FriendStatus.NONE
        viewModelScope.launch { studentRepository.setFriendStatus(student.id, next) }
    }
}
