package dev.feature.chat.domain.model

/**
 * Provayder katalogidan qidirib topilgan stiker (`handoff/06-STICKER-SEARCH.md` §1).
 *
 * [StickerCatalog] dagi [Sticker] dan **ataylab alohida** tur: u — emoji o'rnini bosuvchi
 * belgi (yoki server katalogidagi qator), bu esa begona CDN'dagi tasvirga havola. Ikkalasi
 * bitta turga sig'dirilsa yuborish nuqtasi qaysi biri ekanini `null` maydonlar bo'yicha
 * taxmin qilishga majbur bo'lardi — server esa ularni butunlay boshqacha qabul qiladi
 * (`stickerId` va `sticker` obyekti).
 *
 * ⚠️ [url] — **WebP** (yoki shaffofligi saqlangan GIF), MP4 EMAS: stikerda shaffof fon shart
 * va MP4 alfa kanalni tashlab yuboradi (GIF'dan asosiy farqi shu).
 */
data class StickerSearchItem(
    /** Provayderdagi id — `POST /v1/stickers/{id}/share` shu bilan chaqiriladi. */
    val id: String,
    /** Atribut belgisi shu maydondan tanlanadi — GIF bilan **bir xil** enum (spec: `MediaProviderDto`). */
    val provider: GifProvider,
    val url: String,
    val thumbUrl: String,
    val width: Int,
    val height: Int,
    val isAnimated: Boolean,
) {
    /** To'rda katakni to'g'ri balandlikda chizish uchun (0 ga bo'linishdan himoyalangan). */
    val aspectRatio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 1f

    /** Yuborish uchun shakl — maydonlar **o'zgartirilmaydi**, qarang [StickerRef]. */
    fun toRef(): StickerRef = StickerRef(
        provider = provider,
        externalId = id,
        url = url,
        thumbUrl = thumbUrl,
        width = width,
        height = height,
    )
}

/**
 * `message:send` dagi `sticker` obyekti — `gif` bilan **bir xil qoidalar**
 * (`handoff/06-STICKER-SEARCH.md` §2).
 *
 * ⚠️ [url] ni **o'zgartirmang** (qisqartirish, `http`→`https`, xostni almashtirish ham):
 * server uni domen oq ro'yxatidan o'tkazadi va har qanday o'zgarish
 * `422 STICKER_URL_NOT_ALLOWED` bilan qaytadi.
 *
 * ⚠️ `stickerId` bilan **birga yuborilmaydi** — server buni `422 STICKER_SOURCE_AMBIGUOUS`
 * deb rad etadi. Tekshiruv klientda ham bor ([dev.feature.chat.data] dagi `SendPayload`).
 */
data class StickerRef(
    val provider: GifProvider,
    val externalId: String,
    val url: String,
    val thumbUrl: String,
    val width: Int,
    val height: Int,
)

/**
 * Stiker qidiruvining bitta sahifasi.
 *
 * [next] — **shaffof kursor**: ichiga qaralmaydi, keyingi so'rovda `pos` sifatida qaytariladi.
 * `null` bo'lsa — oxiri.
 */
data class StickerSearchPage(
    val items: List<StickerSearchItem>,
    val next: String?,
    val provider: GifProvider,
) {
    val hasMore: Boolean get() = next != null
}

/**
 * O'sha xato turlari, lekin **stiker** so'zi bilan.
 *
 * Xato turlari GIF bilan bir xil ([GifErrorKind]) — endpoint GIF qidiruvining aynan nusxasi
 * va kodlari ham bir xil naqshda (`STICKER_PROVIDER_RATE_LIMITED` va h.k.). Faqat matn
 * boshqa: panelda "GIF xizmati band" deb yozilsa foydalanuvchi nima band bo'lganini
 * tushunmasdi.
 */
val GifErrorKind.stickerMessage: String
    get() = when (this) {
        GifErrorKind.PROVIDER_RATE_LIMITED ->
            ChatDomainStrings.stickerBusy
        GifErrorKind.RATE_LIMITED -> ChatDomainStrings.searchTooFast
        GifErrorKind.PROVIDER_UNAVAILABLE ->
            ChatDomainStrings.stickerUnavailable
        // Amalda ko'rinmaydi: panel bu holatda qidiruvni umuman yashiradi.
        GifErrorKind.PROVIDER_NOT_CONFIGURED -> ChatDomainStrings.stickerNotConfigured
        GifErrorKind.NETWORK -> ChatDomainStrings.noInternet
        GifErrorKind.UNKNOWN -> ChatDomainStrings.stickerError
    }
