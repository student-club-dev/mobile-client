package dev.core.data.seed

import dev.core.common.AppDispatchers
import dev.core.data.mapper.joinDb
import dev.core.data.mapper.toDb
import dev.core.database.sql.StudentClubDatabase
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Local bazani dizayndagi namuna ma'lumot bilan to'ldiradi (jadval bo'sh bo'lsagina).
 * Backend ulanganda bu seed o'rniga API'dan sinxronlash keladi — tuzilma bir xil.
 */
class LocalDataSeeder(
    private val db: StudentClubDatabase,
    private val dispatchers: AppDispatchers,
) {
    /**
     * @param discountsJson "Siz uchun" e'lonlari (composeResources/files/listings.json).
     * UI qatlamida (App.kt) o'qib beriladi — data qatlami compose resurslariga bog'liq emas.
     */
    suspend fun seedIfEmpty(discountsJson: String? = null) = withContext(dispatchers.io) {
        seedUniversities()
        seedDiscounts(discountsJson)
        seedJobs()
        seedStudents()
        seedAds()
        seedRentals()
        seedTasks()
        seedNotifications()
        seedClubs()
    }

    private fun seedUniversities() {
        val q = db.universityQueries
        if (q.count().executeAsOne() > 0) return
        val list = listOf(
            Uni("tatu", "TATU — al-Xorazmiy nomidagi", "Toshkent shahri", "TA", "Axborot texnologiyalari", 0xFF6C47FF),
            Uni("nuu", "O‘zbekiston Milliy Universiteti", "Toshkent shahri", "NU", null, 0xFF2563EB),
            Uni("tdiu", "Toshkent Davlat Iqtisodiyot U.", "Toshkent shahri", "TD", null, 0xFF059669),
            Uni("tpi", "Toshkent Politexnika Instituti", "Toshkent shahri", "TP", null, 0xFFD97706),
            Uni("tta", "Toshkent Tibbiyot Akademiyasi", "Toshkent shahri", "TT", null, 0xFFBE185D),
            Uni("samdu", "Samarqand Davlat Universiteti", "Samarqand", "SD", null, 0xFF6C47FF),
            Uni("buxdu", "Buxoro Davlat Universiteti", "Buxoro", "BD", null, 0xFF2563EB),
            Uni("qarshi", "Qarshi Davlat Universiteti", "Qarshi", "QD", null, 0xFF059669),
            Uni("namdu", "Namangan Davlat Universiteti", "Namangan", "ND", null, 0xFFD97706),
            Uni("ferpi", "Farg‘ona Politexnika Instituti", "Farg‘ona", "FP", null, 0xFFBE185D),
            Uni("inha", "Inha University in Tashkent", "Toshkent shahri", "IU", null, 0xFF6C47FF),
            Uni("wiut", "Westminster University", "Toshkent shahri", "WU", null, 0xFF2563EB),
            Uni("turin", "Turin Politexnika Universiteti", "Toshkent shahri", "TU", null, 0xFF059669),
            Uni("adu", "Andijon Davlat Universiteti", "Andijon", "AD", null, 0xFFBE185D),
        )
        q.transaction { list.forEach { q.upsert(it.id, it.name, it.city, it.monogram, it.faculty, it.accent) } }
    }

    /**
     * "Siz uchun" e'lonlari — [listings.json] dan (barcha biznes turlari va sub-kategoriyalar,
     * chegirmali + chegirmasiz aralash). Kategoriya `offerCount` va `finalPrice` shu yerda hisoblanadi.
     * Eski (sub-kategoriyasiz) seed avtomatik yangi ma'lumotga almashadi.
     */
    private fun seedDiscounts(json: String?) {
        val q = db.discountQueries
        val settings = db.appSettingQueries
        // Ma'lumot yo'q bo'lsa seed'ga tegmaymiz.
        val catalog = json?.let { runCatching { seedJson.decodeFromString<SeedCatalog>(it) }.getOrNull() } ?: return
        // Seed-versiya belgisi: JSON o'zgarganda [DISCOUNTS_SEED_VERSION] ni oshiring — eski seed
        // avtomatik yangi ma'lumotga almashadi (saqlangan e'lon id'lari o'zgarmaydi).
        if (settings.selectByKey(DISCOUNTS_SEED_KEY).executeAsOneOrNull() == DISCOUNTS_SEED_VERSION) return

        val counts = catalog.offers.groupingBy { it.categoryId }.eachCount()
        // E'lonning bo'limi turidan olinadi — backend'da ham shunday (`CatalogTypeDto.groupKey`),
        // shuning uchun seed'da har bir e'longa alohida yozib chiqilmaydi.
        val groupOf = catalog.categories.associate { it.id to it.groupKey }
        q.transaction {
            settings.upsert(DISCOUNTS_SEED_KEY, DISCOUNTS_SEED_VERSION)
            q.clearOffers()
            q.clearCategories()
            q.clearGroups()
            catalog.groups.forEach { g ->
                q.upsertGroup(g.key, g.name, g.emoji, g.accent.toLong(16), g.sortOrder.toLong())
            }
            catalog.categories.forEach { c ->
                q.upsertCategory(
                    c.id, c.name, c.emoji, (counts[c.id] ?: 0).toLong(), c.accent.toLong(16), c.groupKey,
                )
            }
            catalog.offers.forEach { o ->
                val finalPrice = when {
                    o.finalPrice > 0 -> o.finalPrice
                    o.isDiscount && o.discountPercent > 0 -> o.originalPrice * (100 - o.discountPercent) / 100
                    else -> o.originalPrice
                }
                val (lat, lng) = if (o.lat != 0.0 && o.lng != 0.0) o.lat to o.lng else coordsFor(o.location, o.id)
                q.upsertOffer(
                    o.id, o.categoryId, groupOf[o.categoryId].orEmpty(),
                    o.subcategory, o.gender, o.merchant, o.title,
                    if (o.isDiscount) 1L else 0L, o.discountPercent.toLong(),
                    o.originalPrice, finalPrice, o.priceUnit,
                    o.tag, o.promoCode, o.location, o.expiry, o.emoji, o.bannerAccent.toLong(16),
                    if (o.featured) 1L else 0L, lat, lng, o.imageUrl,
                )
            }
        }
    }

    private fun seedJobs() {
        val q = db.jobQueries
        if (q.countJobs().executeAsOne() > 0) return
        q.transaction {
            q.upsertJob("j-smm", "SMM menejer (part-time)", "Uzum Market", "U", "masofaviy", "IT",
                listOf("IT", "SMM").joinDb(), "3–5 mln so‘m", true.toDb(), true.toDb(), "2 soat oldin", "IT", false.toDb())
            q.upsertJob("j-frontend", "Frontend intern", "PayNet", "P", "Toshkent", "IT",
                listOf("IT", "Vue", "Ofis").joinDb(), "4 mln so‘m", false.toDb(), false.toDb(), "bugun", "IT", false.toDb())
            q.upsertJob("j-ofitsiant", "Ofitsiant (kechqurun)", "Evos", "E", "Chilonzor", "Xizmat",
                listOf("Xizmat", "Smenali").joinDb(), "1.5 mln so‘m", false.toDb(), true.toDb(), "kecha", "Xizmat", false.toDb())
        }
    }

    private fun seedStudents() {
        val q = db.studentQueries
        if (q.countStudents().executeAsOne() > 0) return
        q.transaction {
            // id, first, last, initial, avatarUrl, uniId, uniMono, course, faculty, friendStatus,
            // interests, friends, ads, rating. Namuna ma'lumotda rasm yo'q — avatarda bosh harf.
            q.upsert("st-dilnoza", "Dilnoza", "Rahimova", "D", null, "tatu", "TATU", 2, "IT", "NONE",
                listOf("🎨 Dizayn", "💻 Frontend", "📷 Foto", "🏀 Sport").joinDb(), 148, 12, 4.9)
            q.upsert("st-sardor", "Sardor", "Aliyev", "S", null, "tatu", "TATU", 3, "Telekom", "NONE", "", 96, 3, 4.7)
            q.upsert("st-malika", "Malika", "Yo‘ldosheva", "M", null, "nuu", "O‘zMU", 2, "Iqtisod", "PENDING", "", 54, 1, 4.5)
            q.upsert("st-kamron", "Kamron", "Yusupov", "K", null, "tatu", "TATU", 2, "Dasturiy inj.", "NONE", "", 71, 5, 4.8)
            q.upsert("st-nigora", "Nigora", "Tosheva", "N", null, "tatu", "TATU", 1, "Kiberxavfsizlik", "NONE", "", 33, 0, 4.6)
        }
    }

    private fun seedAds() {
        val q = db.adQueries
        if (q.selectAll().executeAsList().isNotEmpty()) return
        q.transaction {
            q.upsert("ad-1", "RENTAL", "Chilonzorda room-mate", "Turar joy", "1.2 mln/oy",
                "2 xonali, metroga yaqin, student uchun qulay.", "", "seed-user", "3 soat oldin")
            q.upsert("ad-2", "SALE", "MacBook Air M1 sotiladi", "Texnika", "9.5 mln",
                "Holati a'lo, 100% batareya sikli past.", "", "seed-user", "kecha")
        }
    }

    /**
     * Ijara kvartiralari — bosh ekrandagi uchinchi e'lon bo'limi ("Kvartiralar").
     *
     * `listing` jadvalida hozircha faqat foydalanuvchi o'zi joylagan e'lon bo'ladi
     * (ro'yxatni tortadigan endpoint hali yo'q), shuning uchun bo'lim bo'sh turardi.
     *
     * `detailsJson` — `ListingMappers` dagi `DetailsJson.Rental` shakli, `kind`
     * diskriminatori bilan. Bu yerda qo'lda yoziladi: seed core:data da yashaydi va
     * feature:listings ga bog'lanmaydi.
     */
    private fun seedRentals() {
        val q = db.listingQueries
        if (q.countByKind("RENTAL").executeAsOne() > 0) return
        q.transaction {
            insertRental(
                id = "rent-chilonzor",
                title = "Chilonzorda 2 xonali kvartira",
                description = "Metroga 5 daqiqa, jihozlangan. Bitta o'g'il bola sherik izlanmoqda.",
                price = 1_200_000,
                details = """
                    {"kind":"RENTAL","propertyType":"APARTMENT","roomCount":2,"currentTenants":1,
                    "neededTenants":1,"gender":"MALE","period":"MONTHLY","utilitiesIncluded":false,
                    "depositMonths":1,"floor":4,"totalFloors":9,
                    "amenities":["WIFI","FURNITURE","WASHER","NEAR_METRO"]}
                """.compactJson(),
                branch = branchJson("br-chilonzor", 41.2758, 69.2035, "Chilonzor, 11-kvartal", "Chilonzor"),
                updatedAt = SEED_TIME,
            )
            insertRental(
                id = "rent-yunusobod",
                title = "Yunusobodda qizlar uchun xona",
                description = "Alohida xona, oshxona va kir yuvish mashinasi umumiy.",
                price = 900_000,
                details = """
                    {"kind":"RENTAL","propertyType":"ROOM","roomCount":3,"currentTenants":2,
                    "neededTenants":1,"gender":"FEMALE","period":"MONTHLY","utilitiesIncluded":true,
                    "floor":2,"totalFloors":5,
                    "amenities":["WIFI","FRIDGE","KITCHEN","SEPARATE_ROOM"]}
                """.compactJson(),
                branch = branchJson("br-yunusobod", 41.3647, 69.2896, "Yunusobod, 12-kvartal", "Yunusobod"),
                updatedAt = SEED_TIME - 1,
            )
            insertRental(
                id = "rent-olmazor",
                title = "TATU yonida koyka joy",
                description = "Universitetga piyoda 10 daqiqa. Oylik to'lov, depozitsiz.",
                price = 600_000,
                details = """
                    {"kind":"RENTAL","propertyType":"BED_SPACE","roomCount":4,"currentTenants":3,
                    "neededTenants":1,"gender":"ANY","period":"MONTHLY","utilitiesIncluded":true,
                    "amenities":["WIFI","HOT_WATER","NEAR_UNIVERSITY"]}
                """.compactJson(),
                branch = branchJson("br-olmazor", 41.3500, 69.2050, "Olmazor, Amir Temur ko'chasi", "Olmazor"),
                updatedAt = SEED_TIME - 2,
            )
        }
    }

    private fun insertRental(
        id: String,
        title: String,
        description: String,
        price: Long,
        details: String,
        branch: String,
        updatedAt: Long,
    ) = insertListing(
        id = id,
        kind = "RENTAL",
        details = details,
        title = title,
        description = description,
        priceUnit = "PER_MONTH",
        price = price,
        branchesJson = "[$branch]",
        updatedAt = updatedAt,
    )

    /**
     * "Fanlardan yordam" — talabalar qo'ygan topshiriq e'lonlari (bosh ekranning uchinchi
     * bo'limi va "Universitetim" ekranidagi ro'yxat).
     *
     * Har biri UNIVERSITETGA bog'langan (`universityId`): "Universitetim" ekrani aynan shu
     * maydon bo'yicha filtrlaydi, ya'ni namuna ma'lumotsiz u bo'limni umuman ko'rib bo'lmasdi.
     * Universitet id'lari [seedUniversities] dagi bilan bir xil.
     *
     * Muddat (`deadline`) qat'iy [SEED_DEADLINE] ga bog'langan — sana kelib o'tsa
     * kartadagi yorliq yo'qoladi, e'lon esa qolaveradi (muddati o'tgani ko'rsatilmaydi).
     */
    private fun seedTasks() {
        val q = db.listingQueries
        if (q.countByKind("TASK").executeAsOne() > 0) return
        q.transaction {
            insertTask(
                id = "task-tatu-algoritm",
                universityId = "tatu",
                title = "Algoritmlar fanidan 12 ta masala",
                description = "C++ da yechim va qisqacha izoh kerak. Kod ishlashi shart.",
                price = 150_000,
                details = """
                    {"kind":"TASK","category":"EXACT","typeKey":"MATH",
                    "deadline":$SEED_DEADLINE,"format":"ONLINE","volume":"12 ta masala"}
                """.compactJson(),
                branch = branchJson("br-tatu", 41.3500, 69.2050, "Olmazor, TATU", "TATU"),
                updatedAt = SEED_TIME,
            )
            insertTask(
                id = "task-tatu-referat",
                universityId = "tatu",
                title = "Falsafadan 20 betlik referat",
                description = "Mavzu: «Sharq uyg'onish davri». Plagiat 20% dan oshmasin.",
                price = 90_000,
                details = """
                    {"kind":"TASK","category":"WRITTEN","typeKey":"REFERAT",
                    "deadline":${SEED_DEADLINE + 2 * DAY_MILLIS},"format":"ONLINE","volume":"20 bet"}
                """.compactJson(),
                branch = branchJson("br-tatu-2", 41.3500, 69.2050, "Olmazor, TATU", "TATU"),
                updatedAt = SEED_TIME - 1,
            )
            insertTask(
                id = "task-nuu-chizma",
                universityId = "nuu",
                title = "Chizma geometriyadan qo'lyozma ish",
                description = "3 ta varaq, qo'lda chiziladi. Universitetda topshiriladi.",
                price = 120_000,
                details = """
                    {"kind":"TASK","category":"DRAWING","typeKey":"CAD",
                    "deadline":${SEED_DEADLINE - 2 * DAY_MILLIS},"format":"IN_PERSON","volume":"3 varaq"}
                """.compactJson(),
                branch = branchJson("br-nuu", 41.3400, 69.2050, "Olmazor, O'zMU", "O'zMU"),
                updatedAt = SEED_TIME - 2,
            )
        }
    }

    private fun insertTask(
        id: String,
        universityId: String,
        title: String,
        description: String,
        price: Long,
        details: String,
        branch: String,
        updatedAt: Long,
    ) = insertListing(
        id = id,
        kind = "TASK",
        details = details,
        title = title,
        description = description,
        // Topshiriq — bir martalik ish, narx butun ish uchun (`PostListingViewModel` ham shunday).
        priceUnit = "PER_ITEM",
        price = price,
        branchesJson = "[$branch]",
        updatedAt = updatedAt,
        universityId = universityId,
    )

    /** Namuna e'lonining umumiy ustunlari — turga xos qismi `details` da. */
    private fun insertListing(
        id: String,
        kind: String,
        details: String,
        title: String,
        description: String,
        priceUnit: String,
        price: Long,
        branchesJson: String,
        updatedAt: Long,
        universityId: String? = null,
    ) = db.listingQueries.upsert(
        id = id,
        ownerId = SEED_OWNER,
        businessId = null,
        kind = kind,
        detailsJson = details,
        title = title,
        description = description,
        imagesJson = "[]",
        priceUnit = priceUnit,
        price = price,
        priceMax = null,
        currency = "UZS",
        isNegotiable = 0,
        // Chegirmasiz e'londa talaba to'laydigan narx = narxning o'zi.
        finalPrice = price,
        contactPhone = null,
        universityId = universityId,
        branchesJson = branchesJson,
        validFrom = 0,
        // Namuna e'lonlari hammaga ko'rinadi: seed universitetlari (`tatu`/`nuu`) ular
        // faqat tartiblash uchun, doirani cheklash uchun emas.
        audience = "ALL",
        validTo = FAR_FUTURE,
        attributesJson = "{}",
        optionGroupsJson = "[]",
        status = "ACTIVE",
        rejectionReason = null,
        viewsCount = 0,
        createdAt = updatedAt,
        updatedAt = updatedAt,
    )

    private fun branchJson(id: String, lat: Double, lng: Double, address: String, name: String) =
        """{"id":"$id","lat":$lat,"lng":$lng,"address":"$address","name":"$name"}"""

    /** Ko'p qatorli JSON literalini bir qatorga yig'adi (satr uzilishlari faqat o'qish uchun). */
    private fun String.compactJson(): String = trimIndent().lines().joinToString("") { it.trim() }

    private fun seedNotifications() {
        val q = db.notificationQueries
        if (q.count().executeAsOne() > 0) return
        q.transaction {
            q.insert("nt-1", "Yangi ish taklifi", "Uzum Market — Frontend Intern lavozimiga mos keldingiz.", "JOB", "10 daqiqa oldin", 1, 0)
            q.insert("nt-2", "Chegirma tugayapti", "Chorsu Cafe'dagi 25% chegirma bugun tugaydi.", "DISCOUNT", "2 soat oldin", 2, 0)
            q.insert("nt-3", "Yangi xabar", "Dilnoza Rahimova sizga xabar yozdi.", "CHAT", "3 soat oldin", 3, 0)
            q.insert("nt-4", "E'loningiz ko'rildi", "\"MacBook Air M1\" e'loningizni 12 kishi ko'rdi.", "AD", "kecha", 4, 1)
            q.insert("nt-5", "Xush kelibsiz! 🎉", "Student Club'ga xush kelibsiz. Profilingizni to'ldiring.", "SYSTEM", "2 kun oldin", 5, 1)
        }
    }

    private fun seedClubs() {
        val q = db.clubQueries
        if (q.selectAll().executeAsList().isNotEmpty()) return
        q.transaction {
            q.upsert(1, "IT Klub", "Dasturlash, hackathonlar va IT loyihalar jamoasi.", 342, null)
            q.upsert(2, "Debat Klubi", "Mantiqiy fikrlash va notiqlik san'ati.", 128, null)
            q.upsert(3, "Sport Klubi", "Futbol, basketbol va umumjismoniy mashg'ulotlar.", 256, null)
            q.upsert(4, "Volontyorlar", "Ijtimoiy loyihalar va xayriya tadbirlari.", 189, null)
            q.upsert(5, "Dizayn Studiyasi", "UI/UX, grafika va ijodiy ustaxonalar.", 97, null)
            q.upsert(6, "Til Klubi", "Ingliz, koreys va arab tillari amaliyoti.", 214, null)
        }
    }

    private data class Uni(
        val id: String, val name: String, val city: String,
        val monogram: String, val faculty: String?, val accent: Long,
    )

    // --- listings.json tuzilmasi ("Siz uchun" e'lonlari) ---------------------
    @Serializable
    private data class SeedCatalog(
        val groups: List<SeedGroup> = emptyList(),
        val categories: List<SeedCategory> = emptyList(),
        val offers: List<SeedOffer> = emptyList(),
    )

    /** Bosh ekran bo'limi — backend'dagi `catalog/groups` javobining local ko'rinishi. */
    @Serializable
    private data class SeedGroup(
        val key: String,
        val name: String,
        val emoji: String,
        val accent: String,   // ARGB hex
        val sortOrder: Int = 0,
    )

    @Serializable
    private data class SeedCategory(
        val id: String,
        val name: String,
        val emoji: String,
        val accent: String,   // ARGB hex, masalan "FFF97316"
        /** Tur qaysi bo'limda ([SeedGroup.key]) — e'lonlar ham shu orqali bo'limga tushadi. */
        val groupKey: String = "",
    )

    @Serializable
    private data class SeedOffer(
        val id: String,
        val categoryId: String,
        val subcategory: String = "",
        val gender: String = "",
        val merchant: String,
        val title: String,
        val isDiscount: Boolean = true,
        val discountPercent: Int = 0,
        val originalPrice: Long = 0,
        val finalPrice: Long = 0,   // 0 → foizdan hisoblanadi
        val priceUnit: String = "dona",
        val tag: String = "STUDENT_ID",
        val promoCode: String? = null,
        val location: String? = null,
        val expiry: String? = null,
        val emoji: String = "🎁",
        val bannerAccent: String = "FF6C47FF",
        val featured: Boolean = false,
        val lat: Double = 0.0,   // 0.0 → `location` matnidan tuman koordinatasi aniqlanadi
        val lng: Double = 0.0,
        val imageUrl: String? = null,   // null → kartada emoji ko'rinadi
    )

    /**
     * `location` matnidan taxminiy koordinata (demo uchun). Tuman nomi topilsa — o'sha
     * tuman markazi, aks holda Toshkent markazi. [id] bo'yicha kichik siljish (jitter) —
     * bir tumandagi e'lonlar xaritada ustma-ust tushmasligi uchun.
     */
    private fun coordsFor(location: String?, id: String): Pair<Double, Double> {
        val base = location?.let { loc -> DISTRICTS.entries.firstOrNull { loc.contains(it.key, ignoreCase = true) }?.value } ?: TASHKENT_CENTER
        val h = id.hashCode()
        val jLat = ((h % 200) - 100) / 100_000.0        // ±0.001° ≈ ±110 m
        val jLng = (((h / 200) % 200) - 100) / 100_000.0
        return (base.first + jLat) to (base.second + jLng)
    }

    private companion object {
        val seedJson = Json { ignoreUnknownKeys = true }

        /** Namuna e'lonlarning egasi — haqiqiy foydalanuvchi id'si bilan to'qnashmaydi. */
        const val SEED_OWNER = "seed-user"

        /**
         * Namuna e'lonlarning vaqti — QAT'IY qiymat, `Clock` emas: seed har qurilmada
         * bir xil tartibda tushsin va testlar vaqtga bog'lanib qolmasin.
         * 2026-01-01 (epoch millis).
         */
        const val SEED_TIME = 1_767_225_600_000L

        /** Namuna e'lonlar muddati o'tmasin — 2100-01-01 (epoch millis). */
        const val FAR_FUTURE = 4_102_444_800_000L

        /** Bir kun (millis) — topshiriq muddatlarini [SEED_DEADLINE] dan sanash uchun. */
        const val DAY_MILLIS = 86_400_000L

        /**
         * Namuna topshiriqlarning muddati — 2026-12-20 12:00 (UTC), QAT'IY qiymat.
         * `Clock` ishlatilmaydi ([SEED_TIME] dagi sabab bilan): seed hamma qurilmada
         * bir xil bo'lsin. Sana o'tib ketsa kartadagi muddat yorlig'i chizilmaydi,
         * e'lonning o'zi esa qolaveradi.
         */
        const val SEED_DEADLINE = 1_797_768_000_000L

        // "Siz uchun" seed'i shu versiyada. listings.json o'zgarsa bu qiymatni oshiring.
        const val DISCOUNTS_SEED_KEY = "discounts_seed_version"
        // v6 — "Savdo va xizmat" ikkiga bo'lindi: "Savdo" va "Xizmatlar" (printerxona).
        const val DISCOUNTS_SEED_VERSION = "6"

        val TASHKENT_CENTER = 41.311081 to 69.240562

        /** Toshkent tumanlari/joylari markazi (taxminiy). */
        val DISTRICTS = mapOf(
            "Chilonzor" to (41.2758 to 69.2035),
            "Yunusobod" to (41.3647 to 69.2896),
            "Sergeli" to (41.2270 to 69.2216),
            "Olmazor" to (41.3500 to 69.2050),
            "Mirzo Ulug'bek" to (41.3250 to 69.3350),
            "Mirobod" to (41.2900 to 69.2750),
            "Yakkasaroy" to (41.2870 to 69.2560),
            "Samarqand Darvoza" to (41.3120 to 69.2450),
            "Compass Mall" to (41.3380 to 69.3340),
            "Tashkent City" to (41.3150 to 69.2790),
            "Riverside" to (41.3050 to 69.2280),
            "Next Mall" to (41.3480 to 69.2870),
            "Malika" to (41.2960 to 69.2380),
        )
    }
}
