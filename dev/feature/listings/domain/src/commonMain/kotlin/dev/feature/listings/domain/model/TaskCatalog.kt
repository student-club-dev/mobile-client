package dev.feature.listings.domain.model

/**
 * "Fanlardan yordam" topshirig'ining yo'nalishi.
 *
 * Ikki bosqichli: avval **kategoriya** ([TaskCategory]), keyin uning ichidagi **tur**
 * ([TaskCatalog.types]). Yassi ro'yxat qilib bo'lmaydi — turlar 30 dan oshadi va bitta
 * ro'yxatda ularni topib bo'lmaydi. Kategoriya esa talaba o'ylaydigan tabiiy bo'linish:
 * "menga yozma ish kerak" yoki "menga masala yechib berish kerak".
 */
enum class TaskCategory(private val labelUz: String, val emoji: String) {
    WRITTEN("Yozma ishlar", "📝"),
    PRESENTATION("Prezentatsiya va taqdimot", "🖥️"),
    EXACT("Aniq fanlardan masalalar", "📐"),
    IT("Dasturlash va IT", "💻"),
    DRAWING("Chizmachilik va texnik ishlar", "📏"),
    HANDWRITING("Qo'lyozma ishlar", "✍️"),
    TRANSLATION("Tarjima va til", "🌐"),
    CALC("Hisob-kitob va formatlash", "📊")

    ;

    val label: String get() = trListing(labelUz)
}

/**
 * Kategoriya ichidagi aniq turlar.
 *
 * Ro'yxat backend tayyor bo'lganda `/tasks/categories` dan kelishi mumkin va ilova
 * o'zgarmaydi — shuning uchun bu yerda faqat ma'lumot, mantiq yo'q.
 */
object TaskCatalog {

    /** Har kategoriyada mavjud — tanlansa erkin nom so'raladi. */
    const val OTHER_KEY = "OTHER"

    private val typeMap: Map<TaskCategory, List<ListingCategory>> = mapOf(
        TaskCategory.WRITTEN to listOf(
            ListingCategory("REFERAT", "Referat"),
            ListingCategory("MUSTAQIL", "Mustaqil ish"),
            ListingCategory("KURS", "Kurs ishi (kurs loyihasi)"),
            ListingCategory("DIPLOM", "Diplom / Bitiruv malakaviy ishi"),
            ListingCategory("MAGISTR", "Magistrlik dissertatsiyasi"),
            ListingCategory("TAQRIZ", "Taqriz / Rahbar mulohazasi"),
        ),
        TaskCategory.PRESENTATION to listOf(
            ListingCategory("SLIDES", "PowerPoint slaydlar"),
            ListingCategory("POSTER", "Poster / plakat dizayni"),
        ),
        TaskCategory.EXACT to listOf(
            ListingCategory("MATH", "Matematika (analiz, algebra, ehtimollar)"),
            ListingCategory("PHYSICS", "Fizika"),
            ListingCategory("CHEMISTRY", "Kimyo"),
            ListingCategory("STATS", "Statistika / ma'lumotlar tahlili (Excel, SPSS)"),
        ),
        TaskCategory.IT to listOf(
            ListingCategory("WEB", "Web-sayt yoki ilova yaratish"),
            ListingCategory("CODE", "Dasturlash masalalari (Python, Java, C++)"),
            ListingCategory("SQL", "Baza (SQL) loyihasi"),
            ListingCategory("CODE_REPORT", "Kurs ishi kodi + hisobot"),
        ),
        TaskCategory.DRAWING to listOf(
            ListingCategory("CAD", "Muhandislik chizmasi (AutoCAD, chertyoj)"),
            ListingCategory("MAP", "Xarita chizish (geografiya, topografiya)"),
            ListingCategory("DIAGRAM", "Diagramma / sxema (PlantUML, Visio)"),
        ),
        TaskCategory.HANDWRITING to listOf(
            ListingCategory("HW_TEXT", "Qo'lda referat yoki konspekt"),
            ListingCategory("HW_DIARY", "Amaliyot kundaligi"),
        ),
        TaskCategory.TRANSLATION to listOf(
            ListingCategory("ARTICLE", "Ilmiy maqola tarjimasi (ing-o'z-rus)"),
            ListingCategory("ANNOTATION", "Annotatsiya yozish"),
        ),
        TaskCategory.CALC to listOf(
            ListingCategory("GPA", "GPA hisoblash"),
            ListingCategory("DOCX", "Word/Docx formatlash (GOST talablari)"),
            ListingCategory("BIBLIO", "Bibliografiya / adabiyotlar ro'yxati"),
        ),
    )

    /** Kategoriya turlari + har birida "Boshqa" (ro'yxat hech qachon yopiq bo'lmasin). */
    fun types(category: TaskCategory): List<ListingCategory> =
        typeMap[category].orEmpty() + ListingCategory(OTHER_KEY, "Boshqa")

    fun type(category: TaskCategory, key: String): ListingCategory? =
        types(category).firstOrNull { it.key == key }

    /**
     * Hajm maydonining namunasi — turga qarab o'zgaradi, chunki "12 ta masala" referat
     * uchun, "20 bet" esa masala yechish uchun ma'nosiz.
     */
    fun volumeHint(category: TaskCategory, typeKey: String): String = when {
        typeKey == "SLIDES" -> "12 ta slayd"
        typeKey == "GPA" -> "2 semestr"
        category == TaskCategory.EXACT -> "12 ta masala"
        category == TaskCategory.WRITTEN -> "20 bet"
        category == TaskCategory.HANDWRITING -> "15 bet"
        category == TaskCategory.TRANSLATION -> "5 bet matn"
        category == TaskCategory.DRAWING -> "3 ta chizma"
        category == TaskCategory.IT -> "1 ta laboratoriya ishi"
        else -> "Ish hajmi"
    }
}
