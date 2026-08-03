package dev.feature.listings.data.mapper

import dev.core.database.sql.ListingEntity
import dev.feature.listings.domain.model.BusinessType
import dev.feature.listings.domain.model.DiscountType
import dev.feature.listings.domain.model.EmploymentType
import dev.feature.listings.domain.model.ExperienceLevel
import dev.feature.listings.domain.model.Listing
import dev.feature.listings.domain.model.ListingBranch
import dev.feature.listings.domain.model.ListingDetails
import dev.feature.listings.domain.model.ListingRedemption
import dev.feature.listings.domain.model.ListingStatus
import dev.feature.listings.domain.model.OptionGroup
import dev.feature.listings.domain.model.OptionItem
import dev.feature.listings.domain.model.PayPeriod
import dev.feature.listings.domain.model.PriceUnit
import dev.feature.listings.domain.model.PropertyType
import dev.feature.listings.domain.model.RedemptionMethod
import dev.feature.listings.domain.model.RentPeriod
import dev.feature.listings.domain.model.SelectionType
import dev.feature.listings.domain.model.ServiceFormat
import dev.feature.listings.domain.model.ServiceType
import dev.feature.listings.domain.model.TaskCategory
import dev.feature.listings.domain.model.TaskFormat
import dev.feature.listings.domain.model.TenantGender
import dev.feature.listings.domain.model.WeekDay
import dev.feature.listings.domain.model.WorkSchedule
import dev.feature.listings.domain.model.WorkShift
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Local DB (SQLDelight) ↔ domen modeli.
 *
 * Ro'yxatli va turga xos maydonlar JSON matn ustunlarida saqlanadi — shunda har yangi
 * e'lon turi qo'shilganda sxema o'zgarmaydi.
 *
 * `classDiscriminator = "kind"` — turga xos qism ([ListingDetails]) polimorf yoziladi:
 * `{"kind":"RENTAL","roomCount":3,...}`. Ajratgichning nomi domendagi `kind` bilan bir xil,
 * shuning uchun JSON'ni qo'lda o'qiganda ham qaysi tur ekani ko'rinib turadi (migratsiya
 * skripti ham aynan shu shaklni yozadi).
 */
private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "kind"
}

// ---------------------------------------------------------------------------
// JSON ustunlarining DTO'lari — faqat shu faylda ishlatiladi.
// ---------------------------------------------------------------------------

@Serializable
private data class OptionGroupJson(
    val name: String,
    val selectionType: String = "SINGLE",
    val isRequired: Boolean = false,
    val options: List<OptionJson> = emptyList(),
)

@Serializable
private data class OptionJson(
    val name: String,
    val priceDelta: Long = 0,
    val isAvailable: Boolean = true,
)

@Serializable
private data class BranchJson(
    val id: String,
    val lat: Double,
    val lng: Double,
    val address: String,
    val name: String? = null,
    val landmark: String? = null,
    val regionId: String? = null,
    val districtId: String? = null,
)

/**
 * Turga xos qismning saqlanadigan shakli. Domen modelidan alohida, chunki domen enum bilan
 * ishlaydi, saqlashda esa matn kerak: enum qayta nomlansa yoki qiymat olib tashlansa,
 * eski qatorlar o'qilishda dasturni yiqitmasligi kerak (`toEnum` fallback bilan).
 */
@Serializable
private sealed interface DetailsJson {

    @Serializable
    @SerialName("DISCOUNT")
    data class Discount(
        val businessType: String = BusinessType.CAFE_RESTAURANT.name,
        val businessName: String = "",
        val categoryKey: String = "",
        val customCategoryName: String? = null,
        val isDiscounted: Boolean = true,
        val discountType: String = DiscountType.SPECIAL_PRICE.name,
        val discountValue: Long = 0,
        val conditions: String? = null,
        val appliesToOptions: Boolean = false,
        val redemptionMethod: String = RedemptionMethod.STUDENT_ID.name,
        val promoCode: String? = null,
        val perUserLimit: Int? = null,
        val totalLimit: Int? = null,
        val usedCount: Int = 0,
    ) : DetailsJson

    @Serializable
    @SerialName("RENTAL")
    data class Rental(
        val propertyType: String? = null,
        val roomCount: Int? = null,
        val currentTenants: Int? = null,
        val neededTenants: Int? = null,
        val gender: String? = null,
        val period: String = RentPeriod.MONTHLY.name,
        val utilitiesIncluded: Boolean = false,
        val depositMonths: Int? = null,
        val floor: Int? = null,
        val totalFloors: Int? = null,
        val amenities: List<String> = emptyList(),
        val availableFrom: Long? = null,
    ) : DetailsJson

    @Serializable
    @SerialName("SERVICE")
    data class Service(
        val serviceType: String? = null,
        val fields: Map<String, String> = emptyMap(),
        val format: String = ServiceFormat.OFFLINE.name,
        val experienceYears: Int? = null,
        val workingHours: String? = null,
        val hasHomeVisit: Boolean = false,
        val hasFreeTrial: Boolean = false,
    ) : DetailsJson

    @Serializable
    @SerialName("JOB")
    data class Job(
        val employment: String = EmploymentType.DAILY.name,
        val categoryKey: String = "",
        val companyName: String = "",
        val shift: String? = null,
        val scheduleDays: List<String> = emptyList(),
        val startTime: String? = null,
        val endTime: String? = null,
        val hoursPerDay: Int? = null,
        val payPeriod: String = PayPeriod.DAILY.name,
        val vacancies: Int? = null,
        val gender: String? = null,
        val experience: String = ExperienceLevel.NONE.name,
        val ageFrom: Int? = null,
        val ageTo: Int? = null,
        val requirements: List<String> = emptyList(),
        val benefits: List<String> = emptyList(),
        val workDate: Long? = null,
        val payoutNote: String? = null,
    ) : DetailsJson

    @Serializable
    @SerialName("TASK")
    data class Task(
        val category: String? = null,
        val typeKey: String = "",
        val customTypeName: String? = null,
        val deadline: Long? = null,
        val format: String = TaskFormat.ONLINE.name,
        val volume: String? = null,
    ) : DetailsJson
}

// ---------------------------------------------------------------------------
// DB -> domen
// ---------------------------------------------------------------------------

fun ListingEntity.toDomain(): Listing = Listing(
    id = id,
    ownerId = ownerId,
    businessId = businessId,
    details = detailsJson.decodeDetails(),
    title = title,
    description = description,
    images = imagesJson.decodeList(),
    priceUnit = priceUnit.toEnum(PriceUnit.entries, PriceUnit.PER_ITEM),
    price = price,
    priceMax = priceMax,
    currency = currency,
    isNegotiable = isNegotiable != 0L,
    contactPhone = contactPhone,
    universityId = universityId,
    branches = branchesJson.decodeBranches(),
    validFrom = validFrom,
    validTo = validTo,
    attributes = attributesJson.decodeMap(),
    optionGroups = optionGroupsJson.decodeGroups(),
    status = status.toEnum(ListingStatus.entries, ListingStatus.DRAFT),
    rejectionReason = rejectionReason,
    viewsCount = viewsCount.toInt(),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/**
 * Turga xos qismni o'qiydi. JSON buzilgan bo'lsa (qo'lda tahrir, yarim yozilgan qator)
 * e'lonni **yo'qotmaymiz** — bo'sh chegirma tafsilotiga tushiramiz va foydalanuvchi uni
 * tahrirlab to'g'rilay oladi. Bitta buzuq qator butun ro'yxatni yiqitmasligi kerak.
 */
private fun String.decodeDetails(): ListingDetails =
    runCatching { json.decodeFromString<DetailsJson>(this) }
        .getOrNull()
        ?.toDomain()
        ?: ListingDetails.Discount(businessType = BusinessType.CAFE_RESTAURANT)

private fun DetailsJson.toDomain(): ListingDetails = when (this) {
    is DetailsJson.Discount -> ListingDetails.Discount(
        businessType = businessType.toEnum(BusinessType.entries, BusinessType.CAFE_RESTAURANT),
        businessName = businessName,
        categoryKey = categoryKey,
        customCategoryName = customCategoryName,
        isDiscounted = isDiscounted,
        discountType = discountType.toEnum(DiscountType.entries, DiscountType.SPECIAL_PRICE),
        discountValue = discountValue,
        conditions = conditions,
        appliesToOptions = appliesToOptions,
        redemption = ListingRedemption(
            method = redemptionMethod.toEnum(RedemptionMethod.entries, RedemptionMethod.STUDENT_ID),
            promoCode = promoCode,
            perUserLimit = perUserLimit,
            totalLimit = totalLimit,
            usedCount = usedCount,
        ),
    )

    is DetailsJson.Rental -> ListingDetails.Rental(
        propertyType = propertyType?.toEnumOrNull(PropertyType.entries),
        roomCount = roomCount,
        currentTenants = currentTenants,
        neededTenants = neededTenants,
        gender = gender?.toEnumOrNull(TenantGender.entries),
        period = period.toEnum(RentPeriod.entries, RentPeriod.MONTHLY),
        utilitiesIncluded = utilitiesIncluded,
        depositMonths = depositMonths,
        floor = floor,
        totalFloors = totalFloors,
        amenities = amenities,
        availableFrom = availableFrom,
    )

    is DetailsJson.Service -> ListingDetails.Service(
        serviceType = serviceType?.toEnumOrNull(ServiceType.entries),
        fields = fields,
        format = format.toEnum(ServiceFormat.entries, ServiceFormat.OFFLINE),
        experienceYears = experienceYears,
        workingHours = workingHours,
        hasHomeVisit = hasHomeVisit,
        hasFreeTrial = hasFreeTrial,
    )

    is DetailsJson.Task -> ListingDetails.Task(
        category = category?.let { c -> TaskCategory.entries.firstOrNull { it.name == c } },
        typeKey = typeKey,
        customTypeName = customTypeName,
        deadline = deadline,
        format = format.toEnum(TaskFormat.entries, TaskFormat.ONLINE),
        volume = volume,
    )

    is DetailsJson.Job -> ListingDetails.Job(
        employment = employment.toEnum(EmploymentType.entries, EmploymentType.DAILY),
        categoryKey = categoryKey,
        companyName = companyName,
        shift = shift?.toEnumOrNull(WorkShift.entries),
        schedule = WorkSchedule(
            days = scheduleDays.mapNotNull { it.toEnumOrNull(WeekDay.entries) },
            startTime = startTime,
            endTime = endTime,
            hoursPerDay = hoursPerDay,
        ),
        payPeriod = payPeriod.toEnum(PayPeriod.entries, PayPeriod.DAILY),
        vacancies = vacancies,
        gender = gender?.toEnumOrNull(TenantGender.entries),
        experience = experience.toEnum(ExperienceLevel.entries, ExperienceLevel.NONE),
        ageFrom = ageFrom,
        ageTo = ageTo,
        requirements = requirements,
        benefits = benefits,
        workDate = workDate,
        payoutNote = payoutNote,
    )
}

// ---------------------------------------------------------------------------
// Domen -> DB
// ---------------------------------------------------------------------------

/** Domen → DB ustunlari. `finalPrice` shu yerda hisoblanadi (domen formulasi bilan). */
fun Listing.toEntity(): ListingEntity = ListingEntity(
    id = id,
    ownerId = ownerId,
    businessId = businessId,
    kind = kind.name,
    detailsJson = json.encodeToString(details.toJson()),
    title = title,
    description = description,
    imagesJson = json.encodeToString(images),
    priceUnit = priceUnit.name,
    price = price,
    priceMax = priceMax,
    currency = currency,
    isNegotiable = if (isNegotiable) 1L else 0L,
    finalPrice = finalPrice,
    contactPhone = contactPhone,
    universityId = universityId,
    branchesJson = json.encodeToString(
        branches.map { branch ->
            BranchJson(
                id = branch.id,
                lat = branch.lat,
                lng = branch.lng,
                address = branch.address,
                name = branch.name,
                landmark = branch.landmark,
                regionId = branch.regionId,
                districtId = branch.districtId,
            )
        },
    ),
    validFrom = validFrom,
    validTo = validTo,
    attributesJson = json.encodeToString(attributes),
    optionGroupsJson = json.encodeToString(
        optionGroups.map { group ->
            OptionGroupJson(
                name = group.name,
                selectionType = group.selectionType.name,
                isRequired = group.isRequired,
                options = group.options.map { OptionJson(it.name, it.priceDelta, it.isAvailable) },
            )
        },
    ),
    status = status.name,
    rejectionReason = rejectionReason,
    viewsCount = viewsCount.toLong(),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun ListingDetails.toJson(): DetailsJson = when (this) {
    is ListingDetails.Discount -> DetailsJson.Discount(
        businessType = businessType.name,
        businessName = businessName,
        categoryKey = categoryKey,
        customCategoryName = customCategoryName,
        isDiscounted = isDiscounted,
        discountType = discountType.name,
        discountValue = discountValue,
        conditions = conditions,
        appliesToOptions = appliesToOptions,
        redemptionMethod = redemption.method.name,
        promoCode = redemption.promoCode,
        perUserLimit = redemption.perUserLimit,
        totalLimit = redemption.totalLimit,
        usedCount = redemption.usedCount,
    )

    is ListingDetails.Rental -> DetailsJson.Rental(
        propertyType = propertyType?.name,
        roomCount = roomCount,
        currentTenants = currentTenants,
        neededTenants = neededTenants,
        gender = gender?.name,
        period = period.name,
        utilitiesIncluded = utilitiesIncluded,
        depositMonths = depositMonths,
        floor = floor,
        totalFloors = totalFloors,
        amenities = amenities,
        availableFrom = availableFrom,
    )

    is ListingDetails.Service -> DetailsJson.Service(
        serviceType = serviceType?.name,
        fields = fields,
        format = format.name,
        experienceYears = experienceYears,
        workingHours = workingHours,
        hasHomeVisit = hasHomeVisit,
        hasFreeTrial = hasFreeTrial,
    )

    is ListingDetails.Task -> DetailsJson.Task(
        category = category?.name,
        typeKey = typeKey,
        customTypeName = customTypeName,
        deadline = deadline,
        format = format.name,
        volume = volume,
    )

    is ListingDetails.Job -> DetailsJson.Job(
        employment = employment.name,
        categoryKey = categoryKey,
        companyName = companyName,
        shift = shift?.name,
        scheduleDays = schedule.days.map { it.name },
        startTime = schedule.startTime,
        endTime = schedule.endTime,
        hoursPerDay = schedule.hoursPerDay,
        payPeriod = payPeriod.name,
        vacancies = vacancies,
        gender = gender?.name,
        experience = experience.name,
        ageFrom = ageFrom,
        ageTo = ageTo,
        requirements = requirements,
        benefits = benefits,
        workDate = workDate,
        payoutNote = payoutNote,
    )
}

// ---------------------------------------------------------------------------
// Yordamchilar
// ---------------------------------------------------------------------------

private inline fun <reified T : Enum<T>> String.toEnum(entries: List<T>, fallback: T): T =
    entries.firstOrNull { it.name == this } ?: fallback

/** Nomi mos kelmasa `null` — "tanlanmagan" va "noto'g'ri yozuv" bir xil ko'riladi. */
private inline fun <reified T : Enum<T>> String.toEnumOrNull(entries: List<T>): T? =
    entries.firstOrNull { it.name == this }

private fun String.decodeList(): List<String> =
    runCatching { json.decodeFromString<List<String>>(this) }.getOrDefault(emptyList())

private fun String.decodeMap(): Map<String, String> =
    runCatching { json.decodeFromString<Map<String, String>>(this) }.getOrDefault(emptyMap())

private fun String.decodeBranches(): List<ListingBranch> =
    runCatching { json.decodeFromString<List<BranchJson>>(this) }
        .getOrDefault(emptyList())
        .map { branch ->
            ListingBranch(
                id = branch.id,
                lat = branch.lat,
                lng = branch.lng,
                address = branch.address,
                name = branch.name,
                landmark = branch.landmark,
                regionId = branch.regionId,
                districtId = branch.districtId,
            )
        }

private fun String.decodeGroups(): List<OptionGroup> =
    runCatching { json.decodeFromString<List<OptionGroupJson>>(this) }
        .getOrDefault(emptyList())
        .map { group ->
            OptionGroup(
                name = group.name,
                selectionType = group.selectionType.toEnum(SelectionType.entries, SelectionType.SINGLE),
                isRequired = group.isRequired,
                options = group.options.map { OptionItem(it.name, it.priceDelta, it.isAvailable) },
            )
        }
