package dev.core.domain.repository

import dev.core.common.Resource
import dev.core.domain.model.DiscountCategory
import dev.core.domain.model.DiscountOffer
import kotlinx.coroutines.flow.Flow

/** Chegirmalar — kategoriyalar, takliflar, saqlangan takliflar. */
interface DiscountRepository {
    fun observeCategories(): Flow<List<DiscountCategory>>

    /** "Siz uchun" feed'i — barcha e'lonlar (chegirmali + chegirmasiz), UI o'zi filtrlaydi. */
    fun observeAllOffers(): Flow<List<DiscountOffer>>
    fun observeOffers(categoryId: String): Flow<List<DiscountOffer>>
    fun observeFeatured(): Flow<List<DiscountOffer>>
    fun observeSaved(): Flow<List<DiscountOffer>>
    suspend fun setSaved(offerId: String, saved: Boolean)

    /**
     * Backend'dan sinxronlab local DB'ni yangilaydi (offline-first).
     * UI har doim DB'ni kuzatadi; bu faqat DB'ni yangilaydi. Xato bo'lsa cache saqlanadi.
     */
    suspend fun refresh(): Resource<Unit>
}
