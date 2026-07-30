package dev.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.feature.clubs.domain.model.Club
import dev.core.common.Resource
import dev.core.domain.model.DiscountOffer
import dev.feature.connections.domain.model.ConnectionStatus
import dev.feature.connections.domain.model.ConnectionView
import dev.feature.connections.domain.model.SearchedStudent
import dev.feature.connections.domain.model.StudentFilter
import dev.feature.connections.domain.repository.ConnectionsRepository
import dev.feature.listings.domain.model.Listing
import dev.feature.listings.domain.model.ListingKind
import dev.feature.listings.domain.usecase.ObserveListingsByKindUseCase
import dev.feature.clubs.domain.repository.ClubRepository
import dev.core.domain.repository.DiscountRepository
import dev.core.domain.repository.RegionRepository
import dev.feature.notifications.domain.repository.NotificationRepository
import dev.feature.university.domain.repository.UniversityRepository
import dev.core.domain.usecase.ObserveCurrentUserUseCase
import dev.feature.profile.domain.usecase.ObserveProfileUseCase
import dev.feature.profile.domain.usecase.RefreshProfileUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Bosh ekrandagi bitta chegirma bo'limi — katalog GURUHI (`POST /v1/catalog/groups`).
 *
 * Sarlavha, emoji va tartib — hammasi serverdan; ilovada bo'limlar ro'yxati qat'iy
 * yozilmagan. Yangi guruh qo'shilsa yoki nomi o'zgarsa ilovaga tegish shart emas.
 */
data class HomeOfferSection(
    val key: String,
    val title: String,
    val emoji: String,
    val offers: List<DiscountOffer>,
)

/** Home (1p) ekranining holati — barchasi local DB'dan reaktiv. */
data class HomeUiState(
    val userName: String = "Talaba",
    /** Profil rasmi manzili (`null` — bosh harf ko'rsatiladi). */
    val avatarUrl: String? = null,
    val universityMonogram: String? = null,
    val courseLabel: String? = null,
    /**
     * Chegirma bo'limlari — katalog guruhlari (Ovqatlanish, Sport, Ta'lim...), server
     * tartibida. E'loni yo'q guruh ro'yxatga umuman tushmaydi.
     */
    val offerSections: List<HomeOfferSection> = emptyList(),
    /** Faol ish e'lonlari ([ListingKind.JOB]) — "E'lonlar" bo'limidagi bilan bir xil manba. */
    val jobs: List<Listing> = emptyList(),
    /** Faol ijara e'lonlari ([ListingKind.RENTAL]) — sherik izlayotgan kvartiralar. */
    val rentals: List<Listing> = emptyList(),
    /** Faol yordam e'lonlari ([ListingKind.TASK]) — bir martalik topshiriqlar. */
    val tasks: List<Listing> = emptyList(),
    /**
     * Universitetim talabalari — `GET /v1/students?universityId=<profil>&connectionStatus=NONE`,
     * ya'ni hali bog'lanmaganlar. Profilda universitet ko'rsatilmagan bo'lsa bo'sh qoladi
     * va bo'lim yashiriladi.
     */
    val universityStudents: List<SearchedStudent> = emptyList(),
    /**
     * **Barcha talabalar** — `GET /v1/students` filtrsiz. Yuqoridagi "Universitetimda"
     * bo'limida ko'ringanlar bu ro'yxatdan chiqarib tashlanadi (takrorlanmasin).
     */
    val allStudents: List<SearchedStudent> = emptyList(),
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
    private val connectionsRepository: ConnectionsRepository,
    clubRepository: ClubRepository,
    notificationRepository: NotificationRepository,
) : ViewModel() {

    /**
     * Talabalar keshi. `ConnectionsRepository` — `suspend`, oqim bermaydi (holat ikki
     * tomonlama va tez o'zgargani uchun u yerda kesh yo'q), shuning uchun Home o'zi bir marta
     * yuklab shu oqimga qo'yadi. Xato bo'lsa bo'sh qoladi — Home'da xato ko'rsatilmaydi,
     * bo'lim shunchaki yashirinadi.
     */
    private val _students = MutableStateFlow<List<SearchedStudent>>(emptyList())

    /** Universitetim talabalari — xuddi shu sababga ko'ra (oqim yo'q) qo'lda yuklanadi. */
    private val _universityStudents = MutableStateFlow<List<SearchedStudent>>(emptyList())

    init {
        refreshStudents()
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
                    loadUniversityStudents(universityId)
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
        discountRepository.observeGroups(),
        discountRepository.observeAllOffers(),
        listings,
        clubRepository.observeClubs(),
    ) { groups, offers, (jobs, rentals, tasks), clubs ->
        // E'lon bo'limga `groupKey` bo'yicha AYNAN tushadi — nomidan taxmin qilinmaydi.
        // Guruhlar allaqachon server tartibida (`selectGroups` — `ORDER BY sortOrder`).
        val byGroup = offers.groupBy { it.groupKey }
        Content(
            sections = groups.mapNotNull { g ->
                val list = byGroup[g.key].orEmpty()
                // Bo'sh bo'lim chizilmaydi — sarlavha osilib qolmasin.
                if (list.isEmpty()) null
                else HomeOfferSection(g.key, g.name, g.emoji, list)
            },
            jobs = jobs, rentals = rentals, tasks = tasks, clubs = clubs,
        )
    }

    val state: StateFlow<HomeUiState> = combine(
        header, content, notificationRepository.observeUnreadCount(), _universityStudents, _students,
    ) { h, c, unread, universityStudents, students ->
        HomeUiState(
            userName = h.name,
            avatarUrl = h.avatarUrl,
            universityMonogram = h.monogram,
            courseLabel = h.course,
            offerSections = c.sections,
            jobs = c.jobs,
            rentals = c.rentals,
            tasks = c.tasks,
            universityStudents = universityStudents,
            // Yuqoridagi "Universitetimda" bo'limida ko'ringanlar takrorlanmasin.
            allStudents = students.filterNot { s ->
                universityStudents.any { it.student.id == s.student.id }
            },
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

    /**
     * **Barcha talabalar** — `GET /v1/students` filtrsiz (eng yangi hisoblar birinchi).
     * Ekranga qaytilganda ham chaqiriladi: "Do'stlar" bo'limida so'rov yuborilgan/qabul
     * qilingan bo'lsa, Home kartalardagi tugmani eskirgan holatda ko'rsatmasin.
     */
    fun refreshStudents() {
        viewModelScope.launch {
            val res = connectionsRepository.students(size = HOME_STUDENTS_SIZE)
            if (res is Resource.Success) _students.value = res.data.items
        }
    }

    /**
     * Talaba kartasidagi «Bog'lanish». Javobdagi `status = ACCEPTED` — u odam sizga
     * allaqachon so'rov yuborgan ekan, ya'ni bog'lanish darhol sodir bo'ldi.
     *
     * Xato bo'lsa (masalan 24 soatlik sovish muddati) Home'da xabar ko'rsatilmaydi —
     * to'liq oqim "Do'stlar" ekranida; bu yerda tugma shunchaki eski holatida qoladi.
     */
    fun connect(studentId: String) {
        viewModelScope.launch {
            val res = connectionsRepository.sendRequest(studentId)
            if (res !is Resource.Success) return@launch
            val status = if (res.data.status == ConnectionStatus.ACCEPTED) {
                ConnectionView.CONNECTED
            } else {
                ConnectionView.PENDING_OUT
            }
            val update = { list: List<SearchedStudent> ->
                list.map { if (it.student.id == studentId) it.copy(connectionStatus = status) else it }
            }
            _universityStudents.update(update)
            _students.update(update)
        }
    }

    /**
     * "Universitetimda" bo'limi. `universityId` — profildagi **erkin satr** (`emis-142`);
     * serverda universitetlar katalogi yo'q, filtr shu satrni aynan solishtiradi. Shuning
     * uchun profildagi format bilan `University.id` formati bir xil bo'lishi shart.
     */
    private suspend fun loadUniversityStudents(universityId: String?) {
        if (universityId.isNullOrBlank()) {
            _universityStudents.value = emptyList()
            return
        }
        val res = connectionsRepository.students(
            filter = StudentFilter(
                universityIds = listOf(universityId),
                // Allaqachon bog'langanlar pastdagi "Bog'lanishlarim" bo'limida turadi.
                connectionStatus = ConnectionView.NONE,
            ),
            size = HOME_STUDENTS_SIZE,
        )
        if (res is Resource.Success) _universityStudents.value = res.data.items
    }

    private data class Header(
        val name: String,
        val avatarUrl: String?,
        val monogram: String?,
        val course: String?,
    )
    private data class Content(
        val sections: List<HomeOfferSection>,
        val jobs: List<Listing>,
        val rentals: List<Listing>,
        val tasks: List<Listing>,
        val clubs: List<Club>,
    )
}

/** Home'da faqat bir nechta karta ko'rinadi — butun ro'yxatni tortishning hojati yo'q. */
private const val HOME_STUDENTS_SIZE = 10

// Profil "1".."4" yozadi (EditProfileScreen); eski yozuvlarda "ONE".."FOUR" uchraydi.
private fun courseLabel(courseYear: String): String = when (courseYear) {
    "1", "ONE" -> "1-kurs"
    "2", "TWO" -> "2-kurs"
    "3", "THREE" -> "3-kurs"
    "4", "FOUR" -> "4-kurs"
    "MASTER" -> "Magistr"
    else -> courseYear
}
