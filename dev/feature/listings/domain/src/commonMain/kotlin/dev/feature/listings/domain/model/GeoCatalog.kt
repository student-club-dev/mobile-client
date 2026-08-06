package dev.feature.listings.domain.model

/** Viloyat (yoki Toshkent shahri / Qoraqalpog'iston). */
data class Region(val id: String, val name: String, val districts: List<District> = emptyList())

/** Tuman yoki shahar. */
data class District(val id: String, val name: String)

/**
 * Toshkent metro bekati (`GET /v1/geo/metro-stations` — 4 liniya, 50 bekat).
 *
 * Bekat **mo'ljal** sifatida ishlatiladi: teskari geokodlash javobidagi `nearestMetro`
 * shu ro'yxatdan keladi. [line] — liniya kaliti (`CHILONZOR` · `OZBEKISTON` ·
 * `YUNUSOBOD` · `HALQA`); guruhlash nom bo'yicha emas, aynan shu maydon bo'yicha
 * qilinadi (nom takrorlanishi mumkin).
 */
data class MetroStation(
    val id: String,
    val name: String,
    val line: String,
    val lat: Double,
    val lng: Double,
)

/**
 * O'zbekiston viloyat/tuman ma'lumotnomasi — lokatsiya tanlash uchun.
 *
 * Bu backenddagi `GET /geo/regions` va `GET /geo/regions/{id}/districts` ning offline
 * ekvivalenti. Backend yoqilganda ro'yxat serverdan keladi, `id` lar bir xil formatda
 * (`TOSHKENT_SHAHRI`, `CHILONZOR`) bo'lgani uchun saqlangan e'lonlar buzilmaydi.
 */
object GeoCatalog {

    /**
     * Mo'ljal sifatida ko'rsatiladigan eng uzoq masofa.
     *
     * Backend bilan **bir xil** (javob §2.3): undan uzoqdagi bekat mo'ljal emas,
     * adashtiruvchi ma'lumot. Shu sabab zaxira yo'lda ham aynan shu chegara ishlaydi —
     * aks holda ikki yo'l ikki xil javob berardi.
     */
    const val METRO_LANDMARK_RADIUS_METERS = 3_000.0

    fun regions(): List<Region> = all

    fun region(id: String?): Region? = all.firstOrNull { it.id == id }

    fun districts(regionId: String?): List<District> = region(regionId)?.districts.orEmpty()

    fun district(regionId: String?, districtId: String?): District? =
        districts(regionId).firstOrNull { it.id == districtId }

    /**
     * Nuqtaga eng yaqin bekat — [METRO_LANDMARK_RADIUS_METERS] ichida bo'lsa.
     *
     * Ro'yxat backenddan keladi ([stations] bo'sh bo'lsa `null`): Toshkentdan tashqarida
     * metro yo'q, ya'ni bo'sh javob normal holat, xato emas.
     */
    fun nearestStation(
        stations: List<MetroStation>,
        lat: Double,
        lng: Double,
    ): MetroStation? = stations
        .map { it to Geo.distanceMeters(lat, lng, it.lat, it.lng) }
        .filter { (_, meters) -> meters <= METRO_LANDMARK_RADIUS_METERS }
        .minByOrNull { (_, meters) -> meters }
        ?.first

    /** "Mirzo Ulug'bek" → "MIRZO_ULUGBEK" — barqaror ASCII id. */
    private fun slug(name: String): String = buildString {
        for (ch in name) {
            when {
                ch.isLetterOrDigit() && ch.code < 128 -> append(ch.uppercaseChar())
                ch == ' ' || ch == '-' -> append('_')
                // ' va ʻ (o'/g') tashlab yuboriladi: "Qo'qon" → "QOQON"
            }
        }
    }

    private fun region(name: String, vararg districts: String) = Region(
        id = slug(name),
        name = name,
        districts = districts.map { District(slug(it), it) },
    )

    private val all: List<Region> = listOf(
        region(
            "Toshkent shahri",
            "Bektemir", "Chilonzor", "Mirobod", "Mirzo Ulug'bek", "Olmazor", "Sergeli",
            "Shayxontohur", "Uchtepa", "Yakkasaroy", "Yangihayot", "Yashnobod", "Yunusobod",
        ),
        region(
            "Toshkent viloyati",
            "Angren", "Bekobod", "Bo'ka", "Bo'stonliq", "Chinoz", "Chirchiq", "Nurafshon",
            "Ohangaron", "Olmaliq", "Oqqo'rg'on", "Parkent", "Piskent", "Qibray",
            "Quyichirchiq", "O'rtachirchiq", "Yangiyo'l", "Yuqorichirchiq", "Zangiota",
        ),
        region(
            "Andijon viloyati",
            "Andijon shahri", "Andijon tumani", "Asaka", "Baliqchi", "Bo'z", "Buloqboshi",
            "Izboskan", "Jalaquduq", "Marhamat", "Oltinko'l", "Paxtaobod", "Qo'rg'ontepa",
            "Shahrixon", "Ulug'nor", "Xo'jaobod", "Xonobod",
        ),
        region(
            "Buxoro viloyati",
            "Buxoro shahri", "Buxoro tumani", "G'ijduvon", "Jondor", "Kogon", "Olot",
            "Peshku", "Qorako'l", "Qorovulbozor", "Romitan", "Shofirkon", "Vobkent",
        ),
        region(
            "Farg'ona viloyati",
            "Farg'ona shahri", "Farg'ona tumani", "Marg'ilon", "Qo'qon", "Quva", "Quvasoy",
            "Beshariq", "Bog'dod", "Buvayda", "Dang'ara", "Furqat", "Oltiariq", "Rishton",
            "So'x", "Toshloq", "Uchko'prik", "O'zbekiston", "Yozyovon",
        ),
        region(
            "Jizzax viloyati",
            "Jizzax shahri", "Arnasoy", "Baxmal", "Do'stlik", "Forish", "G'allaorol",
            "Mirzacho'l", "Paxtakor", "Sharof Rashidov", "Yangiobod", "Zafarobod", "Zomin",
        ),
        region(
            "Namangan viloyati",
            "Namangan shahri", "Namangan tumani", "Chortoq", "Chust", "Kosonsoy",
            "Mingbuloq", "Norin", "Pop", "To'raqo'rg'on", "Uchqo'rg'on", "Uychi", "Yangiqo'rg'on",
        ),
        region(
            "Navoiy viloyati",
            "Navoiy shahri", "Zarafshon", "Karmana", "Konimex", "Navbahor", "Nurota",
            "Qiziltepa", "Tomdi", "Uchquduq", "Xatirchi",
        ),
        region(
            "Qashqadaryo viloyati",
            "Qarshi shahri", "Qarshi tumani", "Shahrisabz", "Chiroqchi", "Dehqonobod",
            "G'uzor", "Kasbi", "Kitob", "Koson", "Mirishkor", "Muborak", "Nishon", "Yakkabog'",
        ),
        region(
            "Qoraqalpog'iston Respublikasi",
            "Nukus shahri", "Nukus tumani", "Amudaryo", "Beruniy", "Chimboy", "Ellikqal'a",
            "Kegeyli", "Mo'ynoq", "Qanliko'l", "Qo'ng'irot", "Qorao'zak", "Shumanay",
            "Taxiatosh", "Taxtako'pir", "To'rtko'l", "Xo'jayli",
        ),
        region(
            "Samarqand viloyati",
            "Samarqand shahri", "Samarqand tumani", "Kattaqo'rg'on shahri",
            "Kattaqo'rg'on tumani", "Bulung'ur", "Ishtixon", "Jomboy", "Narpay", "Nurobod",
            "Oqdaryo", "Pastdarg'om", "Paxtachi", "Payariq", "Qo'shrabot", "Toyloq", "Urgut",
        ),
        region(
            "Sirdaryo viloyati",
            "Guliston shahri", "Guliston tumani", "Shirin", "Yangiyer", "Boyovut",
            "Mirzaobod", "Oqoltin", "Sardoba", "Sayxunobod", "Sirdaryo tumani", "Xovos",
        ),
        region(
            "Surxondaryo viloyati",
            "Termiz shahri", "Termiz tumani", "Angor", "Bandixon", "Boysun", "Denov",
            "Jarqo'rg'on", "Muzrabot", "Oltinsoy", "Qiziriq", "Qumqo'rg'on", "Sariosiyo",
            "Sherobod", "Sho'rchi", "Uzun",
        ),
        region(
            "Xorazm viloyati",
            "Urganch shahri", "Urganch tumani", "Xiva", "Bog'ot", "Gurlan", "Hazorasp",
            "Qo'shko'pir", "Shovot", "Tuproqqal'a", "Xonqa", "Yangiariq", "Yangibozor",
        ),
    )
}
