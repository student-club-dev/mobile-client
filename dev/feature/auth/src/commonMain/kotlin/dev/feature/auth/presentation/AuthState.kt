package dev.feature.auth.presentation

import dev.core.domain.model.User

enum class AuthMode { EMAIL, PHONE }

data class AuthUiState(
    val mode: AuthMode = AuthMode.EMAIL,
    // Email/parol
    val email: String = "",
    val password: String = "",
    // Telefon / OTP
    val phoneNumber: String = "",
    val otpCode: String = "",
    val otpSent: Boolean = false,
    // Umumiy
    val isLoading: Boolean = false,
    val error: String? = null,
    val user: User? = null,
)
