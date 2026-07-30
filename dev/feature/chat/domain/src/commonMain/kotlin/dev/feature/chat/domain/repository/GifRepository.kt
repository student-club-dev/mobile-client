package dev.feature.chat.domain.repository

import dev.core.common.Resource
import dev.feature.chat.domain.model.GifItem
import dev.feature.chat.domain.model.GifLocale
import dev.feature.chat.domain.model.GifPage

/**
 * GIF qidiruvi (`gif.md`) — `ChatRepository` dan **ataylab alohida**: bu butunlay boshqa
 * manba (tashqi provayder, o'z kvotasi va o'z xatolari) va suhbat holatiga umuman
 * bog'liq emas.
 */
interface GifRepository {

    /**
     * `GET /v1/gifs/search`.
     *
     * [query] bo'sh bo'lsa — **trending** ro'yxati qaytadi (bu provayderning o'z qoidasi,
     * klientda alohida endpoint yo'q).
     *
     * [cursor] — oldingi sahifadagi `next`. **Shaffof**: ichiga qaramaymiz, o'zgartirmaymiz.
     *
     * Natijalar keshlanadi: hozirgi test kaliti **soatiga 100 ta so'rov, GLOBAL** beradi,
     * ya'ni har bir ortiqcha so'rov butun ilovaning kvotasidan yeydi.
     */
    suspend fun search(
        query: String = "",
        cursor: String? = null,
        locale: GifLocale = GifLocale.UZ,
        limit: Int = DEFAULT_LIMIT,
    ): Resource<GifPage>

    /**
     * `POST /v1/gifs/{id}/share` — foydalanuvchi GIF tanlaganda.
     *
     * Hozirgi provayderda **no-op**, lekin kontraktda qoladi: provayder almashsa (bir oyda
     * ikki marta bo'lgan) klient o'zgarmasligi kerak. Shuning uchun natija qaytmaydi ham —
     * bu "eng yaxshi harakat" chaqiruvi va yuborishni **hech qachon** to'sib qo'ymaydi.
     *
     * [query] — natija qaysi qidiruvdan tanlangani (bo'lsa); provayder reytingi uchun.
     */
    suspend fun share(gif: GifItem, query: String? = null)

    companion object {
        /** Backend odatiy qiymati; 1–50 oralig'ida. */
        const val DEFAULT_LIMIT = 30
    }
}
