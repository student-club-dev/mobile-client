package dev.core.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.core.common.AppDispatchers
import dev.core.common.Resource
import dev.core.common.errorOf
import dev.core.common.error.AppException
import dev.core.data.remote.DiscountRemoteDataSource
import dev.core.data.mapper.toDomain
import dev.core.data.mapper.toOfflineDetail
import dev.core.database.sql.StudentClubDatabase
import dev.core.domain.model.DiscountCategory
import dev.core.domain.model.DiscountOffer
import dev.core.domain.model.OfferDetail
import dev.core.domain.model.OfferFilterSchema
import dev.core.domain.model.OfferSuggestion
import dev.core.domain.repository.DiscountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

// ===========================================================================
// Chegirmalar
// ===========================================================================
class DiscountRepositoryImpl(
    private val db: StudentClubDatabase,
    private val dispatchers: AppDispatchers,
    // --- B4 offline-first shabloni: tarmoq manbasi + sinxronlash bayrog'i ---
    private val remote: DiscountRemoteDataSource,
    /** `true` — refresh() backend'dan tortadi; `false` — no-op (backend yo'q, seed saqlanadi). */
    private val syncEnabled: Boolean,
) : DiscountRepository {
    private val q get() = db.discountQueries

    override fun observeCategories(): Flow<List<DiscountCategory>> =
        q.selectCategories().asFlow().mapToList(dispatchers.io).map { r -> r.map { it.toDomain() } }

    override fun observeAllOffers(): Flow<List<DiscountOffer>> =
        q.selectAllOffers().asFlow().mapToList(dispatchers.io).map { r -> r.map { it.toDomain() } }

    override fun observeOffers(categoryId: String): Flow<List<DiscountOffer>> =
        q.selectOffersByCategory(categoryId).asFlow().mapToList(dispatchers.io).map { r -> r.map { it.toDomain() } }

    override fun observeFeatured(): Flow<List<DiscountOffer>> =
        q.selectFeaturedOffers().asFlow().mapToList(dispatchers.io).map { r -> r.map { it.toDomain() } }

    override fun observeSaved(): Flow<List<DiscountOffer>> =
        q.selectSavedOffers().asFlow().mapToList(dispatchers.io).map { r -> r.map { it.toDomain() } }

    /**
     * Avval local (UI darrov yangilanadi va oflaynda ham ishlaydi), keyin — serverga.
     * Tarmoq xatosi jimgina yutiladi: keyingi [refresh] serverdagi holatni qaytaradi.
     */
    override suspend fun setSaved(offerId: String, saved: Boolean) = withContext(dispatchers.io) {
        if (saved) q.saveOffer(offerId) else q.unsaveOffer(offerId)
        if (syncEnabled) remote.setFavorite(offerId, saved)
        Unit
    }

    /**
     * Tafsilot faqat serverda bor (promo-kod, filiallar, shartlar). Tarmoq yo'q yoki sinxronlash
     * o'chirilgan bo'lsa — keshdagi kartadan minimal variant yig'iladi, shunda ekran baribir
     * ochiladi (`fromNetwork = false`).
     */
    override suspend fun getDetail(offerId: String): Resource<OfferDetail> {
        if (syncEnabled) {
            val res = remote.fetchDetail(offerId)
            if (res is Resource.Success) return res
        }
        val cached = withContext(dispatchers.io) { q.selectOfferById(offerId).executeAsOneOrNull() }
            ?: return errorOf(AppException.NotFound())
        return Resource.Success(cached.toDomain().toOfflineDetail(isSaved(offerId)))
    }

    override suspend fun suggest(query: String): Resource<List<OfferSuggestion>> {
        if (!syncEnabled || query.isBlank()) return Resource.Success(emptyList())
        return remote.suggest(query)
    }

    override suspend fun getFilterSchema(typeKeys: List<String>): Resource<OfferFilterSchema> {
        if (!syncEnabled) return Resource.Success(OfferFilterSchema())
        return remote.fetchFilterSchema(typeKeys)
    }

    private suspend fun isSaved(offerId: String): Boolean = withContext(dispatchers.io) {
        q.selectSavedOffers().executeAsList().any { it.id == offerId }
    }

    /**
     * Offline-first sinxronlash: backend'dan oladi, muvaffaqiyatда local DB'ni almashtiradi.
     * Xato/tarmoqsiz bo'lsa — DB'ga tegilmaydi (cache/seed saqlanadi). UI DB'ni kuzatgani
     * uchun yangilanish avtomatik ko'rinadi.
     */
    override suspend fun refresh(): Resource<Unit> {
        if (!syncEnabled) return Resource.Success(Unit) // Backend hali yo'q — no-op.
        return when (val res = remote.fetchDiscounts()) {
            is Resource.Success -> {
                // Bo'sh javob (masalan feed hali to'ldirilmagan) keshni o'chirmasin — aks holda
                // ekran bo'm-bo'sh qolardi. Bor ma'lumot faqat bor ma'lumot bilan almashtiriladi.
                if (res.data.categories.isEmpty() && res.data.offers.isEmpty()) {
                    return Resource.Success(Unit)
                }
                withContext(dispatchers.io) {
                    q.transaction {
                        q.clearCategories()
                        q.clearOffers()
                        res.data.categories.forEach { c ->
                            q.upsertCategory(c.id, c.name, c.emoji, c.offerCount.toLong(), c.accent)
                        }
                        res.data.offers.forEach { o ->
                            q.upsertOffer(
                                o.id, o.categoryId, o.subcategory, o.gender, o.merchant, o.title,
                                if (o.isDiscount) 1L else 0L, o.discountPercent.toLong(),
                                o.originalPrice, o.finalPrice, o.priceUnit,
                                o.tag, o.promoCode, o.location, o.expiry, o.emoji, o.bannerAccent,
                                if (o.featured) 1L else 0L, o.lat, o.lng,
                            )
                            // Saqlanganlar server holatiga tenglashtiriladi (boshqa qurilmada
                            // saqlangan/olib tashlangan e'lon shu yerda ko'rinadi).
                            if (o.saved) q.saveOffer(o.id) else q.unsaveOffer(o.id)
                        }
                    }
                }
                Resource.Success(Unit)
            }
            is Resource.Error -> res           // cache saqlanadi
            Resource.Loading -> Resource.Success(Unit)
        }
    }
}

