package dev.feature.listings.domain.repository

import dev.core.common.Resource
import dev.feature.listings.domain.model.Listing
import dev.feature.listings.domain.model.ListingKind
import dev.feature.listings.domain.model.ListingPage
import dev.feature.listings.domain.model.ListingQuery
import dev.feature.listings.domain.model.ListingStatus
import kotlinx.coroutines.flow.Flow

/**
 * E'lonlarga egalik qiluvchi repository (offline-first).
 *
 * Ikki xil o'qish yo'li bor va ular bir-birini almashtirmaydi:
 *
 * - **[search]** — serverning o'zi (`POST /v1/student-listings/search`). Ro'yxat, filtr,
 *   masofa va sahifalash shu yerda: 100 000 e'lonni telefonga tortib, keyin filtrlash
 *   mumkin emas. Ko'rinish qoidalari (blok, muddat, egasi) ham faqat serverda bajariladi.
 * - **[observeActiveByKind] / [observeMyListings]** — local kesh. Tarmoq yo'q bo'lganda
 *   ekran bo'sh qolmasligi uchun; [search] muvaffaqiyatli bo'lganda uning natijasi shu
 *   keshga yoziladi.
 *
 * Yozish amallari ([save], [submit], [updateStatus], [delete]) **avval serverga** boradi va
 * faqat javob kelgach keshni yangilaydi. Aks holda local va serverdagi e'lon ikkiga
 * bo'linib ketardi: server o'z id'sini beradi, statusni o'zi hal qiladi va anti-spam
 * limitlarini ham o'zi qo'llaydi (§6).
 */
interface ListingRepository {

    /** Joriy foydalanuvchi yuklagan e'lonlar (barcha statuslar va turlar) — keshdan. */
    fun observeMyListings(ownerId: String): Flow<List<Listing>>

    /** Talabaga ko'rinadigan **barcha turdagi** e'lonlar — ACTIVE va muddati o'tmaganlar. */
    fun observeActive(): Flow<List<Listing>>

    /**
     * Bitta bo'limning faol e'lonlari. Chegirmalar, Ijara, Xizmatlar va Ish e'lonlari
     * ilovada alohida ro'yxatlar — ular aralashib ketmasligi kerak.
     */
    fun observeActiveByKind(kind: ListingKind): Flow<List<Listing>>

    /**
     * Serverdan qidiradi va natijani keshga yozadi. Backend o'chirilgan bo'lsa (yoki e'lon
     * turi serverda yo'q — masalan [ListingKind.DISCOUNT]) — local keshdan filtrlaydi.
     */
    suspend fun search(query: ListingQuery): Resource<ListingPage>

    /** `GET /v1/student-listings/mine` — o'z e'lonlarim; kesh serverdagi holat bilan almashadi. */
    suspend fun refreshMine(ownerId: String): Resource<List<Listing>>

    /** Faqat keshdan (tahrirlash formasini ochish uchun — tarmoqni kutmaydi). */
    suspend fun byId(id: String): Listing?

    /**
     * `GET /v1/student-listings/{id}` — to'liq e'lon: `viewsCount` oshadi va `contactPhone`
     * faqat shu javobda keladi. Tarmoq bo'lmasa keshdagi nusxa qaytadi.
     */
    suspend fun fetchById(id: String): Resource<Listing>

    /** Qoralama sifatida saqlaydi — validatsiyasiz (`submit: false`). */
    suspend fun save(listing: Listing): Resource<Listing>

    /**
     * E'lon qiladi. Moderatsiya yo'q: validatsiyadan o'tsa o'sha so'rovning o'zida
     * `ACTIVE` (yoki `validFrom` kelajakda bo'lsa `SCHEDULED`) bo'ladi.
     *
     * Server validatsiyasi buzilganda xato `AppException.Validation.fields` bilan qaytadi —
     * kalitlar aynan `ListingField` nomlari.
     */
    suspend fun submit(listing: Listing): Resource<Listing>

    suspend fun updateStatus(id: String, status: ListingStatus): Resource<Unit>

    suspend fun delete(id: String): Resource<Unit>

    /**
     * Rasmni yuklaydi va uning manzilini qaytaradi.
     * Backend bilan — `POST /v1/media/upload` (`purpose=LISTING`), offline rejimda `data:` URI.
     */
    suspend fun uploadImage(bytes: ByteArray, fileName: String): Resource<String>
}
