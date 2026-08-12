package dev.feature.university.data.remote

import dev.core.common.Resource
import dev.feature.university.data.dto.UniversityDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import dev.core.common.locale.AppLocale

interface UniversityRemoteDataSource { suspend fun fetchUniversities(): Resource<List<UniversityDto>> }

class KtorUniversityRemoteDataSource(private val client: HttpClient) : UniversityRemoteDataSource {
    override suspend fun fetchUniversities(): Resource<List<UniversityDto>> = try {
        Resource.Success(client.get("universities").body())
    } catch (e: Exception) {
        Resource.Error(e.message ?: AppLocale.pick(en = "Couldn't load universities", ru = "Не удалось загрузить университеты", uz = "Universitetlarni yuklab bo'lmadi"), e)
    }
}
