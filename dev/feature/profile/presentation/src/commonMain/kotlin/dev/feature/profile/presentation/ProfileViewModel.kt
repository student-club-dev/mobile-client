package dev.feature.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.core.common.Resource
import dev.feature.ads.domain.model.Ad
import dev.core.domain.model.DiscountOffer
import dev.feature.jobs.domain.model.JobApplication
import dev.feature.university.domain.model.University
import dev.feature.ads.domain.repository.AdRepository
import dev.core.domain.repository.DiscountRepository
import dev.feature.jobs.domain.repository.JobRepository
import dev.feature.university.domain.repository.UniversityRepository
import dev.core.domain.usecase.LogoutUseCase
import dev.core.domain.usecase.ObserveCurrentUserUseCase
import dev.feature.profile.domain.model.ProfilePhoto
import dev.feature.profile.domain.model.UserProfile
import dev.feature.profile.domain.usecase.AddProfilePhotoUseCase
import dev.feature.profile.domain.usecase.DeleteProfilePhotoUseCase
import dev.feature.profile.domain.usecase.ObserveProfileUseCase
import dev.feature.profile.domain.usecase.ProfilePhotosUseCase
import dev.feature.profile.domain.usecase.RefreshProfileUseCase
import dev.feature.profile.domain.usecase.SaveProfileUseCase
import dev.feature.profile.domain.usecase.SetMainProfilePhotoUseCase
import dev.feature.profile.domain.usecase.UploadAvatarUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Profil (1z) ekranining holati. */
data class ProfileUiState(
    val name: String = "Talaba",
    val universityMonogram: String? = null,
    val courseLabel: String? = null,
    val contact: String = "",
    val myAds: List<Ad> = emptyList(),
    val savedDiscounts: List<DiscountOffer> = emptyList(),
    val applications: List<JobApplication> = emptyList(),
    /** Tahrirlash ekrani uchun xom profil ma'lumoti (local keshdan). */
    val profile: UserProfile? = null,
    /** Universitet tanlash uchun ro'yxat. */
    val universities: List<University> = emptyList(),
)

/**
 * Profil rasmlari to'plamining holati (`handoff/08-PROFILE.md` §2).
 *
 * Profilning o'zidan **alohida**: rasmlar local keshda saqlanmaydi (ular faqat tahrirlash
 * ekranida kerak), profil esa offline-first oqimda yashaydi.
 */
data class ProfilePhotosState(
    val items: List<ProfilePhoto> = emptyList(),
    val loading: Boolean = false,
    /** Yuklanayotgan rasm bormi — qo'shish tugmasi shu vaqt o'chiriladi. */
    val uploading: Boolean = false,
    /**
     * Yuklash foizi (`0f..1f`). Fayl ketib bo'lgach `null`: server rasmni qabul qilib,
     * profilni yangilashi qancha davom etishini oldindan bilib bo'lmaydi.
     */
    val progress: Float? = null,
    val error: String? = null,
) {
    /** Yana rasm qo'shish mumkinmi (server chegarasi — 6 ta). */
    val canAdd: Boolean get() = items.size < ProfilePhoto.MAX_PHOTOS && !uploading
}

class ProfileViewModel(
    observeCurrentUserUseCase: ObserveCurrentUserUseCase,
    observeProfileUseCase: ObserveProfileUseCase,
    universityRepository: UniversityRepository,
    private val adRepository: AdRepository,
    discountRepository: DiscountRepository,
    jobRepository: JobRepository,
    private val logoutUseCase: LogoutUseCase,
    private val saveProfileUseCase: SaveProfileUseCase,
    private val refreshProfileUseCase: RefreshProfileUseCase,
    private val uploadAvatarUseCase: UploadAvatarUseCase,
    private val profilePhotosUseCase: ProfilePhotosUseCase,
    private val addProfilePhotoUseCase: AddProfilePhotoUseCase,
    private val setMainProfilePhotoUseCase: SetMainProfilePhotoUseCase,
    private val deleteProfilePhotoUseCase: DeleteProfilePhotoUseCase,
) : ViewModel() {

    private val _photos = MutableStateFlow(ProfilePhotosState())

    /** Profil rasmlari — tahrirlash ekrani uchun. */
    val photos: StateFlow<ProfilePhotosState> = _photos.asStateFlow()

    init {
        // Offline-first: ekran ochilishi bilan fon rejimida masofaviy manbadan
        // yangilaymiz. Xato bo'lsa keshdagi ma'lumot ko'rinaveradi.
        viewModelScope.launch { refreshProfileUseCase() }
        viewModelScope.launch { adRepository.refresh() }
    }

    private val header = combine(
        observeCurrentUserUseCase(),
        observeProfileUseCase(),
        universityRepository.observeUniversities(),
    ) { user, profile, universities ->
        val uni = universities.firstOrNull { it.id == profile?.universityId }
        Header(
            // Ism profildan olinadi; profil hali to'ldirilmagan bo'lsa — sessiya nomidan.
            name = profile?.displayName
                ?: user?.fullName?.takeIf { it.isNotBlank() }
                ?: "Talaba",
            monogram = uni?.monogram,
            course = profile?.courseYear?.let(::courseLabel),
            contact = user?.phoneNumber ?: user?.email.orEmpty(),
            ownerId = (user?.id ?: 0L).toString(),
            profile = profile,
            universities = universities,
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
            profile = h.profile,
            universities = h.universities,
        )
    }
        .catch { emit(ProfileUiState()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileUiState())

    /** E'lonni o'chirish ("Mening e'lonlarim"). */
    fun deleteAd(adId: String) {
        viewModelScope.launch { adRepository.delete(adId) }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            logoutUseCase()
            onDone()
        }
    }

    /**
     * Profilni saqlaydi (masofaviy manba + local kesh). [onResult] `null` — muvaffaqiyat,
     * aks holda xato matni. Kesh yangilangach `state` avtomatik yangilanadi.
     */
    fun saveProfile(updated: UserProfile, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            when (val res = saveProfileUseCase(updated)) {
                is Resource.Success -> onResult(null)
                is Resource.Error -> onResult(res.message)
                else -> onResult(null)
            }
        }
    }

    // --- Profil rasmlari ------------------------------------------------------------------

    /** Ro'yxatni yuklaydi (tahrirlash ekrani ochilganda). */
    fun loadPhotos() {
        if (_photos.value.loading) return
        _photos.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val res = profilePhotosUseCase()) {
                is Resource.Success -> _photos.update { it.copy(items = res.data, loading = false) }
                is Resource.Error -> _photos.update { it.copy(loading = false, error = res.message) }
                else -> _photos.update { it.copy(loading = false) }
            }
        }
    }

    /**
     * Rasm qo'shadi — u **birinchi o'ringa** tushadi va avatar bo'ladi.
     *
     * Javobdagi bitta rasmni ro'yxat boshiga qo'yish o'rniga butun ro'yxat qayta
     * o'qiladi: server tartibni o'zi belgilaydi va uni klientda taxmin qilish
     * (ayniqsa chegaraga yetganda) xatoga olib kelardi.
     */
    fun addPhoto(bytes: ByteArray, fileName: String) {
        if (!_photos.value.canAdd) return
        _photos.update { it.copy(uploading = true, progress = 0f, error = null) }
        viewModelScope.launch {
            val result = addProfilePhotoUseCase(bytes, fileName) { fraction ->
                _photos.update { current ->
                    when {
                        // Fayl to'liq ketdi — server qabul qilmoqda, foiz o'rniga aylanma halqa.
                        fraction >= UPLOAD_DONE -> current.copy(progress = null)
                        // Ktor har bufer bo'shaganda xabar beradi — 1% dan kichik
                        // o'zgarishda ekran qayta chizilmaydi.
                        fraction - (current.progress ?: 0f) < PROGRESS_STEP -> current
                        else -> current.copy(progress = fraction)
                    }
                }
            }
            when (result) {
                is Resource.Success -> {
                    _photos.update { it.copy(uploading = false, progress = null) }
                    loadPhotos()
                }
                is Resource.Error -> _photos.update {
                    it.copy(uploading = false, progress = null, error = result.message)
                }
                else -> _photos.update { it.copy(uploading = false, progress = null) }
            }
        }
    }

    fun setMainPhoto(photoId: String) {
        viewModelScope.launch {
            when (val res = setMainProfilePhotoUseCase(photoId)) {
                is Resource.Error -> _photos.update { it.copy(error = res.message) }
                else -> loadPhotos()
            }
        }
    }

    fun deletePhoto(photoId: String) {
        viewModelScope.launch {
            when (val res = deleteProfilePhotoUseCase(photoId)) {
                is Resource.Error -> _photos.update { it.copy(error = res.message) }
                else -> loadPhotos()
            }
        }
    }

    /**
     * Telefon raqamini kim ko'radi: `EVERYONE` | `CONNECTIONS` | `NOBODY`.
     *
     * `lastSeenVisibility` dan **mustaqil** sozlama; sukut — `NOBODY`
     * (`handoff/08-PROFILE.md` §4).
     */
    fun setPhoneVisibility(value: String) {
        val current = state.value.profile ?: return
        if (current.phoneVisibility == value) return
        viewModelScope.launch { saveProfileUseCase(current.copy(phoneVisibility = value)) }
    }

    /**
     * "Oxirgi ko'rilgan" maxfiyligi: `EVERYONE` | `CONNECTIONS` | `NOBODY`.
     *
     * Profil hali yuklanmagan bo'lsa (kesh bo'sh) bo'sh profilga yozib yuborardik va
     * ism/universitetni o'chirib yuborish xavfi bor edi — shuning uchun `null` da hech
     * nima qilinmaydi.
     */
    fun setLastSeenVisibility(value: String) {
        val current = state.value.profile ?: return
        if (current.lastSeenVisibility == value) return
        viewModelScope.launch { saveProfileUseCase(current.copy(lastSeenVisibility = value)) }
    }

    /**
     * Galereyadan tanlangan rasmni yuklaydi va profilga bog'laydi. [onResult] `null` —
     * muvaffaqiyat (kesh yangilanib, `state.profile.avatarUrl` avtomatik keladi),
     * aks holda xato matni.
     */
    fun uploadAvatar(bytes: ByteArray, fileName: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            when (val res = uploadAvatarUseCase(bytes, fileName)) {
                is Resource.Success -> onResult(null)
                is Resource.Error -> onResult(res.message)
                else -> onResult(null)
            }
        }
    }

    private companion object {
        /** Foiz shundan kam o'zgarsa ekran qayta chizilmaydi. */
        const val PROGRESS_STEP = 0.01f

        /** `MediaUploader` fayl to'liq ketganda aynan shu qiymatni beradi. */
        const val UPLOAD_DONE = 0.99f
    }

    private data class Header(
        val name: String,
        val monogram: String?,
        val course: String?,
        val contact: String,
        val ownerId: String,
        val profile: UserProfile?,
        val universities: List<University>,
    )

    private data class Lists(
        val ads: List<Ad>,
        val saved: List<DiscountOffer>,
        val applications: List<JobApplication>,
    )
}

private fun courseLabel(courseYear: String): String = when (courseYear) {
    "1", "ONE" -> "1-kurs"
    "2", "TWO" -> "2-kurs"
    "3", "THREE" -> "3-kurs"
    "4", "FOUR" -> "4-kurs"
    "MASTER" -> "Magistr"
    else -> courseYear
}
