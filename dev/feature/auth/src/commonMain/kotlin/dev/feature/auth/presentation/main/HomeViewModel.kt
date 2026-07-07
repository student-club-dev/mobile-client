package dev.feature.auth.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.core.domain.model.DiscountCategory
import dev.core.domain.model.DiscountOffer
import dev.core.domain.model.FriendStatus
import dev.core.domain.model.Job
import dev.core.domain.model.Student
import dev.core.domain.repository.DiscountRepository
import dev.core.domain.repository.JobRepository
import dev.core.domain.repository.StudentRepository
import dev.core.domain.repository.UniversityRepository
import dev.core.domain.usecase.ObserveCurrentUserUseCase
import dev.core.domain.usecase.ObserveProfileUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Home (1p) ekranining holati — barchasi local DB'dan reaktiv. */
data class HomeUiState(
    val userName: String = "Talaba",
    val universityMonogram: String? = null,
    val courseLabel: String? = null,
    val categories: List<DiscountCategory> = emptyList(),
    val featured: DiscountOffer? = null,
    val jobs: List<Job> = emptyList(),
    val students: List<Student> = emptyList(),
)

class HomeViewModel(
    observeCurrentUserUseCase: ObserveCurrentUserUseCase,
    observeProfileUseCase: ObserveProfileUseCase,
    universityRepository: UniversityRepository,
    private val discountRepository: DiscountRepository,
    private val jobRepository: JobRepository,
    private val studentRepository: StudentRepository,
) : ViewModel() {

    private val header = combine(
        observeCurrentUserUseCase(),
        observeProfileUseCase(),
        universityRepository.observeUniversities(),
    ) { user, profile, universities ->
        val uni = universities.firstOrNull { it.id == profile?.universityId }
        Header(
            name = user?.fullName?.takeIf { it.isNotBlank() } ?: "Talaba",
            monogram = uni?.monogram,
            course = profile?.courseYear?.let(::courseLabel),
        )
    }

    private val content = combine(
        discountRepository.observeCategories(),
        discountRepository.observeFeatured(),
        jobRepository.observeJobs(),
        studentRepository.observeStudents(),
    ) { categories, featured, jobs, students ->
        Content(categories, featured.firstOrNull(), jobs, students)
    }

    val state: StateFlow<HomeUiState> = combine(header, content) { h, c ->
        HomeUiState(
            userName = h.name,
            universityMonogram = h.monogram,
            courseLabel = h.course,
            categories = c.categories,
            featured = c.featured,
            jobs = c.jobs,
            students = c.students,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    /** Student kartasidagi "+Do'st" ↔ "Kutilmoqda" o'zgartirish. */
    fun toggleFriend(student: Student) {
        val next = if (student.friendStatus == FriendStatus.NONE) FriendStatus.PENDING else FriendStatus.NONE
        viewModelScope.launch { studentRepository.setFriendStatus(student.id, next) }
    }

    /** Ish kartasidagi bookmark. */
    fun toggleBookmark(job: Job) {
        viewModelScope.launch { jobRepository.setBookmarked(job.id, !job.bookmarked) }
    }

    private data class Header(val name: String, val monogram: String?, val course: String?)
    private data class Content(
        val categories: List<DiscountCategory>,
        val featured: DiscountOffer?,
        val jobs: List<Job>,
        val students: List<Student>,
    )
}

private fun courseLabel(courseYear: String): String = when (courseYear) {
    "ONE" -> "1-kurs"
    "TWO" -> "2-kurs"
    "THREE" -> "3-kurs"
    "FOUR" -> "4-kurs"
    "MASTER" -> "Magistr"
    else -> courseYear
}
