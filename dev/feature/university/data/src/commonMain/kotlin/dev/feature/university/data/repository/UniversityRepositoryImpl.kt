package dev.feature.university.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.core.common.AppDispatchers
import dev.core.common.Resource
import dev.core.database.sql.StudentClubsDatabase
import dev.feature.university.data.dto.ProfEmisUniversityDto
import dev.feature.university.data.dto.toUniversity
import dev.feature.university.data.mapper.toDomain
import dev.feature.university.data.remote.UniversityRemoteDataSource
import dev.feature.university.domain.model.University
import dev.feature.university.domain.repository.UniversityRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private const val EMIS_UNIVERSITIES_URL =
    "https://prof-emis.edu.uz/api/v2/integration/stat/public/university?limit=10000"
private const val UNI_SOURCE_KEY = "universities_source"
private const val UNI_SOURCE_VERSION = "prof-emis-1"

class UniversityRepositoryImpl(
    private val db: StudentClubsDatabase,
    private val dispatchers: AppDispatchers,
    private val remote: UniversityRemoteDataSource,
    private val syncEnabled: Boolean,
    private val httpClient: HttpClient,
) : UniversityRepository {
    private val q get() = db.universityQueries

    override fun observeUniversities(): Flow<List<University>> =
        q.selectAll().asFlow().mapToList(dispatchers.io).map { rows -> rows.map { it.toDomain() } }

    override suspend fun fetchSelectableUniversities(): Resource<List<University>> = withContext(dispatchers.io) {
        try {
            val dtos: List<ProfEmisUniversityDto> = httpClient.get(EMIS_UNIVERSITIES_URL).body()
            Resource.Success(dtos.map { it.toUniversity() }.sortedBy { it.name })
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Universitetlarni yuklab bo'lmadi", e)
        }
    }

    override suspend fun addUniversity(university: University) = withContext(dispatchers.io) {
        q.upsert(university.id, university.name, university.city, university.monogram, university.faculty, university.accent)
    }

    override suspend fun ensureRemoteUniversities() {
        val settings = db.appSettingQueries
        val done = withContext(dispatchers.io) { settings.selectByKey(UNI_SOURCE_KEY).executeAsOneOrNull() }
        if (done == UNI_SOURCE_VERSION) return
        when (val res = fetchSelectableUniversities()) {
            is Resource.Success -> withContext(dispatchers.io) {
                q.transaction {
                    q.clear()
                    res.data.forEach { u -> q.upsert(u.id, u.name, u.city, u.monogram, u.faculty, u.accent) }
                    settings.upsert(UNI_SOURCE_KEY, UNI_SOURCE_VERSION)
                }
            }
            else -> Unit
        }
    }

    override suspend fun refresh(): Resource<Unit> {
        if (!syncEnabled) return Resource.Success(Unit)
        return when (val res = remote.fetchUniversities()) {
            is Resource.Success -> {
                withContext(dispatchers.io) {
                    q.transaction {
                        q.clear()
                        res.data.forEach { u -> q.upsert(u.id, u.name, u.city, u.monogram, u.faculty, u.accent) }
                    }
                }
                Resource.Success(Unit)
            }
            is Resource.Error -> res
            Resource.Loading -> Resource.Success(Unit)
        }
    }
}
