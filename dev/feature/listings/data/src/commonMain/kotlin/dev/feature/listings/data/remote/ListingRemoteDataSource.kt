package dev.feature.listings.data.remote

import dev.core.common.Resource
import dev.feature.listings.domain.model.Listing
import dev.feature.listings.domain.model.ListingPage
import dev.feature.listings.domain.model.ListingQuery
import dev.feature.listings.domain.model.ListingStatus

/**
 * Talaba e'lonining masofaviy manbasi — `/v1/student-listings*` (9 endpoint).
 *
 * Ikkita implementatsiya bor:
 *
 * - [ApiListingRemoteDataSource] — real backend.
 * - [LocalListingRemoteDataSource] — backendsiz rejim: rasm `data:` URI'ga aylanadi,
 *   e'lon darrov faol bo'ladi. Ilova internetsiz ham to'liq ishlaydi.
 *
 * Tanlov DI'da (`REMOTE_SYNC_ENABLED`) qilinadi — repository qaysi manba ishlayotganini
 * bilmaydi.
 */
interface ListingRemoteDataSource {

    /** Ro'yxat va qidiruv. Sahifalash kursorli (`query.cursor`). */
    suspend fun search(query: ListingQuery): Resource<ListingPage>

    /** O'z e'lonlarim — barcha status va turlar. */
    suspend fun mine(page: Int, size: Int): Resource<ListingPage>

    /** Bitta e'lon. Begonaga ko'rinmaydigan e'lon `404` beradi (403 emas). */
    suspend fun byId(id: String): Resource<Listing>

    /**
     * E'lonni serverga yozadi.
     *
     * [submit] `true` — to'liq validatsiya va darrov e'lon qilish (moderatsiya yo'q);
     * `false` — validatsiyasiz qoralama. Yangi e'lon `POST`, mavjudi `PATCH` bilan ketadi —
     * buni [Listing.id] ning prefiksi hal qiladi
     * ([dev.feature.listings.domain.model.ListingIds]).
     */
    suspend fun save(listing: Listing, submit: Boolean): Resource<Listing>

    /** `ACTIVE` / `PAUSED` / `ARCHIVED`. Ruxsat etilmagan o'tishda `409`. */
    suspend fun setStatus(id: String, status: ListingStatus): Resource<Listing>

    /** Soft delete — qayta tiklanmaydi. */
    suspend fun delete(id: String): Resource<Unit>

    /** Rasmni yuklaydi va uning manzilini qaytaradi. */
    suspend fun uploadImage(bytes: ByteArray, fileName: String): Resource<String>
}
