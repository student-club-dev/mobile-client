package dev.feature.students.domain.repository

import dev.core.common.Resource
import dev.feature.students.domain.model.FriendStatus
import dev.feature.students.domain.model.Student
import kotlinx.coroutines.flow.Flow

/** Studentlar — do'st topish (universitet bo'yicha) va do'stlik holati. */
interface StudentRepository {
    fun observeStudents(): Flow<List<Student>>
    fun observeByUniversity(universityId: String): Flow<List<Student>>
    suspend fun setFriendStatus(studentId: String, status: FriendStatus)

    /** Backend'dan sinxronlab local DB'ni yangilaydi (offline-first). Xatoda cache saqlanadi. */
    suspend fun refresh(): Resource<Unit>
}
