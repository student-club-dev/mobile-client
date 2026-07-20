package dev.core.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.core.common.AppDispatchers
import dev.core.common.Resource
import dev.core.data.remote.DiscountRemoteDataSource
import dev.core.data.mapper.toDomain
import dev.core.database.sql.StudentClubsDatabase
import dev.core.domain.model.DiscountCategory
import dev.core.domain.model.DiscountOffer
import dev.core.domain.repository.DiscountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

// ===========================================================================
// Chegirmalar
// ===========================================================================
class DiscountRepositoryImpl(
    private val db: StudentClubsDatabase,
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

    override suspend fun setSaved(offerId: String, saved: Boolean) = withContext(dispatchers.io) {
        if (saved) q.saveOffer(offerId) else q.unsaveOffer(offerId)
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

