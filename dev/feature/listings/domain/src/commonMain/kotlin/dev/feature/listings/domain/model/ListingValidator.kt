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
            listing.title.isBlank() -> add(ListingError(ListingField.TITLE, ValidationStrings.titleRequired))
            listing.title.length < 3 -> add(ListingError(ListingField.TITLE, ValidationStrings.titleTooShort))
            listing.title.length > 120 -> add(ListingError(ListingField.TITLE, ValidationStrings.titleTooLong))
        }

        // Ish e'loni va topshiriqda rasm shart emas: ish o'rnining surati odatda bo'lmaydi,
        // topshiriqda esa shart matn bilan beriladi (masala surati — ixtiyoriy qulaylik).
        // Majburiy qilish e'lon qo'yishga to'siq bo'lardi.
        if (listing.kind !in KINDS_WITHOUT_REQUIRED_IMAGE) {
            if (listing.images.isEmpty()) {
                add(ListingError(ListingField.IMAGES, ValidationStrings.imageRequired))
            }
        }
        if (listing.images.size > MAX_IMAGES) {
            add(ListingError(ListingField.IMAGES, ValidationStrings.tooManyImages(MAX_IMAGES)))
        }

        if (listing.price <= 0 && !listing.isNegotiable) {
            add(ListingError(ListingField.PRICE, ValidationStrings.priceRequired))
        }
        val max = listing.priceMax
        if (max != null && max <= listing.price) {
            add(ListingError(ListingField.PRICE, ValidationStrings.priceRangeInvalid))
        }

        // Raqam qolipi butun ilovada bitta: "+998" + 9 xona. Chala raqam serverga ketmasligi
        // kerak — aks holda e'londagi yagona aloqa kanali ishlamaydi.
        if (listing.contactPhone.isNullOrBlank()) {
            add(ListingError(ListingField.CONTACT, ValidationStrings.phoneRequired))
        } else if (!listing.contactPhone.isUzPhoneComplete()) {
            add(ListingError(ListingField.CONTACT, ValidationStrings.phoneIncomplete))
        }

        addAll(validateBranches(listing))

        if (listing.validTo <= listing.validFrom) {
            add(ListingError(ListingField.VALIDITY, ValidationStrings.endBeforeStart))
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
            add(ListingError(ListingField.LOCATION, ValidationStrings.tooManyBranches(MAX_BRANCHES)))
        }

        listing.branches.forEach { branch ->
            if (!branch.hasValidCoordinates) {
                add(ListingError(ListingField.LOCATION, ValidationStrings.pointOutsideCountry))
            }
            if (branch.address.isBlank()) {
                add(ListingError(ListingField.LOCATION, ValidationStrings.addressEmpty))
            }
        }

        // Bitta joyni ikki marta belgilash — spec §6.6: 100 m radiusda dublikat bo'lmaydi.
        listing.branches.forEachIndexed { i, a ->
            listing.branches.drop(i + 1).forEach { b ->
                if (Geo.distanceMeters(a.lat, a.lng, b.lat, b.lng) < MIN_BRANCH_DISTANCE_METERS) {
                    add(ListingError(ListingField.LOCATION, ValidationStrings.duplicateAddress))
                }
            }
        }
    }

    private fun locationRequiredMessage(kind: ListingKind): String = when (kind) {
        ListingKind.DISCOUNT -> ValidationStrings.markBranchOnMap
        ListingKind.RENTAL -> ValidationStrings.markHomeOnMap
        ListingKind.SERVICE -> ValidationStrings.markServiceOnMap
        ListingKind.JOB -> ValidationStrings.markJobOnMap
        // Topshiriq odatda onlayn bajariladi — manzil faqat yuzma-yuz formatda so'raladi.
        ListingKind.TASK -> ValidationStrings.markTaskOnMap
    }

    private fun validateOptions(listing: Listing): List<ListingError> = buildList {
        if (listing.optionGroups.size > MAX_OPTION_GROUPS) {
            add(ListingError(ListingField.OPTIONS, ValidationStrings.tooManyOptionGroups(MAX_OPTION_GROUPS)))
        }
        listing.optionGroups.forEach { group ->
            if (group.name.isBlank()) {
                add(ListingError(ListingField.OPTIONS, ValidationStrings.optionGroupNameRequired))
            }
            if (group.options.isEmpty()) {
                add(ListingError(ListingField.OPTIONS, ValidationStrings.optionGroupEmpty(group.name)))
            }
            if (group.options.size > MAX_OPTIONS_PER_GROUP) {
                add(ListingError(ListingField.OPTIONS, ValidationStrings.optionGroupTooBig(group.name, MAX_OPTIONS_PER_GROUP)))
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
            add(ListingError(ListingField.CATEGORY, ValidationStrings.otherCategoryNeedsName))
        }

        // Oddiy (chegirmasiz) e'londa chegirma maydonlari tekshirilmaydi.
        if (details.isDiscounted) {
            when (details.discountType) {
                DiscountType.PERCENT -> when {
                    details.discountValue <= 0 ->
                        add(ListingError(ListingField.DISCOUNT, ValidationStrings.discountPercentRequired))
                    details.discountValue > MAX_PERCENT ->
                        // Firibgarlikdan himoya: narxni sun'iy ko'tarib "95% chegirma" berish.
                        add(ListingError(ListingField.DISCOUNT, ValidationStrings.discountPercentTooBig(MAX_PERCENT)))
                }

                DiscountType.FIXED_AMOUNT -> when {
                    details.discountValue <= 0 ->
                        add(ListingError(ListingField.DISCOUNT, ValidationStrings.discountAmountRequired))
                    details.discountValue >= listing.price ->
                        add(ListingError(ListingField.DISCOUNT, ValidationStrings.discountBelowPrice))
                }

                DiscountType.SPECIAL_PRICE -> when {
                    details.discountValue <= 0 ->
                        add(ListingError(ListingField.DISCOUNT, ValidationStrings.studentPriceRequired))
                    details.discountValue >= listing.price ->
                        add(ListingError(ListingField.DISCOUNT, ValidationStrings.studentPriceBelowOriginal))
                }

                // 1+1 — narx o'zgarmaydi, lekin talaba nima olishini bilishi kerak.
                DiscountType.FREE_ITEM ->
                    if (details.conditions.isNullOrBlank()) {
                        add(ListingError(ListingField.DISCOUNT, ValidationStrings.giftConditionRequired))
                    }
            }
        }

        if (details.redemption.method == RedemptionMethod.PROMO_CODE &&
            details.redemption.promoCode.isNullOrBlank()
        ) {
            add(ListingError(ListingField.PROMO_CODE, ValidationStrings.promoCodeRequired))
        }
    }

    // -----------------------------------------------------------------------
    // Ijara
    // -----------------------------------------------------------------------

    private fun validateRental(details: ListingDetails.Rental): List<ListingError> = buildList {
        if (details.propertyType == null) {
            add(ListingError(ListingField.PROPERTY_TYPE, ValidationStrings.propertyTypeRequired))
        }

        when (val rooms = details.roomCount) {
            null -> add(ListingError(ListingField.ROOMS, ValidationStrings.roomCountRequired))
            else -> if (rooms !in 1..MAX_ROOMS) {
                add(ListingError(ListingField.ROOMS, ValidationStrings.roomCountRange(MAX_ROOMS)))
            }
        }

        val current = details.currentTenants
        val needed = details.neededTenants

        if (current == null) {
            add(ListingError(ListingField.TENANTS, ValidationStrings.currentTenantsRequired))
        } else if (current !in 0..MAX_TENANTS) {
            add(ListingError(ListingField.TENANTS, ValidationStrings.currentTenantsInvalid))
        }

        when (needed) {
            null -> add(ListingError(ListingField.TENANTS, ValidationStrings.neededTenantsRequired))
            else -> if (needed !in 1..MAX_TENANTS) {
                add(ListingError(ListingField.TENANTS, ValidationStrings.neededTenantsMin))
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
                    ValidationStrings.tooManyTenants(rooms, total),
                ),
            )
        }

        // MAJBURIY: talaba uchun bu birinchi filtr.
        if (details.gender == null) {
            add(ListingError(ListingField.GENDER, ValidationStrings.tenantGenderRequired))
        }

        val floor = details.floor
        val totalFloors = details.totalFloors
        if (floor != null && totalFloors != null && floor > totalFloors) {
            add(ListingError(ListingField.ATTRIBUTES, ValidationStrings.floorAboveTotal))
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
            add(ListingError(ListingField.SERVICE_TYPE, ValidationStrings.serviceTypeRequired))
            // Soha tanlanmagan bo'lsa maydonlarni tekshirishning ma'nosi yo'q —
            // qaysi maydonlar kerakligi aynan sohaga bog'liq.
            return@buildList
        }

        val subjectKey = details.fields[ServiceCatalog.SUBJECT_KEY].orEmpty()
        if (type.hasSubjects) {
            if (subjectKey.isBlank()) {
                add(ListingError(ListingField.SERVICE_SUBJECT, ValidationStrings.subjectRequired(ServiceCatalog.subjectLabel(type))))
            } else if (subjectKey == ServiceCatalog.OTHER_SUBJECT_KEY &&
                details.fields[ServiceCatalog.CUSTOM_SUBJECT_KEY].isNullOrBlank()
            ) {
                add(ListingError(ListingField.SERVICE_SUBJECT, ValidationStrings.otherSubjectNeedsName))
            }
        }

        // Sohaning va tanlangan yo'nalishning majburiy maydonlari.
        val specs = ServiceCatalog.fields(type) + ServiceCatalog.subjectFields(type, subjectKey)
        specs.filter { it.required }.forEach { spec ->
            if (details.fields[spec.key].isNullOrBlank()) {
                add(ListingError(ListingField.ATTRIBUTES, ValidationStrings.attributeEmpty(spec.label)))
            }
        }

        val years = details.experienceYears
        if (years != null && years !in 0..60) {
            add(ListingError(ListingField.ATTRIBUTES, ValidationStrings.experienceInvalid))
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
            add(ListingError(ListingField.TASK_SUBJECT, ValidationStrings.taskSubjectRequired))
        } else if (details.typeKey.isBlank()) {
            add(ListingError(ListingField.TASK_SUBJECT, ValidationStrings.taskTypeRequired))
        }
        if (details.typeKey == TaskCatalog.OTHER_KEY && details.customTypeName.isNullOrBlank()) {
            add(ListingError(ListingField.TASK_SUBJECT, ValidationStrings.taskTypeNameRequired))
        }

        // "Masala sharti" — topshiriqning o'zagi, shuning uchun bu yerda MAJBURIY
        // (boshqa turlarda tavsif ixtiyoriy).
        if (listing.description.isNullOrBlank()) {
            add(ListingError(ListingField.TASK_BRIEF, ValidationStrings.taskBriefRequired))
        }

        when {
            details.deadline == null ->
                add(ListingError(ListingField.TASK_DEADLINE, ValidationStrings.taskDeadlineRequired))

            details.deadline <= listing.createdAt ->
                add(ListingError(ListingField.TASK_DEADLINE, ValidationStrings.taskDeadlineInFuture))
        }
    }

    private fun validateJob(details: ListingDetails.Job): List<ListingError> = buildList {
        if (details.categoryKey.isBlank()) {
            add(ListingError(ListingField.JOB_CATEGORY, ValidationStrings.taskTypeRequired))
        }
        if (details.companyName.isBlank()) {
            add(ListingError(ListingField.BUSINESS_NAME, ValidationStrings.businessNameRequired))
        }

        if (details.shift == null) {
            add(ListingError(ListingField.JOB_SHIFT, ValidationStrings.shiftRequired))
        }

        val schedule = details.schedule
        val start = schedule.startTime
        val end = schedule.endTime

        // Erkin grafikda aniq vaqt so'ralmaydi — uning butun mohiyati shu.
        if (details.shift != WorkShift.FLEXIBLE) {
            if (start.isNullOrBlank() || end.isNullOrBlank()) {
                add(ListingError(ListingField.JOB_SCHEDULE, ValidationStrings.scheduleRequired))
            }
        }

        when (details.employment) {
            // Kunlik ish — qaysi kuni ekani aytilmasa e'lon foydasiz.
            EmploymentType.DAILY ->
                if (details.workDate == null) {
                    add(ListingError(ListingField.JOB_SCHEDULE, ValidationStrings.workDateRequired))
                }

            // Doimiy ish — haftaning qaysi kunlari ishlanishi kerak.
            EmploymentType.PERMANENT ->
                if (schedule.days.isEmpty() && details.shift != WorkShift.SHIFT_2_2 &&
                    details.shift != WorkShift.SHIFT_1_2 && details.shift != WorkShift.FLEXIBLE
                ) {
                    add(ListingError(ListingField.JOB_SCHEDULE, ValidationStrings.workDaysRequired))
                }
        }

        val hours = schedule.hoursPerDay
        if (hours != null && hours !in 1..24) {
            add(ListingError(ListingField.JOB_SCHEDULE, ValidationStrings.hoursPerDayRange))
        }

        when (val vacancies = details.vacancies) {
            null -> add(ListingError(ListingField.JOB_PAY, ValidationStrings.vacanciesRequired))
            else -> if (vacancies !in 1..MAX_VACANCIES) {
                add(ListingError(ListingField.JOB_PAY, ValidationStrings.vacanciesRange(MAX_VACANCIES)))
            }
        }

        val ageFrom = details.ageFrom
        val ageTo = details.ageTo
        if (ageFrom != null && ageTo != null && ageFrom > ageTo) {
            add(ListingError(ListingField.ATTRIBUTES, ValidationStrings.ageRangeInvalid))
        }
    }
}
