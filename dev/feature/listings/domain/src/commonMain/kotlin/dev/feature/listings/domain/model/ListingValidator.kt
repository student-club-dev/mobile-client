package dev.feature.listings.domain.model

import dev.core.common.format.isUzPhoneComplete

/**
 * E'lonni publish qilish shartlari — `DISCOUNTS_BUSINESS_API.md` §6.1 ning klient tomoni.
 *
 * Backend baribir o'zi tekshiradi (klientga ishonib bo'lmaydi), lekin foydalanuvchi
 * xatoni **serverga bormasdan** ko'rishi kerak: forma tugmasi shu yerdan yoqiladi.
 *
 * Xato matnlari forma maydoniga bog'lanadi ([ListingField]) — UI ularni tegishli
 * maydon ostida ko'rsatadi.
 */
enum class ListingField {
    // Umumiy
    BUSINESS_NAME,
    CATEGORY,
    TITLE,
    IMAGES,
    PRICE,
    LOCATION,
    VALIDITY,
    ATTRIBUTES,
    OPTIONS,
    CONTACT,

    // Chegirma
    DISCOUNT,
    PROMO_CODE,

    // Ijara
    PROPERTY_TYPE,
    ROOMS,
    TENANTS,
    GENDER,

    // Xizmat
    SERVICE_TYPE,
    SERVICE_SUBJECT,

    // Fanlardan yordam
    TASK_SUBJECT,
    TASK_BRIEF,
    TASK_DEADLINE,

    // Ish
    JOB_CATEGORY,
    JOB_SHIFT,
    JOB_SCHEDULE,
    JOB_PAY,
}

data class ListingError(val field: ListingField, val message: String)

object ListingValidator {

    const val MAX_IMAGES = 5
    const val MAX_PERCENT = 90L
    const val MAX_OPTION_GROUPS = 10
    const val MAX_OPTIONS_PER_GROUP = 30
    const val MAX_BRANCHES = 20
    const val MIN_BRANCH_DISTANCE_METERS = 100.0

    /**
     * Rasm majburiy bo'lmagan turlar: ish o'rnining surati odatda bo'lmaydi, topshiriqda esa
     * shart matn bilan beriladi. Majburiy qilish e'lon qo'yishga keraksiz to'siq bo'lardi.
     */
    private val KINDS_WITHOUT_REQUIRED_IMAGE = setOf(ListingKind.JOB, ListingKind.TASK)

    /** Ijarada shundan ko'p xona — kiritishda xato bo'lgani aniq. */
    const val MAX_ROOMS = 20
    const val MAX_TENANTS = 30
    const val MAX_VACANCIES = 100

    /** Bo'sh ro'yxat — e'lon publish qilishga tayyor. */
    fun validate(listing: Listing): List<ListingError> = buildList {
        addAll(validateCommon(listing))

        // Turga xos qoidalar. `when` to'liq — yangi tur qo'shilsa kompilyator shu yerni
        // ko'rsatadi va validatsiyasiz qolib ketmaydi.
        when (val details = listing.details) {
            is ListingDetails.Discount -> addAll(validateDiscount(listing, details))
            is ListingDetails.Rental -> addAll(validateRental(details))
            is ListingDetails.Service -> addAll(validateService(details))
            is ListingDetails.Job -> addAll(validateJob(details))
            is ListingDetails.Task -> addAll(validateTask(listing, details))
        }
    }

    // -----------------------------------------------------------------------
    // Hamma turga umumiy
    // -----------------------------------------------------------------------

    private fun validateCommon(listing: Listing): List<ListingError> = buildList {
        when {
            listing.title.isBlank() -> add(ListingError(ListingField.TITLE, "Sarlavhani kiriting"))
            listing.title.length < 3 -> add(ListingError(ListingField.TITLE, "Sarlavha juda qisqa"))
            listing.title.length > 120 -> add(ListingError(ListingField.TITLE, "Sarlavha 120 belgidan oshmasin"))
        }

        // Ish e'loni va topshiriqda rasm shart emas: ish o'rnining surati odatda bo'lmaydi,
        // topshiriqda esa shart matn bilan beriladi (masala surati — ixtiyoriy qulaylik).
        // Majburiy qilish e'lon qo'yishga to'siq bo'lardi.
        if (listing.kind !in KINDS_WITHOUT_REQUIRED_IMAGE) {
            if (listing.images.isEmpty()) {
                add(ListingError(ListingField.IMAGES, "Kamida 1 ta rasm qo'shing"))
            }
        }
        if (listing.images.size > MAX_IMAGES) {
            add(ListingError(ListingField.IMAGES, "Maksimal $MAX_IMAGES ta rasm"))
        }

        if (listing.price <= 0 && !listing.isNegotiable) {
            add(ListingError(ListingField.PRICE, "Narxni kiriting yoki \"kelishilgan\" ni belgilang"))
        }
        val max = listing.priceMax
        if (max != null && max <= listing.price) {
            add(ListingError(ListingField.PRICE, "Yuqori chegara quyi chegaradan katta bo'lsin"))
        }

        // Raqam qolipi butun ilovada bitta: "+998" + 9 xona. Chala raqam serverga ketmasligi
        // kerak — aks holda e'londagi yagona aloqa kanali ishlamaydi.
        if (listing.contactPhone.isNullOrBlank()) {
            add(ListingError(ListingField.CONTACT, "Telefon raqamini kiriting"))
        } else if (!listing.contactPhone.isUzPhoneComplete()) {
            add(ListingError(ListingField.CONTACT, "Raqamni to'liq kiriting: +998 90 123 45 67"))
        }

        addAll(validateBranches(listing))

        if (listing.validTo <= listing.validFrom) {
            add(ListingError(ListingField.VALIDITY, "Tugash sanasi boshlanishdan keyin bo'lsin"))
        }

        addAll(validateOptions(listing))
    }

    /**
     * Manzillar. Har biri xaritadan tanlangani uchun koordinatasi bor — tekshiriladigan narsa
     * uning O'zbekiston chegarasida ekani va manzillar bir-birini takrorlamasligi.
     */
    private fun validateBranches(listing: Listing): List<ListingError> = buildList {
        // Onlayn bajariladigan topshiriqning joyi yo'q — manzil so'rash ma'nosiz.
        val task = listing.details as? ListingDetails.Task
        if (task != null && task.format != TaskFormat.IN_PERSON) return@buildList

        if (listing.branches.isEmpty()) {
            add(ListingError(ListingField.LOCATION, locationRequiredMessage(listing.kind)))
            return@buildList
        }
        if (listing.branches.size > MAX_BRANCHES) {
            add(ListingError(ListingField.LOCATION, "Maksimal $MAX_BRANCHES ta manzil"))
        }

        listing.branches.forEach { branch ->
            if (!branch.hasValidCoordinates) {
                add(ListingError(ListingField.LOCATION, "Nuqta O'zbekiston hududidan tashqarida"))
            }
            if (branch.address.isBlank()) {
                add(ListingError(ListingField.LOCATION, "Manzil bo'sh"))
            }
        }

        // Bitta joyni ikki marta belgilash — spec §6.6: 100 m radiusda dublikat bo'lmaydi.
        listing.branches.forEachIndexed { i, a ->
            listing.branches.drop(i + 1).forEach { b ->
                if (Geo.distanceMeters(a.lat, a.lng, b.lat, b.lng) < MIN_BRANCH_DISTANCE_METERS) {
                    add(ListingError(ListingField.LOCATION, "Ikkita manzil bir joyda belgilangan"))
                }
            }
        }
    }

    private fun locationRequiredMessage(kind: ListingKind): String = when (kind) {
        ListingKind.DISCOUNT -> "Kamida 1 ta filialni xaritadan belgilang"
        ListingKind.RENTAL -> "Uy joyini xaritadan belgilang"
        ListingKind.SERVICE -> "Xizmat ko'rsatiladigan joyni xaritadan belgilang"
        ListingKind.JOB -> "Ish joyini xaritadan belgilang"
        // Topshiriq odatda onlayn bajariladi — manzil faqat yuzma-yuz formatda so'raladi.
        ListingKind.TASK -> "Ish topshiriladigan joyni xaritadan belgilang"
    }

    private fun validateOptions(listing: Listing): List<ListingError> = buildList {
        if (listing.optionGroups.size > MAX_OPTION_GROUPS) {
            add(ListingError(ListingField.OPTIONS, "Maksimal $MAX_OPTION_GROUPS ta qo'shimcha guruhi"))
        }
        listing.optionGroups.forEach { group ->
            if (group.name.isBlank()) {
                add(ListingError(ListingField.OPTIONS, "Qo'shimcha guruhining nomini kiriting"))
            }
            if (group.options.isEmpty()) {
                add(ListingError(ListingField.OPTIONS, "\"${group.name}\" guruhida kamida 1 ta variant bo'lsin"))
            }
            if (group.options.size > MAX_OPTIONS_PER_GROUP) {
                add(ListingError(ListingField.OPTIONS, "\"${group.name}\" da $MAX_OPTIONS_PER_GROUP tadan ko'p variant"))
            }
        }
    }

    // -----------------------------------------------------------------------
    // Chegirma
    // -----------------------------------------------------------------------

    private fun validateDiscount(
        listing: Listing,
        details: ListingDetails.Discount,
    ): List<ListingError> = buildList {
        if (details.categoryKey == ListingCatalog.OTHER_KEY && details.customCategoryName.isNullOrBlank()) {
            add(ListingError(ListingField.CATEGORY, "\"Boshqa\" tanlandi — bo'lim nomini yozing"))
        }

        // Oddiy (chegirmasiz) e'londa chegirma maydonlari tekshirilmaydi.
        if (details.isDiscounted) {
            when (details.discountType) {
                DiscountType.PERCENT -> when {
                    details.discountValue <= 0 ->
                        add(ListingError(ListingField.DISCOUNT, "Chegirma foizini kiriting"))
                    details.discountValue > MAX_PERCENT ->
                        // Firibgarlikdan himoya: narxni sun'iy ko'tarib "95% chegirma" berish.
                        add(ListingError(ListingField.DISCOUNT, "Chegirma $MAX_PERCENT% dan oshmasin"))
                }

                DiscountType.FIXED_AMOUNT -> when {
                    details.discountValue <= 0 ->
                        add(ListingError(ListingField.DISCOUNT, "Chegirma summasini kiriting"))
                    details.discountValue >= listing.price ->
                        add(ListingError(ListingField.DISCOUNT, "Chegirma narxdan kam bo'lsin"))
                }

                DiscountType.SPECIAL_PRICE -> when {
                    details.discountValue <= 0 ->
                        add(ListingError(ListingField.DISCOUNT, "Talaba narxini kiriting"))
                    details.discountValue >= listing.price ->
                        add(ListingError(ListingField.DISCOUNT, "Talaba narxi asl narxdan past bo'lsin"))
                }

                // 1+1 — narx o'zgarmaydi, lekin talaba nima olishini bilishi kerak.
                DiscountType.FREE_ITEM ->
                    if (details.conditions.isNullOrBlank()) {
                        add(ListingError(ListingField.DISCOUNT, "Aksiya shartini yozing (masalan: ikkinchi kofe bepul)"))
                    }
            }
        }

        if (details.redemption.method == RedemptionMethod.PROMO_CODE &&
            details.redemption.promoCode.isNullOrBlank()
        ) {
            add(ListingError(ListingField.PROMO_CODE, "Promokodni kiriting"))
        }
    }

    // -----------------------------------------------------------------------
    // Ijara
    // -----------------------------------------------------------------------

    private fun validateRental(details: ListingDetails.Rental): List<ListingError> = buildList {
        if (details.propertyType == null) {
            add(ListingError(ListingField.PROPERTY_TYPE, "Turarjoy turini tanlang"))
        }

        when (val rooms = details.roomCount) {
            null -> add(ListingError(ListingField.ROOMS, "Nechi xonaligini kiriting"))
            else -> if (rooms !in 1..MAX_ROOMS) {
                add(ListingError(ListingField.ROOMS, "Xonalar soni 1 dan $MAX_ROOMS gacha bo'lsin"))
            }
        }

        val current = details.currentTenants
        val needed = details.neededTenants

        if (current == null) {
            add(ListingError(ListingField.TENANTS, "Hozir nechi kishi yashashini kiriting"))
        } else if (current !in 0..MAX_TENANTS) {
            add(ListingError(ListingField.TENANTS, "Hozirgi kishilar soni noto'g'ri"))
        }

        when (needed) {
            null -> add(ListingError(ListingField.TENANTS, "Nechi kishi kerakligini kiriting"))
            else -> if (needed !in 1..MAX_TENANTS) {
                add(ListingError(ListingField.TENANTS, "Kamida 1 kishi kerak bo'lsin"))
            }
        }

        // Sig'im xonalar sonidan mantiqan oshib ketmasin — bitta xonaga 10 kishi
        // yozilgan e'lon deyarli har doim xato kiritish natijasi.
        val rooms = details.roomCount
        val total = details.totalCapacity
        if (rooms != null && total != null && total > rooms * MAX_PER_ROOM) {
            add(
                ListingError(
                    ListingField.TENANTS,
                    "$rooms xonaga $total kishi ko'p — sonlarni tekshiring",
                ),
            )
        }

        // MAJBURIY: talaba uchun bu birinchi filtr.
        if (details.gender == null) {
            add(ListingError(ListingField.GENDER, "Kim uchun ekanini tanlang — qiz yoki o'g'il"))
        }

        val floor = details.floor
        val totalFloors = details.totalFloors
        if (floor != null && totalFloors != null && floor > totalFloors) {
            add(ListingError(ListingField.ATTRIBUTES, "Qavat binoning qavatlar sonidan katta"))
        }
    }

    /** Bitta xonaga real joylashadigan maksimal odam soni. */
    private const val MAX_PER_ROOM = 4

    // -----------------------------------------------------------------------
    // Xizmat
    // -----------------------------------------------------------------------

    private fun validateService(details: ListingDetails.Service): List<ListingError> = buildList {
        val type = details.serviceType
        if (type == null) {
            add(ListingError(ListingField.SERVICE_TYPE, "Xizmat sohasini tanlang"))
            // Soha tanlanmagan bo'lsa maydonlarni tekshirishning ma'nosi yo'q —
            // qaysi maydonlar kerakligi aynan sohaga bog'liq.
            return@buildList
        }

        val subjectKey = details.fields[ServiceCatalog.SUBJECT_KEY].orEmpty()
        if (type.hasSubjects) {
            if (subjectKey.isBlank()) {
                add(ListingError(ListingField.SERVICE_SUBJECT, "${ServiceCatalog.subjectLabel(type)}ni tanlang"))
            } else if (subjectKey == ServiceCatalog.OTHER_SUBJECT_KEY &&
                details.fields[ServiceCatalog.CUSTOM_SUBJECT_KEY].isNullOrBlank()
            ) {
                add(ListingError(ListingField.SERVICE_SUBJECT, "\"Boshqa\" tanlandi — nomini yozing"))
            }
        }

        // Sohaning va tanlangan yo'nalishning majburiy maydonlari.
        val specs = ServiceCatalog.fields(type) + ServiceCatalog.subjectFields(type, subjectKey)
        specs.filter { it.required }.forEach { spec ->
            if (details.fields[spec.key].isNullOrBlank()) {
                add(ListingError(ListingField.ATTRIBUTES, "\"${spec.label}\" to'ldirilmagan"))
            }
        }

        val years = details.experienceYears
        if (years != null && years !in 0..60) {
            add(ListingError(ListingField.ATTRIBUTES, "Tajriba yillari noto'g'ri"))
        }
    }

    // -----------------------------------------------------------------------
    // Ish
    // -----------------------------------------------------------------------

    /**
     * Topshiriq. Uchta narsa hal qiluvchi: **qaysi fan**, **nima qilinishi kerak** (shart) va
     * **qachongacha**. Muddatsiz yoki shartsiz topshiriq bajaruvchi uchun foydasiz.
     */
    private fun validateTask(listing: Listing, details: ListingDetails.Task): List<ListingError> = buildList {
        if (details.category == null) {
            add(ListingError(ListingField.TASK_SUBJECT, "Ish yo'nalishini tanlang"))
        } else if (details.typeKey.isBlank()) {
            add(ListingError(ListingField.TASK_SUBJECT, "Ish turini tanlang"))
        }
        if (details.typeKey == TaskCatalog.OTHER_KEY && details.customTypeName.isNullOrBlank()) {
            add(ListingError(ListingField.TASK_SUBJECT, "Ish turini yozing"))
        }

        // "Masala sharti" — topshiriqning o'zagi, shuning uchun bu yerda MAJBURIY
        // (boshqa turlarda tavsif ixtiyoriy).
        if (listing.description.isNullOrBlank()) {
            add(ListingError(ListingField.TASK_BRIEF, "Topshiriq shartini yozing"))
        }

        when {
            details.deadline == null ->
                add(ListingError(ListingField.TASK_DEADLINE, "Topshirish muddatini belgilang"))

            details.deadline <= listing.createdAt ->
                add(ListingError(ListingField.TASK_DEADLINE, "Muddat hozirgi vaqtdan keyin bo'lsin"))
        }
    }

    private fun validateJob(details: ListingDetails.Job): List<ListingError> = buildList {
        if (details.categoryKey.isBlank()) {
            add(ListingError(ListingField.JOB_CATEGORY, "Ish turini tanlang"))
        }
        if (details.companyName.isBlank()) {
            add(ListingError(ListingField.BUSINESS_NAME, "Tashkilot yoki ish beruvchi nomini kiriting"))
        }

        if (details.shift == null) {
            add(ListingError(ListingField.JOB_SHIFT, "Ish smenasini tanlang"))
        }

        val schedule = details.schedule
        val start = schedule.startTime
        val end = schedule.endTime

        // Erkin grafikda aniq vaqt so'ralmaydi — uning butun mohiyati shu.
        if (details.shift != WorkShift.FLEXIBLE) {
            if (start.isNullOrBlank() || end.isNullOrBlank()) {
                add(ListingError(ListingField.JOB_SCHEDULE, "Ish vaqti oralig'ini kiriting"))
            }
        }

        when (details.employment) {
            // Kunlik ish — qaysi kuni ekani aytilmasa e'lon foydasiz.
            EmploymentType.DAILY ->
                if (details.workDate == null) {
                    add(ListingError(ListingField.JOB_SCHEDULE, "Ish qaysi kuni ekanini belgilang"))
                }

            // Doimiy ish — haftaning qaysi kunlari ishlanishi kerak.
            EmploymentType.PERMANENT ->
                if (schedule.days.isEmpty() && details.shift != WorkShift.SHIFT_2_2 &&
                    details.shift != WorkShift.SHIFT_1_2 && details.shift != WorkShift.FLEXIBLE
                ) {
                    add(ListingError(ListingField.JOB_SCHEDULE, "Ish kunlarini tanlang"))
                }
        }

        val hours = schedule.hoursPerDay
        if (hours != null && hours !in 1..24) {
            add(ListingError(ListingField.JOB_SCHEDULE, "Kunlik soat 1 dan 24 gacha bo'lsin"))
        }

        when (val vacancies = details.vacancies) {
            null -> add(ListingError(ListingField.JOB_PAY, "Nechta odam kerakligini kiriting"))
            else -> if (vacancies !in 1..MAX_VACANCIES) {
                add(ListingError(ListingField.JOB_PAY, "Kerakli odamlar soni 1 dan $MAX_VACANCIES gacha"))
            }
        }

        val ageFrom = details.ageFrom
        val ageTo = details.ageTo
        if (ageFrom != null && ageTo != null && ageFrom > ageTo) {
            add(ListingError(ListingField.ATTRIBUTES, "Yosh oralig'i noto'g'ri"))
        }
    }
}
