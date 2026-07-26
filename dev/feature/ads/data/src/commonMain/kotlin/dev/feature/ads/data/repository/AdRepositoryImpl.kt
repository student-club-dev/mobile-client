package dev.feature.ads.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.core.common.AppDispatchers
import dev.core.common.Resource
import dev.core.database.sql.StudentClubDatabase
import dev.feature.ads.data.mapper.joinDb
import dev.feature.ads.data.mapper.toDomain
import dev.feature.ads.data.remote.AdRemoteDataSource
import dev.feature.ads.domain.model.Ad
import dev.feature.ads.domain.repository.AdRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AdRepositoryImpl(
    private val db: StudentClubDatabase,
    private val dispatchers: AppDispatchers,
    private val remote: AdRemoteDataSource,
    private val syncEnabled: Boolean,
) : AdRepository {
    private val q get() = db.adQueries

    override fun observeAds(): Flow<List<Ad>> =
        q.selectAll().asFlow().mapToList(dispatchers.io).map { r -> r.map { it.toDomain() } }

    override fun observeByOwner(ownerId: String): Flow<List<Ad>> =
        q.selectByOwner(ownerId).asFlow().mapToList(dispatchers.io).map { r -> r.map { it.toDomain() } }

    override suspend fun post(ad: Ad) = withContext(dispatchers.io) {
        q.upsert(
            id = ad.id,
            type = ad.type.name,
            title = ad.title,
            category = ad.category,
            price = ad.price,
            description = ad.description,
            images = ad.images.joinDb(),
            ownerId = ad.ownerId,
            createdAgo = ad.createdAgo,
        )
    }

    override suspend fun delete(adId: String) = withContext(dispatchers.io) {
        q.deleteById(adId)
    }

    override suspend fun refresh(): Resource<Unit> {
        if (!syncEnabled) return Resource.Success(Unit)
        return when (val res = remote.fetchAds()) {
            is Resource.Success -> {
                withContext(dispatchers.io) {
                    q.transaction {
                        q.clear()
                        res.data.forEach { a ->
                            q.upsert(
                                a.id, a.type, a.title, a.category, a.price, a.description,
                                a.images.joinDb(), a.ownerId, a.createdAgo,
                            )
                        }
                    }
                }
                Resource.Success(Unit)
            }
            is Resource.Error -> res
            Resource.Loading -> Resource.Success(Unit)
        }
    }
}
