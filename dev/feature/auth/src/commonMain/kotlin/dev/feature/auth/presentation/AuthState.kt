package dev.feature.auth.presentation

import dev.core.domain.model.User

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val user: User? = null,
)
