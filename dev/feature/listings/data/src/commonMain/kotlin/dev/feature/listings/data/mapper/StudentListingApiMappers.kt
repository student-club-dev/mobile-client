package dev.feature.listings.data.mapper

import dev.core.network.generated.model.CreateStudentListingDto
import dev.core.network.generated.model.ListingBranchDto
import dev.core.network.generated.model.ListingOptionDto
import dev.core.network.generated.model.ListingOptionGroupDto
import dev.core.network.generated.model.ListingSearchPageDto
import dev.core.network.generated.model.SetListingStatusDto
import dev.core.network.generated.model.StudentListingDto
import dev.core.network.generated.model.StudentListingPageDto
import dev.core.network.generated.model.UpdateStudentListingDto
import dev.core.network.media.MediaUrl
import dev.feature.listings.domain.model.EmploymentType
import dev.feature.listings.domain.model.ExperienceLevel
import dev.feature.listings.domain.model.Listing
import dev.feature.listings.domain.model.ListingBranch
import dev.feature.listings.domain.model.ListingDetails
import dev.feature.listings.domain.model.ListingKind
import dev.feature.listings.domain.model.ListingPage
import dev.feature.listings.domain.model.ListingStatus
import dev.feature.listings.domain.model.OptionGroup
import dev.feature.listings.domain.model.OptionItem
import dev.feature.listings.domain.model.PayPeriod
import dev.feature.listings.domain.model.PriceUnit
import dev.feature.listings.domain.model.PropertyType
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
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Talaba e'loni: generatsiya qilingan DTO ↔ domen modeli
 * (`STUDENT_LISTINGS_BACKEND.md` §2 va §4).
 *
 * Ikki narsa local bazadagi mapperdan ([ListingMappers.kt]) farq qiladi va aynan shu sabab
 * bu alohida fayl:
 *
 * 1. **Sana** — serverda ISO-8601 matn, domenda epoch millis.
 * 2. **`details`** — serverda polimorf JSON obyekt (ajratgich `kind`), `schedule` esa ichma-ich
 *    obyekt. Local ustunda esa u yassi saqlanadi (`scheduleDays`, `startTime`…).
 *
 * `details` generatorda [JsonObject] bo'lib chiqadi — `oneOf` dan ishlatib bo'ladigan Kotlin
 * kodi chiqmaydi va backend spec'da tur sxemalarini umuman e'lon qilmagan. Shakl shu yerda,
 * qo'lda, spec §4 bo'yicha tasvirlangan.
 */
private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    classDiscriminator = "kind"
}

// ---------------------------------------------------------------------------
// `details` — sim ustidagi shakl (§4)
// ---------------------------------------------------------------------------

/**
 * Turga xos qism. Enum'lar **matn** sifatida: server ro'yxatni kengaytirsa (yangi xizmat
 * sohasi, yangi ish kategoriyasi) noma'lum qiymat butun e'lonni pars qilinmaydigan qilib
 * qo'ymasligi kerak — domenga o'tishda `toEnumOrNull` bilan o'qiladi.
 */
@Serializable
private sealed interface DetailsWire {

    @Serializable
    @SerialName("TASK")
    data class Task(
        val category: String? = null,
        val typeKey: String = "",
        val customTypeName: String? = null,
        /** ISO-8601. */
        val deadline: String? = null,
        val format: String = TaskFormat.ONLINE.name,
        val volume: String? = null,
    ) : DetailsWire

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
        /** ISO-8601. */
        val availableFrom: String? = null,
    ) : DetailsWire

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
    ) : DetailsWire

    @Serializable
    @SerialName("JOB")
    data class Job(
        val employment: String = EmploymentType.DAILY.name,
        val categoryKey: String = "",
        val companyName: String = "",
        val shift: String? = null,
        val schedule: ScheduleWire = ScheduleWire(),
        val payPeriod: String = PayPeriod.DAILY.name,
        val vacancies: Int? = null,
        val gender: String? = null,
        val experience: String = ExperienceLevel.NONE.name,
        val ageFrom: Int? = null,
        val ageTo: Int? = null,
        val requirements: List<String> = emptyList(),
        val benefits: List<String> = emptyList(),
        /** ISO-8601 — `DAILY` ishda majburiy, `PERMANENT` da `null`. */
        val workDate: String? = null,
        val payoutNote: String? = null,
    ) : DetailsWire
}

/** Ish grafigi — serverda ichma-ich obyekt (`details.schedule`). */
@Serializable
private data class ScheduleWire(
    val days: List<String> = emptyList(),
    /** "HH:mm" matn. */
    val startTime: String? = null,
    val endTime: String? = null,
    val hoursPerDay: Int? = null,
)

/**
 * Xom `details` ni domenga o'giradi.
 *
 * Pars bo'lmasa (noma'lum `kind`, kutilmagan shakl) e'lon **yo'qotilmaydi** — bo'sh, lekin
 * to'g'ri turdagi tafsilotga tushiriladi. Bitta g'alati e'lon butun ro'yxatni yiqitmasligi
 * kerak, foydalanuvchi esa qolgan maydonlarni (sarlavha, narx, manzil) baribir ko'radi.
 */
private fun JsonObject.toDetails(kind: ListingKind): ListingDetails =
    runCatching { json.decodeFromJsonElement(DetailsWire.serializer(), this) }
        .getOrNull()
        ?.toDomain()
        ?: kind.emptyDetails()

private fun ListingKind.emptyDetails(): ListingDetails = when (this) {
    ListingKind.TASK -> ListingDetails.Task()
    ListingKind.RENTAL -> ListingDetails.Rental()
    ListingKind.SERVICE -> ListingDetails.Service()
    ListingKind.JOB -> ListingDetails.Job()
    // Chegirma talaba e'loni EMAS (server `kind` enum'ida u yo'q) — bu yo'lga tushmaydi.
    ListingKind.DISCOUNT -> ListingDetails.Rental()
}

private fun DetailsWire.toDomain(): ListingDetails = when (this) {
    is DetailsWire.Task -> ListingDetails.Task(
        category = category?.toEnumOrNull(TaskCategory.entries),
        typeKey = typeKey,
        customTypeName = customTypeName,
        deadline = deadline.toEpochMillisOrNull(),
        format = format.toEnum(TaskFormat.entries, TaskFormat.ONLINE),
        volume = volume,
    )

    is DetailsWire.Rental -> ListingDetails.Rental(
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
        availableFrom = availableFrom.toEpochMillisOrNull(),
    )

    is DetailsWire.Service -> ListingDetails.Service(
        serviceType = serviceType?.toEnumOrNull(ServiceType.entries),
        fields = fields,
        format = format.toEnum(ServiceFormat.entries, ServiceFormat.OFFLINE),
        experienceYears = experienceYears,
        workingHours = workingHours,
        hasHomeVisit = hasHomeVisit,
        hasFreeTrial = hasFreeTrial,
    )

    is DetailsWire.Job -> ListingDetails.Job(
        employment = employment.toEnum(EmploymentType.entries, EmploymentType.DAILY),
        categoryKey = categoryKey,
        companyName = companyName,
        shift = shift?.toEnumOrNull(WorkShift.entries),
        schedule = WorkSchedule(
            days = schedule.days.mapNotNull { it.toEnumOrNull(WeekDay.entries) },
            startTime = schedule.startTime,
            endTime = schedule.endTime,
            hoursPerDay = schedule.hoursPerDay,
        ),
        payPeriod = payPeriod.toEnum(PayPeriod.entries, PayPeriod.DAILY),
        vacancies = vacancies,
        gender = gender?.toEnumOrNull(TenantGender.entries),
        experience = experience.toEnum(ExperienceLevel.entries, ExperienceLevel.NONE),
        ageFrom = ageFrom,
        ageTo = ageTo,
        requirements = requirements,
        benefits = benefits,
        workDate = workDate.toEpochMillisOrNull(),
        payoutNote = payoutNote,
    )
}

/** Domen → xom `details`. Chegirma bu API'ga umuman yuborilmaydi (§1). */
private fun ListingDetails.toWire(): JsonObject? {
    val wire: DetailsWire = when (this) {
        is ListingDetails.Task -> DetailsWire.Task(
            category = category?.name,
            typeKey = typeKey,
            customTypeName = customTypeName,
            deadline = deadline.toIsoOrNull(),
            format = format.name,
            volume = volume,
        )

        is ListingDetails.Rental -> DetailsWire.Rental(
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
            availableFrom = availableFrom.toIsoOrNull(),
        )

        is ListingDetails.Service -> DetailsWire.Service(
            serviceType = serviceType?.name,
            fields = fields,
            format = format.name,
            experienceYears = experienceYears,
            workingHours = workingHours,
            hasHomeVisit = hasHomeVisit,
            hasFreeTrial = hasFreeTrial,
        )

        is ListingDetails.Job -> DetailsWire.Job(
            employment = employment.name,
            categoryKey = categoryKey,
            companyName = companyName,
            shift = shift?.name,
            schedule = ScheduleWire(
                days = schedule.days.map { it.name },
                startTime = schedule.startTime,
                endTime = schedule.endTime,
                hoursPerDay = schedule.hoursPerDay,
            ),
            payPeriod = payPeriod.name,
            vacancies = vacancies,
            gender = gender?.name,
            experience = experience.name,
            ageFrom = ageFrom,
            ageTo = ageTo,
            requirements = requirements,
            benefits = benefits,
            workDate = workDate.toIsoOrNull(),
            payoutNote = payoutNote,
        )

        is ListingDetails.Discount -> return null
    }
    return json.encodeToJsonElement<DetailsWire>(wire) as JsonObject
}

// ---------------------------------------------------------------------------
// Javob → domen
// ---------------------------------------------------------------------------

/**
 * [apiOrigin] — rasm havolalarini to'liq holga keltirish uchun: backend ularni ba'zan
 * nisbiy (`/uploads/…`) yoki `localhost` bilan qaytaradi va Coil bunday havolani ocholmaydi.
 */
fun StudentListingDto.toDomain(apiOrigin: String): Listing {
    val listingKind = kind.toDomainKind()
    val created = createdAt.toEpochMilliseconds()
    return Listing(
        id = id,
        ownerId = ownerId,
        // Talaba e'lonining biznesi yo'q — u faqat chegirma tomonida bo'ladi.
        businessId = null,
        details = details.toDetails(listingKind),
        title = title,
        description = description,
        images = images.mapNotNull { MediaUrl.normalize(it, apiOrigin) },
        priceUnit = priceUnit?.value?.toEnumOrNull(PriceUnit.entries) ?: listingKind.defaultPriceUnit(),
        price = price.toLong(),
        priceMax = priceMax?.toLong(),
        currency = currency,
        isNegotiable = isNegotiable,
        contactPhone = contactPhone,
        universityId = universityId,
        branches = branches.map { branch ->
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
        },
        // Qoralamada muddat hali qo'yilmagan bo'lishi mumkin. Keshdagi "faol e'lonlar"
        // so'rovi `validTo >= now` bo'yicha filtrlaydi, shuning uchun bo'sh muddat
        // serverning o'z maksimumi (90 kun) bilan to'ldiriladi — aks holda e'lon
        // keshga tushishi bilan "muddati o'tgan" bo'lib qolardi.
        validFrom = validFrom?.toEpochMilliseconds() ?: created,
        validTo = validTo?.toEpochMilliseconds() ?: (created + MAX_VALIDITY_MILLIS),
        attributes = attributes,
        optionGroups = optionGroups.map { group ->
            OptionGroup(
                name = group.name,
                selectionType = group.selectionType.value
                    .toEnum(SelectionType.entries, SelectionType.SINGLE),
                isRequired = group.isRequired,
                options = group.options.map { OptionItem(it.name, it.priceDelta.toLong(), it.isAvailable) },
            )
        },
        status = status.value.toEnum(ListingStatus.entries, ListingStatus.DRAFT),
        rejectionReason = rejectionReason,
        viewsCount = viewsCount,
        createdAt = created,
        updatedAt = updatedAt.toEpochMilliseconds(),
    )
}

fun ListingSearchPageDto.toDomain(apiOrigin: String): ListingPage = ListingPage(
    items = items.map { it.toDomain(apiOrigin) },
    hasNext = hasNext,
    nextCursor = nextCursor,
    total = total,
)

fun StudentListingPageDto.toDomain(apiOrigin: String): ListingPage = ListingPage(
    items = items.map { it.toDomain(apiOrigin) },
    hasNext = hasNext,
    nextCursor = null,
    total = total,
)

// ---------------------------------------------------------------------------
// Domen → so'rov
// ---------------------------------------------------------------------------

/**
 * Yaratish so'rovi. [submit] `true` bo'lsa server to'liq validatsiya qiladi va e'lon
 * o'sha so'rovning o'zida faol bo'ladi; `false` — validatsiyasiz qoralama.
 *
 * `audience` **yuborilmaydi**: `MY_UNIVERSITY` / `NEARBY_UNIVERSITIES` Faza 2 gacha
 * amalda emas va ularni yuborish e'lonni egasi mo'ljallaganidan kengroq ko'rsatardi.
 * `ownerId` ham yuborilmaydi — server uni token'dan oladi.
 *
 * Turi serverga mos kelmasa (chegirma) — `null`.
 */
fun Listing.toCreateDto(submit: Boolean): CreateStudentListingDto? {
    val apiKind = kind.toApiCreateKind() ?: return null
    val wire = details.toWire() ?: return null
    return CreateStudentListingDto(
        kind = apiKind,
        details = wire,
        submit = submit,
        title = title.ifBlank { null },
        description = description,
        images = images,
        priceUnit = priceUnit.toCreateUnit(),
        price = price.toWirePrice(),
        priceMax = priceMax?.toWirePrice(),
        isNegotiable = isNegotiable,
        contactPhone = contactPhone,
        universityId = universityId,
        branches = branches.map { it.toDto() },
        validFrom = validFrom.toIsoOrNull(),
        validTo = validTo.toIsoOrNull(),
        attributes = attributes,
        optionGroups = optionGroups.map { it.toDto() },
    )
}

/** Tahrirlash. `kind` **yuborilmaydi** — u o'zgarmas, yuborilsa `409 LISTING_KIND_IMMUTABLE`. */
fun Listing.toUpdateDto(): UpdateStudentListingDto? {
    val wire = details.toWire() ?: return null
    return UpdateStudentListingDto(
        details = wire,
        title = title.ifBlank { null },
        description = description,
        images = images,
        priceUnit = priceUnit.toUpdateUnit(),
        price = price.toWirePrice(),
        priceMax = priceMax?.toWirePrice(),
        isNegotiable = isNegotiable,
        contactPhone = contactPhone,
        universityId = universityId,
        branches = branches.map { it.toDto() },
        validFrom = validFrom.toIsoOrNull(),
        validTo = validTo.toIsoOrNull(),
        attributes = attributes,
        optionGroups = optionGroups.map { it.toDto() },
    )
}

/** `POST /{id}/status` faqat shu uchtasini qabul qiladi; qolgani serverning ishi. */
fun ListingStatus.toStatusDto(): SetListingStatusDto? = when (this) {
    ListingStatus.ACTIVE -> SetListingStatusDto.Status.ACTIVE
    ListingStatus.PAUSED -> SetListingStatusDto.Status.PAUSED
    ListingStatus.ARCHIVED -> SetListingStatusDto.Status.ARCHIVED
    else -> null
}?.let { SetListingStatusDto(it) }

private fun ListingBranch.toDto() = ListingBranchDto(
    lat = lat,
    lng = lng,
    address = address,
    name = name,
    landmark = landmark,
    regionId = regionId,
    districtId = districtId,
)

private fun OptionGroup.toDto() = ListingOptionGroupDto(
    name = name,
    selectionType = when (selectionType) {
        SelectionType.SINGLE -> ListingOptionGroupDto.SelectionType.SINGLE
        SelectionType.MULTIPLE -> ListingOptionGroupDto.SelectionType.MULTIPLE
    },
    isRequired = isRequired,
    options = options.map { ListingOptionDto(it.name, it.priceDelta.toInt(), it.isAvailable) },
)

// ---------------------------------------------------------------------------
// Enum ko'priklari
// ---------------------------------------------------------------------------

private fun StudentListingDto.Kind.toDomainKind(): ListingKind = when (this) {
    StudentListingDto.Kind.RENTAL -> ListingKind.RENTAL
    StudentListingDto.Kind.SERVICE -> ListingKind.SERVICE
    StudentListingDto.Kind.JOB -> ListingKind.JOB
    StudentListingDto.Kind.TASK -> ListingKind.TASK
}

/** Chegirma — biznes tomonining shartnomasi, talaba e'loni API'sida bunday tur yo'q. */
private fun ListingKind.toApiCreateKind(): CreateStudentListingDto.Kind? = when (this) {
    ListingKind.RENTAL -> CreateStudentListingDto.Kind.RENTAL
    ListingKind.SERVICE -> CreateStudentListingDto.Kind.SERVICE
    ListingKind.JOB -> CreateStudentListingDto.Kind.JOB
    ListingKind.TASK -> CreateStudentListingDto.Kind.TASK
    ListingKind.DISCOUNT -> null
}

private fun PriceUnit.toCreateUnit(): CreateStudentListingDto.PriceUnit? =
    CreateStudentListingDto.PriceUnit.entries.firstOrNull { it.value == name }

private fun PriceUnit.toUpdateUnit(): UpdateStudentListingDto.PriceUnit? =
    UpdateStudentListingDto.PriceUnit.entries.firstOrNull { it.value == name }

/** Server narx birligini bermasa — bo'limning eng tabiiy birligi. */
private fun ListingKind.defaultPriceUnit(): PriceUnit = when (this) {
    ListingKind.RENTAL -> PriceUnit.PER_MONTH
    ListingKind.SERVICE -> PriceUnit.PER_HOUR
    ListingKind.JOB -> PriceUnit.PER_MONTH
    else -> PriceUnit.PER_ITEM
}

// ---------------------------------------------------------------------------
// Yordamchilar
// ---------------------------------------------------------------------------

/** Serverning o'z maksimal amal muddati (§6) — bo'sh `validTo` uchun zaxira. */
private const val MAX_VALIDITY_MILLIS = 90L * 24 * 60 * 60 * 1000

/**
 * Narx domenda `Long`, spec'da esa formatsiz `integer` — ya'ni `Int` (§2.2 da `int64`
 * deyilgan, kelishmovchilik backendga yozildi).
 *
 * Oddiy `toInt()` chegaradan oshganda **manfiy** songa aylanadi va e'lon "−2 mlrd so'm"
 * bo'lib ketardi. Talaba e'lonlarida 2.1 mlrd so'mga yetadigan narx amalda yo'q, lekin
 * kesib qo'yish hech bo'lmasa ma'noli xato — server uni `422` bilan qaytaradi.
 */
private fun Long.toWirePrice(): Int = coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

/** Buzuq sana butun e'lonni yiqitmasin — "berilmagan" deb o'qiladi. */
private fun String?.toEpochMillisOrNull(): Long? =
    this?.takeIf { it.isNotBlank() }
        ?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }

private fun Long?.toIsoOrNull(): String? =
    this?.let { runCatching { Instant.fromEpochMilliseconds(it).toString() }.getOrNull() }

private inline fun <reified T : Enum<T>> String.toEnum(entries: List<T>, fallback: T): T =
    entries.firstOrNull { it.name == this } ?: fallback

private inline fun <reified T : Enum<T>> String.toEnumOrNull(entries: List<T>): T? =
    entries.firstOrNull { it.name == this }
