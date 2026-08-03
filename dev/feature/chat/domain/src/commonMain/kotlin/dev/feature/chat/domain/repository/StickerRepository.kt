package dev.feature.chat.domain.repository

import dev.core.common.Resource
import dev.feature.chat.domain.model.GifLocale
import dev.feature.chat.domain.model.StickerCatalogState
import dev.feature.chat.domain.model.StickerSearchItem
import dev.feature.chat.domain.model.StickerSearchPage

/**
 * Stiker katalogi (`GET /v1/stickers/packs`) — butun katalog **bitta javobda**, `ETag` bilan —
 * va provayder katalogi bo'yicha qidiruv (`GET /v1/stickers/search`).
 *
 * `ChatRepository` dan alohida: katalog suhbatga bog'liq emas, ilova ishlagan davomida
 * bir marta o'qiladi va keshda yashaydi.
 */
interface StickerRepository {

    /**
     * Katalogni qaytaradi.
     *
     * **Xato holati yo'q**: server bo'sh ro'yxat qaytarsa ham, umuman javob bermasa ham
     * panel ishlashi kerak — shunda ilovaga kiritilgan emoji katalogi ko'rsatiladi
     * (`StickerCatalogState.fromServer = false`). Xatoning o'zi
     * `StickerCatalogState.error` da qoladi.
     *
     * [forceRefresh] — keshni chetlab o'tib qayta so'rash (foydalanuvchi "yangilash"
     * bosganda). Odatiy holda kesh ishlatiladi, mavjud `ETag` esa `If-None-Match` bo'lib
     * ketadi va o'zgarmagan katalog **304** bilan, tanasiz qaytadi.
     */
    suspend fun catalog(forceRefresh: Boolean = false): StickerCatalogState

    /**
     * `GET /v1/stickers/search` — provayder (KLIPY) katalogi bo'yicha qidiruv.
     *
     * [query] bo'sh bo'lsa — trending (alohida endpoint yo'q). [cursor] — oldingi sahifadagi
     * `next`, **shaffof**.
     *
     * Katalogdan farqli o'laroq bu yerda **xato holati bor**: natija panelda ko'rsatiladi va
     * usiz bo'sh to'r qolardi. Xato turi `Resource.Error.cause` da
     * [dev.feature.chat.domain.model.GifException] bo'lib keladi — GIF bilan bir xil turlar,
     * matni esa [dev.feature.chat.domain.model.stickerMessage].
     */
    suspend fun search(
        query: String,
        cursor: String? = null,
        locale: GifLocale = GifLocale.UZ,
        limit: Int = DEFAULT_SEARCH_LIMIT,
    ): Resource<StickerSearchPage>

    /**
     * `POST /v1/stickers/{id}/share` — provayder reytingi uchun signal, **eng yaxshi harakat**.
     * Xatosi yutiladi: stikerni yuborish unga bog'liq bo'lmasligi kerak.
     */
    suspend fun share(item: StickerSearchItem, query: String?)

    companion object {
        /** Bir sahifada nechta stiker — kontraktdagi odatiy qiymat. */
        const val DEFAULT_SEARCH_LIMIT = 30
    }
}
