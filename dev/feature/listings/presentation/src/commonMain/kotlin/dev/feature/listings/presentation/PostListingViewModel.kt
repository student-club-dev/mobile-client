package dev.feature.listings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.core.common.Resource
import dev.core.common.format.UZ_PHONE_CODE
import dev.core.common.format.toAmountDigits
import dev.core.common.format.toUzPhoneDigits
import dev.core.domain.usecase.ObserveCurrentUserUseCase
import dev.feature.profile.domain.usecase.ObserveProfileUseCase
import dev.feature.listings.domain.model.BusinessType
import dev.feature.listings.domain.model.EmploymentType
import dev.feature.listings.domain.model.JobCatalog
import dev.feature.listings.domain.model.Listing
import dev.feature.listings.domain.model.ListingAudience
import dev.feature.listings.domain.model.ListingDetails
import dev.feature.listings.domain.model.ListingIds
import dev.feature.listings.domain.model.ListingKind
import dev.feature.listings.domain.model.ListingRedemption
import dev.feature.listings.domain.model.ListingStatus
import dev.feature.listings.domain.model.ListingValidator
import dev.feature.listings.domain.model.PayPeriod
import dev.feature.listings.domain.model.PriceUnit
import dev.feature.listings.domain.model.ServiceCatalog
import dev.feature.listings.domain.model.TaskCatalog
import dev.feature.listings.domain.model.TaskFormat
import dev.feature.listings.domain.model.WorkSchedule
import dev.feature.listings.domain.usecase.CreateBranchFromPointUseCase
import dev.feature.listings.domain.usecase.GetListingUseCase
import dev.feature.listings.domain.usecase.PublishListingUseCase
import dev.feature.listings.domain.usecase.SaveDraftUseCase
import dev.feature.listings.domain.usecase.SearchPlacesUseCase
import dev.feature.listings.domain.usecase.UploadListingImageUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * E'lon qo'yish ekranining ViewModel'i — to'rt xil e'lon uchun bitta oqim.
 *
 * Umumiy maydonlar (sarlavha, rasm, narx, joylashuv) har turda bir xil ishlaydi va shu
 * yerda nomlangan metodlar bilan boshqariladi. Turga xos maydonlar esa o'z holatiga ega
 * ([RentalFormState], [ServiceFormState], [JobFormState]) va ular `updateRental { ... }`
 * ko'rinishidagi metodlar orqali yangilanadi.
 *
 * Nega har bir maydon uchun alohida metod emas: to'rt turda jami yuzga yaqin maydon bor,
 * ularning har biriga `onX` yozish ViewModel'ni faqat bir qatorli o'tkazgichlardan iborat
 * ming qatorlik faylga aylantirar edi. Turga xos holat esa baribir yaxlit `copy` bilan
 * yangilanadi — shuning uchun o'zgartirish funksiyasini uzatish ham qisqa, ham xavfsiz
 * (boshqa turning holatiga tegib bo'lmaydi).
 */
class PostListingViewModel(
    observeCurrentUserUseCase: ObserveCurrentUserUseCase,
    observeProfileUseCase: ObserveProfileUseCase,
    private val publishListing: PublishListingUseCase,
    private val saveDraft: SaveDraftUseCase,
    private val uploadImage: UploadListingImageUseCase,
    private val getListing: GetListingUseCase,
    private val createBranch: CreateBranchFromPointUseCase,
    private val searchPlaces: SearchPlacesUseCase,
) : ViewModel() {

    private val user = observeCurrentUserUseCase()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * E'lon egasining universiteti — `Listing.universityId` ning odatiy qiymati (spec §7.2.4).
     * Formada so'ralmaydi: talaba o'z e'lonini deyarli har doim o'z OTMiga bog'laydi,
     * profilda universitet ko'rsatilmagan bo'lsa esa e'lon shunchaki bog'lanmagan qoladi.
     */
    private val universityId = observeProfileUseCase()
        .map { it?.universityId?.takeIf { id -> id.isNotBlank() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _state = MutableStateFlow(PostListingUiState())
    val state: StateFlow<PostListingUiState> = _state.asStateFlow()

    init {
        // Ko'rinish doirasi tanlovi universitetsiz ma'nosiz — profil kelgach ochiladi.
        // Universitet keyin olib tashlansa tanlov ham `ALL` ga qaytadi, aks holda e'lon
        // jimgina hech kimga ko'rinmay qolardi.
        universityId
            .onEach { id ->
                val known = id != null
                _state.update {
                    it.copy(
                        hasUniversity = known,
                        audience = if (known) it.audience else ListingAudience.ALL,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /** Tahrirlanayotgan e'lonning id/yaratilgan vaqti — publish o'shani upsert qiladi. */
    private var editingId: String? = null
    private var editingCreatedAt: Long? = null
    private var editingBusinessId: String? = null

    /** Tahrirlanayotgan e'lon qaysi universitetga bog'langan (`null` — bog'lanmagan). */
    private var editingUniversityId: String? = null

    // -----------------------------------------------------------------------
    // Qadamlar: tur → (biznes turi) → forma
    // -----------------------------------------------------------------------

    /**
     * E'lon turi tanlandi. Chegirmada yana bir qadam bor (biznes turi), qolganlarida
     * to'g'ridan-to'g'ri formaga o'tiladi.
     */
    fun selectKind(kind: ListingKind) = _state.update { state ->
        state.copy(
            kind = kind,
            step = if (kind == ListingKind.DISCOUNT) PostListingStep.TYPE else PostListingStep.FORM,
            priceUnit = defaultPriceUnit(kind, state),
        )
    }

    fun selectBusinessType(type: BusinessType) = _state.update { state ->
        state.copy(
            step = PostListingStep.FORM,
            // Har turning o'z narx birligi va kategoriyalari bor — eskilarini tashlaymiz.
            priceUnit = type.defaultPriceUnit,
            discount = state.discount.copy(businessType = type, categoryKey = ""),
        )
    }

    /** Formadan orqaga: chegirmada biznes turiga, qolganlarida e'lon turiga qaytadi. */
    fun back() = _state.update { state ->
        when {
            state.step == PostListingStep.FORM && state.kind == ListingKind.DISCOUNT ->
                state.copy(step = PostListingStep.TYPE)
            state.step == PostListingStep.FORM || state.step == PostListingStep.TYPE ->
                state.copy(step = PostListingStep.KIND)
            else -> state
        }
    }

    private fun defaultPriceUnit(kind: ListingKind, state: PostListingUiState): PriceUnit =
        when (kind) {
            ListingKind.DISCOUNT -> state.discount.businessType?.defaultPriceUnit ?: PriceUnit.PER_ITEM
            ListingKind.RENTAL -> state.rental.period.priceUnit
            ListingKind.SERVICE -> state.service.serviceType?.defaultPriceUnit ?: PriceUnit.PER_HOUR
            ListingKind.JOB -> JobCatalog.priceUnit(state.job.payPeriod)
            // Topshiriq — bir martalik ish, narx butun ish uchun.
            ListingKind.TASK -> PriceUnit.PER_ITEM
        }

    // -----------------------------------------------------------------------
    // Umumiy maydonlar
    // -----------------------------------------------------------------------

    fun onTitle(v: String) = _state.update { it.copy(title = v) }
    fun onDescription(v: String) = _state.update { it.copy(description = v) }
    fun onPriceUnit(v: PriceUnit) = _state.update { it.copy(priceUnit = v) }
    fun onPrice(v: String) = _state.update { it.copy(price = v.toAmountDigits()) }
    fun onPriceMax(v: String) = _state.update { it.copy(priceMax = v.toAmountDigits()) }
    fun onNegotiable(v: Boolean) = _state.update { it.copy(isNegotiable = v) }

    /** Holatda faqat 9 xonali milliy raqam turadi — "+998" va probellar saqlanmaydi. */
    fun onContactPhone(v: String) = _state.update { it.copy(contactPhone = v.toUzPhoneDigits()) }
    fun onDuration(days: Int) = _state.update { it.copy(durationDays = days) }

    fun onAudience(value: ListingAudience) = _state.update { it.copy(audience = value) }
    fun consumeMessage() = _state.update { it.copy(message = null) }

    // -----------------------------------------------------------------------
    // Turga xos holatlar
    // -----------------------------------------------------------------------

    fun updateDiscount(transform: (DiscountFormState) -> DiscountFormState) =
        _state.update { it.copy(discount = transform(it.discount)) }

    /**
     * Ijara holatini yangilaydi. Ijara muddati (oylik/kunlik) narx birligini ham belgilaydi —
     * ikkalasi doim mos bo'lishi uchun shu yerda birga yangilanadi.
     */
    fun updateRental(transform: (RentalFormState) -> RentalFormState) = _state.update { state ->
        val rental = transform(state.rental)
        state.copy(rental = rental, priceUnit = rental.period.priceUnit)
    }

    /**
     * Xizmat holatini yangilaydi. Soha o'zgarsa maydonlar boshqacha bo'ladi — eski
     * qiymatlar tozalanadi, aks holda ular ko'rinmas holda saqlanib qoladi va e'longa
     * boshqa sohaning ma'lumoti tushib ketadi.
     */
    fun updateService(transform: (ServiceFormState) -> ServiceFormState) = _state.update { state ->
        val next = transform(state.service)
        val typeChanged = next.serviceType != state.service.serviceType
        val subjectChanged = next.subjectKey != state.service.subjectKey

        val cleaned = when {
            typeChanged -> next.copy(subjectKey = "", customSubject = "", fields = emptyMap())
            // Yo'nalish o'zgarsa faqat YO'NALISHGA xos maydonlar tozalanadi,
            // sohaning umumiy maydonlari (narx, tajriba) joyida qoladi.
            subjectChanged -> next.copy(fields = next.fields.retainingOnly(next.serviceType))
            else -> next
        }
        state.copy(
            service = cleaned,
            priceUnit = if (typeChanged) {
                cleaned.serviceType?.defaultPriceUnit ?: state.priceUnit
            } else {
                state.priceUnit
            },
        )
    }

    /** Sohaning umumiy maydonlarigina qoldiriladi (yo'nalishga xoslari olib tashlanadi). */
    private fun Map<String, String>.retainingOnly(
        type: dev.feature.listings.domain.model.ServiceType?,
    ): Map<String, String> {
        if (type == null) return emptyMap()
        val commonKeys = ServiceCatalog.fields(type).map { it.key }.toSet()
        return filterKeys { it in commonKeys }
    }

    /** Xizmatning bitta maydonini yozadi (bo'sh bo'lsa o'chiradi). */
    fun onServiceField(key: String, value: String) = updateService { service ->
        service.copy(
            fields = if (value.isBlank()) service.fields - key else service.fields + (key to value),
        )
    }

    /**
     * Ish holatini yangilaydi. To'lov davri narx birligini belgilaydi, ish turi esa
     * mavjud smenalar ro'yxatini — mos kelmay qolgan smena tozalanadi.
     */
    fun updateTask(transform: (TaskFormState) -> TaskFormState) = _state.update { state ->
        val next = transform(state.task)
        // Fan almashsa erkin nom eskirib qoladi — "Boshqa" bo'lmasa tozalaymiz.
        // Kategoriya almashsa eski tur unga tegishli emas — tozalaymiz.
        val categoryChanged = next.category != state.task.category
        var task = if (categoryChanged) next.copy(typeKey = "", customTypeName = "") else next
        if (task.typeKey != TaskCatalog.OTHER_KEY) task = task.copy(customTypeName = "")
        // Onlayn ishga aylansa xaritadan tanlangan manzillar ortiqcha.
        val branches = if (task.format == TaskFormat.IN_PERSON) state.branches else emptyList()
        state.copy(task = task, branches = branches)
    }

    fun updateJob(transform: (JobFormState) -> JobFormState) = _state.update { state ->
        val next = transform(state.job)
        val employmentChanged = next.employment != state.job.employment

        val job = if (employmentChanged) {
            next.copy(
                shift = next.shift?.takeIf { it in JobCatalog.shifts(next.employment) },
                payPeriod = next.payPeriod.takeIf { it in JobCatalog.payPeriods(next.employment) }
                    ?: JobCatalog.payPeriods(next.employment).first(),
                // Kunlik ishda haftalik grafik, doimiy ishda esa aniq sana ma'nosiz.
                days = if (next.employment == EmploymentType.DAILY) emptySet() else next.days,
                workDate = if (next.employment == EmploymentType.PERMANENT) null else next.workDate,
            )
        } else {
            next
        }
        state.copy(job = job, priceUnit = JobCatalog.priceUnit(job.payPeriod))
    }

    /** Chegirma rejimi tab tomonidan belgilanadi — qulflab qo'yamiz (tanlov ko'rinmaydi). */
    fun setInitialDiscountMode(discounted: Boolean) = _state.update { state ->
        state.copy(
            kind = ListingKind.DISCOUNT,
            step = if (state.discount.businessType == null) PostListingStep.TYPE else state.step,
            discount = state.discount.copy(isDiscounted = discounted, modeLocked = true),
        )
    }

    // -----------------------------------------------------------------------
    // Manzillar — xaritadan
    // -----------------------------------------------------------------------

    /** "+" bosilganda xarita ochiladi. */
    fun openMap() = _state.update { it.copy(pickingOnMap = true) }

    fun closeMap() = _state.update {
        it.copy(pickingOnMap = false, searchQuery = "", searchResults = emptyList())
    }

    private var searchJob: Job? = null

    /**
     * Har bosilgan harfda so'rov yubormaymiz: oldingi qidiruv bekor qilinadi va foydalanuvchi
     * yozishni to'xtatgandan keyingina Nominatim'ga boramiz (uning qoidasi ham shuni talab qiladi).
     */
    fun onSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
        searchJob?.cancel()

        if (query.trim().length < SearchPlacesUseCase.MIN_QUERY_LENGTH) {
            _state.update { it.copy(searchResults = emptyList(), searching = false) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _state.update { it.copy(searching = true) }
            val results = searchPlaces(query)
            _state.update { it.copy(searchResults = results, searching = false) }
        }
    }

    /** Natija tanlandi — qidiruv yopiladi, ekran xaritani o'sha joyga olib boradi. */
    fun clearSearch() = _state.update {
        it.copy(searchQuery = "", searchResults = emptyList(), searching = false)
    }

    /**
     * Xaritada nuqta tanlandi. Manzil teskari geokodlash bilan avtomatik to'ladi —
     * foydalanuvchi uni qo'lda yozmaydi. Internet bo'lmasa manzil o'rniga koordinata
     * yoziladi va manzil baribir qo'shiladi (nuqta yo'qolmasligi kerak).
     */
    fun addBranchFromMap(lat: Double, lng: Double) {
        viewModelScope.launch {
            _state.update { it.copy(resolvingAddress = true) }
            val branch = createBranch(
                id = "br-${Clock.System.now().toEpochMilliseconds()}",
                lat = lat,
                lng = lng,
            )
            _state.update {
                it.copy(
                    branches = it.branches + branch,
                    resolvingAddress = false,
                    pickingOnMap = false,
                )
            }
        }
    }

    fun removeBranch(index: Int) = _state.update {
        it.copy(branches = it.branches.filterIndexed { i, _ -> i != index })
    }

    /** Avtomatik topilgan manzilni aniqlashtirish (masalan "2-qavat" qo'shish). */
    fun onBranchAddress(index: Int, address: String) = _state.update { state ->
        state.copy(
            branches = state.branches.mapIndexed { i, branch ->
                if (i == index) branch.copy(address = address) else branch
            },
        )
    }

    /** Manzil nomi ("Chilonzor filiali") — ixtiyoriy. */
    fun onBranchName(index: Int, name: String) = _state.update { state ->
        state.copy(
            branches = state.branches.mapIndexed { i, branch ->
                if (i == index) branch.copy(name = name.ifBlank { null }) else branch
            },
        )
    }

    // -----------------------------------------------------------------------
    // Rasmlar
    // -----------------------------------------------------------------------

    fun addImage(bytes: ByteArray, fileName: String) {
        if (_state.value.images.size >= ListingValidator.MAX_IMAGES) {
            _state.update { it.copy(message = Lt.maxImages(ListingValidator.MAX_IMAGES)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(uploadingImage = true) }
            when (val res = uploadImage(bytes, fileName)) {
                is Resource.Success -> _state.update {
                    it.copy(images = it.images + res.data, uploadingImage = false)
                }
                is Resource.Error -> _state.update {
                    it.copy(uploadingImage = false, message = res.message)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun removeImage(index: Int) = _state.update {
        it.copy(images = it.images.filterIndexed { i, _ -> i != index })
    }

    // -----------------------------------------------------------------------
    // Saqlash / publish
    // -----------------------------------------------------------------------

    /** Mavjud e'lonni formaga yuklaydi (tahrirlash). */
    fun loadForEdit(listingId: String) {
        if (editingId == listingId) return
        viewModelScope.launch {
            val listing = getListing(listingId) ?: return@launch
            editingId = listing.id
            editingCreatedAt = listing.createdAt
            editingBusinessId = listing.businessId
            editingUniversityId = listing.universityId
            _state.value = listing.toUiState()
        }
    }

    fun saveDraft() {
        val listing = buildListing() ?: return
        viewModelScope.launch {
            when (val res = saveDraft.invoke(listing)) {
                is Resource.Success -> {
                    editingId = res.data.id
                    editingCreatedAt = res.data.createdAt
                    _state.update { it.copy(message = lt("Qoralama saqlandi")) }
                }
                is Resource.Error -> _state.update { it.copy(message = res.message) }
                Resource.Loading -> Unit
            }
        }
    }

    fun publish() {
        val listing = buildListing() ?: return
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, errors = emptyList()) }
            when (val res = publishListing(listing)) {
                is PublishListingUseCase.Result.Success ->
                    _state.update { it.copy(submitting = false, published = true) }

                // Validatsiya xatolari maydonlarga bog'lanadi — forma ularni joyida
                // ko'rsatadi. Server xatolari ham shu yo'ldan keladi: uning `error.fields`
                // kalitlari `ListingField` nomlari bilan bir xil.
                is PublishListingUseCase.Result.Invalid ->
                    _state.update {
                        it.copy(submitting = false, errors = res.errors, message = res.message)
                    }

                is PublishListingUseCase.Result.Failed ->
                    _state.update { it.copy(submitting = false, message = res.message) }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Forma ↔ domen
    // -----------------------------------------------------------------------

    /** Forma holatidan domen modelini quradi. Tur tanlanmagan bo'lsa — `null`. */
    private fun buildListing(): Listing? {
        val s = _state.value
        val kind = s.kind ?: return null
        val details = s.buildDetails(kind) ?: return null

        val ownerId = (user.value?.id ?: 0L).toString()
        val now = Clock.System.now().toEpochMilliseconds()

        return Listing(
            // Yangi e'lonning id'si vaqtinchalik: haqiqiysini server beradi va repository
            // javob kelgach almashtiradi. Prefiks aynan shuning uchun —
            // `POST` va `PATCH` ni ajratadigan yagona belgi (qarang [ListingIds]).
            id = editingId ?: ListingIds.newLocalId(ownerId, now),
            ownerId = ownerId,
            businessId = editingBusinessId,
            details = details,
            title = s.title.trim(),
            description = s.description.trim().ifBlank { null },
            images = s.images,
            priceUnit = s.priceUnit,
            price = s.price.toLongOrNull() ?: 0,
            priceMax = s.priceMax.toLongOrNull(),
            isNegotiable = s.isNegotiable,
            // Holatda 9 xonali raqam turadi — modelga doim "+998..." ko'rinishida ketadi.
            // Chala raqam ham shu ko'rinishda ketadi: uni [ListingValidator] ushlaydi.
            contactPhone = s.contactPhone.toUzPhoneDigits().ifBlank { null }?.let { UZ_PHONE_CODE + it },
            // Tahrirlashda e'lon o'z universitetida qoladi; yangi e'lon egasinikiga bog'lanadi.
            universityId = editingUniversityId ?: universityId.value,
            audience = s.audience,
            // Chegirmada kategoriyaga xos maydonlar shu yerda saqlanadi.
            attributes = s.discount.attributeValues.takeIf { kind == ListingKind.DISCOUNT }.orEmpty(),
            branches = s.branches,
            validFrom = now,
            validTo = validTo(now, s.durationDays, details),
            status = ListingStatus.DRAFT,
            createdAt = editingCreatedAt ?: now,
            updatedAt = now,
        )
    }

    /**
     * E'lon qachongacha ro'yxatda turadi.
     *
     * Topshiriqda muddat **topshirish muddatidan oshmaydi**: server buni talab qiladi
     * (`422 VALIDITY` — `STUDENT_LISTINGS_BACKEND.md` §6) va mantiqan ham to'g'ri —
     * muddati o'tgan topshiriqni ko'rsatib turishning ma'nosi yo'q. Formada "30 kun"
     * tanlab, muddatni "ertaga" qo'yish juda oson, shuning uchun tanlov emas, hisob.
     */
    private fun validTo(now: Long, durationDays: Int, details: ListingDetails): Long {
        val requested = now + durationDays.toLong() * MILLIS_PER_DAY
        val deadline = (details as? ListingDetails.Task)?.deadline ?: return requested
        return minOf(requested, deadline)
    }

    /** Turga xos holatdan [ListingDetails] quradi. Chegirmada biznes turi tanlanmagan — `null`. */
    private fun PostListingUiState.buildDetails(kind: ListingKind): ListingDetails? = when (kind) {
        ListingKind.TASK -> ListingDetails.Task(
            category = task.category,
            typeKey = task.typeKey,
            customTypeName = task.customTypeName.trim().ifBlank { null },
            deadline = task.deadline,
            format = task.format,
            volume = task.volume.trim().ifBlank { null },
        )

        ListingKind.DISCOUNT -> discount.businessType?.let { type ->
            ListingDetails.Discount(
                businessType = type,
                businessName = discount.businessName.trim(),
                categoryKey = discount.categoryKey,
                customCategoryName = discount.customCategoryName.trim().ifBlank { null },
                isDiscounted = discount.isDiscounted,
                discountType = discount.discountType,
                discountValue = discount.discountValue.toLongOrNull() ?: 0,
                conditions = discount.conditions.trim().ifBlank { null },
                redemption = ListingRedemption(
                    method = discount.redemptionMethod,
                    promoCode = discount.promoCode.trim().ifBlank { null },
                ),
            )
        }

        ListingKind.RENTAL -> ListingDetails.Rental(
            propertyType = rental.propertyType,
            roomCount = rental.roomCount.toIntOrNull(),
            currentTenants = rental.currentTenants.toIntOrNull(),
            neededTenants = rental.neededTenants.toIntOrNull(),
            gender = rental.gender,
            period = rental.period,
            utilitiesIncluded = rental.utilitiesIncluded,
            depositMonths = rental.depositMonths.toIntOrNull(),
            floor = rental.floor.toIntOrNull(),
            totalFloors = rental.totalFloors.toIntOrNull(),
            amenities = rental.amenities.toList(),
            availableFrom = rental.availableFrom,
        )

        ListingKind.SERVICE -> ListingDetails.Service(
            serviceType = service.serviceType,
            // Yo'nalish ham maydonlar ichida saqlanadi — domen uni shu kalitda kutadi.
            fields = service.fields +
                buildMap {
                    if (service.subjectKey.isNotBlank()) put(ServiceCatalog.SUBJECT_KEY, service.subjectKey)
                    if (service.customSubject.isNotBlank()) {
                        put(ServiceCatalog.CUSTOM_SUBJECT_KEY, service.customSubject.trim())
                    }
                },
            format = service.format,
            experienceYears = service.experienceYears.toIntOrNull(),
            workingHours = service.workingHours.trim().ifBlank { null },
            hasHomeVisit = service.hasHomeVisit,
            hasFreeTrial = service.hasFreeTrial,
        )

        ListingKind.JOB -> ListingDetails.Job(
            employment = job.employment,
            categoryKey = job.categoryKey,
            companyName = job.companyName.trim(),
            shift = job.shift,
            schedule = WorkSchedule(
                days = job.days.toList().sortedBy { it.ordinal },
                startTime = job.startTime.ifBlank { null },
                endTime = job.endTime.ifBlank { null },
                hoursPerDay = job.hoursPerDay.toIntOrNull(),
            ),
            payPeriod = job.payPeriod,
            vacancies = job.vacancies.toIntOrNull(),
            gender = job.gender,
            experience = job.experience,
            ageFrom = job.ageFrom.toIntOrNull(),
            ageTo = job.ageTo.toIntOrNull(),
            requirements = job.requirements,
            benefits = job.benefits,
            workDate = job.workDate,
            payoutNote = job.payoutNote.trim().ifBlank { null },
        )
    }

    /** Domen modelidan forma holatini tiklaydi (tahrirlash). */
    private fun Listing.toUiState(): PostListingUiState {
        val base = PostListingUiState(
            step = PostListingStep.FORM,
            kind = kind,
            title = title,
            description = description.orEmpty(),
            images = images,
            priceUnit = priceUnit,
            price = price.takeIf { it > 0 }?.toString().orEmpty(),
            priceMax = priceMax?.takeIf { it > 0 }?.toString().orEmpty(),
            isNegotiable = isNegotiable,
            contactPhone = contactPhone.orEmpty().toUzPhoneDigits(),
            branches = branches,
            durationDays = ((validTo - validFrom) / MILLIS_PER_DAY).toInt().coerceAtLeast(1),
            audience = audience,
            hasUniversity = universityId != null ||
                this@PostListingViewModel.universityId.value != null,
            editing = true,
        )

        return when (val d = details) {
            is ListingDetails.Task -> base.copy(
                task = TaskFormState(
                    category = d.category,
                    typeKey = d.typeKey,
                    customTypeName = d.customTypeName.orEmpty(),
                    deadline = d.deadline,
                    format = d.format,
                    volume = d.volume.orEmpty(),
                ),
            )

            is ListingDetails.Discount -> base.copy(
                discount = DiscountFormState(
                    businessType = d.businessType,
                    businessName = d.businessName,
                    categoryKey = d.categoryKey,
                    customCategoryName = d.customCategoryName.orEmpty(),
                    attributeValues = attributes,
                    isDiscounted = d.isDiscounted,
                    modeLocked = true,
                    discountType = d.discountType,
                    discountValue = d.discountValue.takeIf { it > 0 }?.toString().orEmpty(),
                    conditions = d.conditions.orEmpty(),
                    redemptionMethod = d.redemption.method,
                    promoCode = d.redemption.promoCode.orEmpty(),
                ),
            )

            is ListingDetails.Rental -> base.copy(
                rental = RentalFormState(
                    propertyType = d.propertyType,
                    roomCount = d.roomCount?.toString().orEmpty(),
                    currentTenants = d.currentTenants?.toString().orEmpty(),
                    neededTenants = d.neededTenants?.toString().orEmpty(),
                    gender = d.gender,
                    period = d.period,
                    utilitiesIncluded = d.utilitiesIncluded,
                    depositMonths = d.depositMonths?.toString().orEmpty(),
                    floor = d.floor?.toString().orEmpty(),
                    totalFloors = d.totalFloors?.toString().orEmpty(),
                    amenities = d.amenities.toSet(),
                    availableFrom = d.availableFrom,
                ),
            )

            is ListingDetails.Service -> base.copy(
                service = ServiceFormState(
                    serviceType = d.serviceType,
                    subjectKey = d.fields[ServiceCatalog.SUBJECT_KEY].orEmpty(),
                    customSubject = d.fields[ServiceCatalog.CUSTOM_SUBJECT_KEY].orEmpty(),
                    fields = d.fields - ServiceCatalog.SUBJECT_KEY - ServiceCatalog.CUSTOM_SUBJECT_KEY,
                    format = d.format,
                    experienceYears = d.experienceYears?.toString().orEmpty(),
                    workingHours = d.workingHours.orEmpty(),
                    hasHomeVisit = d.hasHomeVisit,
                    hasFreeTrial = d.hasFreeTrial,
                ),
            )

            is ListingDetails.Job -> base.copy(
                job = JobFormState(
                    employment = d.employment,
                    categoryKey = d.categoryKey,
                    companyName = d.companyName,
                    shift = d.shift,
                    days = d.schedule.days.toSet(),
                    startTime = d.schedule.startTime.orEmpty(),
                    endTime = d.schedule.endTime.orEmpty(),
                    hoursPerDay = d.schedule.hoursPerDay?.toString().orEmpty(),
                    payPeriod = d.payPeriod,
                    vacancies = d.vacancies?.toString().orEmpty(),
                    gender = d.gender,
                    experience = d.experience,
                    ageFrom = d.ageFrom?.toString().orEmpty(),
                    ageTo = d.ageTo?.toString().orEmpty(),
                    requirements = d.requirements,
                    benefits = d.benefits,
                    workDate = d.workDate,
                    payoutNote = d.payoutNote.orEmpty(),
                ),
            )
        }
    }

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
        const val SEARCH_DEBOUNCE_MS = 400L
    }
}

/** Ish e'lonida to'lov davri o'zgarganda narx birligi ham mos bo'lishi uchun. */
internal fun PayPeriod.toPriceUnit(): PriceUnit = JobCatalog.priceUnit(this)
