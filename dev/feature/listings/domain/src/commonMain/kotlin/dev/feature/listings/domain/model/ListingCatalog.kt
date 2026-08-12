package dev.feature.listings.domain.model

/**
 * Biznes turi. E'lon yaratilganda tanlanadi va **keyin o'zgarmaydi** — chunki
 * kategoriyalar ([ListingCatalog.categories]) va turga xos maydonlar
 * ([ListingCatalog.attributes]) aynan shunga bog'liq.
 */
enum class BusinessType(
    private val labelUz: String,
    val emoji: String,
    val accent: Long,
    val defaultPriceUnit: PriceUnit,
) {
    GAME_CLUB("Game Club", "🎮", 0xFF7C5CFF, PriceUnit.PER_HOUR),
    GROCERY("Oziq-ovqat", "🛒", 0xFF22C55E, PriceUnit.PER_ITEM),
    CLOTHING("Kiyim-kechak", "👕", 0xFFEC4899, PriceUnit.PER_ITEM),
    CAFE_RESTAURANT("Kafe va Restoran", "🍕", 0xFFF97316, PriceUnit.PER_ITEM),
    EDUCATION_CENTER("O'quv markaz", "📚", 0xFF3B82F6, PriceUnit.PER_MONTH),
    ENTERTAINMENT("Kino va ko'ngilochar", "🎬", 0xFFEF4444, PriceUnit.PER_TICKET),
    ELECTRONICS("Texnikalar", "💻", 0xFF06B6D4, PriceUnit.PER_ITEM),
    ;

    val label: String get() = trListing(labelUz)
}

/** Biznes turi ichidagi bo'lim: "Pitsa", "PS5", "IELTS kurslari". */
data class ListingCategory(val key: String, private val labelUz: String) {
    /** Ko'rsatiladigan nom — joriy tilda ([trListing]). */
    val label: String get() = trListing(labelUz)
}

/** Turga xos maydonning kiritish usuli. */
enum class AttributeKind {
    TEXT,
    NUMBER,
    BOOLEAN,
    /** [AttributeSpec.options] dan bittasi tanlanadi. */
    SELECT,
    /** Vergul bilan ajratilgan ro'yxat: "Mozzarella, Pepperoni". */
    TAGS,
}

/**
 * Turga xos maydon tavsifi. Forma **shu ro'yxatdan dinamik quriladi** — ilovada
 * har bir biznes turi uchun alohida forma yozilmaydi.
 *
 * Bu backenddagi `GET /business/types/{type}/attributes-schema` ning offline ekvivalenti:
 * backend yoqilganda shu ro'yxat serverdan keladi va ilova o'zgarmaydi.
 */
data class AttributeSpec(
    val key: String,
    private val labelUz: String,
    val kind: AttributeKind,
    private val hintUz: String = "",
    private val optionsUz: List<String> = emptyList(),
    val required: Boolean = false,
    /** Raqamli maydon uchun o'lchov birligi ("gramm", "oy", "daqiqa"). */
    private val suffixUz: String? = null,
) {
    val label: String get() = trListing(labelUz)
    val hint: String get() = if (hintUz.isEmpty()) "" else trListing(hintUz)
    val options: List<String> get() = optionsUz.map(::trListing)
    val suffix: String? get() = suffixUz?.let(::trListing)
}

/**
 * Biznes turlarining kategoriyalari va maydonlari — `DISCOUNTS_BUSINESS_API.md` §4 dan.
 * Backend tayyor bo'lganda bu katalog `/business/types/...` javoblari bilan almashtiriladi.
 */
object ListingCatalog {

    /** "Boshqa" kategoriyasi — tanlansa `customCategoryName` majburiy bo'ladi. */
    const val OTHER_KEY = "OTHER"

    /** "Hammasiga" — chegirma butun assortimentga amal qiladi (eng ko'p ishlatiladigan holat). */
    const val ALL_KEY = "ALL"

    /** `attributes` ichidagi belgi: e'lon ODDIY (chegirmasiz). Validatsiya chegirmani o'tkazadi. */
    const val REGULAR_KEY = "_regular"

    /** `attributes` ichidagi aloqa telefoni. */
    const val PHONE_KEY = "_phone"

    /** Turga qarab "hammasiga" chipining yozuvi. */
    fun allLabel(type: BusinessType): String = when (type) {
        BusinessType.CAFE_RESTAURANT -> "Butun menyuga"
        BusinessType.GROCERY -> "Barcha mahsulotlarga"
        BusinessType.CLOTHING -> "Butun assortimentga"
        BusinessType.GAME_CLUB -> "Barcha zallarga"
        BusinessType.EDUCATION_CENTER -> "Barcha kurslarga"
        BusinessType.ENTERTAINMENT -> "Barcha seanslarga"
        BusinessType.ELECTRONICS -> "Barcha texnikaga"
    }

    fun categories(type: BusinessType): List<ListingCategory> = categoryMap.getValue(type)

    fun category(type: BusinessType, key: String): ListingCategory? =
        categories(type).firstOrNull { it.key == key }

    fun attributes(type: BusinessType): List<AttributeSpec> = attributeMap.getValue(type)

    /**
     * KATEGORIYAGA xos maydonlar — masalan Game Club'da "PlayStation" tanlansa model/joystik,
     * "Billiard" tanlansa stol turi so'raladi. Hozircha Game Club uchun to'liq; boshqa turlar
     * bo'sh (ular kategoriya-xos maydonsiz).
     */
    fun categoryAttributes(type: BusinessType, categoryKey: String): List<AttributeSpec> =
        if (type == BusinessType.GAME_CLUB) gameClubCategoryAttributes[categoryKey].orEmpty() else emptyList()

    private val gameClubCategoryAttributes: Map<String, List<AttributeSpec>> = mapOf(
        "PLAYSTATION" to listOf(
            AttributeSpec("model", "Model", AttributeKind.SELECT, optionsUz = listOf("PS5", "PS4 Pro", "PS4", "PS3"), required = true),
            AttributeSpec("joysticks", "Joystiklar", AttributeKind.SELECT, optionsUz = listOf("2 ta", "4 ta")),
            AttributeSpec("sessionMinutes", "Sessiya davomiyligi", AttributeKind.NUMBER, hintUz = "60", suffixUz = "daqiqa"),
            AttributeSpec("games", "Mashhur o'yinlar", AttributeKind.TAGS, hintUz = "FIFA 25, Mortal Kombat, UFC"),
        ),
        "TABLE_TENNIS" to listOf(
            AttributeSpec("tables", "Stollar soni", AttributeKind.NUMBER, hintUz = "2", suffixUz = "ta"),
            AttributeSpec("sessionMinutes", "Sessiya davomiyligi", AttributeKind.NUMBER, hintUz = "60", suffixUz = "daqiqa"),
            AttributeSpec("racketsIncluded", "Raketka va koptok beriladi", AttributeKind.BOOLEAN),
        ),
        "TENNIS" to listOf(
            AttributeSpec("courtType", "Kort turi", AttributeKind.SELECT, optionsUz = listOf("Ochiq", "Yopiq"), required = true),
            AttributeSpec("surface", "Qoplama", AttributeKind.SELECT, optionsUz = listOf("Gruntli", "Sun'iy o't", "Qattiq (hard)")),
            AttributeSpec("sessionMinutes", "Sessiya davomiyligi", AttributeKind.NUMBER, hintUz = "60", suffixUz = "daqiqa"),
            AttributeSpec("gearIncluded", "Raketka beriladi", AttributeKind.BOOLEAN),
        ),
        "PC_GAMING" to listOf(
            AttributeSpec("pcTier", "Kompyuter quvvati", AttributeKind.SELECT, optionsUz = listOf("Standart", "Gaming", "Pro / e-sport"), required = true),
            AttributeSpec("sessionMinutes", "Sessiya davomiyligi", AttributeKind.NUMBER, hintUz = "60", suffixUz = "daqiqa"),
            AttributeSpec("games", "O'yinlar", AttributeKind.TAGS, hintUz = "CS2, Dota 2, Valorant"),
        ),
        "BILLIARDS" to listOf(
            AttributeSpec("tableType", "Stol turi", AttributeKind.SELECT, optionsUz = listOf("Pul (Amerika)", "Rus", "Snuker"), required = true),
            AttributeSpec("tables", "Stollar soni", AttributeKind.NUMBER, hintUz = "3", suffixUz = "ta"),
            AttributeSpec("sessionMinutes", "Sessiya davomiyligi", AttributeKind.NUMBER, hintUz = "60", suffixUz = "daqiqa"),
        ),
        "POLYA" to listOf(
            AttributeSpec("fieldType", "Maydon turi", AttributeKind.SELECT, optionsUz = listOf("Mini-futbol", "Basketbol", "Voleybol", "Boshqa")),
            AttributeSpec("fields", "Maydonlar soni", AttributeKind.NUMBER, hintUz = "2", suffixUz = "ta"),
            AttributeSpec("sessionMinutes", "Sessiya davomiyligi", AttributeKind.NUMBER, hintUz = "60", suffixUz = "daqiqa"),
        ),
    )

    /** Turga mos narx birliklari (birinchisi — odatiy). */
    fun priceUnits(type: BusinessType): List<PriceUnit> = when (type) {
        BusinessType.GAME_CLUB -> listOf(PriceUnit.PER_HOUR, PriceUnit.PER_SESSION, PriceUnit.PER_PERSON)
        BusinessType.GROCERY -> listOf(PriceUnit.PER_ITEM, PriceUnit.PER_KG)
        BusinessType.CLOTHING -> listOf(PriceUnit.PER_ITEM)
        BusinessType.CAFE_RESTAURANT -> listOf(PriceUnit.PER_ITEM, PriceUnit.PER_KG)
        BusinessType.EDUCATION_CENTER -> listOf(PriceUnit.PER_MONTH, PriceUnit.PER_COURSE, PriceUnit.PER_LESSON)
        BusinessType.ENTERTAINMENT -> listOf(PriceUnit.PER_TICKET, PriceUnit.PER_PERSON, PriceUnit.PER_SESSION)
        BusinessType.ELECTRONICS -> listOf(PriceUnit.PER_ITEM)
    }

    /** Turga mos qo'shimchalar guruhi uchun taklif ("Hajmni tanlang" kabi). */
    fun optionGroupHint(type: BusinessType): String = when (type) {
        BusinessType.GAME_CLUB -> "Zal turi, qo'shimcha joystik"
        BusinessType.GROCERY -> "Hajm, ta'm"
        BusinessType.CLOTHING -> "O'lcham, rang"
        BusinessType.CAFE_RESTAURANT -> "Hajm, qo'shimchalar"
        BusinessType.EDUCATION_CENTER -> "Guruh vaqti, format"
        BusinessType.ENTERTAINMENT -> "Seans vaqti, joy turi"
        BusinessType.ELECTRONICS -> "Xotira hajmi, rang"
    }

    private fun cats(type: BusinessType, vararg pairs: Pair<String, String>): List<ListingCategory> =
        listOf(ListingCategory(ALL_KEY, allLabel(type))) +
            pairs.map { (key, label) -> ListingCategory(key, label) } +
            ListingCategory(OTHER_KEY, "Boshqa")

    private val categoryMap: Map<BusinessType, List<ListingCategory>> = mapOf(
        BusinessType.GAME_CLUB to cats(
            BusinessType.GAME_CLUB,
            "PLAYSTATION" to "PlayStation",
            "TABLE_TENNIS" to "Stol tennis",
            "TENNIS" to "Katta tennis",
            "PC_GAMING" to "Kompyuter o'yinlari",
            "BILLIARDS" to "Billiard",
            "POLYA" to "Polya",
        ),
        BusinessType.GROCERY to cats(
            BusinessType.GROCERY,
            "BAKERY" to "Non va yopilgan",
            "DAIRY" to "Sut mahsulotlari",
            "MEAT_FISH" to "Go'sht va baliq",
            "FRUITS_VEGETABLES" to "Meva-sabzavot",
            "DRINKS" to "Ichimliklar",
            "SWEETS" to "Shirinliklar",
            "GROCERY_BASICS" to "Bakaleya",
            "READY_MEALS" to "Tayyor ovqat",
            "SNACKS" to "Gazaklar",
        ),
        BusinessType.CLOTHING to cats(
            BusinessType.CLOTHING,
            "MEN" to "Erkaklar",
            "WOMEN" to "Ayollar",
            "OUTERWEAR" to "Ustki kiyim",
            "SHOES" to "Poyabzal",
            "SPORTSWEAR" to "Sport kiyim",
            "BAGS" to "Sumkalar",
            "ACCESSORIES" to "Aksessuarlar",
        ),
        BusinessType.CAFE_RESTAURANT to cats(
            BusinessType.CAFE_RESTAURANT,
            "NATIONAL" to "Milliy taomlar",
            "PIZZA" to "Pitsa",
            "BURGER" to "Burger",
            "LAVASH_SHAWARMA" to "Lavash / Shaurma",
            "SUSHI" to "Sushi",
            "FAST_FOOD" to "Fast food",
            "SOUPS" to "Sho'rvalar",
            "SALADS" to "Salatlar",
            "GRILL_BBQ" to "Kabob va grill",
            "BREAKFAST" to "Nonushta",
            "DESSERTS" to "Shirinliklar",
            "HOT_DRINKS" to "Choy va kofe",
            "COLD_DRINKS" to "Sovuq ichimliklar",
            "COMBO_SETS" to "Setlar (combo)",
        ),
        BusinessType.EDUCATION_CENTER to cats(
            BusinessType.EDUCATION_CENTER,
            "FOREIGN_LANGUAGES" to "Chet tillari",
            "IELTS_CEFR" to "IELTS / CEFR",
            "IT_PROGRAMMING" to "IT va dasturlash",
            "DESIGN" to "Dizayn",
            "MATH_SCIENCE" to "Matematika va fanlar",
            "UNIVERSITY_PREP" to "Abituriyent tayyorlash",
            "BUSINESS_MARKETING" to "Biznes va marketing",
            "MASTER_CLASS" to "Master-klass",
        ),
        BusinessType.ENTERTAINMENT to cats(
            BusinessType.ENTERTAINMENT,
            "CINEMA" to "Kino seans",
            "THEATER_CONCERT" to "Teatr va konsert",
            "ESCAPE_ROOM" to "Kvest (escape room)",
            "TRAMPOLINE_PARK" to "Batut park",
            "BOWLING" to "Bouling",
            "AQUAPARK" to "Akvapark",
            "AMUSEMENT_PARK" to "Attraksionlar",
            "MUSEUM_EXPO" to "Muzey va ko'rgazma",
            "KARAOKE" to "Karaoke",
        ),
        BusinessType.ELECTRONICS to cats(
            BusinessType.ELECTRONICS,
            "PHONES" to "Telefonlar",
            "LAPTOPS" to "Noutbuklar",
            "TABLETS" to "Planshetlar",
            "AUDIO" to "Audio",
            "WEARABLES" to "Smart-soat",
            "GAMING_GEAR" to "Gaming qurilmalar",
            "HOME_APPLIANCES" to "Maishiy texnika",
            "ACCESSORIES" to "Aksessuarlar",
        ),
    )

    /**
     * Turga xos maydonlar — **faqat eng zaruriylari**. Ilgari 8-9 tadan edi va forma
     * cho'zilib ketardi; talabaga foydasi bo'lmagan maydonlar olib tashlandi.
     * Kalitlar backend spec'i bilan bir xil (`DISCOUNTS_BUSINESS_API.md` §4).
     */
    private val attributeMap: Map<BusinessType, List<AttributeSpec>> = mapOf(
        BusinessType.GAME_CLUB to listOf(
            AttributeSpec("hallType", "Zal turi", AttributeKind.SELECT, optionsUz = listOf("Standart", "VIP", "Alohida xona")),
            AttributeSpec("deviceModel", "Qurilma", AttributeKind.TEXT, hintUz = "PlayStation 5 Slim"),
            AttributeSpec("seatsCount", "Nechta o'yinchi", AttributeKind.NUMBER, hintUz = "4", suffixUz = "kishi"),
            AttributeSpec("sessionMinutes", "Sessiya davomiyligi", AttributeKind.NUMBER, hintUz = "60", suffixUz = "daqiqa"),
            AttributeSpec("gamesList", "O'yinlar", AttributeKind.TAGS, hintUz = "FIFA 25, Mortal Kombat"),
        ),
        BusinessType.GROCERY to listOf(
            AttributeSpec("brand", "Brend", AttributeKind.TEXT, hintUz = "Nestlé"),
            AttributeSpec("weightGrams", "Og'irlik / hajm", AttributeKind.NUMBER, hintUz = "500", suffixUz = "gramm"),
            AttributeSpec("expiryDate", "Yaroqlilik muddati", AttributeKind.TEXT, hintUz = "2026-12-01"),
            AttributeSpec("isHalal", "Halol", AttributeKind.BOOLEAN),
            AttributeSpec("stockCount", "Qoldiq", AttributeKind.NUMBER, hintUz = "40", suffixUz = "dona"),
        ),
        BusinessType.CLOTHING to listOf(
            AttributeSpec("brand", "Brend", AttributeKind.TEXT, hintUz = "Zara"),
            AttributeSpec("gender", "Kimlar uchun", AttributeKind.SELECT, optionsUz = listOf("Erkaklar", "Ayollar", "Uniseks", "Bolalar")),
            AttributeSpec("material", "Material", AttributeKind.TEXT, hintUz = "100% paxta"),
            AttributeSpec("season", "Mavsum", AttributeKind.SELECT, optionsUz = listOf("Qish", "Bahor", "Yoz", "Kuz", "Barcha mavsum")),
        ),
        BusinessType.CAFE_RESTAURANT to listOf(
            AttributeSpec("portionGrams", "Porsiya", AttributeKind.NUMBER, hintUz = "550", suffixUz = "gramm"),
            AttributeSpec("ingredients", "Tarkibi", AttributeKind.TAGS, hintUz = "Mozzarella, Pepperoni, Tomat sousi"),
            AttributeSpec("spicyLevel", "O'tkirlik", AttributeKind.SELECT, optionsUz = listOf("Yo'q", "Yengil", "O'rtacha", "O'tkir")),
            AttributeSpec("isHalal", "Halol", AttributeKind.BOOLEAN),
            AttributeSpec("hasDelivery", "Yetkazib berish bor", AttributeKind.BOOLEAN),
        ),
        BusinessType.EDUCATION_CENTER to listOf(
            AttributeSpec("subject", "Yo'nalish", AttributeKind.TEXT, hintUz = "Ingliz tili — IELTS 6.5+"),
            AttributeSpec("level", "Daraja", AttributeKind.SELECT, optionsUz = listOf("Boshlang'ich", "O'rta", "Yuqori")),
            AttributeSpec("format", "Format", AttributeKind.SELECT, optionsUz = listOf("Offline", "Online", "Aralash")),
            AttributeSpec("durationMonths", "Davomiyligi", AttributeKind.NUMBER, hintUz = "3", suffixUz = "oy"),
            AttributeSpec("lessonsPerWeek", "Haftada", AttributeKind.NUMBER, hintUz = "3", suffixUz = "marta"),
            AttributeSpec("hasFreeTrialLesson", "Birinchi dars bepul", AttributeKind.BOOLEAN),
        ),
        BusinessType.ENTERTAINMENT to listOf(
            AttributeSpec("eventTitle", "Film / tadbir nomi", AttributeKind.TEXT, hintUz = "Dune: Part Three"),
            AttributeSpec("format", "Format", AttributeKind.SELECT, optionsUz = listOf("2D", "3D", "IMAX", "4DX", "VR")),
            AttributeSpec("language", "Til", AttributeKind.SELECT, optionsUz = listOf("O'zbek", "Rus", "Ingliz", "Original (subtitr)")),
            AttributeSpec("ageLimit", "Yosh chegarasi", AttributeKind.SELECT, optionsUz = listOf("0+", "6+", "12+", "16+", "18+")),
            AttributeSpec("sessionTimes", "Seans vaqtlari", AttributeKind.TAGS, hintUz = "12:30, 16:00, 19:40"),
        ),
        BusinessType.ELECTRONICS to listOf(
            AttributeSpec("brand", "Brend", AttributeKind.TEXT, hintUz = "Apple"),
            AttributeSpec("condition", "Holati", AttributeKind.SELECT, optionsUz = listOf("Yangi", "Qayta tiklangan", "Ishlatilgan")),
            AttributeSpec("warrantyMonths", "Kafolat", AttributeKind.NUMBER, hintUz = "12", suffixUz = "oy"),
            AttributeSpec("hasInstallment", "Muddatli to'lov bor", AttributeKind.BOOLEAN),
        ),
    )
}
