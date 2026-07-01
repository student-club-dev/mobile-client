package dev.core.data.repository

import dev.core.common.Resource
import dev.core.data.dto.AuthResponse
import dev.core.data.dto.LoginRequest
import dev.core.domain.model.ExternalAuthUser
import dev.core.domain.model.User
import dev.core.domain.model.UserRole
import dev.core.domain.repository.AuthRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class AuthRepositoryImpl(
    private val client: HttpClient,
) : AuthRepository {

    private var cachedUser: User? = null

    override suspend fun login(email: String, password: String): Resource<User> = try {
        val response: AuthResponse = client.post("auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email, password))
        }.body()
        val user = User(
            id = response.userId,
            fullName = response.fullName.ifBlank { email },
            email = email,
            role = UserRole.STUDENT,
        )
        cachedUser = user
        Resource.Success(user)
    } catch (e: Exception) {
        Resource.Error(e.message ?: "Login amalga oshmadi", e)
    }

    override suspend fun syncExternalUser(external: ExternalAuthUser): Resource<User> = try {
        // TODO: haqiqiy backend kelganda Firebase ID token'ni backendga yuborib,
        //  ilova sessiyasini oching (masalan: client.post("auth/firebase") { setBody(...) }).
        //  Hozircha Firebase user ma'lumotidan domen User yasaymiz.
        val user = User(
            id = (external.uid.hashCode().toLong() and 0xffffffffL),
            fullName = external.fullName
                ?: external.email?.substringBefore('@')
                ?: external.phoneNumber
                ?: "Foydalanuvchi",
            email = external.email.orEmpty(),
            role = UserRole.STUDENT,
            phoneNumber = external.phoneNumber,
            photoUrl = external.photoUrl,
        )
        cachedUser = user
        Resource.Success(user)
    } catch (e: Exception) {
        Resource.Error(e.message ?: "Foydalanuvchini sinxronlash amalga oshmadi", e)
    }

    override suspend fun logout() {
        cachedUser = null
    }

    override suspend fun currentUser(): User? = cachedUser
}
