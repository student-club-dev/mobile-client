package dev.feature.listings.presentation

import dev.feature.listings.domain.model.BusinessType
import dev.feature.listings.domain.model.DiscountType
import dev.feature.listings.domain.model.EmploymentType
import dev.feature.listings.domain.model.ExperienceLevel
import dev.feature.listings.domain.model.ListingCatalog
import dev.feature.listings.domain.model.ListingError
import dev.feature.listings.domain.model.ListingField
import dev.feature.listings.domain.model.ListingAudience
import dev.feature.listings.domain.model.ListingBranch
import dev.feature.listings.domain.model.ListingKind
import dev.feature.listings.domain.model.PayPeriod
import dev.feature.listings.domain.model.PriceUnit
import dev.feature.listings.domain.model.PropertyType
import dev.feature.listings.domain.model.RedemptionMethod
import dev.feature.listings.domain.model.RentPeriod
import dev.feature.listings.domain.model.ServiceCatalog
import dev.feature.listings.domain.model.ServiceFormat
import dev.feature.listings.domain.model.ServiceType
import dev.feature.listings.domain.model.TaskCatalog
import dev.feature.listings.domain.model.TaskCategory
import dev.feature.listings.domain.model.TaskFormat
import dev.feature.listings.domain.model.TenantGender
import dev.feature.listings.domain.model.WeekDay
import dev.feature.listings.domain.model.WorkShift
import dev.feature.listings.domain.repository.PlaceSuggestion

/**
 * E'lon qo'yish oqimining qadamlari.
 *
 * [KIND] — nima e'lon qilinmoqda (chegirma / ijara / xizmat / ish).
 * [TYPE] — faqat chegirmada: qaysi biznes turi. Boshqa turlarda bu qadam o'tkazib yuboriladi,
 *          chunki ularning "biznes turi" degan tushunchasi yo'q.
 * [FORM] — tanlangan turning formasi.
 */
enum class PostListingStep { KIND, TYPE, FORM }

/**
 * E'lon qo'yish formasining holati.
 *
 * Tuzilishi domendagi [dev.feature.listings.domain.model.ListingDetails] ni takrorlaydi:
 * umumiy maydonlar tepada, turga xoslari alohida holatlarda. Shu sabab ijara formasini
 * tahrirlaganda chegirma maydonlariga tegib ketish imkoni yo'q.
 *
 * Barcha raqamli maydonlar **matn** ko'rinishida — foydalanuvchi yozayotganda qisman
 * kiritishga yo'l qo'yish uchun ("5" → "55" → "55000"). Domen modeliga o'girish
 * [PostListingViewModel] dagi `buildListing` da bo'ladi.
 */
data class PostListingUiState(
    val step: PostListingStep = PostListingStep.KIND,
    val kind: ListingKind? = null,

    // --- Hamma turga umumiy ---
    val title: String = "",
    val description: String = "",
    val images: List<String> = emptyList(),
    val uploadingImage: Boolean = false,

    val priceUnit: PriceUnit = PriceUnit.PER_ITEM,
    val price: String = "",
    /** Narx oralig'ining yuqori chegarasi (ish e'lonidagi maosh vilkasi). */
    val priceMax: String = "",
    val isNegotiable: Boolean = false,
    val contactPhone: String = "",

    /** Manzillar — har biri xaritadan tanlangan (koordinatasi bor). */
    val branches: List<ListingBranch> = emptyList(),
    /** Xarita ochiqmi (yangi manzil belgilash uchun). */
    val pickingOnMap: Boolean = false,
    /** Xaritadan nuqta tanlandi, manzil aniqlanmoqda. */
    val resolvingAddress: Boolean = false,

    /** Xaritadagi qidiruv. */
    val searchQuery: String = "",
    val searchResults: List<PlaceSuggestion> = emptyList(),
    val searching: Boolean = false,

    val durationDays: Int = 30,

    /**
     * E'lonni kim ko'radi (§7.2.4). Tanlov FAQAT foydalanuvchining universiteti ma'lum
     * bo'lganda ko'rsatiladi ([hasUniversity]) — usiz "faqat universitetim" qaysi OTM
     * ekanini bilmaydi va e'lon hech kimga ko'rinmay qolardi.
     */
    val audience: ListingAudience = ListingAudience.ALL,
    val hasUniversity: Boolean = false,

    // --- Turga xos ---
    val discount: DiscountFormState = DiscountFormState(),
    val rental: RentalFormState = RentalFormState(),
    val service: ServiceFormState = ServiceFormState(),
    val job: JobFormState = JobFormState(),
    val task: TaskFormState = TaskFormState(),

    val errors: List<ListingError> = emptyList(),
    val submitting: Boolean = false,
    val published: Boolean = false,
    /** Bir martalik xabar (masalan rasm yuklashdagi xato). */
    val message: String? = null,
    val editing: Boolean = false,
) {
    fun errorFor(field: ListingField): String? = errors.firstOrNull { it.field == field }?.message

    /** Bir nechta maydonning birinchi xatosi — bitta bo'limda bir nechta maydon bo'lganda. */
    fun errorForAny(vararg fields: ListingField): String? =
        fields.firstNotNullOfOrNull { field -> errorFor(field) }
}

/** Chegirma / sotuv e'loni formasi. */
data class DiscountFormState(
    val businessType: BusinessType? = null,
    val businessName: String = "",
    val categoryKey: String = "",
    val customCategoryName: String = "",
    /** Kategoriyaga xos maydonlar ([ListingCatalog.categoryAttributes] kalitlari). */
    val attributeValues: Map<String, String> = emptyMap(),

    /** `true` — chegirma e'loni, `false` — oddiy e'lon (faqat narx). */
    val isDiscounted: Boolean = true,
    /** Rejim tashqaridan (tab) belgilangan — E'lon turi tanlovi yashiriladi. */
    val modeLocked: Boolean = false,

    val discountType: DiscountType = DiscountType.SPECIAL_PRICE,
    val discountValue: String = "",
    val conditions: String = "",

    val redemptionMethod: RedemptionMethod = RedemptionMethod.STUDENT_ID,
    val promoCode: String = "",
) {
    /** Tanlangan biznes turining kategoriyalari. */
    fun categories() = businessType?.let { ListingCatalog.categories(it) }.orEmpty()

    /** Tanlangan KATEGORIYAGA xos maydonlar (masalan Game Club > PlayStation). */
    fun categoryAttributes() =
        businessType?.let { ListingCatalog.categoryAttributes(it, categoryKey) }.orEmpty()
}

/** Ijara (turarjoy) formasi. */
data class RentalFormState(
    val propertyType: PropertyType? = null,
    val roomCount: String = "",
    val currentTenants: String = "",
    val neededTenants: String = "",
    /** MAJBURIY — `null` bo'lsa publish qilinmaydi. */
    val gender: TenantGender? = null,
    val period: RentPeriod = RentPeriod.MONTHLY,

    val utilitiesIncluded: Boolean = false,
    val depositMonths: String = "",
    val floor: String = "",
    val totalFloors: String = "",
    /** [dev.feature.listings.domain.model.RentalCatalog.AMENITIES] kalitlari. */
    val amenities: Set<String> = emptySet(),
    val availableFrom: Long? = null,
)

/** Xizmat formasi. */
data class ServiceFormState(
    val serviceType: ServiceType? = null,
    /** Soha ichidagi yo'nalish (fan, chop etish turi...). */
    val subjectKey: String = "",
    /** Yo'nalish "Boshqa" bo'lganda erkin nom. */
    val customSubject: String = "",
    /** Soha va yo'nalish maydonlarining qiymatlari. */
    val fields: Map<String, String> = emptyMap(),

    val format: ServiceFormat = ServiceFormat.OFFLINE,
    val experienceYears: String = "",
    val workingHours: String = "",
    val hasHomeVisit: Boolean = false,
    val hasFreeTrial: Boolean = false,
) {
    /** Tanlangan sohaning yo'nalishlari. */
    fun subjects() = serviceType?.let { ServiceCatalog.subjects(it) }.orEmpty()

    /** Soha maydonlari + tanlangan yo'nalishga xos maydonlar. */
    fun specs() = serviceType?.let { type ->
        ServiceCatalog.fields(type) + ServiceCatalog.subjectFields(type, subjectKey)
    }.orEmpty()
}

/** "Fanlardan yordam" — bir martalik topshiriq formasi. */
data class TaskFormState(
    val category: TaskCategory? = null,
    val typeKey: String = "",
    /** Tur "Boshqa" bo'lganda erkin nom. */
    val customTypeName: String = "",
    /** Topshirish muddati (epoch millis) — MAJBURIY, validator talab qiladi. */
    val deadline: Long? = null,
    val format: TaskFormat = TaskFormat.ONLINE,
    /** "12 ta masala", "15 bet" — ixtiyoriy, lekin bajaruvchi uchun juda muhim. */
    val volume: String = "",
) {
    /** "Boshqa" tanlanganda erkin nom maydoni ochiladi. */
    val needsCustomType: Boolean get() = typeKey == TaskCatalog.OTHER_KEY

    /** Tanlangan kategoriyaning turlari. */
    fun types() = category?.let { TaskCatalog.types(it) }.orEmpty()

    /** Yuzma-yuz bajariladigan ishdagina manzil so'raladi. */
    val needsLocation: Boolean get() = format == TaskFormat.IN_PERSON
}

/** Ish e'loni formasi. */
data class JobFormState(
    val employment: EmploymentType = EmploymentType.DAILY,
    val categoryKey: String = "",
    val customCategoryName: String = "",
    val companyName: String = "",

    val shift: WorkShift? = null,
    val days: Set<WeekDay> = emptySet(),
    val startTime: String = "",
    val endTime: String = "",
    val hoursPerDay: String = "",
    val payPeriod: PayPeriod = PayPeriod.DAILY,

    val vacancies: String = "",
    val gender: TenantGender? = null,
    val experience: ExperienceLevel = ExperienceLevel.NONE,
    val ageFrom: String = "",
    val ageTo: String = "",

    val requirements: List<String> = emptyList(),
    val benefits: List<String> = emptyList(),

    /** Kunlik ish uchun — qaysi kuni (epoch millis). */
    val workDate: Long? = null,
    val payoutNote: String = "",
) {
    val isDaily: Boolean get() = employment == EmploymentType.DAILY

    /** Erkin grafikda aniq vaqt so'ralmaydi — uning butun mohiyati shu. */
    val needsExactTime: Boolean get() = shift != WorkShift.FLEXIBLE
}
