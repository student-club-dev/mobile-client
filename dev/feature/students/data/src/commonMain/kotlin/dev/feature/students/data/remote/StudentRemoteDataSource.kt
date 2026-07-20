package dev.feature.students.data.remote

import dev.core.common.Resource
import dev.feature.students.data.dto.StudentDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

interface StudentRemoteDataSource { suspend fun fetchStudents(): Resource<List<StudentDto>> }

class KtorStudentRemoteDataSource(private val client: HttpClient) : StudentRemoteDataSource {
    override suspend fun fetchStudents(): Resource<List<StudentDto>> = try {
        Resource.Success(client.get("students").body())
    } catch (e: Exception) {
        Resource.Error(e.message ?: "Studentlarni yuklab bo'lmadi", e)
    }
}
