package dev.feature.listings.domain.model

/**
 * E'lonning **turi** — e'lon qo'yish oqimining birinchi qadami va butun formaning shakli
 * shunga bog'liq.
 *
 * Nega [BusinessType] dan alohida: BusinessType — "chegirma beruvchi biznes qanaqa"
 * (kafe, game club...). Bu esa boshqa o'q: ijara e'lonini biznes emas, talabaning o'zi
 * qo'yadi va uning maydonlari (nechi xonalik, nechi kishi kerak) chegirma maydonlariga
 * umuman o'xshamaydi. Ikkalasini bitta enum'ga qo'shish — kafeda "nechi xonalik" so'rash
 * demakdir.
 */
enum class ListingKind(
    val label: String,
    val emoji: String,
    val accent: Long,
    val subtitle: String,
) {
    DISCOUNT(
        label = "Chegirma va sotuv",
        emoji = "🏷️",
        accent = 0xFF7C5CFF,
        subtitle = "Biznes talabaga chegirma yoki mahsulot taklif qiladi",
    ),
    RENTAL(
        label = "Ijara — turarjoy",
        emoji = "🏠",
        accent = 0xFF22C55E,
        subtitle = "Kvartira, hovli yoki yotoqxonaga sherik izlash",
    ),
    SERVICE(
        label = "Xizmatlar",
        emoji = "🛠️",
        accent = 0xFF3B82F6,
        subtitle = "Repetitor, chop etish, dizayn va boshqa xizmatlar",
    ),
    JOB(
        label = "Ish e'loni",
        emoji = "💼",
        accent = 0xFFF97316,
        subtitle = "Kunlik ish yoki doimiy ish o'rni",
    ),
    TASK(
        label = "Fanlardan yordam",
        emoji = "📚",
        accent = 0xFFEC4899,
        subtitle = "Bir martalik topshiriq: masala, qo'lyozma, referat, IT ishi",
    ),
}

/**
 * E'lonning turga xos qismi.
 *
 * [Listing] da faqat **hamma turga umumiy** maydonlar turadi (sarlavha, rasm, narx, joylashuv,
 * status). Turga xos hamma narsa shu yerda — shuning uchun ijara e'lonida `redemption`,
 * chegirma e'lonida esa `nechi kishi kerak` degan ma'nosiz maydon umuman mavjud bo'lmaydi.
 *
 * Majburiy bo'lgan maydonlar ham **nullable**: qoralama validatsiyasiz saqlanadi
 * ([dev.feature.listings.domain.usecase.SaveDraftUseCase]), ya'ni yarim to'ldirilgan forma
 * ham modelga o'girilishi kerak. "Majburiy" degani — publish paytida [ListingValidator]
 * uni tekshiradi, degani.
 */
sealed interface ListingDetails {

    val kind: ListingKind

    // -----------------------------------------------------------------------
    // 1. Chegirma / sotuv e'loni
    // -----------------------------------------------------------------------

    /**
     * Biznesning chegirma yoki oddiy sotuv e'loni — ilovaning dastlabki ssenariysi.
     *
     * [businessType] va [categoryKey] shu yerda, chunki ular faqat shu turga tegishli:
     * ijara e'lonining "biznes turi" yo'q.
     */
    data class Discount(
        val businessType: BusinessType,
        val businessName: String = "",
        val categoryKey: String = "",
        /** [ListingCatalog.OTHER_KEY] tanlanganda majburiy. */
        val customCategoryName: String? = null,

        /** `false` — chegirmasiz oddiy e'lon (narx bor, chegirma yo'q). */
        val isDiscounted: Boolean = true,
        val discountType: DiscountType = DiscountType.SPECIAL_PRICE,
        /** Ma'nosi [discountType] ga bog'liq: foiz, ayiriladigan summa yoki yangi narx. */
        val discountValue: Long = 0,
        val conditions: String? = null,
        /** `false` — chegirma qo'shimchalar narxiga ([OptionItem.priceDelta]) tarqalmaydi. */
        val appliesToOptions: Boolean = false,

        val redemption: ListingRedemption = ListingRedemption(),
    ) : ListingDetails {

        override val kind: ListingKind get() = ListingKind.DISCOUNT

        /** Chegirmadan keyingi narx. Oddiy e'londa narx o'zgarmaydi. */
        fun finalPrice(originalPrice: Long): Long {
            if (!isDiscounted) return originalPrice
            return when (discountType) {
                DiscountType.PERCENT -> originalPrice * (100 - discountValue) / 100
                DiscountType.FIXED_AMOUNT -> originalPrice - discountValue
                DiscountType.SPECIAL_PRICE -> discountValue
                DiscountType.FREE_ITEM -> originalPrice
            }.coerceAtLeast(0)
        }

        /** Kartochkadagi yorliq: "−20%", "−10 000 so'm", "1+1". Oddiy e'londa — `null`. */
        fun badge(): String? {
            if (!isDiscounted) return null
            return when (discountType) {
                DiscountType.PERCENT -> "−$discountValue%"
                DiscountType.FIXED_AMOUNT -> "−${discountValue.formatSum()} so'm"
                DiscountType.SPECIAL_PRICE -> "${discountValue.formatSum()} so'm"
                DiscountType.FREE_ITEM -> "1+1"
            }
        }
    }

    // -----------------------------------------------------------------------
    // 2. Ijara — turarjoy
    // -----------------------------------------------------------------------

    /**
     * Turarjoy ijarasi. Asosiy ssenariy — talaba kvartirada sherik izlaydi:
     * "3 xonali, hozir 2 kishi bor, yana 2 kishi kerak, faqat o'g'il bolalar".
     *
     * [gender] — **majburiy**: talaba uchun bu birinchi filtr, uni bilmasdan e'lon
     * foydasiz. Validator uni publish paytida talab qiladi.
     */
    data class Rental(
        val propertyType: PropertyType? = null,
        /** Uydagi jami xonalar soni. */
        val roomCount: Int? = null,
        /** Hozir yashab turgan kishilar (0 — hech kim yo'q, uy bo'sh). */
        val currentTenants: Int? = null,
        /** Yana nechta sherik izlanmoqda. */
        val neededTenants: Int? = null,
        /** Kim uchun — MAJBURIY. */
        val gender: TenantGender? = null,
        val period: RentPeriod = RentPeriod.MONTHLY,

        /** Kommunal to'lov narxga kiradimi. */
        val utilitiesIncluded: Boolean = false,
        /** Oldindan necha oylik depozit so'raladi (`null` — depozit yo'q). */
        val depositMonths: Int? = null,

        val floor: Int? = null,
        val totalFloors: Int? = null,
        /** [RentalCatalog.AMENITIES] kalitlari: "WIFI", "WASHER", "CONDITIONER"... */
        val amenities: List<String> = emptyList(),
        /** Qachondan bo'shaydi (epoch millis, `null` — hoziroq). */
        val availableFrom: Long? = null,
    ) : ListingDetails {

        override val kind: ListingKind get() = ListingKind.RENTAL

        /** Uyda jami necha kishi bo'ladi (hozirgi + izlanayotgan). */
        val totalCapacity: Int? get() = currentTenants?.let { cur -> neededTenants?.let { cur + it } }

        /** "3 xonali · 2 kishi bor · 2 kishi kerak" — kartochkadagi qisqa satr. */
        fun summary(): String = listOfNotNull(
            roomCount?.let { "$it xonali" },
            currentTenants?.let { "$it kishi bor" },
            neededTenants?.let { "$it kishi kerak" },
            gender?.label,
        ).joinToString(" · ")
    }

    // -----------------------------------------------------------------------
    // 3. Xizmatlar
    // -----------------------------------------------------------------------

    /**
     * Xizmat e'loni: repetitorlik, chop etish, dizayn, ta'mirlash...
     *
     * Har bir soha o'z maydonlariga ega, lekin ular **kodda alohida data class emas** —
     * [ServiceCatalog.fields] dagi spetsifikatsiyadan forma dinamik quriladi va qiymatlar
     * [fields] ga tushadi. Nega: sohalar ro'yxati o'sib boradi (bugun repetitor va printer,
     * ertaga yana o'ntasi) va har biri uchun yangi klass + mapper + forma yozish — bir xil
     * kodni o'nlab marta ko'chirish demak. Spetsifikatsiya esa keyinchalik backenddan
     * keladi va ilova umuman o'zgarmaydi.
     */
    data class Service(
        val serviceType: ServiceType? = null,
        /** Sohaga xos qiymatlar — kalitlar [ServiceCatalog.fields] dan. */
        val fields: Map<String, String> = emptyMap(),

        val format: ServiceFormat = ServiceFormat.OFFLINE,
        /** Xizmat ko'rsatuvchining tajribasi (yil). */
        val experienceYears: Int? = null,
        /** Buyurtma qabul qilinadigan vaqt: "09:00 — 21:00". */
        val workingHours: String? = null,
        /** Xizmat mijozning joyiga borib bajariladimi. */
        val hasHomeVisit: Boolean = false,
        /** Birinchi dars / sinov buyurtmasi bepulmi. */
        val hasFreeTrial: Boolean = false,
    ) : ListingDetails {

        override val kind: ListingKind get() = ListingKind.SERVICE

        /** Faqat to'ldirilgan maydonlar, ekranda ko'rsatish uchun (label → qiymat). */
        fun filledFields(): List<Pair<String, String>> {
            val type = serviceType ?: return emptyList()
            return ServiceCatalog.fields(type).mapNotNull { spec ->
                fields[spec.key]?.takeIf { it.isNotBlank() }?.let { spec.label to it }
            }
        }
    }

    // -----------------------------------------------------------------------
    // 4. Ish e'loni
    // -----------------------------------------------------------------------

    /**
     * Ish e'loni. Ikki xil bo'ladi va shartlari tubdan farq qiladi:
     * - [EmploymentType.DAILY] — kunlik ish: "ertaga yuk tushiramiz, 8 soat, 300 000 so'm".
     *   Muhimi — **qachon** va **qancha to'lanadi**.
     * - [EmploymentType.PERMANENT] — doimiy ish o'rni: smena, grafik, oylik, talablar.
     *
     * Shu sabab [schedule] va [shift] ikkalasida ham bor, lekin ma'nosi boshqacha:
     * kunlik ishda konkret sana, doimiy ishda haftalik grafik.
     */
    data class Job(
        val employment: EmploymentType = EmploymentType.DAILY,
        val categoryKey: String = "",
        val companyName: String = "",

        val shift: WorkShift? = null,
        val schedule: WorkSchedule = WorkSchedule(),
        val payPeriod: PayPeriod = PayPeriod.DAILY,

        /** Nechta odam kerak. */
        val vacancies: Int? = null,
        /** Kimlar uchun (`null` — farqi yo'q). */
        val gender: TenantGender? = null,
        val experience: ExperienceLevel = ExperienceLevel.NONE,
        val ageFrom: Int? = null,
        val ageTo: Int? = null,

        /** Talablar: "Rus tili", "Haydovchilik guvohnomasi". */
        val requirements: List<String> = emptyList(),
        /** Sharoit: "Tushlik bepul", "Yotoqxona bor". */
        val benefits: List<String> = emptyList(),

        /** Kunlik ish uchun — qaysi kuni (epoch millis). Doimiy ishda `null`. */
        val workDate: Long? = null,
        /** To'lov qachon beriladi: "Ish kuni oxirida", "Har oy 5-sanasida". */
        val payoutNote: String? = null,
    ) : ListingDetails {

        override val kind: ListingKind get() = ListingKind.JOB

        val isDaily: Boolean get() = employment == EmploymentType.DAILY

        /** "Kunlik · Ertalabki smena · 08:00–17:00" — kartochkadagi qisqa satr. */
        fun summary(): String = listOfNotNull(
            employment.label,
            shift?.label,
            schedule.timeRange(),
        ).joinToString(" · ")
    }

    // -----------------------------------------------------------------------
    // 5. Fanlardan yordam — bir martalik topshiriq
    // -----------------------------------------------------------------------

    /**
     * Talaba **bajarilishi kerak bo'lgan ishni** e'lon qiladi: "Matematikadan 12 ta masala,
     * ertaga 18:00 gacha, 50 000 so'm".
     *
     * Nega [Service] dan alohida: xizmat e'loni — **taklif** ("men repetitorlik qilaman",
     * doimiy, narxi soatiga). Bu esa **so'rov** — bir martalik, aniq shartli va eng muhimi
     * **muddatli**. Ikkalasini bitta turga qo'shish "mening xizmatim qachon tugaydi?" degan
     * ma'nosiz savolni keltirib chiqaradi va talaba uchun ikki xil ro'yxat aralashib ketardi.
     *
     * [deadline] — MAJBURIY: muddatsiz topshiriq bajaruvchi uchun ma'nosiz. Validator uni
     * publish paytida talab qiladi.
     */
    data class Task(
        /** Kategoriya: yozma ish, masala yechish, IT topshirig'i... */
        val category: TaskCategory? = null,
        /** Kategoriya ichidagi tur — [TaskCatalog.types] kaliti ("REFERAT", "MATH"...). */
        val typeKey: String = "",
        /** [TaskCatalog.OTHER_KEY] tanlanganda majburiy. */
        val customTypeName: String? = null,

        /** Qachongacha bajarilishi kerak (epoch millis) — MAJBURIY. */
        val deadline: Long? = null,

        val format: TaskFormat = TaskFormat.ONLINE,

        /** Hajm — "12 ta masala", "20 bet", "12 ta slayd". Ixtiyoriy, lekin juda foydali. */
        val volume: String? = null,
    ) : ListingDetails {

        override val kind: ListingKind get() = ListingKind.TASK

        /** Ro'yxatda ko'rinadigan ish turi nomi. */
        fun typeLabel(): String =
            customTypeName?.takeIf { it.isNotBlank() }
                ?: category?.let { TaskCatalog.type(it, typeKey)?.label }
                ?: category?.label
                ?: ListingKind.TASK.label

        /** "Referat · 20 bet · Onlayn" — kartochkadagi qisqa satr. */
        fun summary(): String = listOfNotNull(
            typeLabel(),
            volume?.takeIf { it.isNotBlank() },
            format.label,
        ).joinToString(" · ")
    }
}

// ---------------------------------------------------------------------------
// Topshiriq enum'lari
// ---------------------------------------------------------------------------

/** Topshiriq qanday bajariladi. Qo'lyozma kabi ishlar yuzma-yuz topshirishni talab qiladi. */
enum class TaskFormat(val label: String) {
    ONLINE("Onlayn"),
    IN_PERSON("Yuzma-yuz"),
    ANY("Farqi yo'q"),
}

// ---------------------------------------------------------------------------
// Ijara enum'lari
// ---------------------------------------------------------------------------

/** Turarjoy turi. */
enum class PropertyType(val label: String, val emoji: String) {
    APARTMENT("Kvartira", "🏢"),
    ROOM("Alohida xona", "🚪"),
    HOUSE("Hovli / uy", "🏡"),
    DORMITORY("Yotoqxona", "🏨"),
    BED_SPACE("Koyka joy", "🛏️"),
}

/**
 * Kim uchun. Ijarada **majburiy** — talaba uchun asosiy filtr.
 * Ish e'lonida ixtiyoriy (`null` — farqi yo'q).
 */
enum class TenantGender(val label: String, val emoji: String) {
    MALE("O'g'il bolalar", "👦"),
    FEMALE("Qizlar", "👧"),
    ANY("Farqi yo'q", "👥"),
}

/** Ijara muddati — narx shu davrga tegishli. */
enum class RentPeriod(val label: String, val priceUnit: PriceUnit) {
    MONTHLY("Oylik", PriceUnit.PER_MONTH),
    DAILY("Kunlik", PriceUnit.PER_DAY),
}

// ---------------------------------------------------------------------------
// Xizmat enum'lari
// ---------------------------------------------------------------------------

/** Xizmat qanday ko'rsatiladi. */
enum class ServiceFormat(val label: String) {
    OFFLINE("Yuzma-yuz"),
    ONLINE("Onlayn"),
    HYBRID("Aralash"),
}

// ---------------------------------------------------------------------------
// Ish enum'lari
// ---------------------------------------------------------------------------

/** Ish turi — formaning qaysi maydonlari ko'rinishini belgilaydi. */
enum class EmploymentType(val label: String, val hint: String) {
    DAILY("Kunlik ish", "Bir martalik yoki bir necha kunlik ish"),
    PERMANENT("Doimiy ish", "Doimiy ish o'rni, oylik maosh bilan"),
}

/** Smena — doimiy ishda eng ko'p so'raladigan shart. */
enum class WorkShift(val label: String, val hint: String) {
    MORNING("Ertalabki", "08:00 — 16:00"),
    DAY("Kunduzgi", "10:00 — 18:00"),
    EVENING("Kechki", "16:00 — 00:00"),
    NIGHT("Tungi", "00:00 — 08:00"),
    SHIFT_2_2("2/2 smena", "Ikki kun ish, ikki kun dam"),
    SHIFT_1_2("1/2 smena", "Bir kun ish, ikki kun dam"),
    FLEXIBLE("Erkin grafik", "Vaqtni o'zingiz tanlaysiz"),
}

/** Maosh qanday hisoblanadi — kunlik ishda odatda kunlik, doimiy ishda oylik. */
enum class PayPeriod(val label: String, val suffix: String) {
    HOURLY("Soatbay", "soatiga"),
    DAILY("Kunlik", "kuniga"),
    WEEKLY("Haftalik", "haftasiga"),
    MONTHLY("Oylik", "oyiga"),
    PER_TASK("Ish bajarilishiga", "ish uchun"),
}

/** Tajriba talabi. */
enum class ExperienceLevel(val label: String) {
    NONE("Tajriba shart emas"),
    LESS_THAN_YEAR("1 yilgacha"),
    ONE_TO_THREE("1–3 yil"),
    MORE_THAN_THREE("3 yildan ortiq"),
}

/** Hafta kuni. [WorkSchedule.days] shu ro'yxatdan iborat. */
enum class WeekDay(val label: String, val shortLabel: String) {
    MONDAY("Dushanba", "Du"),
    TUESDAY("Seshanba", "Se"),
    WEDNESDAY("Chorshanba", "Cho"),
    THURSDAY("Payshanba", "Pay"),
    FRIDAY("Juma", "Ju"),
    SATURDAY("Shanba", "Sha"),
    SUNDAY("Yakshanba", "Yak"),
}

/**
 * Ish grafigi — qaysi kunlar va soat nechadan nechagacha.
 *
 * Vaqt `HH:mm` matn sifatida saqlanadi (`kotlinx-datetime` da soat turi yo'q va bizga
 * vaqt mintaqasi ham kerak emas — bu shunchaki e'londagi yozuv).
 */
data class WorkSchedule(
    val days: List<WeekDay> = emptyList(),
    val startTime: String? = null,
    val endTime: String? = null,
    /** Kuniga necha soat — smena vaqtidan mustaqil kiritilishi mumkin. */
    val hoursPerDay: Int? = null,
) {
    /** "08:00 — 17:00" yoki `null`. */
    fun timeRange(): String? {
        val from = startTime?.takeIf { it.isNotBlank() } ?: return null
        val to = endTime?.takeIf { it.isNotBlank() } ?: return from
        return "$from — $to"
    }

    /** "Du, Se, Cho, Pay, Ju" yoki "Har kuni". */
    fun daysLabel(): String? = when {
        days.isEmpty() -> null
        days.size == WeekDay.entries.size -> "Har kuni"
        else -> days.joinToString(", ") { it.shortLabel }
    }
}
