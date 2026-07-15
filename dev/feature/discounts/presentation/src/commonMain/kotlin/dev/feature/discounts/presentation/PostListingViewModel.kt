package dev.feature.discounts.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.core.common.Resource
import dev.core.domain.usecase.ObserveCurrentUserUseCase
import dev.feature.discounts.domain.model.BusinessType
import dev.feature.discounts.domain.model.DiscountType
import dev.feature.discounts.domain.model.Listing
import dev.feature.discounts.domain.model.ListingCatalog
import dev.feature.discounts.domain.model.ListingDiscount
import dev.feature.discounts.domain.model.ListingError
import dev.feature.discounts.domain.model.ListingField
import dev.feature.discounts.domain.model.ListingBranch
import dev.feature.discounts.domain.model.ListingRedemption
import dev.feature.discounts.domain.model.ListingStatus
import dev.feature.discounts.domain.model.ListingValidator
import dev.feature.discounts.domain.model.PriceUnit
import dev.feature.discounts.domain.model.RedemptionMethod
import dev.feature.discounts.domain.repository.PlaceSuggestion
import dev.feature.discounts.domain.usecase.CreateBranchFromPointUseCase
import dev.feature.discounts.domain.usecase.GetListingUseCase
import dev.feature.discounts.domain.usecase.PublishListingUseCase
import dev.feature.discounts.domain.usecase.SaveDraftUseCase
import dev.feature.discounts.domain.usecase.SearchPlacesUseCase
import dev.feature.discounts.domain.usecase.UploadListingImageUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/** E'lon qo'yish oqimi: avval biznes turi, keyin forma. */
enum class PostListingStep { TYPE, FORM }

/**
 * E'lon qo'yish formasining holati. Barcha raqamli maydonlar **matn** ko'rinishida —
 * foydalanuvchi yozayotganda qisman kiritishga yo'l qo'yish uchun ("5" → "55" → "55000").
 * Domen modeliga o'girish [PostListingViewModel.buildListing] da bo'ladi.
 */
data class PostListingUiState(
    val step: PostListingStep = PostListingStep.TYPE,
    val businessType: BusinessType? = null,

    val businessName: String = "",
    val categoryKey: String = "",
    val customCategoryName: String = "",

    val title: String = "",
    val description: String = "",
    val images: List<String> = emptyList(),
    val uploadingImage: Boolean = false,

    val priceUnit: PriceUnit = PriceUnit.PER_ITEM,
    val originalPrice: String = "",

    val discountType: DiscountType = DiscountType.PERCENT,
    val discountValue: String = "",
    val conditions: String = "",

    val redemptionMethod: RedemptionMethod = RedemptionMethod.STUDENT_ID,
    val promoCode: String = "",

    /** Filiallar — har biri xaritadan tanlangan (koordinatasi bor). */
    val branches: List<ListingBranch> = emptyList(),
    /** Xarita ochiqmi (yangi filial belgilash uchun). */
    val pickingOnMap: Boolean = false,
    /** Xaritadan nuqta tanlandi, manzil aniqlanmoqda. */
    val resolvingAddress: Boolean = false,

    /** Xaritadagi qidiruv. */
    val searchQuery: String = "",
    val searchResults: List<PlaceSuggestion> = emptyList(),
    val searching: Boolean = false,

    val durationDays: Int = 30,

    val errors: List<ListingError> = emptyList(),
    val submitting: Boolean = false,
    val published: Boolean = false,
    /** Bir martalik xabar (masalan rasm yuklashdagi xato). */
    val message: String? = null,
    val editing: Boolean = false,
) {
    /** Live hisoblanadigan yakuniy narx — foydalanuvchi yozayotganda ko'rinadi. */
    val finalPrice: Long
        get() = ListingDiscount(discountType, discountValue.toLongOrNull() ?: 0)
            .finalPrice(originalPrice.toLongOrNull() ?: 0)

    fun errorFor(field: ListingField): String? = errors.firstOrNull { it.field == field }?.message
}

class PostListingViewModel(
    observeCurrentUserUseCase: ObserveCurrentUserUseCase,
    private val publishListing: PublishListingUseCase,
    private val saveDraft: SaveDraftUseCase,
    private val uploadImage: UploadListingImageUseCase,
    private val getListing: GetListingUseCase,
    private val createBranch: CreateBranchFromPointUseCase,
    private val searchPlaces: SearchPlacesUseCase,
) : ViewModel() {

    private val user = observeCurrentUserUseCase()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _state = MutableStateFlow(PostListingUiState())
    val state: StateFlow<PostListingUiState> = _state.asStateFlow()

    /** Tahrirlanayotgan e'lonning id/yaratilgan vaqti — publish o'shani upsert qiladi. */
    private var editingId: String? = null
    private var editingCreatedAt: Long? = null
    private var editingBusinessId: String? = null

    // -----------------------------------------------------------------------
    // Tur va kategoriya
    // -----------------------------------------------------------------------

    fun selectType(type: BusinessType) = _state.update {
        it.copy(
            businessType = type,
            step = PostListingStep.FORM,
            // Har turning o'z narx birligi va kategoriyalari bor — eskilarini tashlaymiz.
            priceUnit = type.defaultPriceUnit,
            categoryKey = "",
        )
    }

    fun backToTypes() = _state.update { it.copy(step = PostListingStep.TYPE) }

    fun onCategory(key: String) = _state.update { it.copy(categoryKey = key) }
    fun onCustomCategory(v: String) = _state.update { it.copy(customCategoryName = v) }

    // -----------------------------------------------------------------------
    // Asosiy maydonlar
    // -----------------------------------------------------------------------

    fun onBusinessName(v: String) = _state.update { it.copy(businessName = v) }
    fun onTitle(v: String) = _state.update { it.copy(title = v) }
    fun onDescription(v: String) = _state.update { it.copy(description = v) }
    fun onPriceUnit(v: PriceUnit) = _state.update { it.copy(priceUnit = v) }
    fun onPrice(v: String) = _state.update { it.copy(originalPrice = v.digits()) }

    fun onDiscountType(v: DiscountType) = _state.update {
        // 1+1 da qiymat maydoni yo'q — eski foizni tozalaymiz, aks holda u yashirin qolib ketadi.
        it.copy(discountType = v, discountValue = if (v == DiscountType.FREE_ITEM) "" else it.discountValue)
    }

    fun onDiscountValue(v: String) = _state.update { it.copy(discountValue = v.digits()) }
    fun onConditions(v: String) = _state.update { it.copy(conditions = v) }

    fun onRedemptionMethod(v: RedemptionMethod) = _state.update { it.copy(redemptionMethod = v) }
    fun onPromoCode(v: String) = _state.update { it.copy(promoCode = v.uppercase()) }
    fun onDuration(days: Int) = _state.update { it.copy(durationDays = days) }

    // -----------------------------------------------------------------------
    // Filiallar — xaritadan
    // -----------------------------------------------------------------------

    /** "+" bosilganda xarita ochiladi. */
    fun openMap() = _state.update { it.copy(pickingOnMap = true) }

    fun closeMap() = _state.update {
        it.copy(pickingOnMap = false, searchQuery = "", searchResults = emptyList())
    }

    // -----------------------------------------------------------------------
    // Xaritadagi qidiruv
    // -----------------------------------------------------------------------

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
     * yoziladi va filial baribir qo'shiladi (nuqta yo'qolmasligi kerak).
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

    /** Filial nomi ("Chilonzor filiali") — ixtiyoriy. */
    fun onBranchName(index: Int, name: String) = _state.update { state ->
        state.copy(
            branches = state.branches.mapIndexed { i, branch ->
                if (i == index) branch.copy(name = name.ifBlank { null }) else branch
            },
        )
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    // -----------------------------------------------------------------------
    // Rasmlar
    // -----------------------------------------------------------------------

    fun addImage(bytes: ByteArray, fileName: String) {
        if (_state.value.images.size >= ListingValidator.MAX_IMAGES) {
            _state.update { it.copy(message = "Maksimal ${ListingValidator.MAX_IMAGES} ta rasm") }
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
                    _state.update { it.copy(message = "Qoralama saqlandi") }
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

                // Validatsiya xatolari maydonlarga bog'lanadi — forma ularni joyida ko'rsatadi.
                is PublishListingUseCase.Result.Invalid ->
                    _state.update { it.copy(submitting = false, errors = res.errors) }

                is PublishListingUseCase.Result.Failed ->
                    _state.update { it.copy(submitting = false, message = res.message) }
            }
        }
    }

    /** Forma holatidan domen modelini quradi. Tur tanlanmagan bo'lsa — `null`. */
    private fun buildListing(): Listing? {
        val s = _state.value
        val type = s.businessType ?: return null
        val ownerId = (user.value?.id ?: 0L).toString()
        val now = Clock.System.now().toEpochMilliseconds()

        return Listing(
            id = editingId ?: "lst-$ownerId-$now",
            ownerId = ownerId,
            businessId = editingBusinessId,
            businessType = type,
            businessName = s.businessName.trim(),
            categoryKey = s.categoryKey,
            customCategoryName = s.customCategoryName.trim().ifBlank { null },
            title = s.title.trim(),
            description = s.description.trim().ifBlank { null },
            images = s.images,
            priceUnit = s.priceUnit,
            originalPrice = s.originalPrice.toLongOrNull() ?: 0,
            discount = ListingDiscount(
                type = s.discountType,
                value = s.discountValue.toLongOrNull() ?: 0,
                conditions = s.conditions.trim().ifBlank { null },
            ),
            redemption = ListingRedemption(
                method = s.redemptionMethod,
                promoCode = s.promoCode.trim().ifBlank { null },
            ),
            branches = s.branches,
            validFrom = now,
            validTo = now + s.durationDays.toLong() * MILLIS_PER_DAY,
            status = ListingStatus.DRAFT,
            createdAt = editingCreatedAt ?: now,
            updatedAt = now,
        )
    }

    private fun Listing.toUiState() = PostListingUiState(
        step = PostListingStep.FORM,
        businessType = businessType,
        businessName = businessName,
        categoryKey = categoryKey,
        customCategoryName = customCategoryName.orEmpty(),
        title = title,
        description = description.orEmpty(),
        images = images,
        priceUnit = priceUnit,
        originalPrice = originalPrice.toString(),
        discountType = discount.type,
        discountValue = discount.value.toString(),
        conditions = discount.conditions.orEmpty(),
        redemptionMethod = redemption.method,
        promoCode = redemption.promoCode.orEmpty(),
        branches = branches,
        durationDays = ((validTo - validFrom) / MILLIS_PER_DAY).toInt().coerceAtLeast(1),
        editing = true,
    )

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
        const val SEARCH_DEBOUNCE_MS = 400L
    }
}

/** Raqamli maydonlarga faqat raqam kiritiladi (klaviatura turi kafolat bermaydi). */
private fun String.digits(): String = filter { it.isDigit() }

/** Tanlangan biznes turining kategoriyalari. */
fun PostListingUiState.categories() =
    businessType?.let { ListingCatalog.categories(it) }.orEmpty()

