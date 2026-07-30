package dev.feature.chat.domain.repository

import dev.feature.chat.domain.model.StickerCatalogState

/**
 * Stiker katalogi (`GET /v1/stickers/packs`) — butun katalog **bitta javobda**, `ETag` bilan.
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
}
