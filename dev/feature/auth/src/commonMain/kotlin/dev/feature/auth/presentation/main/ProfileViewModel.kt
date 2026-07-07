package dev.feature.auth.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.core.domain.model.Ad
import dev.core.domain.model.DiscountOffer
import dev.core.domain.model.JobApplication
import dev.core.domain.repository.AdRepository
import dev.core.domain.repository.DiscountRepository
import dev.core.domain.repository.JobRepository
import dev.core.domain.repository.UniversityRepository
import dev.core.domain.usecase.LogoutUseCase
import dev.core.domain.usecase.ObserveCurrentUserUseCase
import dev.core.domain.usecase.ObserveProfileUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Profil (1z) holati. */
data class ProfileUiState(
    val name: String = "Talaba",
    val universityMonogram: String? = null,
    val courseLabel: String? = null,
    val contact: String = "",
    val myAds: List<Ad> = emptyList(),
    val savedDiscounts: List<DiscountOffer> = emptyList(),
    val applications: List<JobApplication> = emptyList(),
)

class ProfileViewModel(
    observeCurrentUserUseCase: ObserveCurrentUserUseCase,
    observeProfileUseCase: ObserveProfileUseCase,
    universityRepository: UniversityRepository,
    adRepository: AdRepository,
    discountRepository: DiscountRepository,
    jobRepository: JobRepository,
    private val logoutUseCase: LogoutUseCase,
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
            course = profile?.courseYear?.let(::courseLabelProfile),
            contact = user?.phoneNumber ?: user?.email.orEmpty(),
            ownerId = (user?.id ?: 0L).toString(),
        )
    }

    private val lists = combine(
        adRepository.observeAds(),
        discountRepository.observeSaved(),
        jobRepository.observeApplications(),
    ) { ads, saved, applications -> Lists(ads, saved, applications) }

    val state: StateFlow<ProfileUiState> = combine(header, lists) { h, l ->
        ProfileUiState(
            name = h.name,
            universityMonogram = h.monogram,
            courseLabel = h.course,
            contact = h.contact,
            myAds = l.ads.filter { it.ownerId == h.ownerId },
            savedDiscounts = l.saved,
            applications = l.applications,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileUiState())

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            logoutUseCase()
            onDone()
        }
    }

    private data class Header(val name: String, val monogram: String?, val course: String?, val contact: String, val ownerId: String)
    private data class Lists(val ads: List<Ad>, val saved: List<DiscountOffer>, val applications: List<JobApplication>)
}

private fun courseLabelProfile(courseYear: String): String = when (courseYear) {
    "ONE" -> "1-kurs"
    "TWO" -> "2-kurs"
    "THREE" -> "3-kurs"
    "FOUR" -> "4-kurs"
    "MASTER" -> "Magistr"
    else -> courseYear
}
