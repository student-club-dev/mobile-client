package dev.feature.chat.domain.model

/**
 * GIF provayderi — javobdagi `provider` maydoni (`gif.md`).
 *
 * Bu **faqat brend nomi emas**: panelda ko'rsatiladigan atribut belgisi aynan shu maydondan
 * tanlanadi va uni ko'rsatish **shartnomaviy majburiyat**. Provayder almashsa (bir oyda ikki
 * marta bo'lgan) klientda faqat shu enum kengayadi, qolgan kod o'zgarmaydi.
 */
enum class GifProvider(val attribution: String) {
    KLIPY("Powered by KLIPY"),
    TENOR("Powered by Tenor"),
    GIPHY("Powered by GIPHY"),

    /** Noma'lum/kelmagan qiymat — belgini yashirib bo'lmaydi, shuning uchun umumiy matn. */
    UNKNOWN("Powered by GIF provider");

    companion object {
        fun of(raw: String?): GifProvider =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}

/** Qidiruv tili — `uz_UZ` odatiy (`gif.md`). */
enum class GifLocale(val wire: String) {
    UZ("uz_UZ"),
    RU("ru_RU"),
    EN("en_US"),
}

/**
 * Panelda ko'rinadigan bitta GIF.
 *
 * ⚠️ [url] — **MP4**, `.gif` emas: bir xil animatsiya GIF'da ~20 barobar og'ir bo'ladi.
 * U **avtomatik, cheksiz takror va OVOZSIZ** o'ynatilishi kerak. [thumbUrl] — yuklanguncha
 * ko'rsatiladigan statik kadr (u haqiqatan `.gif`).
 */
data class GifItem(
    /** Provayderdagi id — `POST /v1/gifs/{id}/share` shu bilan chaqiriladi. */
    val id: String,
    val provider: GifProvider,
    /** Ovozsiz, takrorlanuvchi MP4. */
    val url: String,
    /** Statik kadr — pleyer tayyor bo'lguncha (va pleyer yo'q joyda) shu ko'rsatiladi. */
    val thumbUrl: String,
    val width: Int,
    val height: Int,
    val durationMs: Int?,
) {
    /** To'rda katakni to'g'ri balandlikda chizish uchun (0 ga bo'linishdan himoyalangan). */
    val aspectRatio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 1f

    /** Yuborish uchun shakl — maydonlar **o'zgartirilmaydi**, qarang [GifRef]. */
    fun toRef(): GifRef = GifRef(
        provider = provider,
        externalId = id,
        url = url,
        thumbUrl = thumbUrl,
        width = width,
        height = height,
        durationMs = durationMs,
    )
}

/**
 * `message:send` dagi `gif` obyekti — qidiruvdan tanlangan GIF **yuklanmaydi**, javobda
 * kelgan maydonlar aynan shu holda serverga qaytariladi (`gif.md`).
 *
 * ⚠️ [url] ni **o'zgartirmang** (qisqartirish, `http`→`https`, xostni almashtirish ham):
 * server uni domen oq ro'yxatidan o'tkazadi va har qanday o'zgarish `422 GIF_URL_NOT_ALLOWED`
 * bilan qaytadi. Shu sababli `MediaUrl.normalize` ham bu havolaga qo'llanmaydi — u begona
 * CDN, bizniki emas.
 */
data class GifRef(
    val provider: GifProvider,
    val externalId: String,
    val url: String,
    val thumbUrl: String,
    val width: Int,
    val height: Int,
    val durationMs: Int?,
)

/**
 * Qidiruvning bitta sahifasi.
 *
 * [next] — **shaffof kursor**: ichiga qaramaymiz, keyingi so'rovda `pos` sifatida qaytariladi.
 * `null` bo'lsa — oxiri.
 */
data class GifPage(
    val items: List<GifItem>,
    val next: String?,
    val provider: GifProvider,
) {
    val hasMore: Boolean get() = next != null
}
