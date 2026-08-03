package dev.feature.listings.domain.model

/**
 * Serverdagi qidiruv so'rovi — `POST /v1/student-listings/search` ning domen ko'rinishi
 * (`STUDENT_LISTINGS_BACKEND.md` §7.2.1).
 *
 * Nega [ListingFilters] ning o'zi yetarli emas: filtr — "qanaqa e'lon kerak" degan savol va
 * u offline ro'yxatga ham qo'llanadi. So'rov esa undan tashqari **qayerdan izlanayotganini**
 * (geo), **qanday tartibda** (sort) va **qaysi sahifadan** (cursor) ham o'z ichiga oladi —
 * bularning hech biri e'lonning o'ziga tegishli emas va local filtrlashda ma'nosiz.
 */
data class ListingQuery(
    /** MAJBURIY — server turlarni aralashtirmaydi, berilmasa `422`. */
    val kind: ListingKind,
    /** Matnli qidiruv: sarlavha, tavsif, manzil. */
    val text: String = "",
    val filters: ListingFilters = ListingFilters(),
    val geo: ListingGeoFilter? = null,
    val sort: ListingSort = ListingSort.NEWEST,
    val size: Int = DEFAULT_PAGE_SIZE,
    /**
     * Kursorli sahifalash — cheksiz skroll uchun asosiy rejim. `null` — birinchi sahifa.
     *
     * Filtr yoki [sort] o'zgargan kursor serverda `422 PAGE_CURSOR_INVALID` beradi, shuning
     * uchun so'rovning har qanday o'zgarishi kursorni **tashlab yuborishi** kerak.
     */
    val cursor: String? = null,
    /**
     * Sahifa raqamli rejim — faqat [ListingPage.total] kerak bo'lganda ("Qo'llash · 137").
     * Kursorli rejimda `total` hisoblanmaydi, chunki cheksiz skroll uni ko'rsatmaydi va
     * `COUNT(*)` so'rovning eng qimmat qismi bo'lardi.
     *
     * Ikkalasi birga berilsa serverda **kursor ustun turadi**.
     */
    val page: Int? = null,
) {
    companion object {
        const val DEFAULT_PAGE_SIZE = 20

        /** Server kattaroq `size` ni jimgina qisqartiradi — klientda ham bir xil chegara. */
        const val MAX_PAGE_SIZE = 50
    }
}

/**
 * Saralash tartibi (§5.4). Har biri serverda `id DESC` bilan tugaydi, ya'ni sahifalar
 * orasida e'lon sakrab ketmaydi.
 *
 * [NEAREST] koordinatasiz so'ralsa server xato bermaydi — jimgina [NEWEST] ga tushadi.
 * [RELEVANCE] hozircha [NEWEST] bilan bir xil (universitet reytingi — Faza 2).
 */
enum class ListingSort(val label: String) {
    RELEVANCE("Mos kelishi bo'yicha"),
    NEWEST("Yangi e'lonlar"),
    PRICE_ASC("Arzondan qimmatga"),
    PRICE_DESC("Qimmatdan arzonga"),
    NEAREST("Eng yaqin"),
    DEADLINE("Muddati yaqin"),
    ;

    companion object {
        /**
         * Bo'limda ma'noli saralashlar. Muddat faqat topshiriqda bor, narx esa ishda
         * maosh — uni "arzondan qimmatga" deb ko'rsatish chalg'itadi.
         */
        fun optionsFor(kind: ListingKind): List<ListingSort> = when (kind) {
            ListingKind.TASK -> listOf(NEWEST, DEADLINE, PRICE_DESC, NEAREST)
            ListingKind.JOB -> listOf(NEWEST, PRICE_DESC, NEAREST)
            else -> listOf(NEWEST, PRICE_ASC, PRICE_DESC, NEAREST)
        }
    }
}

/**
 * Joylashuv filtri (§7.2.3). Uchala usul mustaqil va birga berilsa `AND` bilan kesishadi;
 * hech biri berilmasa — butun O'zbekiston.
 *
 * Manzilsiz onlayn topshiriq geo filtr berilganda ham ro'yxatdan tushib qolmaydi — u
 * masofasiz, ro'yxat oxirida chiqadi.
 */
data class ListingGeoFilter(
    val lat: Double? = null,
    val lng: Double? = null,
    /** Server 200 km dan kattasini jimgina qisqartiradi. */
    val radiusMeters: Int? = null,
    val regionIds: List<String> = emptyList(),
    val districtIds: List<String> = emptyList(),
) {
    /** Koordinata to'liq berilganmi — [ListingSort.NEAREST] faqat shunda ma'noli. */
    val hasPoint: Boolean get() = lat != null && lng != null

    val isEmpty: Boolean get() = !hasPoint && regionIds.isEmpty() && districtIds.isEmpty()

    companion object {
        /** Radius berilmasa server shu qiymatni oladi. */
        const val DEFAULT_RADIUS_METERS = 5_000
        const val MAX_RADIUS_METERS = 200_000
    }
}

/**
 * Qidiruv natijasining bir sahifasi.
 *
 * [total] faqat sahifa raqamli rejimda hisoblanadi — kursorli (cheksiz skroll) rejimda
 * `COUNT(*)` so'rovning eng qimmat qismi bo'lardi va uni hech kim ko'rmaydi.
 */
data class ListingPage(
    val items: List<Listing> = emptyList(),
    val hasNext: Boolean = false,
    val nextCursor: String? = null,
    val total: Int? = null,
)
