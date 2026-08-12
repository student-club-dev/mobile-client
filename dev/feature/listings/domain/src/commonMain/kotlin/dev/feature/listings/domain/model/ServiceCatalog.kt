package dev.feature.listings.domain.model

/**
 * Xizmat sohasi. Har birining o'z maydonlari bor ([ServiceCatalog.fields]) — repetitorda
 * "Fan", "Daraja", "Dars davomiyligi"; chop etishda "Format", "Rangli/oq-qora", "Qog'oz zichligi".
 *
 * [hasSubjects] — sohaning ichida yana **yo'nalish** tanlanadimi. Repetitorda bu fan
 * (matematika, IELTS, dasturlash), chop etishda — xizmat turi (banner, diplom ishi, foto).
 */
enum class ServiceType(
    private val labelUz: String,
    val emoji: String,
    val hasSubjects: Boolean = false,
    val defaultPriceUnit: PriceUnit = PriceUnit.PER_HOUR,
) {
    TUTOR("Repetitor", "📖", hasSubjects = true, defaultPriceUnit = PriceUnit.PER_LESSON),
    PRINTING("Chop etish (printer)", "🖨️", hasSubjects = true, defaultPriceUnit = PriceUnit.PER_PAGE),
    IT_DEV("IT va dasturlash", "💻", hasSubjects = true, defaultPriceUnit = PriceUnit.PER_HOUR),
    DESIGN("Dizayn", "🎨", hasSubjects = true, defaultPriceUnit = PriceUnit.PER_ITEM),
    PHOTO_VIDEO("Foto va video", "📸", hasSubjects = true, defaultPriceUnit = PriceUnit.PER_HOUR),
    TRANSLATION("Tarjima", "🌐", hasSubjects = true, defaultPriceUnit = PriceUnit.PER_PAGE),
    REPAIR("Ta'mirlash", "🔧", hasSubjects = true, defaultPriceUnit = PriceUnit.PER_ITEM),
    BEAUTY("Go'zallik", "💇", hasSubjects = true, defaultPriceUnit = PriceUnit.PER_SESSION),
    TRANSPORT("Transport va yetkazish", "🚗", hasSubjects = true, defaultPriceUnit = PriceUnit.PER_ITEM),
    EVENT("Tadbir tashkil qilish", "🎉", hasSubjects = true, defaultPriceUnit = PriceUnit.PER_SESSION),
    CLEANING("Tozalash", "🧹", defaultPriceUnit = PriceUnit.PER_SESSION),
    OTHER("Boshqa xizmat", "🛠️", defaultPriceUnit = PriceUnit.PER_ITEM),
    ;

    val label: String get() = trListing(labelUz)
}

/**
 * Xizmat sohalari va ularning maydonlari.
 *
 * Tuzilishi [ListingCatalog] bilan bir xil — ikki bosqichli: avval **soha** ([ServiceType]),
 * keyin soha ichidagi **yo'nalish** ([subjects]), va har ikkalasining o'z maydonlari bor.
 * Repetitorda: soha = Repetitor, yo'nalish = IELTS, yo'nalishga xos maydon = "Maqsad ball".
 *
 * Forma shu spetsifikatsiyadan **dinamik quriladi** — har bir soha uchun alohida ekran
 * yozilmaydi. Backend tayyor bo'lganda spetsifikatsiya `/services/types/{type}/schema`
 * dan keladi va ilova o'zgarmaydi.
 */
object ServiceCatalog {

    /** Yo'nalish saqlanadigan kalit — [ListingDetails.Service.fields] ichida. */
    const val SUBJECT_KEY = "subject"

    /** "Boshqa" yo'nalishi — tanlansa erkin nom so'raladi. */
    const val OTHER_SUBJECT_KEY = "OTHER"

    /** "Boshqa" tanlanganda yoziladigan erkin nom. */
    const val CUSTOM_SUBJECT_KEY = "customSubject"

    // -----------------------------------------------------------------------
    // Yo'nalishlar (soha ichidagi tanlov)
    // -----------------------------------------------------------------------

    fun subjects(type: ServiceType): List<ListingCategory> = subjectMap[type].orEmpty()

    fun subject(type: ServiceType, key: String): ListingCategory? =
        subjects(type).firstOrNull { it.key == key }

    /** Sohaga qarab yo'nalish tanlovining sarlavhasi. */
    fun subjectLabel(type: ServiceType): String = when (type) {
        ServiceType.TUTOR -> "Fan yoki yo'nalish"
        ServiceType.PRINTING -> "Chop etish turi"
        ServiceType.IT_DEV -> "Yo'nalish"
        ServiceType.DESIGN -> "Dizayn turi"
        ServiceType.PHOTO_VIDEO -> "Suratga olish turi"
        ServiceType.TRANSLATION -> "Til yo'nalishi"
        ServiceType.REPAIR -> "Nimani ta'mirlaysiz"
        ServiceType.BEAUTY -> "Xizmat turi"
        ServiceType.TRANSPORT -> "Transport xizmati"
        ServiceType.EVENT -> "Tadbir turi"
        ServiceType.CLEANING, ServiceType.OTHER -> "Yo'nalish"
    }

    private fun subj(vararg pairs: Pair<String, String>): List<ListingCategory> =
        pairs.map { (key, label) -> ListingCategory(key, label) } +
            ListingCategory(OTHER_SUBJECT_KEY, "Boshqa")

    private val subjectMap: Map<ServiceType, List<ListingCategory>> = mapOf(
        ServiceType.TUTOR to subj(
            "MATH" to "Matematika",
            "PHYSICS" to "Fizika",
            "CHEMISTRY" to "Kimyo",
            "BIOLOGY" to "Biologiya",
            "ENGLISH" to "Ingliz tili",
            "IELTS" to "IELTS",
            "CEFR" to "CEFR / Multilevel",
            "SAT" to "SAT",
            "RUSSIAN" to "Rus tili",
            "UZBEK" to "O'zbek tili va adabiyot",
            "KOREAN" to "Koreys tili",
            "TURKISH" to "Turk tili",
            "GERMAN" to "Nemis tili",
            "ARABIC" to "Arab tili",
            "CHINESE" to "Xitoy tili",
            "HISTORY" to "Tarix",
            "GEOGRAPHY" to "Geografiya",
            "PROGRAMMING" to "Dasturlash",
            "MUSIC" to "Musiqa",
            "ART" to "Rasm va san'at",
            "PRIMARY" to "Boshlang'ich sinflar",
            "UNIVERSITY_PREP" to "Abituriyent tayyorlash",
        ),
        ServiceType.PRINTING to subj(
            "DOCUMENT" to "Hujjat chop etish",
            "DIPLOMA_WORK" to "Diplom / kurs ishi",
            "PHOTO" to "Foto chop etish",
            "BANNER" to "Banner va reklama",
            "BUSINESS_CARD" to "Vizitka",
            "BOOKLET" to "Buklet va flayer",
            "COPY" to "Nusxa ko'chirish",
            "SCAN" to "Skanerlash",
            "LAMINATION" to "Laminatsiya",
            "BINDING" to "Muqovalash / tikish",
            "STICKER" to "Nakleyka va stiker",
            "TSHIRT" to "Futbolkaga bosma",
            "PLOTTER" to "Plotter (katta format)",
        ),
        ServiceType.IT_DEV to subj(
            "WEB" to "Veb-sayt",
            "MOBILE" to "Mobil ilova",
            "BOT" to "Telegram bot",
            "BACKEND" to "Backend / API",
            "DATA" to "Ma'lumotlar tahlili",
            "QA" to "Testlash",
            "DEVOPS" to "DevOps",
            "SUPPORT" to "Kompyuter yordami",
        ),
        ServiceType.DESIGN to subj(
            "LOGO" to "Logotip",
            "BRANDING" to "Brending",
            "UI_UX" to "UI/UX dizayn",
            "SMM_DESIGN" to "SMM postlar",
            "PRESENTATION" to "Prezentatsiya",
            "PRINT_DESIGN" to "Poligrafiya maketi",
            "INTERIOR" to "Interyer dizayn",
            "MOTION" to "Motion / animatsiya",
        ),
        ServiceType.PHOTO_VIDEO to subj(
            "EVENT_PHOTO" to "Tadbir surati",
            "PORTRAIT" to "Portret / lavha",
            "PRODUCT" to "Mahsulot surati",
            "WEDDING" to "To'y",
            "VIDEO_SHOOT" to "Videosuratga olish",
            "VIDEO_EDIT" to "Video montaj",
            "DRONE" to "Kvadrokopter",
            "REELS" to "Reels / TikTok",
        ),
        ServiceType.TRANSLATION to subj(
            "UZ_EN" to "O'zbek ⇄ Ingliz",
            "UZ_RU" to "O'zbek ⇄ Rus",
            "EN_RU" to "Ingliz ⇄ Rus",
            "UZ_TR" to "O'zbek ⇄ Turk",
            "UZ_KO" to "O'zbek ⇄ Koreys",
            "DOCUMENT_TRANSLATION" to "Hujjat tarjimasi",
            "SIMULTANEOUS" to "Sinxron tarjima",
        ),
        ServiceType.REPAIR to subj(
            "PHONE" to "Telefon",
            "LAPTOP" to "Noutbuk / kompyuter",
            "APPLIANCE" to "Maishiy texnika",
            "FURNITURE" to "Mebel",
            "PLUMBING" to "Santexnika",
            "ELECTRIC" to "Elektrika",
            "CLOTHES" to "Kiyim tikish / tuzatish",
            "SHOES" to "Poyabzal",
        ),
        ServiceType.BEAUTY to subj(
            "HAIRCUT" to "Soch olish",
            "HAIR_STYLE" to "Soch turmagi",
            "MANICURE" to "Manikyur",
            "MAKEUP" to "Vizaj",
            "BROWS" to "Qosh va kiprik",
            "COSMETOLOGY" to "Kosmetologiya",
            "MASSAGE" to "Massaj",
        ),
        ServiceType.TRANSPORT to subj(
            "DELIVERY" to "Yetkazib berish",
            "TAXI" to "Taksi",
            "MOVING" to "Ko'chish (yuk tashish)",
            "FREIGHT" to "Yuk mashinasi",
            "DRIVER" to "Haydovchi xizmati",
        ),
        ServiceType.EVENT to subj(
            "HOST" to "Boshlovchi",
            "DJ" to "DJ va musiqa",
            "DECOR" to "Bezatish",
            "ANIMATOR" to "Animator",
            "CATERING" to "Ovqatlantirish",
            "SOUND_LIGHT" to "Ovoz va yorug'lik",
        ),
    )

    // -----------------------------------------------------------------------
    // Sohaga xos maydonlar
    // -----------------------------------------------------------------------

    /** Butun soha uchun umumiy maydonlar (yo'nalishdan qat'i nazar). */
    fun fields(type: ServiceType): List<AttributeSpec> = fieldMap[type].orEmpty()

    /**
     * YO'NALISHGA xos qo'shimcha maydonlar — IELTS tanlansa "Maqsad ball", banner tanlansa
     * "O'lcham va material" so'raladi. Yo'nalish tanlanmagan bo'lsa — bo'sh.
     */
    fun subjectFields(type: ServiceType, subjectKey: String): List<AttributeSpec> =
        subjectFieldMap[type]?.get(subjectKey).orEmpty()

    /** Sohaga mos narx birliklari (birinchisi — odatiy). */
    fun priceUnits(type: ServiceType): List<PriceUnit> = when (type) {
        ServiceType.TUTOR -> listOf(PriceUnit.PER_LESSON, PriceUnit.PER_HOUR, PriceUnit.PER_MONTH, PriceUnit.PER_COURSE)
        ServiceType.PRINTING -> listOf(PriceUnit.PER_PAGE, PriceUnit.PER_ITEM)
        ServiceType.IT_DEV -> listOf(PriceUnit.PER_HOUR, PriceUnit.PER_ITEM, PriceUnit.PER_MONTH)
        ServiceType.DESIGN -> listOf(PriceUnit.PER_ITEM, PriceUnit.PER_HOUR)
        ServiceType.PHOTO_VIDEO -> listOf(PriceUnit.PER_HOUR, PriceUnit.PER_SESSION, PriceUnit.PER_ITEM)
        ServiceType.TRANSLATION -> listOf(PriceUnit.PER_PAGE, PriceUnit.PER_HOUR, PriceUnit.PER_ITEM)
        ServiceType.REPAIR -> listOf(PriceUnit.PER_ITEM, PriceUnit.PER_HOUR)
        ServiceType.BEAUTY -> listOf(PriceUnit.PER_SESSION, PriceUnit.PER_ITEM)
        ServiceType.TRANSPORT -> listOf(PriceUnit.PER_ITEM, PriceUnit.PER_HOUR, PriceUnit.PER_DAY)
        ServiceType.EVENT -> listOf(PriceUnit.PER_SESSION, PriceUnit.PER_HOUR, PriceUnit.PER_DAY)
        ServiceType.CLEANING -> listOf(PriceUnit.PER_SESSION, PriceUnit.PER_HOUR)
        ServiceType.OTHER -> listOf(PriceUnit.PER_ITEM, PriceUnit.PER_HOUR, PriceUnit.PER_SESSION)
    }

    private val fieldMap: Map<ServiceType, List<AttributeSpec>> = mapOf(
        ServiceType.TUTOR to listOf(
            AttributeSpec(
                "level", "Qaysi daraja uchun", AttributeKind.SELECT,
                optionsUz = listOf("Boshlang'ich sinf", "5–9 sinf", "10–11 sinf", "Abituriyent", "Talaba", "Kattalar"),
                required = true,
            ),
            AttributeSpec(
                "lessonMode", "Dars shakli", AttributeKind.SELECT,
                optionsUz = listOf("Yakka tartibda", "Kichik guruh (2–4)", "Guruh (5+)"),
                required = true,
            ),
            AttributeSpec("lessonMinutes", "Bitta dars davomiyligi", AttributeKind.NUMBER, hintUz = "60", suffixUz = "daqiqa"),
            AttributeSpec("lessonsPerWeek", "Haftada necha marta", AttributeKind.NUMBER, hintUz = "3", suffixUz = "marta"),
            AttributeSpec("studentsMax", "Guruhda maksimal", AttributeKind.NUMBER, hintUz = "6", suffixUz = "kishi"),
            AttributeSpec("place", "Dars joyi", AttributeKind.SELECT, optionsUz = listOf("Mening joyimda", "O'quvchi uyida", "Onlayn", "Kelishilgan holda")),
            AttributeSpec("certificates", "Sertifikat va yutuqlar", AttributeKind.TAGS, hintUz = "IELTS 7.5, TEFL"),
        ),
        ServiceType.PRINTING to listOf(
            AttributeSpec(
                "paperFormat", "Qog'oz formati", AttributeKind.SELECT,
                optionsUz = listOf("A6", "A5", "A4", "A3", "A2", "A1", "A0", "Boshqa"),
                required = true,
            ),
            AttributeSpec("colorMode", "Rang", AttributeKind.SELECT, optionsUz = listOf("Oq-qora", "Rangli", "Ikkalasi ham"), required = true),
            AttributeSpec("sides", "Tomonlama", AttributeKind.SELECT, optionsUz = listOf("Bir tomonlama", "Ikki tomonlama")),
            AttributeSpec("paperDensity", "Qog'oz zichligi", AttributeKind.SELECT, optionsUz = listOf("80 g/m²", "120 g/m²", "160 g/m²", "200 g/m²", "300 g/m²")),
            AttributeSpec("minOrder", "Minimal buyurtma", AttributeKind.NUMBER, hintUz = "10", suffixUz = "dona"),
            AttributeSpec("turnaround", "Tayyorlash muddati", AttributeKind.SELECT, optionsUz = listOf("30 daqiqada", "1 soatda", "Shu kuni", "1–2 kun", "3+ kun")),
            AttributeSpec("hasUrgent", "Shoshilinch buyurtma qabul qilinadi", AttributeKind.BOOLEAN),
            AttributeSpec("hasDelivery", "Yetkazib berish bor", AttributeKind.BOOLEAN),
        ),
        ServiceType.IT_DEV to listOf(
            AttributeSpec("stack", "Texnologiyalar", AttributeKind.TAGS, hintUz = "Kotlin, Spring, PostgreSQL", required = true),
            AttributeSpec("deliveryDays", "Bajarish muddati", AttributeKind.NUMBER, hintUz = "14", suffixUz = "kun"),
            AttributeSpec("hasSupport", "Topshirgandan keyin qo'llab-quvvatlash", AttributeKind.BOOLEAN),
            AttributeSpec("portfolio", "Portfolio havolasi", AttributeKind.TEXT, hintUz = "github.com/username"),
        ),
        ServiceType.DESIGN to listOf(
            AttributeSpec("tools", "Dasturlar", AttributeKind.TAGS, hintUz = "Figma, Illustrator, Photoshop"),
            AttributeSpec("revisions", "Nechta tuzatish kiradi", AttributeKind.NUMBER, hintUz = "3", suffixUz = "marta"),
            AttributeSpec("deliveryDays", "Bajarish muddati", AttributeKind.NUMBER, hintUz = "5", suffixUz = "kun"),
            AttributeSpec("sourceFiles", "Manba fayllar beriladi", AttributeKind.BOOLEAN),
            AttributeSpec("portfolio", "Portfolio havolasi", AttributeKind.TEXT, hintUz = "behance.net/username"),
        ),
        ServiceType.PHOTO_VIDEO to listOf(
            AttributeSpec("gear", "Texnika", AttributeKind.TAGS, hintUz = "Sony A7 IV, Godox, stabilizator"),
            AttributeSpec("shootMinutes", "Suratga olish davomiyligi", AttributeKind.NUMBER, hintUz = "120", suffixUz = "daqiqa"),
            AttributeSpec("photoCount", "Nechta tayyor surat", AttributeKind.NUMBER, hintUz = "30", suffixUz = "dona"),
            AttributeSpec("editingDays", "Montaj muddati", AttributeKind.NUMBER, hintUz = "7", suffixUz = "kun"),
            AttributeSpec("hasStudio", "O'z studiyam bor", AttributeKind.BOOLEAN),
        ),
        ServiceType.TRANSLATION to listOf(
            AttributeSpec("translationKind", "Tarjima turi", AttributeKind.SELECT, optionsUz = listOf("Yozma", "Og'zaki", "Sinxron", "Notarial")),
            AttributeSpec("pagesPerDay", "Kuniga qancha", AttributeKind.NUMBER, hintUz = "10", suffixUz = "sahifa"),
            AttributeSpec("hasNotary", "Notarial tasdiq bor", AttributeKind.BOOLEAN),
        ),
        ServiceType.REPAIR to listOf(
            AttributeSpec("diagnosticFree", "Diagnostika bepul", AttributeKind.BOOLEAN),
            AttributeSpec("warrantyDays", "Kafolat", AttributeKind.NUMBER, hintUz = "30", suffixUz = "kun"),
            AttributeSpec("partsIncluded", "Ehtiyot qism narxga kiradi", AttributeKind.BOOLEAN),
            AttributeSpec("repairHours", "O'rtacha ta'mirlash vaqti", AttributeKind.NUMBER, hintUz = "24", suffixUz = "soat"),
        ),
        ServiceType.BEAUTY to listOf(
            AttributeSpec("clientGender", "Kimlar uchun", AttributeKind.SELECT, optionsUz = listOf("Ayollar", "Erkaklar", "Ikkalasi ham")),
            AttributeSpec("sessionMinutes", "Xizmat davomiyligi", AttributeKind.NUMBER, hintUz = "60", suffixUz = "daqiqa"),
            AttributeSpec("brands", "Ishlatiladigan vositalar", AttributeKind.TAGS, hintUz = "Kapous, Estel"),
        ),
        ServiceType.TRANSPORT to listOf(
            AttributeSpec("vehicle", "Transport", AttributeKind.TEXT, hintUz = "Damas / Labo / Yengil avtomobil", required = true),
            AttributeSpec("capacityKg", "Yuk sig'imi", AttributeKind.NUMBER, hintUz = "500", suffixUz = "kg"),
            AttributeSpec("coverage", "Qayerlarga", AttributeKind.SELECT, optionsUz = listOf("Shahar ichida", "Viloyat ichida", "Respublika bo'ylab")),
            AttributeSpec("hasLoaders", "Yuk ortuvchilar bor", AttributeKind.BOOLEAN),
        ),
        ServiceType.EVENT to listOf(
            AttributeSpec("eventKinds", "Qanday tadbirlar", AttributeKind.TAGS, hintUz = "To'y, tug'ilgan kun, korporativ"),
            AttributeSpec("guestsMax", "Maksimal mehmon", AttributeKind.NUMBER, hintUz = "200", suffixUz = "kishi"),
            AttributeSpec("equipmentIncluded", "Jihoz narxga kiradi", AttributeKind.BOOLEAN),
        ),
        ServiceType.CLEANING to listOf(
            AttributeSpec("cleaningKind", "Tozalash turi", AttributeKind.SELECT, optionsUz = listOf("Umumiy", "Ta'mirdan keyin", "Kundalik", "Deraza va fasad")),
            AttributeSpec("areaSqm", "Maydon", AttributeKind.NUMBER, hintUz = "60", suffixUz = "m²"),
            AttributeSpec("suppliesIncluded", "Vosita va jihoz o'zimizdan", AttributeKind.BOOLEAN),
        ),
        ServiceType.OTHER to listOf(
            AttributeSpec("serviceName", "Xizmat nomi", AttributeKind.TEXT, hintUz = "Qanday xizmat ko'rsatasiz", required = true),
        ),
    )

    /**
     * Yo'nalishga xos maydonlar. Faqat haqiqatan farq qiladigan yo'nalishlar uchun yozilgan —
     * qolganlariga soha maydonlari yetarli va ortiqcha savol formani cho'zib yuboradi.
     */
    private val subjectFieldMap: Map<ServiceType, Map<String, List<AttributeSpec>>> = mapOf(
        ServiceType.TUTOR to mapOf(
            "IELTS" to listOf(
                AttributeSpec("targetBand", "Maqsad ball", AttributeKind.SELECT, optionsUz = listOf("5.5", "6.0", "6.5", "7.0", "7.5", "8.0+"), required = true),
                AttributeSpec("skills", "Qaysi bo'limlar", AttributeKind.TAGS, hintUz = "Writing, Speaking, Reading, Listening"),
                AttributeSpec("mockIncluded", "Mock imtihon kiradi", AttributeKind.BOOLEAN),
                AttributeSpec("myBand", "Mening ballim", AttributeKind.TEXT, hintUz = "7.5"),
            ),
            "CEFR" to listOf(
                AttributeSpec("targetLevel", "Maqsad daraja", AttributeKind.SELECT, optionsUz = listOf("A2", "B1", "B2", "C1"), required = true),
                AttributeSpec("mockIncluded", "Mock imtihon kiradi", AttributeKind.BOOLEAN),
            ),
            "SAT" to listOf(
                AttributeSpec("targetScore", "Maqsad ball", AttributeKind.SELECT, optionsUz = listOf("1200+", "1300+", "1400+", "1500+"), required = true),
                AttributeSpec("section", "Bo'lim", AttributeKind.SELECT, optionsUz = listOf("Math", "English", "Ikkalasi")),
            ),
            "ENGLISH" to listOf(
                AttributeSpec("startLevel", "Boshlang'ich daraja", AttributeKind.SELECT, optionsUz = listOf("Noldan", "Elementary", "Pre-Intermediate", "Intermediate", "Upper-Intermediate")),
                AttributeSpec("focus", "E'tibor", AttributeKind.SELECT, optionsUz = listOf("Grammatika", "Speaking", "Umumiy", "Biznes ingliz tili")),
            ),
            "MATH" to listOf(
                AttributeSpec("topics", "Mavzular", AttributeKind.TAGS, hintUz = "Algebra, Geometriya, Trigonometriya"),
                AttributeSpec("examPrep", "Imtihonga tayyorlash", AttributeKind.SELECT, optionsUz = listOf("Maktab dasturi", "DTM / Milliy sertifikat", "Olimpiada")),
            ),
            "PROGRAMMING" to listOf(
                AttributeSpec("language", "Dasturlash tili", AttributeKind.SELECT, optionsUz = listOf("Python", "JavaScript", "Java", "Kotlin", "C++", "C#", "PHP", "Go"), required = true),
                AttributeSpec("projectBased", "Loyiha asosida o'qitiladi", AttributeKind.BOOLEAN),
            ),
            "MUSIC" to listOf(
                AttributeSpec("instrument", "Cholg'u", AttributeKind.SELECT, optionsUz = listOf("Piano", "Gitara", "Skripka", "Doira", "Nay", "Vokal"), required = true),
                AttributeSpec("instrumentProvided", "Cholg'u beriladi", AttributeKind.BOOLEAN),
            ),
            "UNIVERSITY_PREP" to listOf(
                AttributeSpec("targetUniversity", "Qaysi OTMga", AttributeKind.TEXT, hintUz = "TATU, INHA, Westminster"),
                AttributeSpec("blockSubjects", "Blok fanlar", AttributeKind.TAGS, hintUz = "Matematika, Fizika"),
            ),
        ),
        ServiceType.PRINTING to mapOf(
            "DOCUMENT" to listOf(
                AttributeSpec("fileFormats", "Qabul qilinadigan fayllar", AttributeKind.TAGS, hintUz = "PDF, DOCX, PPTX"),
                AttributeSpec("staplingIncluded", "Steplerlash bepul", AttributeKind.BOOLEAN),
            ),
            "DIPLOMA_WORK" to listOf(
                AttributeSpec("bindingType", "Muqova turi", AttributeKind.SELECT, optionsUz = listOf("Yumshoq muqova", "Qattiq muqova", "Prujinali", "Termo tikish"), required = true),
                AttributeSpec("coverColor", "Muqova rangi", AttributeKind.SELECT, optionsUz = listOf("Ko'k", "Qora", "Bordo", "Yashil")),
                AttributeSpec("goldEmboss", "Oltin bosma yozuv", AttributeKind.BOOLEAN),
                AttributeSpec("pagesMax", "Maksimal sahifa", AttributeKind.NUMBER, hintUz = "120", suffixUz = "bet"),
            ),
            "PHOTO" to listOf(
                AttributeSpec("photoSizes", "O'lchamlar", AttributeKind.TAGS, hintUz = "3x4, 10x15, 20x30"),
                AttributeSpec("paperFinish", "Qog'oz turi", AttributeKind.SELECT, optionsUz = listOf("Glyansli", "Matoviy", "Shoyi")),
            ),
            "BANNER" to listOf(
                AttributeSpec("material", "Material", AttributeKind.SELECT, optionsUz = listOf("Banner mato", "Setka", "Oracal plyonka", "Bakalit"), required = true),
                AttributeSpec("sizeNote", "O'lcham", AttributeKind.TEXT, hintUz = "3x1 m", required = true),
                AttributeSpec("hasEyelets", "Lyuversli (halqali)", AttributeKind.BOOLEAN),
                AttributeSpec("hasInstall", "O'rnatib beriladi", AttributeKind.BOOLEAN),
            ),
            "BUSINESS_CARD" to listOf(
                AttributeSpec("cardFinish", "Ustki qatlam", AttributeKind.SELECT, optionsUz = listOf("Oddiy", "Matoviy laminatsiya", "Glyans laminatsiya", "Softtouch")),
                AttributeSpec("cornerRounding", "Burchaklari yumaloq", AttributeKind.BOOLEAN),
            ),
            "TSHIRT" to listOf(
                AttributeSpec("technique", "Bosma texnikasi", AttributeKind.SELECT, optionsUz = listOf("Termotransfer", "DTF", "Sublimatsiya", "Shelkografiya"), required = true),
                AttributeSpec("sizes", "O'lchamlar", AttributeKind.TAGS, hintUz = "S, M, L, XL"),
                AttributeSpec("shirtIncluded", "Futbolka narxga kiradi", AttributeKind.BOOLEAN),
            ),
            "LAMINATION" to listOf(
                AttributeSpec("laminationFormat", "Format", AttributeKind.SELECT, optionsUz = listOf("A6", "A5", "A4", "A3")),
                AttributeSpec("thickness", "Plyonka qalinligi", AttributeKind.SELECT, optionsUz = listOf("75 mkm", "100 mkm", "125 mkm")),
            ),
            "BINDING" to listOf(
                AttributeSpec("bindingType", "Tikish turi", AttributeKind.SELECT, optionsUz = listOf("Prujinali (plastik)", "Prujinali (metall)", "Termo tikish", "Qattiq muqova"), required = true),
                AttributeSpec("pagesMax", "Maksimal sahifa", AttributeKind.NUMBER, hintUz = "200", suffixUz = "bet"),
            ),
            "PLOTTER" to listOf(
                AttributeSpec("plotterWidth", "Maksimal kenglik", AttributeKind.SELECT, optionsUz = listOf("1 m", "1.6 m", "2 m", "3 m")),
                AttributeSpec("resolution", "Aniqlik", AttributeKind.SELECT, optionsUz = listOf("720 dpi", "1440 dpi")),
            ),
        ),
        ServiceType.IT_DEV to mapOf(
            "WEB" to listOf(
                AttributeSpec("siteKind", "Sayt turi", AttributeKind.SELECT, optionsUz = listOf("Landing", "Korporativ sayt", "Internet-do'kon", "Veb-ilova")),
                AttributeSpec("hostingIncluded", "Hosting va domen kiradi", AttributeKind.BOOLEAN),
            ),
            "MOBILE" to listOf(
                AttributeSpec("platforms", "Platformalar", AttributeKind.SELECT, optionsUz = listOf("Android", "iOS", "Ikkalasi (KMP/Flutter)"), required = true),
                AttributeSpec("storePublish", "Do'konga joylash kiradi", AttributeKind.BOOLEAN),
            ),
            "BOT" to listOf(
                AttributeSpec("botFeatures", "Imkoniyatlar", AttributeKind.TAGS, hintUz = "To'lov, admin panel, ko'p tillilik"),
                AttributeSpec("hostingIncluded", "Server narxga kiradi", AttributeKind.BOOLEAN),
            ),
        ),
        ServiceType.PHOTO_VIDEO to mapOf(
            "WEDDING" to listOf(
                AttributeSpec("crewSize", "Nechta operator", AttributeKind.NUMBER, hintUz = "2", suffixUz = "kishi"),
                AttributeSpec("clipIncluded", "Klip montaji kiradi", AttributeKind.BOOLEAN),
            ),
            "REELS" to listOf(
                AttributeSpec("clipCount", "Nechta rolik", AttributeKind.NUMBER, hintUz = "4", suffixUz = "dona"),
                AttributeSpec("scriptIncluded", "Ssenariy yozib beriladi", AttributeKind.BOOLEAN),
            ),
        ),
        ServiceType.TRANSPORT to mapOf(
            "MOVING" to listOf(
                AttributeSpec("floorService", "Qavatga ko'tarish", AttributeKind.BOOLEAN),
                AttributeSpec("packingIncluded", "Qadoqlash kiradi", AttributeKind.BOOLEAN),
            ),
            "DELIVERY" to listOf(
                AttributeSpec("deliveryTime", "Yetkazish vaqti", AttributeKind.SELECT, optionsUz = listOf("1 soat ichida", "Shu kuni", "Ertasi kuni")),
            ),
        ),
    )
}
