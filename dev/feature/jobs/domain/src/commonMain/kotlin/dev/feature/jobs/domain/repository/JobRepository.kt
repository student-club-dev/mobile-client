package dev.feature.jobs.domain.repository

import dev.core.common.Resource
import dev.feature.jobs.domain.model.Job
import dev.feature.jobs.domain.model.JobApplication
import kotlinx.coroutines.flow.Flow

/** Ishlar — ro'yxat, saqlangan (bookmark), arizalar. */
interface JobRepository {
    fun observeJobs(): Flow<List<Job>>
    fun observeBookmarked(): Flow<List<Job>>
    fun observeApplications(): Flow<List<JobApplication>>
    suspend fun setBookmarked(jobId: String, bookmarked: Boolean)
    suspend fun apply(job: Job)

    /** Backend'dan sinxronlab local DB'ni yangilaydi (offline-first). Xatoda cache saqlanadi. */
    suspend fun refresh(): Resource<Unit>
}
