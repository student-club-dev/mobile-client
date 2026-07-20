package dev.feature.listings.domain.repository

import dev.core.common.Resource
import dev.feature.listings.domain.model.Listing
import dev.feature.listings.domain.model.ListingKind
import kotlinx.coroutines.flow.Flow

/**
 * E'lonlarga egalik qiluvchi repository (offline-first).
 *
 * UI **har doim** local DB'ni kuzatadi ([observeMyListings] / [observeActive]) — shu sabab
 * tarmoq bo'lmasa ham e'lonlar ko'rinadi. [save] va [submit] masofaviy manba bilan
 * gaplashib (agar u yoqilgan bo'lsa) local keshni yangilaydi.
 */
interface ListingRepository {

    /** Joriy foydalanuvchi yuklagan e'lonlar (barcha statuslar va turlar). */
    fun observeMyListings(ownerId: String): Flow<List<Listing>>

    /** Talabaga ko'rinadigan **barcha turdagi** e'lonlar — ACTIVE va muddati o'tmaganlar. */
    fun observeActive(): Flow<List<Listing>>

    /**
     * Bitta bo'limning faol e'lonlari. Chegirmalar, Ijara, Xizmatlar va Ish e'lonlari
     * ilovada alohida ro'yxatlar — ular aralashib ketmasligi kerak.
     */
    fun observeActiveByKind(kind: ListingKind): Flow<List<Listing>>

    suspend fun byId(id: String): Listing?

    /** Qoralama sifatida saqlaydi (moderatsiyaga yubormaydi). */
    suspend fun save(listing: Listing): Resource<Listing>

    /**
     * Moderatsiyaga yuboradi. Backend yoqilganda `PENDING_REVIEW`, offline rejimda
     * darrov `ACTIVE` bo'ladi (moderator yo'q).
     */
    suspend fun submit(listing: Listing): Resource<Listing>

    suspend fun updateStatus(id: String, status: dev.feature.listings.domain.model.ListingStatus): Resource<Unit>

    suspend fun delete(id: String): Resource<Unit>

    /**
     * Rasmni yuklaydi va uning manzilini qaytaradi.
     * Backend bilan — CDN havolasi (`POST /media/upload`), offline rejimda — `data:` URI.
     */
    suspend fun uploadImage(bytes: ByteArray, fileName: String): Resource<String>
}
