package dev.feature.connections.presentation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.core.common.Resource
import dev.feature.connections.domain.model.ConnectionView
import dev.feature.connections.domain.model.StudentSummary
import dev.feature.connections.domain.repository.ConnectionsRepository
import dev.feature.university.domain.repository.UniversityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Boshqa talaba profilining holati (`GET /v1/students/{id}`). */
@Immutable
data class StudentProfileState(
    val student: StudentSummary? = null,
    val connection: ConnectionView = ConnectionView.NONE,
    /** Universitet **nomi** — server qisqa profilda faqat `universityId` beradi. */
    val universityName: String? = null,
    val loading: Boolean = true,
    val message: String? = null,
    /** Profil yopilsin — bog'lanish uzildi yoki odam bloklandi. */
    val closed: Boolean = false,
)

/**
 * Boshqa talabaning profili — ilovadagi **yagona** shunday varaq: chat sarlavhasidan ham,
 * story ko'ruvchisidagi muallif ustiga bosilganda ham shu ochiladi.
 *
 * Bo'limlar (Postlar / Media / Fayllar / Havolalar) bu yerda emas, chaqiruvchida quriladi
 * (`ProfileSection`) — ularning mazmuni modulga bog'liq va aks holda `connections` chat va
 * story modullariga bog'lanib qolardi.
 */
class StudentProfileViewModel(
    private val repository: ConnectionsRepository,
    private val universityRepository: UniversityRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StudentProfileState())
    val state: StateFlow<StudentProfileState> = _state.asStateFlow()

    private var loadedId: String? = null

    /**
     * Bir xil id uchun **qayta yuklanmaydi** — ekran qayta chizilganda so'rov takrorlanmasin.
     *
     * [known] — chaqiruvchida allaqachon bor qisqa profil (chat sarlavhasi, story muallifi).
     * Berilsa varaq **darhol** to'ldiriladi va so'rov fonda ketadi: aks holda ism o'rnida
     * bir lahza «Talaba» ko'rinib, keyin almashardi.
     */
    fun load(studentId: String, known: StudentSummary? = null) {
        if (loadedId == studentId) return
        loadedId = studentId
        _state.value = StudentProfileState(student = known, loading = known == null)
        viewModelScope.launch {
            when (val result = repository.student(studentId)) {
                is Resource.Success -> {
                    val student = result.data.student
                    _state.update {
                        it.copy(
                            student = student,
                            connection = result.data.connectionStatus,
                            universityName = universityName(student.universityId),
                            loading = false,
                        )
                    }
                }
                is Resource.Error -> _state.update {
                    it.copy(loading = false, message = result.message)
                }
                else -> _state.update { it.copy(loading = false) }
            }
        }
    }

    fun connect() {
        val id = loadedId ?: return
        viewModelScope.launch {
            when (val result = repository.sendRequest(id)) {
                is Resource.Error -> _state.update { it.copy(message = result.message) }
                // Tugma darhol «Yuborildi» ga o'tadi — server javobini kutib turish
                // foydalanuvchiga «bosilmadi» bo'lib ko'rinardi.
                else -> _state.update { it.copy(connection = ConnectionView.PENDING_OUT) }
            }
        }
    }

    fun disconnect() {
        val id = loadedId ?: return
        viewModelScope.launch {
            when (val result = repository.disconnect(id)) {
                is Resource.Error -> _state.update { it.copy(message = result.message) }
                // Bog'lanish uzilgach story ham, profil ham ko'rinmasligi kerak.
                else -> _state.update { it.copy(closed = true) }
            }
        }
    }

    fun block() {
        val id = loadedId ?: return
        viewModelScope.launch {
            when (val result = repository.block(id)) {
                is Resource.Error -> _state.update { it.copy(message = result.message) }
                else -> _state.update { it.copy(closed = true) }
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    /**
     * Universitet nomi local katalogdan olinadi (`ConnectionsScreen` dagi bilan bir xil
     * yo'l): server qisqa profilda faqat `universityId` ni qaytaradi.
     */
    private suspend fun universityName(universityId: String?): String? {
        if (universityId == null) return null
        return runCatching {
            universityRepository.observeUniversities().first().firstOrNull { it.id == universityId }?.shortName
        }.getOrNull()
    }
}
