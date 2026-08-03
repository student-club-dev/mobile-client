package dev.feature.students.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.core.common.AppDispatchers
import dev.core.common.Resource
import dev.core.database.sql.StudentClubDatabase
import dev.feature.students.data.mapper.joinDb
import dev.feature.students.data.mapper.toDomain
import dev.feature.students.data.remote.StudentRemoteDataSource
import dev.feature.students.domain.model.FriendStatus
import dev.feature.students.domain.model.Student
import dev.feature.students.domain.repository.StudentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class StudentRepositoryImpl(
    private val db: StudentClubDatabase,
    private val dispatchers: AppDispatchers,
    private val remote: StudentRemoteDataSource,
    private val syncEnabled: Boolean,
) : StudentRepository {
    private val q get() = db.studentQueries

    override fun observeStudents(): Flow<List<Student>> =
        q.selectAll().asFlow().mapToList(dispatchers.io).map { r -> r.map { it.toDomain() } }

    override fun observeByUniversity(universityId: String): Flow<List<Student>> =
        q.selectByUniversity(universityId).asFlow().mapToList(dispatchers.io).map { r -> r.map { it.toDomain() } }

    override suspend fun setFriendStatus(studentId: String, status: FriendStatus) = withContext(dispatchers.io) {
        q.setFriendStatus(status.name, studentId)
    }

    override suspend fun refresh(): Resource<Unit> {
        if (!syncEnabled) return Resource.Success(Unit)
        return when (val res = remote.fetchStudents()) {
            is Resource.Success -> {
                withContext(dispatchers.io) {
                    q.transaction {
                        q.clear()
                        res.data.forEach { s ->
                            q.upsert(
                                s.id, s.firstName, s.lastName, s.initial, s.avatarUrl, s.universityId, s.universityMonogram,
                                s.course.toLong(), s.faculty, s.friendStatus, s.interests.joinDb(),
                                s.friendsCount.toLong(), s.adsCount.toLong(), s.rating,
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
