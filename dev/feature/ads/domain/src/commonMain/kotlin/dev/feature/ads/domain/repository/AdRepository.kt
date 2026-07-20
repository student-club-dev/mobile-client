package dev.feature.ads.domain.repository

import dev.core.common.Resource
import dev.feature.ads.domain.model.Ad
import kotlinx.coroutines.flow.Flow

/** E'lonlar — joylash, o'chirish, foydalanuvchiniki. */
interface AdRepository {
    fun observeAds(): Flow<List<Ad>>
    fun observeByOwner(ownerId: String): Flow<List<Ad>>
    suspend fun post(ad: Ad)
    suspend fun delete(adId: String)

    /** Backend'dan sinxronlab local DB'ni yangilaydi (offline-first). Xatoda cache saqlanadi. */
    suspend fun refresh(): Resource<Unit>
}
