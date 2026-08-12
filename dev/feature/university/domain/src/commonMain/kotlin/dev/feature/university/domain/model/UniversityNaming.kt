package dev.feature.university.domain.model

import dev.core.common.locale.AppLocale

/**
 * Universitetning UI'da ko'rsatiladigan ko'rinishi — [UniversityNaming] hisoblab beradi.
 *
 * Manbadagi rasmiy nom (`prof-emis.edu.uz`) o'rtacha 45, eng uzuni 189 belgidan iborat:
 * «"Rossiya Federatsiyasi Tashqi ishlar vazirligining Moskva davlat xalqaro munosabatlar
 * instituti (universiteti)" Federal davlat avtonom oliy ta'lim muassasasining Toshkent
 * shahridagi filiali». Bunday satr har qanday ro'yxat qatorini yorib yuboradi, shuning
 * uchun UI **hech qachon** xom nomni ko'rsatmaydi.
 */
data class UniversityDisplay(
    /** Asosiy qator: huquqiy shtamplar, taxallus va filial olib tashlangan nom. */
    val shortName: String,
    /** Tile uchun qisqartma: `TATU`, `SamDU`, `O'zMU`, `PDP`. */
    val abbr: String,
    /** «… nomidagi» qismi — `Muhammad al-Xorazmiy`. Ko'p nomlarda yo'q. */
    val eponym: String?,
    /** Filial — `Nukus filiali`. Bir xil nomli qatorlarni FAQAT shu ajratadi. */
    val branch: String?,
    /** Manzildan ajratilgan shahar/viloyat — `Toshkent shahri`. */
    val city: String,
) {
    /**
     * Ikkilamchi qator. Filial oldinda: ro'yxatda ketma-ket turgan «Samarqand davlat
     * veterinariya … universiteti» qatorlari faqat shu bilan farq qiladi.
     */
    val subtitle: String
        get() = listOfNotNull(branch, city.ifBlank { null }).joinToString(" · ")
}

/**
 * Rasmiy nomni qisqa nom + qisqartmaga ajratadi.
 *
 * Butun mantiq **sof funksiya**: manba (prof-emis) hech qanday logotip, qisqartma yoki
 * ajratilgan shahar maydonini bermaydi — faqat `name_uz` va erkin yozilgan `address`.
 * Shuning uchun ko'rinadigan hamma narsa shu yerda nomdan hosil qilinadi va
 * `UniversityNamingTest` da 205 ta haqiqiy nom ustida tekshiriladi.
 *
 * Natija [University] ichida bir marta hisoblanadi (`display`), ya'ni ro'yxat chizilganda
 * har kadrda qayta ishlamaydi.
 */
object UniversityNaming {

    fun display(rawName: String, rawAddress: String = ""): UniversityDisplay {
        val parsed = parse(rawName)
        return UniversityDisplay(
            shortName = parsed.shortName.ifBlank { normalize(rawName) },
            abbr = abbreviation(parsed.shortName, parsed.shouting),
            eponym = parsed.eponym,
            branch = parsed.branch,
            city = shortCity(rawAddress),
        )
    }

    // ----------------------------------------------------------------------------------
    // Nomni bo'laklarga ajratish
    // ----------------------------------------------------------------------------------

    private data class Parsed(
        val shortName: String,
        val eponym: String?,
        val branch: String?,
        val shouting: Boolean,
    )

    private fun parse(raw: String): Parsed {
        var s = normalize(raw)
        // Nom BUTUNLAY bosh harflarda bo'lsa, qisqartma qidirish mantiqsiz bo'ladi:
        // har bir so'z «akronim»ga o'xshaydi. Buni boshida eslab qolamiz.
        val shouting = s.length > 3 && s == s.uppercase() && s.contains(' ')

        // 1. Huquqiy shtamp — «Federal davlat byudjeti oliy ta'lim muassasasining».
        //    Ma'nosi: muassasa turi. Foydalanuvchi uchun qiymati nol, uzunligi 40+ belgi.
        for (pattern in BOILERPLATE) s = pattern.replace(s, " ")

        // 2. Qaratqich kelishigi: «universitetining» → «universiteti». Busiz filialni
        //    kesib olganda nomning turi ham («… universiteti») birga ketardi.
        for ((from, to) in GENITIVE) s = from.replace(s) { to }

        // 3. Qavs ichidagi taxallus — «(Green University)», «(universiteti)».
        s = PARENS.replace(s, " ").replace("(", " ").replace(")", " ")

        // 4. Filial. Ikki shaklda yoziladi: «Toshkent shahridagi … filiali» va
        //    «… universiteti Nukus filiali».
        var branch: String? = null
        var cityPrefix: String? = null
        LOCATIVE.find(s)?.let { m ->
            cityPrefix = m.groupValues[1]
            s = s.substring(0, m.range.first) + " " + s.substring(m.range.last + 1)
        }
        BRANCH.find(s)?.let { m ->
            val prev = m.groups[1]
            val prevText = prev?.value.orEmpty()
            // «universiteti filiali» — oldingi so'z shahar emas, nomning o'z bo'lagi va
            // kesib tashlansa nomdan turi yo'qolardi.
            val prevIsCity = prevText.length >= 3 &&
                prevText.lowercase() !in TYPE_WORDS && prevText.lowercase() !in STOP_WORDS
            val city = if (prevIsCity) prevText else cityPrefix
            branch = if (!city.isNullOrBlank()) {
                AppLocale.pick(en = "$city branch", ru = "$city филиал", uz = "$city filiali")
            } else {
                AppLocale.pick(en = "Branch", ru = "Филиал", uz = "Filial")
            }
            val cutFrom = when {
                prev == null -> m.range.first
                prevIsCity -> prev.range.first
                else -> prev.range.last + 1
            }
            s = s.substring(0, cutFrom) + " " + s.substring(m.range.last + 1)
        }
        if (branch == null && cityPrefix != null) branch = cityPrefix

        // 5. Taxallus: «Muhammad al-Xorazmiy nomidagi». Ikkilamchi ma'lumot — asosiy
        //    qatorni ikki barobar uzaytiradi, lekin universitetni ajratmaydi.
        var eponym: String? = null
        EPONYM.find(s)?.let { m ->
            eponym = m.groupValues[1].trim(' ', ',', '.')
            s = s.substring(0, m.range.first) + " " + s.substring(m.range.last + 1)
        }

        s = DANGLING_NING.replace(s, " ")
        s = collapse(s)
        return Parsed(shortName = fixShouting(s), eponym = eponym?.ifBlank { null }, branch = branch, shouting = shouting)
    }

    // ----------------------------------------------------------------------------------
    // Qisqartma (tile matni)
    // ----------------------------------------------------------------------------------

    private fun abbreviation(core: String, shouting: Boolean): String {
        val words = core.split(' ', ',', '.').filter { it.isNotBlank() }
        if (words.isEmpty()) return FALLBACK

        // Nomning o'zida tayyor qisqartma bo'lsa (MEI, MMFI, MISiS, PDP) — o'shani olamiz.
        // BUTUN nom bosh harflarda bo'lganda bu qoida ishlamaydi: u yerda har so'z
        // «akronim»ga o'xshaydi va «CENTRAL ASIAN UNIVERSITY» dan «ASIAN» chiqib qolardi.
        if (!shouting) {
            words.firstOrNull { w ->
                val letters = w.filter { it.isLetter() }
                w.lowercase() !in STOP_WORDS && letters.length in 2..6 &&
                    letters.first().isUpperCase() && letters.drop(1).any { it.isUpperCase() }
            }?.let { return it }
        }

        val significant = words.filter { it.length > 1 && it.lowercase() !in STOP_WORDS }
        if (significant.isEmpty()) return FALLBACK

        // Muassasa turi qaysi so'zda — u qisqartmaning OXIRGI harfi bo'ladi (…U, …I, …A).
        val typeIndex = significant.indexOfLast { it.lowercase() in TYPE_WORDS }
        val tail = significant.getOrNull(typeIndex)?.let { TYPE_WORDS[it.lowercase()] }
        val heads = when {
            typeIndex < 0 -> significant
            // «University of economics and pedagogy» — tur so'zi BOSHIDA turadi.
            typeIndex == 0 -> significant.drop(1)
            else -> significant.take(typeIndex)
        }

        if (heads.isEmpty()) return alphaRun(significant.first()).take(6).ifBlank { FALLBACK }

        // Brend nomlar («Turon universiteti», «PDP University», «IMPULS BSR») — qisqartma
        // emas, nomning o'zi tanitadi. Sig'sa, o'zini ko'rsatamiz.
        if (heads.size == 1 || (tail == null && heads.size <= 2)) {
            val head = alphaRun(heads.first())
            if (head.length <= 6) return head
            if (head.contains('-')) {
                // Chiziqcha ikki xil ishlatiladi: `EMU-UNIVERSITY` (brend + tur) va
                // `Al-Xorazmiy` (yaxlit ism). Birinchisida tur bo'lagi tashlanadi,
                // ikkinchisida har bo'lakdan bosh harf olinadi: `AXU`.
                val parts = head.split('-').filter { it.isNotBlank() }
                val brand = parts.filter { it.lowercase() !in TYPE_WORDS }
                val typePart = parts.firstOrNull { it.lowercase() in TYPE_WORDS }
                    ?.let { TYPE_WORDS[it.lowercase()] }
                if (brand.size == 1 && brand.first().length <= 6) return brand.first()
                return brand.joinToString("") { it.first().uppercase() } + (tail ?: typePart ?: "")
            }
            return (head.first().uppercase() + (tail ?: "")).ifBlank { FALLBACK }
        }

        val plain = heads.take(3).joinToString("") { it.first().uppercase() } + (tail ?: "")
        // Shahar nomi qisqartmada an'anaviy tarzda uch harf bilan turadi: SamDU, BuxDU.
        // Sig'masa (6 belgidan uzun) — oddiy bosh harflarga qaytamiz.
        val cityPrefix = CITY_PREFIX[heads.first().lowercase().trim('-', '\'')]
        val pretty = cityPrefix?.let {
            it + heads.drop(1).take(2).joinToString("") { w -> w.first().uppercase() } + (tail ?: "")
        }
        val result = if (pretty != null && pretty.length <= 6) pretty else plain
        return result.take(6).ifBlank { FALLBACK }
    }

    // ----------------------------------------------------------------------------------
    // Manzildan shahar
    // ----------------------------------------------------------------------------------

    /**
     * Manba `address` ni erkin matn sifatida beradi: «Toshkent viloyati, Toshkent tumani,
     * Kensoy MFY, Kensoy ko'chasi 15 - uy». Ro'yxat qatoriga ko'chaning ismi kerak emas —
     * faqat shahar/viloyat.
     */
    fun shortCity(rawAddress: String): String {
        val address = normalize(rawAddress)
        if (address.isBlank()) return ""
        val parts = address.split(',', ';').map { it.trim(' ', '.', ',') }.filter { it.isNotBlank() }

        var region: String? = null
        var best: String? = null
        for (part in parts) {
            if (NOISE.containsMatchIn(part)) continue
            if (!HAS_WORD.containsMatchIn(part)) continue
            if (!CITY_MARK.containsMatchIn(part)) continue
            // «O'zbekiston Respublikasi» — aniqroq bo'lagi topilmasa ishlatiladi.
            if (REPUBLIC.containsMatchIn(part)) { if (region == null) region = part; continue }
            best = part
            break
        }
        // Bo'laklarga bo'linmagan manzil («Andijon shahri Boburshoh ko'chasi 5») — matn
        // ichidan tanish viloyat nomini qidiramiz. Topilmasa qator umuman ko'rsatilmaydi:
        // «Dom» yoki telefon raqamini shahar sifatida chiqarish — xato ma'lumot.
        val chosen = best ?: region ?: REGIONS
            .mapNotNull { name -> address.indexOf(name, ignoreCase = true).takeIf { it >= 0 }?.let { it to name } }
            .minByOrNull { it.first }?.second
            ?: return ""

        var out = DIGITS.replace(chosen, " ")
        out = SH_SHORT.replace(out) { "shahri" }
        out = SHAHAR.replace(out) { "shahri" }
        out = VILOYAT.replace(out) { "viloyati" }
        out = collapse(out)
        val words = out.split(' ')
        if (words.size > 2) out = words.take(2).joinToString(" ")
        return out.trim(' ', ',', '.', '-').take(28)
    }

    // ----------------------------------------------------------------------------------
    // Matn yordamchilari
    // ----------------------------------------------------------------------------------

    /** Apostrof va qo'shtirnoqlarning barcha ko'rinishlarini bittaga keltiradi. */
    private fun normalize(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw) when (ch) {
            in APOSTROPHES -> sb.append('\'')
            in QUOTES -> sb.append(' ')
            else -> sb.append(ch)
        }
        return collapse(sb.toString()).trim(' ', ',', '.', ';', '-')
    }

    private fun collapse(s: String): String = SPACES.replace(s, " ").trim()

    /** So'zning birinchi harflar ketma-ketligi: `EMU-UNIVERSITY` → `EMU`. */
    private fun alphaRun(word: String): String {
        val cleaned = word.trim('-', '\'', '"')
        val end = cleaned.indexOfFirst { !it.isLetter() && it != '\'' && it != '-' }
        val run = if (end <= 0) cleaned else cleaned.substring(0, end)
        return run.trim('-', '\'')
    }

    /**
     * BUTUN nom bosh harflarda yozilgan bo'lsa uni o'qiladigan holga keltiradi
     * («AXBOROT TEXNOLOGIYALARI VA MENEJMENT UNIVERSITETI»). Qisqa so'zlar (`TMC`, `BSR`,
     * `IT`) qisqartma bo'lishi mumkin — ular tegilmaydi.
     */
    private fun fixShouting(s: String): String {
        val words = s.split(' ').filter { it.isNotBlank() }
        if (words.size < 2 || s != s.uppercase()) return s
        // Ingliz nomlari Title Case bilan yoziladi («Central Asian University»), o'zbeklari —
        // faqat birinchi so'z bosh harf bilan («Axborot texnologiyalari va menejment
        // universiteti»). Tur so'zi qaysi tilda ekani shuni aytib beradi.
        val english = words.any { it.lowercase() in ENGLISH_TYPES }
        return words.mapIndexed { i, w ->
            when {
                i > 0 && w.lowercase() in STOP_WORDS -> w.lowercase()
                w.length <= 4 -> w // TMC, BSR, IT — qisqartma bo'lishi mumkin
                english || i == 0 -> w.first() + w.drop(1).lowercase()
                else -> w.lowercase()
            }
        }.joinToString(" ")
    }

    private const val FALLBACK = "OTM"
    private const val APOSTROPHES = "ʼʻ‘’`´"
    private const val QUOTES = "«»\"“”„"

    private val SPACES = Regex("\\s+")
    private val PARENS = Regex("\\([^)]*\\)")
    private val DANGLING_NING = Regex("\\bning\\b", RegexOption.IGNORE_CASE)
    private val LOCATIVE = Regex("\\b(\\S+)\\s+(?:shahridagi|shahrida|viloyatidagi|tumanidagi)\\b", RegexOption.IGNORE_CASE)
    private val BRANCH = Regex("(?:\\b(\\S+)\\s+)?\\bfiliali\\b", RegexOption.IGNORE_CASE)
    private val EPONYM = Regex("\\b(.{2,45}?)\\s+nom(?:idagi|li)\\b", RegexOption.IGNORE_CASE)

    private val BOILERPLATE = listOf(
        Regex("\\bfederal davlat\\s+(?:byudjeti|budjeti|avtonom)?\\s*oliy\\s+ta'lim\\s+muassasasi(?:ning|si)?\\b", RegexOption.IGNORE_CASE),
        Regex("\\boliy\\s+ta'lim\\s+muassasasi(?:ning|si)?\\b", RegexOption.IGNORE_CASE),
    )

    private val GENITIVE = listOf(
        Regex("\\buniversitetining\\b", RegexOption.IGNORE_CASE) to "universiteti",
        Regex("\\binstitutining\\b", RegexOption.IGNORE_CASE) to "instituti",
        Regex("\\bakademiyasining\\b", RegexOption.IGNORE_CASE) to "akademiyasi",
        Regex("\\bmaktabining\\b", RegexOption.IGNORE_CASE) to "maktabi",
        Regex("\\bmarkazining\\b", RegexOption.IGNORE_CASE) to "markazi",
    )

    private val STOP_WORDS = setOf("va", "of", "the", "and", "in", "nomidagi", "nomli", "hamda", "bilan", "ning")

    private val TYPE_WORDS = mapOf(
        "universiteti" to "U", "universitet" to "U", "university" to "U",
        "instituti" to "I", "institut" to "I", "institute" to "I",
        "akademiyasi" to "A", "academy" to "A",
        "markazi" to "M", "maktabi" to "M", "school" to "S",
    )

    private val ENGLISH_TYPES = setOf("university", "institute", "academy", "school", "college")

    /** An'anaviy shahar prefikslari — `SamDU`, `BuxDPI`, `O'zMU`. */
    private val CITY_PREFIX = mapOf(
        "toshkent" to "T", "o'zbekiston" to "O'z", "o'zbek" to "O'z", "samarqand" to "Sam",
        "buxoro" to "Bux", "andijon" to "And", "namangan" to "Nam", "farg'ona" to "Far",
        "qarshi" to "Qar", "termiz" to "Ter", "urganch" to "Ur", "jizzax" to "Jiz",
        "navoiy" to "Nav", "guliston" to "Gul", "chirchiq" to "Chir", "olmaliq" to "Olm",
        "qo'qon" to "Qo'", "nukus" to "Nuk",
    )

    private val REGIONS = listOf(
        "Toshkent", "Tashkent", "Samarqand", "Samarkand", "Buxoro", "Bukhara", "Andijon",
        "Namangan", "Farg'ona", "Fergana", "Qashqadaryo", "Surxondaryo", "Xorazm", "Navoiy",
        "Jizzax", "Sirdaryo", "Qoraqalpog'iston", "Nukus", "Qarshi", "Termiz", "Urganch",
        "Guliston", "Chirchiq",
    )

    private val CITY_MARK = Regex("viloyati|shahri|shahar|shaxar|\\bsh\\b|tumani|respublikasi|respuplikasi", RegexOption.IGNORE_CASE)
    private val REPUBLIC = Regex("respublikasi|respuplikasi", RegexOption.IGNORE_CASE)
    private val NOISE = Regex("ko'chasi|kochasi|street|avenue|MFY|mahalla|-uy|road|district|\\bdom\\b|qishlog", RegexOption.IGNORE_CASE)
    private val HAS_WORD = Regex("[A-Za-z']{3}")
    private val DIGITS = Regex("\\d+")
    private val SH_SHORT = Regex("\\bsh\\.?\\b", RegexOption.IGNORE_CASE)
    private val SHAHAR = Regex("\\bshaxar\\b|\\bshahar\\b", RegexOption.IGNORE_CASE)
    private val VILOYAT = Regex("\\bviloyat\\b", RegexOption.IGNORE_CASE)
}
