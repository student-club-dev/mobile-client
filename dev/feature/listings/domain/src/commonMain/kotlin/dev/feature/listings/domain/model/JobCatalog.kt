package dev.feature.listings.domain.model

/**
 * Ish e'lonining kategoriyalari va shartlari.
 *
 * Talabaga mo'ljallangan: ro'yxat "buxgalter, direktor" emas, balki talaba haqiqatan
 * qidiradigan ishlar — kuryer, ofitsiant, promouter, call-markaz.
 */
object JobCatalog {

    /** "Boshqa" kategoriyasi — tanlansa erkin nom so'raladi. */
    const val OTHER_KEY = "OTHER"

    val CATEGORIES: List<ListingCategory> = listOf(
        ListingCategory("COURIER", "Kuryer"),
        ListingCategory("WAITER", "Ofitsiant"),
        ListingCategory("BARISTA", "Barista"),
        ListingCategory("COOK_HELPER", "Oshpaz yordamchisi"),
        ListingCategory("CASHIER", "Kassir"),
        ListingCategory("SALES", "Sotuvchi"),
        ListingCategory("PROMOTER", "Promouter"),
        ListingCategory("CALL_CENTER", "Call-markaz operatori"),
        ListingCategory("LOADER", "Yuk ortuvchi"),
        ListingCategory("WAREHOUSE", "Ombor xodimi"),
        ListingCategory("CLEANER", "Farrosh"),
        ListingCategory("ANIMATOR", "Animator"),
        ListingCategory("TUTOR_JOB", "O'qituvchi / repetitor"),
        ListingCategory("ADMIN", "Administrator"),
        ListingCategory("SMM", "SMM va kontent"),
        ListingCategory("IT", "IT va dasturlash"),
        ListingCategory("DESIGNER", "Dizayner"),
        ListingCategory("DRIVER", "Haydovchi"),
        ListingCategory("SECURITY", "Qorovul"),
        ListingCategory("BUILDER", "Quruvchi / ishchi"),
        ListingCategory(OTHER_KEY, "Boshqa"),
    )

    fun category(key: String): ListingCategory? = CATEGORIES.firstOrNull { it.key == key }

    /** Nechta odam kerak — tayyor chiplar. */
    val VACANCY_COUNTS = listOf(1, 2, 3, 5, 10)

    /** Kuniga necha soat. */
    val HOURS_PER_DAY = listOf(4, 6, 8, 10, 12)

    /** Smena boshlanish/tugash vaqti uchun tayyor tanlovlar. */
    val TIME_OPTIONS: List<String> = buildList {
        for (hour in 0..23) add(hour.toString().padStart(2, '0') + ":00")
    }

    /**
     * Ish turiga mos smenalar. Kunlik ishda "2/2 smena" ma'nosiz — u doimiy ish grafigi.
     */
    fun shifts(employment: EmploymentType): List<WorkShift> = when (employment) {
        EmploymentType.DAILY -> listOf(
            WorkShift.MORNING,
            WorkShift.DAY,
            WorkShift.EVENING,
            WorkShift.NIGHT,
            WorkShift.FLEXIBLE,
        )
        EmploymentType.PERMANENT -> WorkShift.entries
    }

    /**
     * Ish turiga mos to'lov davri. Kunlik ishda odatda kun oxirida to'lanadi,
     * doimiy ishda — oylik.
     */
    fun payPeriods(employment: EmploymentType): List<PayPeriod> = when (employment) {
        EmploymentType.DAILY -> listOf(PayPeriod.DAILY, PayPeriod.HOURLY, PayPeriod.PER_TASK)
        EmploymentType.PERMANENT -> listOf(PayPeriod.MONTHLY, PayPeriod.DAILY, PayPeriod.HOURLY, PayPeriod.WEEKLY)
    }

    /** To'lov davriga mos narx birligi — narx bo'limi shuni ko'rsatadi. */
    fun priceUnit(payPeriod: PayPeriod): PriceUnit = when (payPeriod) {
        PayPeriod.HOURLY -> PriceUnit.PER_HOUR
        PayPeriod.DAILY -> PriceUnit.PER_DAY
        PayPeriod.WEEKLY -> PriceUnit.PER_DAY
        PayPeriod.MONTHLY -> PriceUnit.PER_MONTH
        PayPeriod.PER_TASK -> PriceUnit.PER_ITEM
    }

    /** Tez-tez uchraydigan talablar — chip sifatida bosib qo'shiladi. */
    val COMMON_REQUIREMENTS = listOf(
        "Rus tilini bilish",
        "Ingliz tilini bilish",
        "Haydovchilik guvohnomasi",
        "Shaxsiy transport",
        "Sanitar kitobcha",
        "Jismoniy chidamlilik",
        "Kompyuter savodxonligi",
        "Muloqot ko'nikmasi",
    )

    /** Tez-tez uchraydigan sharoitlar. */
    val COMMON_BENEFITS = listOf(
        "Tushlik bepul",
        "Yotoqxona beriladi",
        "Yo'l kira to'lanadi",
        "Erkin grafik",
        "O'qish bilan birga",
        "Bonus va ustama",
        "Ish kiyimi beriladi",
        "Tajriba shart emas — o'rgatamiz",
    )
}
