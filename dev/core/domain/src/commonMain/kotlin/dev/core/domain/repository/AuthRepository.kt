package dev.core.domain.repository

import dev.core.common.Resource
import dev.core.domain.model.User

interface AuthRepository {
    suspend fun login(email: String, password: String): Resource<User>
    suspend fun logout()
    suspend fun currentUser(): User?
}
