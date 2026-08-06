package dev.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.core.common.Resource
import dev.core.domain.model.DiscountCategory
import dev.core.domain.model.DiscountGroup
import dev.core.domain.model.DiscountOffer
import dev.feature.connections.domain.model.ConnectionStatus
import dev.feature.connections.domain.model.ConnectionView
import dev.feature.connections.domain.model.SearchedStudent
import dev.feature.connections.domain.model.StudentFilter
import dev.feature.connections.domain.repository.ConnectionsRepository
import dev.feature.listings.domain.model.Listing
import dev.feature.listings.domain.model.ListingKind
import dev.feature.listings.domain.usecase.ObserveListingsByKindUseCase
import dev.feature.listings.domain.usecase.RefreshListingsUseCase
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
 * Bosh ekrandagi bitta e'lon bo'limi.
 *
 * Sarlavha va emoji katalogdan (`POST /v1/catalog/groups` va `/types`) olinadi — matn
 * ilovada qat'iy yozilmagan, server nomini o'zgartirsa bo'lim sarlavhasi ham o'zgaradi.
 *
 * [key] — "Barchasi" bosilganda ochiladigan katalog GURUHI (`DiscountGroup.key`). Kiyim
 * alohida guruh emas (u `SHOPPING` ichidagi biznes turi), shuning uchun uning bo'limida
 * ham guruh kaliti turadi.
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
    /**
     * Chegirma bo'limlari — Home'da ATIGI IKKITASI: "Ovqatlanish" (butun `FOOD` guruhi) va
     * "Kiyim-kechak" (talabaning jinsiga mos). Uchinchi e'lon bo'limi — [rentals].
     * E'loni yo'q bo'lim ro'yxatga tushmaydi.
     */
    val offerSections: List<HomeOfferSection> = emptyList(),
    /**
     * Faol topshiriq e'lonlari ([ListingKind.TASK]) — "Fanlardan yordam": referat, masala,
     * qo'lyozma, IT ishi. Ovqat va kiyimdan KEYIN, kvartiralardan OLDIN turadi.
     */
    val tasks: List<Listing> = emptyList(),
    /** Faol ijara e'lonlari ([ListingKind.RENTAL]) — sherik izlayotgan kvartiralar. */
    val rentals: List<Listing> = emptyList(),
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
    val hasUnreadNotifications: Boolean = false,
    /**
     * Birinchi yuklanish tugamagan va ko'rsatadigan bo'lim yo'q — ekran o'rniga skelet
     * (shimmer) chiziladi. Keshda ma'lumot bo'lsa `false`: tayyor kontent ustiga skelet
     * qo'yilmaydi.
     */
    val loading: Boolean = false,
    /**
     * Foydalanuvchi ekranni pastga tortdi va yangilanish ketyapti — tepadagi aylanma
     * indikator shu bayroq bilan turadi ([loading] dan farqli: bu skelet emas, kontent
     * joyida qoladi).
     */
    val refreshing: Boolean = false,
)

class HomeViewModel(
    observeCurrentUserUseCase: ObserveCurrentUserUseCase,
    private val observeProfileUseCase: ObserveProfileUseCase,
    private val refreshProfileUseCase: RefreshProfileUseCase,
    private val universityRepository: UniversityRepository,
    private val discountRepository: DiscountRepository,
    private val regionRepository: RegionRepository,
    observeListingsByKind: ObserveListingsByKindUseCase,
    private val refreshListings: RefreshListingsUseCase,
    private val connectionsRepository: ConnectionsRepository,
    private val notificationRepository: NotificationRepository,
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

    /** Birinchi chegirma `refresh()` i tugaguncha `true` — Home skeletini shu boshqaradi. */
    private val refreshing = MutableStateFlow(true)

    /**
     * "Tepadan tortib yangilash" ketyapti. [refreshing] dan ALOHIDA: u birinchi
     * yuklanishning skeletini boshqaradi, bu esa foydalanuvchi o'zi so'ragan yangilanishning
     * aylanma indikatorini — kontent joyida turgan holda.
     */
    private val pullRefreshing = MutableStateFlow(false)

    init {
        refreshStudents()
        // Offline-first: universitetlarni backend'dan sinxronlashga urinamiz.
        viewModelScope.launch { universityRepository.refresh() }
        // Qo'ng'iroq ikonasidagi nuqta ([HomeUiState.hasUnreadNotifications]) local keshdan
        // o'qiladi. Ro'yxatni ham shu yerda yangilaymiz — aks holda nuqta faqat foydalanuvchi
        // bildirishnomalar ekranini OCHGANDAN keyin paydo bo'lardi, ya'ni "yangi bildirishnoma
        // bor" degan yagona belgi hech qachon o'z vaqtida ko'rinmasdi. Xatosi yutiladi: bosh
        // ekran bildirishnoma so'rovi tufayli hech nima ko'rsatmaydi.
        viewModelScope.launch { runCatching { notificationRepository.refresh() } }
        // Chegirma bo'limlari backend feed'idan yuradi — `POST /v1/catalog/*` +
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
                    runCatching { discountRepository.refresh() }
                    // Xatoda ham bayroq tushadi — aks holda skelet abadiy qolib ketardi.
                    refreshing.value = false
                    loadUniversityStudents(universityId)
                }
        }
        // Kirishdan keyin ilova shu ekrandan boshlanadi — profilni masofaviy manbadan
        // keshga tortamiz, shunda sarlavhadagi universitet/kurs darrov ko'rinadi.
        viewModelScope.launch { refreshProfileUseCase() }

        // E'lon bo'limlari keshdan kuzatiladi, lekin kesh o'zi to'lmaydi: usiz Home'dagi
        // "Fanlardan yordam" va "Ijara" faqat foydalanuvchi E'lonlar ekranini ochgandan
        // keyin jonlanardi. Xatosi yutiladi — Home'da e'lon bo'limi shunchaki bo'sh qoladi.
        viewModelScope.launch { refreshListings(ListingKind.TASK) }
        viewModelScope.launch { refreshListings(ListingKind.RENTAL) }
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
            // Kurs ATAYLAB olinmaydi: bosh ekran sarlavhasida talabaning nechanchi
            // bosqichda o'qishi ko'rsatilmaydi (u yerda faqat universitet mo'ljali).
            monogram = uni?.monogram,
        )
    }

    /**
     * Bosh ekranning chegirma bo'limlari — **faqat ikkitasi**: ovqat va kiyim. Butun katalog
     * guruhlari ro'yxati Home'da chizilmaydi; qolganlari "Siz uchun" ekranida.
     *
     * Jins profildan kuzatiladi: talaba profilida jinsni o'zgartirsa, kiyim bo'limi
     * qayta so'rov yubormasdan (kesh local) darrov almashadi.
     */
    private val catalogSections = combine(
        discountRepository.observeGroups(),
        discountRepository.observeCategories(),
        discountRepository.observeAllOffers(),
        observeProfileUseCase().map { it?.gender }.distinctUntilChanged(),
    ) { groups, categories, offers, gender ->
        listOfNotNull(
            foodSection(groups, categories, offers),
            clothingSection(categories, offers, gender),
        )
    }

    private val content = combine(
        catalogSections,
        observeListingsByKind(ListingKind.TASK),
        observeListingsByKind(ListingKind.RENTAL),
        refreshing,
    ) { sections, tasks, rentals, loading ->
        Content(
            sections = sections,
            tasks = tasks,
            rentals = rentals,
            // Skelet faqat KO'RSATADIGAN HECH NARSA yo'q bo'lganda: keshdagi e'lonlar
            // bo'lsa ekran darrov to'ladi va yangilanish jimgina bo'ladi.
            loading = loading && sections.isEmpty() && tasks.isEmpty() && rentals.isEmpty(),
        )
    }

    private val extras = combine(
        notificationRepository.observeUnreadCount(),
        pullRefreshing,
    ) { unread, pulling -> unread to pulling }

    val state: StateFlow<HomeUiState> = combine(
        header, content, extras, _universityStudents, _students,
    ) { h, c, (unread, pulling), universityStudents, students ->
        HomeUiState(
            userName = h.name,
            avatarUrl = h.avatarUrl,
            universityMonogram = h.monogram,
            offerSections = c.sections,
            tasks = c.tasks,
            rentals = c.rentals,
            universityStudents = universityStudents,
            // Yuqoridagi "Universitetimda" bo'limida ko'ringanlar takrorlanmasin.
            allStudents = students.filterNot { s ->
                universityStudents.any { it.student.id == s.student.id }
            },
            hasUnreadNotifications = unread > 0,
            loading = c.loading,
            refreshing = pulling,
        )
    }
        .catch { emit(HomeUiState()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    /**
     * Ekran pastga tortildi — bosh ekrandagi HAMMA manba qayta o'qiladi.
     *
     * Bo'limlar turli repozitoriylardan keladi va ularning har biri o'z keshini yangilaydi;
     * oqimlar (`observe…`) natijani o'zi ekranga olib chiqadi, ya'ni bu yerda holatga
     * qo'lda yozish kerak emas.
     *
     * Har bir chaqiruv `runCatching` bilan: bittasi yiqilsa qolganlari baribir yangilanadi,
     * xato esa `safeApiCall` ichida allaqachon toast qilingan.
     */
    fun refresh() {
        if (pullRefreshing.value) return
        viewModelScope.launch {
            pullRefreshing.value = true
            try {
                val universityId = observeProfileUseCase().first()?.universityId
                runCatching { refreshProfileUseCase() }
                runCatching { universityRepository.refresh() }
                runCatching { discountRepository.refresh() }
                runCatching { refreshListings(ListingKind.TASK) }
                runCatching { refreshListings(ListingKind.RENTAL) }
                runCatching { notificationRepository.refresh() }
                loadUniversityStudents(universityId)
                refreshStudents()
            } finally {
                pullRefreshing.value = false
                // Birinchi yuklanish bayrog'i ham tushadi: skelet qolib ketmasin.
                refreshing.value = false
            }
        }
    }

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
    )
    private data class Content(
        val sections: List<HomeOfferSection>,
        val tasks: List<Listing>,
        val rentals: List<Listing>,
        val loading: Boolean = false,
    )
}

/** Home'dagi "Ovqatlanish" bo'limi — butun `FOOD` katalog guruhi. */
private fun foodSection(
    groups: List<DiscountGroup>,
    categories: List<DiscountCategory>,
    offers: List<DiscountOffer>,
): HomeOfferSection? {
    val group = groups.firstOrNull { it.key == FOOD_GROUP }
    // Odatda e'londa `groupKey` turadi; eski keshda bo'sh bo'lsa — biznes turi orqali.
    val foodTypes = categories.filter { it.groupKey == FOOD_GROUP }.map { it.id }.toSet()
    val list = offers.filter { it.groupKey == FOOD_GROUP || it.categoryId in foodTypes }
    if (list.isEmpty()) return null
    // Guruh hali yuklanmagan bo'lsa ham bo'lim ko'rinadi — sarlavha zaxira matndan.
    return HomeOfferSection(FOOD_GROUP, group?.name ?: "Ovqatlanish", group?.emoji ?: "🍽", list)
}

/**
 * "Kiyim-kechak" bo'limi — talabaning jinsiga mos e'lonlar: profilda `FEMALE` bo'lsa
 * ayollar kiyimi, aks holda (`MALE` yoki umuman ko'rsatilmagan) erkaklar kiyimi.
 * Jinsi belgilanmagan (uniseks) e'lonlar ikkala holatda ham ko'rinadi.
 *
 * Biznes turi id bo'yicha topiladi; backend kaliti boshqacha bo'lsa nomdan izlanadi —
 * shu sabab local seed ("kiyim") ham, server katalogi ham ishlaydi.
 */
private fun clothingSection(
    categories: List<DiscountCategory>,
    offers: List<DiscountOffer>,
    profileGender: String?,
): HomeOfferSection? {
    val type = categories.firstOrNull { it.id.equals(CLOTHING_TYPE, ignoreCase = true) }
        ?: categories.firstOrNull { it.name.startsWith("Kiyim", ignoreCase = true) }
        ?: return null
    val wanted = if (profileGender.equals("FEMALE", ignoreCase = true)) "FEMALE" else "MALE"
    val list = offers.filter {
        it.categoryId == type.id && (it.gender.isBlank() || it.gender.equals(wanted, ignoreCase = true))
    }
    if (list.isEmpty()) return null
    return HomeOfferSection(type.groupKey, type.name, type.emoji, list)
}

/** Ovqat bo'limining katalog guruhi kaliti. */
private const val FOOD_GROUP = "FOOD"

/**
 * Kiyim biznes turining kaliti. Katalog turlari serverdan keladi va kalit hali barqaror
 * emas — shu sabab yuqorida nom bo'yicha zaxira moslik ham bor.
 */
private const val CLOTHING_TYPE = "kiyim"

/** Home'da faqat bir nechta karta ko'rinadi — butun ro'yxatni tortishning hojati yo'q. */
private const val HOME_STUDENTS_SIZE = 10
