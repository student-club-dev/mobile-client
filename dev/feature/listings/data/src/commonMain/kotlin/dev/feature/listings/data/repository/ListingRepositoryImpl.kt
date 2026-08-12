package dev.feature.listings.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.core.common.AppDispatchers
import dev.core.common.Resource
import dev.core.common.error.AppException
import dev.core.common.errorOf
import dev.core.database.sql.StudentClubDatabase
import dev.feature.listings.data.mapper.toDomain
import dev.feature.listings.data.mapper.toEntity
import dev.feature.listings.data.remote.ListingRemoteDataSource
import dev.feature.listings.domain.model.Listing
import dev.feature.listings.domain.model.ListingIds
import dev.feature.listings.domain.model.ListingKind
import dev.feature.listings.domain.model.ListingPage
import dev.feature.listings.domain.model.ListingQuery
import dev.feature.listings.domain.model.ListingStatus
import dev.feature.listings.domain.model.filterBy
import dev.feature.listings.domain.repository.ListingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import dev.core.common.locale.AppLocale

/**
 * E'lon repository'si — server yagona haqiqat manbasi, local baza esa kesh.
 *
 * Nega aynan shunday taqsimlangan:
 *
 * - **Ro'yxat va qidiruv** serverda. Filtr, masofa, saralash va ko'rinish qoidalari
 *   (bloklangan foydalanuvchi, muddati o'tgan e'lon, o'zganing qoralamasi) barchasi
 *   `/v1/student-listings` da hal qilinadi. Klientda takrorlash — natijani noto'g'ri
 *   qilish demak: telefonda hamma e'lon yo'q, blok ro'yxati esa umuman yo'q.
 * - **Yozish** ham serverda: id, status va anti-spam limitlari (§6) uning qaroriga
 *   tegishli. Javob kelgach kesh yangilanadi.
 * - **Kesh** faqat ko'rsatish uchun: tarmoq yo'qolganda ekran bo'sh qolmaydi va
 *   "Mening e'lonlarim" darrov ochiladi.
 *
 * [syncEnabled] `false` bo'lganda (backendsiz rejim) hamma narsa local bazada ishlaydi —
 * qidiruv ham keshdan filtrlanadi.
 */
class ListingRepositoryImpl(
    private val db: StudentClubDatabase,
    private val dispatchers: AppDispatchers,
    private val remote: ListingRemoteDataSource,
    /** `true` — server bilan ishlaydi; `false` — faqat local baza (backend hali yo'q). */
    private val syncEnabled: Boolean,
) : ListingRepository {

    private val q get() = db.listingQueries

    override fun observeMyListings(ownerId: String): Flow<List<Listing>> =
        q.selectByOwner(ownerId)
            .asFlow()
            .mapToList(dispatchers.io)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeActive(): Flow<List<Listing>> =
        q.selectActive(now = now())
            .asFlow()
            .mapToList(dispatchers.io)
            .map { rows -> rows.map { it.toDomain() } }

    // Filtrlash SQL'da — `listing_kind_idx` indeksidan foydalanadi. Domenda filtrlash
    // butun jadvalni o'qib, keyin tashlab yuborish bo'lar edi.
    override fun observeActiveByKind(kind: ListingKind): Flow<List<Listing>> =
        q.selectActiveByKind(kind = kind.name, now = now())
            .asFlow()
            .mapToList(dispatchers.io)
            .map { rows -> rows.map { it.toDomain() } }

    /**
     * Serverdan qidiradi va natijani keshga yozadi.
     *
     * Chegirma ([ListingKind.DISCOUNT]) bu API'da yo'q — u biznes tomonining shartnomasi,
     * shuning uchun har doim keshdan filtrlanadi.
     */
    override suspend fun search(query: ListingQuery): Resource<ListingPage> {
        if (!syncEnabled || query.kind == ListingKind.DISCOUNT) return searchCache(query)

        return when (val res = remote.search(query)) {
            is Resource.Success -> {
                cacheAll(res.data.items)
                res
            }
            // Kursor eskirgan bo'lsa (filtr o'zgargan) — chaqiruvchi birinchi sahifadan
            // boshlaydi; keshga tushish bu yerda noto'g'ri bo'lardi, chunki so'rovning
            // o'zi haqiqiy va qayta urinish kerak.
            is Resource.Error -> if (query.cursor == null) searchCache(query, res) else res
            Resource.Loading -> Resource.Loading
        }
    }

    /**
     * Tarmoqsiz zaxira: keshdagi faol e'lonlar domen filtri bilan saralanadi.
     *
     * Sahifalash yo'q ([ListingPage.hasNext] doim `false`) — keshda baribir faqat
     * ko'rilgan sahifalar bor va ularni "yana yuklash" mumkin emas. Kesh ham bo'sh bo'lsa
     * serverning xatosi ([failure]) qaytadi: shunda ekran "internet yo'q" deb aytadi,
     * "e'lon yo'q" deb emas.
     */
    private suspend fun searchCache(
        query: ListingQuery,
        failure: Resource.Error? = null,
    ): Resource<ListingPage> = withContext(dispatchers.io) {
        val cached = q.selectActiveByKind(kind = query.kind.name, now = now())
            .executeAsList()
            .map { it.toDomain() }
            .filterBy(query.filters, query.text)

        if (cached.isEmpty() && failure != null) failure else Resource.Success(ListingPage(items = cached))
    }

    /**
     * `GET /mine` — barcha sahifalar. Qoralama va arxivlanganlar bilan birga ro'yxat
     * uzun bo'lishi mumkin, lekin u faqat bitta ekranda va bir marta o'qiladi.
     *
     * Kesh **almashtiriladi**, qo'shilmaydi: boshqa qurilmada o'chirilgan e'lon shu
     * telefonda qolib ketmasligi kerak. Xato bo'lsa keshga tegilmaydi.
     */
    override suspend fun refreshMine(ownerId: String): Resource<List<Listing>> {
        if (!syncEnabled) {
            return Resource.Success(withContext(dispatchers.io) {
                q.selectByOwner(ownerId).executeAsList().map { it.toDomain() }
            })
        }

        val all = mutableListOf<Listing>()
        var page = 1
        while (page <= MAX_MINE_PAGES) {
            when (val res = remote.mine(page = page, size = ListingQuery.MAX_PAGE_SIZE)) {
                is Resource.Success -> {
                    all += res.data.items
                    if (!res.data.hasNext) break
                    page++
                }
                is Resource.Error -> return res
                Resource.Loading -> return Resource.Error(AppLocale.pick(en = "Couldn't load listings", ru = "Не удалось загрузить объявления", uz = "E'lonlarni yuklab bo'lmadi"))
            }
        }

        withContext(dispatchers.io) {
            q.transaction {
                // Serverda qolmagan qatorlar tushadi (boshqa qurilmada o'chirilgan e'lon shu
                // telefonda abadiy qolib ketmasin), LEKIN serverga hali bormagan local
                // qoralamalar saqlanadi — ularni o'chirish foydalanuvchining ishini yo'qotish.
                q.selectByOwner(ownerId).executeAsList()
                    .filterNot { ListingIds.isLocal(it.id) }
                    .forEach { q.deleteById(it.id) }
                all.forEach { upsert(it) }
            }
        }
        return Resource.Success(all)
    }

    override suspend fun byId(id: String): Listing? = withContext(dispatchers.io) {
        q.selectById(id).executeAsOneOrNull()?.toDomain()
    }

    /**
     * To'liq e'lon: `viewsCount` shu so'rovda oshadi va `contactPhone` faqat shu javobda
     * keladi (ro'yxatda u `null`). Tarmoq bo'lmasa keshdagi nusxa qaytadi — ekran
     * baribir ochiladi, faqat telefon raqami bo'lmaydi.
     */
    override suspend fun fetchById(id: String): Resource<Listing> {
        if (syncEnabled) {
            when (val res = remote.byId(id)) {
                is Resource.Success -> {
                    cache(res.data)
                    return res
                }
                // E'lon serverda yo'q (o'chirilgan yoki sizga ko'rinmaydi) — keshdagi
                // eskirgan nusxani ko'rsatish yolg'on bo'lardi, uni tozalaymiz.
                is Resource.Error -> if (res.error is AppException.NotFound) {
                    withContext(dispatchers.io) { q.deleteById(id) }
                    return res
                }
                Resource.Loading -> Unit
            }
        }
        val cached = byId(id) ?: return errorOf(AppException.NotFound())
        return Resource.Success(cached)
    }

    override suspend fun save(listing: Listing): Resource<Listing> =
        send(listing.copy(status = ListingStatus.DRAFT), submit = false)

    override suspend fun submit(listing: Listing): Resource<Listing> = send(listing, submit = true)

    /**
     * Serverga yuboradi va javobni keshga yozadi.
     *
     * Server e'longa **o'z id'sini** beradi, shuning uchun vaqtinchalik local qator
     * o'chiriladi — aks holda bitta e'lon ro'yxatda ikki marta ko'rinardi.
     */
    private suspend fun send(listing: Listing, submit: Boolean): Resource<Listing> {
        val prepared = listing.copy(updatedAt = now())
        return when (val res = remote.save(prepared, submit)) {
            is Resource.Success -> {
                val saved = res.data
                withContext(dispatchers.io) {
                    q.transaction {
                        if (saved.id != prepared.id) q.deleteById(prepared.id)
                        upsert(saved)
                    }
                }
                Resource.Success(saved)
            }
            is Resource.Error -> res
            Resource.Loading -> Resource.Error(AppLocale.pick(en = "Couldn't submit the listing", ru = "Не удалось отправить объявление", uz = "E'lonni yuborib bo'lmadi"))
        }
    }

    override suspend fun updateStatus(id: String, status: ListingStatus): Resource<Unit> {
        if (syncEnabled) {
            return when (val res = remote.setStatus(id, status)) {
                is Resource.Success -> {
                    cache(res.data)
                    Resource.Success(Unit)
                }
                is Resource.Error -> res
                Resource.Loading -> Resource.Error(AppLocale.pick(en = "Couldn't change the status", ru = "Не удалось изменить статус", uz = "Holatni o'zgartirib bo'lmadi"))
            }
        }
        return withContext(dispatchers.io) {
            q.updateStatus(status = status.name, updatedAt = now(), id = id)
            Resource.Success(Unit)
        }
    }

    /**
     * O'chirish. Serverdagi e'lon o'chgach keshdan ham tushadi; local qoralama
     * (server ko'rmagan) to'g'ridan-to'g'ri keshdan o'chadi.
     */
    override suspend fun delete(id: String): Resource<Unit> {
        if (syncEnabled) {
            val res = remote.delete(id)
            // Serverda allaqachon yo'q bo'lsa — bu ham muvaffaqiyat: natija bir xil.
            val gone = res is Resource.Success ||
                (res as? Resource.Error)?.error is AppException.NotFound
            if (!gone) return res
        }
        return withContext(dispatchers.io) {
            q.deleteById(id)
            Resource.Success(Unit)
        }
    }

    override suspend fun uploadImage(bytes: ByteArray, fileName: String): Resource<String> =
        remote.uploadImage(bytes, fileName)

    // -----------------------------------------------------------------------
    // Kesh
    // -----------------------------------------------------------------------

    private suspend fun cache(listing: Listing) = withContext(dispatchers.io) { upsert(listing) }

    private suspend fun cacheAll(listings: List<Listing>) {
        if (listings.isEmpty()) return
        withContext(dispatchers.io) { q.transaction { listings.forEach { upsert(it) } } }
    }

    private fun upsert(listing: Listing) {
        val e = listing.toEntity()
        q.upsert(
            id = e.id,
            ownerId = e.ownerId,
            businessId = e.businessId,
            kind = e.kind,
            detailsJson = e.detailsJson,
            title = e.title,
            description = e.description,
            imagesJson = e.imagesJson,
            priceUnit = e.priceUnit,
            price = e.price,
            priceMax = e.priceMax,
            currency = e.currency,
            isNegotiable = e.isNegotiable,
            finalPrice = e.finalPrice,
            contactPhone = e.contactPhone,
            universityId = e.universityId,
            audience = e.audience,
            branchesJson = e.branchesJson,
            validFrom = e.validFrom,
            validTo = e.validTo,
            attributesJson = e.attributesJson,
            optionGroupsJson = e.optionGroupsJson,
            status = e.status,
            rejectionReason = e.rejectionReason,
            viewsCount = e.viewsCount,
            createdAt = e.createdAt,
            updatedAt = e.updatedAt,
        )
    }

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    private companion object {
        /** Xavfsizlik chegarasi: server `hasNext` ni to'xtatmasa ham halqa cheksiz bo'lmaydi. */
        const val MAX_MINE_PAGES = 10
    }
}
