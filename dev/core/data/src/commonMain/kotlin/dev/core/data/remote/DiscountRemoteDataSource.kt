package dev.core.data.remote

import dev.core.common.Resource
import dev.core.data.dto.DiscountOfferDto
import dev.core.data.dto.DiscountsResponseDto
import dev.core.domain.model.OfferDetail
import dev.core.domain.model.OfferFilterSchema
import dev.core.domain.model.OfferSuggestion

/**
 * "Siz uchun" feed'ining masofaviy (backend) manbasi — B4 offline-first shablonining tarmoq qismi.
 *
 * Repository shu interfeys orqali serverdan oladi va local DB'ga yozadi. Ktor klientiga sessiya
 * tokeni avtomatik qo'shiladi. Haqiqiy implementatsiya — [ApiDiscountRemoteDataSource]
 * (`catalog` + `discounts` endpoint'lari). Boshqa domenlar (Jobs, Students...) aynan shu
 * shakldan nusxa oladi.
 */
interface DiscountRemoteDataSource {
    suspend fun fetchDiscounts(): Resource<DiscountsResponseDto>

    /**
     * Saqlanganlar ro'yxatini serverda ham yangilaydi
     * (`POST /v1/discounts/favorites/toggle`). Local yozuv baribir birinchi bo'ladi —
     * bu faqat sinxronlash; qaytgan qiymat — serverdagi yakuniy holat.
     */
    suspend fun setFavorite(listingId: String, saved: Boolean): Resource<Boolean>

    /** Bitta e'lon to'liq holda (`POST /v1/discounts/detail`). */
    suspend fun fetchDetail(listingId: String): Resource<OfferDetail>

    /** Qidiruv avtoto'ldirishi (`POST /v1/discounts/suggest`). */
    suspend fun suggest(query: String): Resource<List<OfferSuggestion>>

    /** Bitta guruhning e'lonlari — bo'lim ekrani uchun kattaroq sahifa bilan. */
    suspend fun fetchGroupOffers(groupKey: String): Resource<List<DiscountOfferDto>>

    /** Filtr ekrani sxemasi (`POST /v1/catalog/filter-schema`). */
    suspend fun fetchFilterSchema(typeKeys: List<String>): Resource<OfferFilterSchema>
}
