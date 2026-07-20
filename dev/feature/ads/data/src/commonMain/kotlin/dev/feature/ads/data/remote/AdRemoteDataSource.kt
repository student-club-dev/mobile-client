package dev.feature.ads.data.remote

import dev.core.common.Resource
import dev.feature.ads.data.dto.AdDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

interface AdRemoteDataSource { suspend fun fetchAds(): Resource<List<AdDto>> }

class KtorAdRemoteDataSource(private val client: HttpClient) : AdRemoteDataSource {
    override suspend fun fetchAds(): Resource<List<AdDto>> = try {
        Resource.Success(client.get("ads").body())
    } catch (e: Exception) {
        Resource.Error(e.message ?: "E'lonlarni yuklab bo'lmadi", e)
    }
}
