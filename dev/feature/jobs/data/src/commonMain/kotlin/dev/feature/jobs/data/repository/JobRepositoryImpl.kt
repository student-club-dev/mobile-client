package dev.feature.jobs.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.core.common.AppDispatchers
import dev.core.common.Resource
import dev.core.database.sql.StudentClubDatabase
import dev.feature.jobs.data.mapper.joinDb
import dev.feature.jobs.data.mapper.toBool
import dev.feature.jobs.data.mapper.toDb
import dev.feature.jobs.data.mapper.toDomain
import dev.feature.jobs.data.remote.JobRemoteDataSource
import dev.feature.jobs.domain.model.ApplicationStatus
import dev.feature.jobs.domain.model.Job
import dev.feature.jobs.domain.model.JobApplication
import dev.feature.jobs.domain.repository.JobRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class JobRepositoryImpl(
    private val db: StudentClubDatabase,
    private val dispatchers: AppDispatchers,
    private val remote: JobRemoteDataSource,
    private val syncEnabled: Boolean,
) : JobRepository {
    private val q get() = db.jobQueries

    override fun observeJobs(): Flow<List<Job>> =
        q.selectAllJobs().asFlow().mapToList(dispatchers.io).map { r -> r.map { it.toDomain() } }

    override fun observeBookmarked(): Flow<List<Job>> =
        q.selectBookmarked().asFlow().mapToList(dispatchers.io).map { r -> r.map { it.toDomain() } }

    override fun observeApplications(): Flow<List<JobApplication>> =
        q.selectApplications().asFlow().mapToList(dispatchers.io).map { r -> r.map { it.toDomain() } }

    override suspend fun setBookmarked(jobId: String, bookmarked: Boolean) = withContext(dispatchers.io) {
        q.setBookmark(bookmarked.toDb(), jobId)
    }

    override suspend fun apply(job: Job) = withContext(dispatchers.io) {
        q.upsertApplication(
            id = "app-${job.id}",
            jobId = job.id,
            jobTitle = job.title,
            company = job.company,
            status = ApplicationStatus.SENT.name,
            appliedAgo = "hozir",
        )
    }

    override suspend fun refresh(): Resource<Unit> {
        if (!syncEnabled) return Resource.Success(Unit)
        return when (val res = remote.fetchJobs()) {
            is Resource.Success -> {
                withContext(dispatchers.io) {
                    q.transaction {
                        q.clearJobs()
                        res.data.forEach { j ->
                            q.upsertJob(
                                j.id, j.title, j.company, j.companyMonogram, j.location, j.category,
                                j.tags.joinDb(), j.salary, j.remote.toDb(), j.partTime.toDb(),
                                j.postedAgo, j.field, j.bookmarked.toDb(),
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
