package dev.feature.jobs.data.remote

import dev.core.common.Resource
import dev.feature.jobs.data.dto.JobDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

/** Ishlarni masofaviy manbadan oladi. Ktor klientiga Firebase token avtomatik qo'shiladi. */
interface JobRemoteDataSource {
    suspend fun fetchJobs(): Resource<List<JobDto>>
}

class KtorJobRemoteDataSource(private val client: HttpClient) : JobRemoteDataSource {
    override suspend fun fetchJobs(): Resource<List<JobDto>> = try {
        Resource.Success(client.get("jobs").body())
    } catch (e: Exception) {
        Resource.Error(e.message ?: "Ishlarni yuklab bo'lmadi", e)
    }
}
