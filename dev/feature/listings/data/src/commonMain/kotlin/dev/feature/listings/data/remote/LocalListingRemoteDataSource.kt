package dev.feature.listings.data.remote

import dev.core.common.Resource
import dev.feature.listings.domain.model.Listing
import dev.feature.listings.domain.model.ListingPage
import dev.feature.listings.domain.model.ListingQuery
import dev.feature.listings.domain.model.ListingStatus
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Backendsiz rejim (`REMOTE_SYNC_ENABLED = false`).
 *
 * - Rasm hech qayerga yuklanmaydi: baytlar `data:image/...;base64,...` URI'ga aylanadi va
 *   e'lon bilan birga local bazada saqlanadi. Shu sabab ilova internetsiz ham to'liq ishlaydi.
 * - Moderator ham, validatsiya serveri ham yo'q — `submit` darrov [ListingStatus.ACTIVE] qiladi.
 * - O'qish amallari **bo'sh** qaytadi: bu manbada hech narsa saqlanmaydi, ro'yxatni
 *   repository local bazadan o'qiydi ([dev.feature.listings.data.repository.ListingRepositoryImpl]).
 */
class LocalListingRemoteDataSource : ListingRemoteDataSource {

    override suspend fun search(query: ListingQuery): Resource<ListingPage> =
        Resource.Success(ListingPage())

    override suspend fun mine(page: Int, size: Int): Resource<ListingPage> =
        Resource.Success(ListingPage())

    override suspend fun byId(id: String): Resource<Listing> =
        Resource.Error("E'lon topilmadi")

    override suspend fun save(listing: Listing, submit: Boolean): Resource<Listing> =
        Resource.Success(
            listing.copy(status = if (submit) ListingStatus.ACTIVE else ListingStatus.DRAFT),
        )

    override suspend fun setStatus(id: String, status: ListingStatus): Resource<Listing> =
        Resource.Error("Backend ulanmagan")

    override suspend fun delete(id: String): Resource<Unit> = Resource.Success(Unit)

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun uploadImage(bytes: ByteArray, fileName: String): Resource<String> = try {
        val mime = if (fileName.endsWith(".png", ignoreCase = true)) "image/png" else "image/jpeg"
        Resource.Success("data:$mime;base64,${Base64.encode(bytes)}")
    } catch (e: Exception) {
        Resource.Error(e.message ?: "Rasmni o'qib bo'lmadi", e)
    }
}
