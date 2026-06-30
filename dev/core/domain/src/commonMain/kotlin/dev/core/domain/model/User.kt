package dev.core.domain.model

data class User(
    val id: Long,
    val fullName: String,
    val email: String,
    val role: UserRole,
)

enum class UserRole { STUDENT, ADMIN }
