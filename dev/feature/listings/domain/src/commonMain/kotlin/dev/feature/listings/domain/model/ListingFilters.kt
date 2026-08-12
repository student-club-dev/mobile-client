package dev.feature.listings.domain.model

/**
 * Talaba tomonidagi ro'yxat filtrlari.
 *
 * Bitta model to'rt turga ham xizmat qiladi, lekin har bir maydon **faqat o'z turida**
 * ma'noli: "qiz yoki o'g'il" ijarada filtr, xizmatda esa ma'nosiz. Shu sabab [activeCount]
 * va [matches] doim tur bilan birga ishlaydi va boshqa turning maydonlarini e'tiborsiz
 * qoldiradi — foydalanuvchi tab almashtirganda ko'rinmas filtr qolib ketmasligi kerak.
 *
 * Filtrlash domenda, chunki bu ma'lumot qoidasi: "600 000 dan arzon, faqat qizlar uchun"
 * degan shart ekranga emas, e'longa tegishli.
 */
data class ListingFilters(
    // --- Ijara ---
    val gender: TenantGender? = null,
    val propertyType: PropertyType? = null,
    /** Kamida shuncha xona. */
    val minRooms: Int? = null,
    /** Faqat hozir bo'sh joyi borlari (kerakli sherik soni > 0). */
    val onlyAvailable: Boolean = false,

    // --- Xizmat ---
    val serviceType: ServiceType? = null,
    val serviceFormat: ServiceFormat? = null,
    val onlyFreeTrial: Boolean = false,

    // --- Ish ---
    val employment: EmploymentType? = null,
    val jobCategoryKey: String? = null,
    val shift: WorkShift? = null,
    val noExperienceOnly: Boolean = false,

    // --- Fanlardan yordam ---
    val taskCategory: TaskCategory? = null,
    val taskTypeKey: String? = null,
    val taskFormat: TaskFormat? = null,
    /** Muddati hali o'tmaganlari (bajarishga ulgurish mumkin). */
    val onlyOpenDeadline: Boolean = false,

    // --- Umumiy ---
    /** Shu summadan qimmat e'lonlar chiqmaydi. */
    val maxPrice: Long? = null,
) {

    /** Filtr tugmasidagi raqam — faqat shu turga tegishli faol filtrlar sanaladi. */
    fun activeCount(kind: ListingKind): Int = activeFor(kind).size

    /** Faol filtrlarning yorliqlari — "tozalash" tugmasi va tekshiruv uchun. */
    fun activeFor(kind: ListingKind): List<String> = buildList {
        when (kind) {
            ListingKind.RENTAL -> {
                gender?.let { add(it.label) }
                propertyType?.let { add(it.label) }
                minRooms?.let { add(ListingText.roomsPlus(it)) }
                if (onlyAvailable) add("Joyi bor")
            }

            ListingKind.SERVICE -> {
                serviceType?.let { add(it.label) }
                serviceFormat?.let { add(it.label) }
                if (onlyFreeTrial) add("Sinov bepul")
            }

            ListingKind.JOB -> {
                employment?.let { add(it.label) }
                jobCategoryKey?.let { key -> JobCatalog.category(key)?.let { add(it.label) } }
                shift?.let { add(it.label) }
                if (noExperienceOnly) add("Tajribasiz")
            }

            ListingKind.TASK -> {
                taskCategory?.let { add(it.label) }
                taskTypeKey?.let { key ->
                    taskCategory?.let { cat -> TaskCatalog.type(cat, key)?.let { add(it.label) } }
                }
                taskFormat?.let { add(it.label) }
                if (onlyOpenDeadline) add("Muddati o'tmagan")
            }

            ListingKind.DISCOUNT -> Unit
        }
        maxPrice?.let { add(ListingText.upTo(it.formatSum())) }
    }

    /**
     * Tur almashganda boshqa turning filtrlari tushib qolishi kerak — aks holda talaba
     * "Ish" tab'iga o'tganda ijaraning "faqat qizlar" filtri jimgina ishlab turadi.
     * Narx chegarasi saqlanadi: u har turda bir xil ma'noga ega.
     */
    fun resetForKind(): ListingFilters = ListingFilters(maxPrice = maxPrice)

    /** E'lon shu filtrlarga mos keladimi. */
    fun matches(listing: Listing): Boolean {
        // Kelishilgan narxli e'lon narx filtridan tushib qolmasligi kerak — uning summasi
        // yo'q, lekin talaba uchun baribir mos bo'lishi mumkin.
        val price = maxPrice
        if (price != null && !listing.isNegotiable && listing.price > price) return false

        return when (val details = listing.details) {
            is ListingDetails.Rental -> matchesRental(details)
            is ListingDetails.Service -> matchesService(details)
            is ListingDetails.Job -> matchesJob(details)
            is ListingDetails.Task -> matchesTask(details)
            is ListingDetails.Discount -> true
        }
    }

    private fun matchesRental(details: ListingDetails.Rental): Boolean {
        // "Qizlar uchun" izlagan talabaga "farqi yo'q" e'loni ham to'g'ri keladi.
        val wanted = gender
        if (wanted != null && wanted != TenantGender.ANY) {
            val actual = details.gender
            if (actual != wanted && actual != TenantGender.ANY) return false
        }
        if (propertyType != null && details.propertyType != propertyType) return false

        val rooms = minRooms
        if (rooms != null && (details.roomCount ?: 0) < rooms) return false
        if (onlyAvailable && (details.neededTenants ?: 0) <= 0) return false
        return true
    }

    private fun matchesService(details: ListingDetails.Service): Boolean {
        if (serviceType != null && details.serviceType != serviceType) return false

        // "Aralash" formatdagi xizmat ham onlayn, ham oflayn izlovchiga mos.
        val format = serviceFormat
        if (format != null && details.format != format && details.format != ServiceFormat.HYBRID) {
            return false
        }
        if (onlyFreeTrial && !details.hasFreeTrial) return false
        return true
    }

    private fun matchesTask(details: ListingDetails.Task): Boolean {
        if (taskCategory != null && details.category != taskCategory) return false
        if (taskTypeKey != null && details.typeKey != taskTypeKey) return false
        // "Farqi yo'q" formatidagi topshiriq har qanday tanlovga to'g'ri keladi.
        val wanted = taskFormat
        if (wanted != null && details.format != wanted && details.format != TaskFormat.ANY) return false
        return true
    }

    private fun matchesJob(details: ListingDetails.Job): Boolean {
        if (employment != null && details.employment != employment) return false
        if (jobCategoryKey != null && details.categoryKey != jobCategoryKey) return false

        // Erkin grafik har qanday smena izlovchiga to'g'ri keladi.
        val wantedShift = shift
        if (wantedShift != null && details.shift != wantedShift && details.shift != WorkShift.FLEXIBLE) {
            return false
        }
        if (noExperienceOnly && details.experience != ExperienceLevel.NONE) return false
        return true
    }

    companion object {
        /** Narx chegarasi uchun tayyor tanlovlar — turga qarab boshqacha masshtab. */
        fun priceOptions(kind: ListingKind): List<Long> = when (kind) {
            ListingKind.RENTAL -> listOf(1_000_000, 2_000_000, 3_000_000, 5_000_000)
            ListingKind.SERVICE -> listOf(50_000, 100_000, 300_000, 1_000_000)
            ListingKind.JOB -> emptyList() // Ishda narx — maosh, uni yuqoridan cheklash noto'g'ri.
            ListingKind.DISCOUNT -> listOf(50_000, 100_000, 500_000, 1_000_000)
            // Topshiriq — bir martalik ish, summalar kichik.
            ListingKind.TASK -> listOf(30_000, 50_000, 100_000, 300_000)
        }

        /** Ijarada tez tanlanadigan xona chegaralari. */
        val ROOM_OPTIONS = listOf(1, 2, 3, 4)
    }
}

/**
 * Qidiruv — sarlavha, tavsif, manzil va turga xos matnlar bo'ylab.
 *
 * Manzil ham qidiriladi, chunki talaba odatda "Chilonzor" deb yozadi, e'lon nomini emas.
 */
fun Listing.matchesQuery(query: String): Boolean {
    val q = query.trim()
    if (q.isBlank()) return true

    val haystack = buildList {
        add(title)
        description?.let { add(it) }
        add(categoryLabel)
        add(displayName)
        branches.forEach { add(it.address); it.name?.let { name -> add(name) } }

        when (val d = details) {
            is ListingDetails.Task -> {
                add(d.typeLabel())
                d.volume?.let { add(it) }
            }

            is ListingDetails.Rental -> {
                d.propertyType?.let { add(it.label) }
                d.gender?.let { add(it.label) }
                d.amenities.forEach { key -> RentalCatalog.amenity(key)?.let { add(it.label) } }
            }

            is ListingDetails.Service -> {
                d.serviceType?.let { add(it.label) }
                d.filledFields().forEach { (label, value) -> add(label); add(value) }
            }

            is ListingDetails.Job -> {
                add(d.companyName)
                add(d.employment.label)
                d.shift?.let { add(it.label) }
                d.requirements.forEach { add(it) }
                d.benefits.forEach { add(it) }
            }

            is ListingDetails.Discount -> add(d.businessName)
        }
    }

    return haystack.any { it.contains(q, ignoreCase = true) }
}

/** Ro'yxatni filtr va qidiruv bo'yicha saralaydi. */
fun List<Listing>.filterBy(filters: ListingFilters, query: String): List<Listing> =
    filter { filters.matches(it) && it.matchesQuery(query) }
