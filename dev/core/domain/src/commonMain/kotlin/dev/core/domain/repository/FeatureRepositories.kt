package dev.core.domain.repository

import dev.core.common.Resource
import dev.core.domain.model.DiscountCategory
import dev.core.domain.model.DiscountGroup
import dev.core.domain.model.DiscountOffer
import dev.core.domain.model.OfferDetail
import dev.core.domain.model.OfferFilterSchema
import dev.core.domain.model.OfferSuggestion
import kotlinx.coroutines.flow.Flow

/** Chegirmalar — kategoriyalar, takliflar, saqlangan takliflar. */
interface DiscountRepository {
    /** Bosh ekrandagi bo'limlar — katalog guruhlari, server tartibida. */
    fun observeGroups(): Flow<List<DiscountGroup>>

    fun observeCategories(): Flow<List<DiscountCategory>>

    /** "Siz uchun" feed'i — barcha e'lonlar (chegirmali + chegirmasiz), UI o'zi filtrlaydi. */
    fun observeAllOffers(): Flow<List<DiscountOffer>>
    fun observeOffers(categoryId: String): Flow<List<DiscountOffer>>
    fun observeFeatured(): Flow<List<DiscountOffer>>
    fun observeSaved(): Flow<List<DiscountOffer>>
    suspend fun setSaved(offerId: String, saved: Boolean)

    /**
     * Bitta e'lon to'liq holda (promo-kod, shartlar, filiallar). Tarmoq bo'lmasa yoki
     * sinxronlash o'chirilgan bo'lsa — keshdagi kartadan yig'ilgan minimal variant
     * ([OfferDetail.fromNetwork] = `false`).
     */
    suspend fun getDetail(offerId: String): Resource<OfferDetail>

    /** Qidiruv qatori uchun avtoto'ldirish takliflari. Bo'sh so'rovda — bo'sh ro'yxat. */
    suspend fun suggest(query: String): Resource<List<OfferSuggestion>>

    /**
     * Filtr ekrani sxemasi. [typeKeys] berilsa — faqat o'sha biznes turlari doirasida
     * (bo'limlar va sonlar shunga qarab toraytiriladi).
     */
    suspend fun getFilterSchema(typeKeys: List<String> = emptyList()): Resource<OfferFilterSchema>

    /**
     * Backend'dan sinxronlab local DB'ni yangilaydi (offline-first).
     * UI har doim DB'ni kuzatadi; bu faqat DB'ni yangilaydi. Xato bo'lsa cache saqlanadi.
     */
    suspend fun refresh(): Resource<Unit>
}
