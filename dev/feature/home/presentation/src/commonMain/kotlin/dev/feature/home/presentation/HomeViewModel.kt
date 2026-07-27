package dev.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.feature.clubs.domain.model.Club
import dev.core.domain.model.DiscountOffer
import dev.feature.students.domain.model.FriendStatus
import dev.feature.listings.domain.model.Listing
import dev.feature.listings.domain.model.ListingKind
import dev.feature.listings.domain.usecase.ObserveListingsByKindUseCase
import dev.feature.students.domain.model.Student
import dev.feature.clubs.domain.repository.ClubRepository
import dev.core.domain.repository.DiscountRepository
import dev.core.domain.repository.RegionRepository
import dev.feature.notifications.domain.repository.NotificationRepository
import dev.feature.students.domain.repository.StudentRepository
import dev.feature.university.domain.repository.UniversityRepository
import dev.core.domain.usecase.ObserveCurrentUserUseCase
import dev.feature.profile.domain.usecase.ObserveProfileUseCase
import dev.feature.profile.domain.usecase.RefreshProfileUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Bosh ekrandagi uchta bo'lim qaysi biznes turlarini yig'adi.
 *
 * MUHIM: `categoryId` ikki xil manbadan kelishi mumkin — local seed'da `"ovqat"`, `"game"`
 * kabi, backend'da esa katalog kaliti (`FAST_FOOD`, `NATIONAL_FOOD`, `GAMES`...). Shuning
 * uchun qat'iy ro'yxat emas, KALIT SO'Z bo'yicha moslashtiramiz: tur kaliti + turning nomi +
 * e'lonning bo'limi birga qidiriladi. Yangi tur qo'shilsa ham bo'lim o'zi topib oladi.
 */
private val FOOD_KEYWORDS = listOf(
    "food", "ovqat", "oziq", "kafe", "cafe", "restoran", "market", "pitsa", "pizza", "somsa", "palov",
)
private val CLOTHING_KEYWORDS = listOf("kiyim", "cloth", "wear", "poyabzal", "obuv", "moda")
private val LEISURE_KEYWORDS = listOf(
    "game", "oyin", "o'yin", "kino", "cinema", "playstation", "bilyard", "billiard",
    "dam olish", "ko'ngil", "kongil", "entertain",
)

/** E'lon qaysi bo'limga tushishini aniqlaydi (`null` — uchalasiga ham kirmaydi). */
private fun homeSectionOf(offer: DiscountOffer, categoryNames: Map<String, String>): Int {
    val key = buildString {
        append(offer.categoryId).append(' ')
        append(categoryNames[offer.categoryId].orEmpty()).append(' ')
        append(offer.subcategory)
    }.lowercase()
    return when {
        CLOTHING_KEYWORDS.any { it in key } -> SECTION_CLOTHING
        LEISURE_KEYWORDS.any { it in key } -> SECTION_LEISURE
        FOOD_KEYWORDS.any { it in key } -> SECTION_FOOD
        else -> SECTION_NONE
    }
}

private const val SECTION_NONE = 0
private const val SECTION_FOOD = 1
private const val SECTION_CLOTHING = 2
private const val SECTION_LEISURE = 3

/** Home (1p) ekranining holati — barchasi local DB'dan reaktiv. */
data class HomeUiState(
    val userName: String = "Talaba",
    /** Profil rasmi manzili (`null` — bosh harf ko'rsatiladi). */
    val avatarUrl: String? = null,
    val universityMonogram: String? = null,
    val courseLabel: String? = null,
    /** "Ovqatlar" bo'limi — kafe/restoran va oziq-ovqat e'lonlari. */
    val foodOffers: List<DiscountOffer> = emptyList(),
    /** "Kiyim-kechak" bo'limi. */
    val clothingOffers: List<DiscountOffer> = emptyList(),
    /** "Dam olish" bo'limi — barcha o'yin klublari va kino/ko'ngilochar. */
    val leisureOffers: List<DiscountOffer> = emptyList(),
    /** Faol ish e'lonlari ([ListingKind.JOB]) — "E'lonlar" bo'limidagi bilan bir xil manba. */
    val jobs: List<Listing> = emptyList(),
    /** Faol ijara e'lonlari ([ListingKind.RENTAL]) — sherik izlayotgan kvartiralar. */
    val rentals: List<Listing> = emptyList(),
    /** Faol yordam e'lonlari ([ListingKind.TASK]) — bir martalik topshiriqlar. */
    val tasks: List<Listing> = emptyList(),
    val students: List<Student> = emptyList(),
    val clubs: List<Club> = emptyList(),
    val hasUnreadNotifications: Boolean = false,
)

class HomeViewModel(
    observeCurrentUserUseCase: ObserveCurrentUserUseCase,
    private val observeProfileUseCase: ObserveProfileUseCase,
    private val refreshProfileUseCase: RefreshProfileUseCase,
    private val universityRepository: UniversityRepository,
    private val discountRepository: DiscountRepository,
    private val regionRepository: RegionRepository,
    observeListingsByKind: ObserveListingsByKindUseCase,
    private val studentRepository: StudentRepository,
    clubRepository: ClubRepository,
    notificationRepository: NotificationRepository,
) : ViewModel() {

    init {
        // Offline-first: universitetlarni backend'dan sinxronlashga urinamiz.
        viewModelScope.launch { universityRepository.refresh() }
        // Bosh ekrandagi uchta bo'lim ham backend feed'idan yuradi — `POST /v1/catalog/*` +
        // `/v1/discounts/search`. Busiz ekran faqat local seed'ni ko'rsatib turardi.
        // Xato bo'lsa kesh saqlanadi (repository o'zi hal qiladi).
        //
        // Feed BUTUN mamlakat bo'yicha kelmasligi uchun avval viloyat aniqlanadi (universitet
        // manzilidan). Tartib muhim — refresh geo filtrini `RegionRepository` dan sinxron o'qiydi.
        //
        // Universitet KUZATILADI: profil tahrirlanib boshqa universitet tanlansa, viloyat qayta
        // hisoblanadi va feed yangilanadi. `collectLatest` — universitet ketma-ket o'zgarsa
        // eskirgan so'rov bekor qilinadi. Qo'lda tanlangan viloyatga tegilmaydi (RegionRepository).
        viewModelScope.launch {
            observeProfileUseCase()
                .map { it?.universityId }
                .distinctUntilChanged()
                .collectLatest { universityId ->
                    runCatching {
                        regionRepository.syncWithUniversity(universityId, addressOf(universityId))
                    }
                    discountRepository.refresh()
                }
        }
        // Kirishdan keyin ilova shu ekrandan boshlanadi — profilni masofaviy manbadan
        // keshga tortamiz, shunda sarlavhadagi universitet/kurs darrov ko'rinadi.
        viewModelScope.launch { refreshProfileUseCase() }
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
            // Rasm ham shu tartibda: profil keshi, so'ng sessiya qatoridagi nusxa.
            avatarUrl = profile?.avatarUrl?.takeIf { it.isNotBlank() }
                ?: user?.photoUrl?.takeIf { it.isNotBlank() },
            monogram = uni?.monogram,
            course = profile?.courseYear?.let(::courseLabel),
        )
    }

    // E'lon turlari bitta oqimga yig'iladi — aks holda `content` 6+ manbaga aylanib,
    // typed `combine` overload'i qolmaydi.
    private val listings = combine(
        observeListingsByKind(ListingKind.JOB),
        observeListingsByKind(ListingKind.RENTAL),
        observeListingsByKind(ListingKind.TASK),
    ) { jobs, rentals, tasks -> Triple(jobs, rentals, tasks) }

    private val content = combine(
        discountRepository.observeCategories(),
        discountRepository.observeAllOffers(),
        listings,
        studentRepository.observeStudents(),
        clubRepository.observeClubs(),
    ) { categories, offers, (jobs, rentals, tasks), students, clubs ->
        // Tur nomi ham kerak: backend kaliti (`NATIONAL_FOOD`) o'zi yetarli bo'lmasligi mumkin.
        val names = categories.associate { it.id to it.name }
        val grouped = offers.groupBy { homeSectionOf(it, names) }
        Content(
            food = grouped[SECTION_FOOD].orEmpty(),
            clothing = grouped[SECTION_CLOTHING].orEmpty(),
            leisure = grouped[SECTION_LEISURE].orEmpty(),
            jobs = jobs, rentals = rentals, tasks = tasks, students = students, clubs = clubs,
        )
    }

    val state: StateFlow<HomeUiState> = combine(
        header, content, notificationRepository.observeUnreadCount(),
    ) { h, c, unread ->
        HomeUiState(
            userName = h.name,
            avatarUrl = h.avatarUrl,
            universityMonogram = h.monogram,
            courseLabel = h.course,
            foodOffers = c.food,
            clothingOffers = c.clothing,
            leisureOffers = c.leisure,
            jobs = c.jobs,
            rentals = c.rentals,
            tasks = c.tasks,
            students = c.students,
            clubs = c.clubs,
            hasUnreadNotifications = unread > 0,
        )
    }
        .catch { emit(HomeUiState()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    /** Universitet manzili (prof-emis `address`) — viloyatni aniqlash uchun. */
    private suspend fun addressOf(universityId: String?): String? {
        if (universityId == null) return null
        return universityRepository.observeUniversities().first()
            .firstOrNull { it.id == universityId }?.city
    }

    /** Student kartasidagi "+Do'st" ↔ "Kutilmoqda" o'zgartirish. */
    fun toggleFriend(student: Student) {
        val next = if (student.friendStatus == FriendStatus.NONE) FriendStatus.PENDING else FriendStatus.NONE
        viewModelScope.launch { studentRepository.setFriendStatus(student.id, next) }
    }

    private data class Header(
        val name: String,
        val avatarUrl: String?,
        val monogram: String?,
        val course: String?,
    )
    private data class Content(
        val food: List<DiscountOffer>,
        val clothing: List<DiscountOffer>,
        val leisure: List<DiscountOffer>,
        val jobs: List<Listing>,
        val rentals: List<Listing>,
        val tasks: List<Listing>,
        val students: List<Student>,
        val clubs: List<Club>,
    )
}

// Profil "1".."4" yozadi (EditProfileScreen); eski yozuvlarda "ONE".."FOUR" uchraydi.
private fun courseLabel(courseYear: String): String = when (courseYear) {
    "1", "ONE" -> "1-kurs"
    "2", "TWO" -> "2-kurs"
    "3", "THREE" -> "3-kurs"
    "4", "FOUR" -> "4-kurs"
    "MASTER" -> "Magistr"
    else -> courseYear
}
