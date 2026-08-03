package dev.feature.listings.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.core.common.AppDispatchers
import dev.core.common.Resource
import dev.core.database.sql.StudentClubDatabase
import dev.feature.listings.data.mapper.toDomain
import dev.feature.listings.data.mapper.toEntity
import dev.feature.listings.data.remote.ListingRemoteDataSource
import dev.feature.listings.domain.model.Listing
import dev.feature.listings.domain.model.ListingKind
import dev.feature.listings.domain.model.ListingStatus
import dev.feature.listings.domain.repository.ListingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * Offline-first e'lon repository'si.
 *
 * UI **faqat** local DB'ni kuzatadi — shu sabab e'lon tarmoqsiz ham darrov ro'yxatda paydo
 * bo'ladi. [submit] avval masofaviy manbaga yuboradi (yoqilgan bo'lsa), keyin qaytgan
 * natijani (server bergan id va status) keshga yozadi.
 */
class ListingRepositoryImpl(
    private val db: StudentClubDatabase,
    private val dispatchers: AppDispatchers,
    private val remote: ListingRemoteDataSource,
) : ListingRepository {

    private val q get() = db.listingQueries

    override fun observeMyListings(ownerId: String): Flow<List<Listing>> =
        q.selectByOwner(ownerId)
            .asFlow()
            .mapToList(dispatchers.io)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeActive(): Flow<List<Listing>> =
        q.selectActive(now = Clock.System.now().toEpochMilliseconds())
            .asFlow()
            .mapToList(dispatchers.io)
            .map { rows -> rows.map { it.toDomain() } }

    // Filtrlash SQL'da — `listing_kind_idx` indeksidan foydalanadi. Domenda filtrlash
    // butun jadvalni o'qib, keyin tashlab yuborish bo'lar edi.
    override fun observeActiveByKind(kind: ListingKind): Flow<List<Listing>> =
        q.selectActiveByKind(kind = kind.name, now = Clock.System.now().toEpochMilliseconds())
            .asFlow()
            .mapToList(dispatchers.io)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun byId(id: String): Listing? = withContext(dispatchers.io) {
        q.selectById(id).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun save(listing: Listing): Resource<Listing> {
        val draft = listing.copy(updatedAt = now())
        cache(draft)
        return Resource.Success(draft)
    }

    override suspend fun submit(listing: Listing): Resource<Listing> {
        // Avval masofaviy manba — u id/status'ni o'zgartirishi mumkin (server DRAFT ni
        // PENDING_REVIEW ga o'tkazadi va o'z id'sini beradi). Keshga o'shani yozamiz,
        // aks holda local va serverdagi e'lon ikkiga bo'linib ketadi.
        return when (val res = remote.publish(listing.copy(updatedAt = now()))) {
            is Resource.Success -> {
                val published = res.data.copy(updatedAt = now())
                cache(published)
                Resource.Success(published)
            }
            is Resource.Error -> res
            Resource.Loading -> Resource.Error("E'lonni yuborib bo'lmadi")
        }
    }

    override suspend fun updateStatus(id: String, status: ListingStatus): Resource<Unit> =
        withContext(dispatchers.io) {
            q.updateStatus(status = status.name, updatedAt = now(), id = id)
            Resource.Success(Unit)
        }

    override suspend fun delete(id: String): Resource<Unit> = withContext(dispatchers.io) {
        q.deleteById(id)
        Resource.Success(Unit)
    }

    override suspend fun uploadImage(bytes: ByteArray, fileName: String): Resource<String> =
        remote.uploadImage(bytes, fileName)

    private suspend fun cache(listing: Listing) = withContext(dispatchers.io) {
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
}
