package dev.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.core.common.Resource
import dev.core.domain.model.ExternalAuthUser
import dev.core.domain.usecase.LoginUseCase
import dev.core.domain.usecase.SyncExternalUserUseCase
import dev.feature.auth.social.OtpSendResult
import dev.feature.auth.social.SocialAuthController
import dev.feature.auth.social.SocialAuthResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val loginUseCase: LoginUseCase,
    private val syncExternalUserUseCase: SyncExternalUserUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onModeChange(mode: AuthMode) = _state.update { it.copy(mode = mode, error = null) }
    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }
    fun onPhoneChange(value: String) = _state.update { it.copy(phoneNumber = value, error = null) }
    fun onOtpChange(value: String) = _state.update { it.copy(otpCode = value, error = null) }

    // --- Email/parol ---
    fun login() {
        val current = _state.value
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = loginUseCase(current.email, current.password)) {
                is Resource.Success ->
                    _state.update { it.copy(isLoading = false, user = result.data) }
                is Resource.Error ->
                    _state.update { it.copy(isLoading = false, error = result.message) }
                Resource.Loading -> Unit
            }
        }
    }

    // --- Google Sign-In ---
    fun signInWithGoogle(controller: SocialAuthController) {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = controller.signInWithGoogle()) {
                is SocialAuthResult.Success -> syncAndFinish(result.user)
                SocialAuthResult.Cancelled -> _state.update { it.copy(isLoading = false) }
                is SocialAuthResult.Error ->
                    _state.update { it.copy(isLoading = false, error = result.message) }
            }
        }
    }

    // --- Telefon (OTP) ---
    fun sendOtp(controller: SocialAuthController) {
        val phone = _state.value.phoneNumber.trim()
        if (!isValidPhone(phone)) {
            _state.update { it.copy(error = "Telefon raqamini xalqaro formatda kiriting, masalan +998901234567") }
            return
        }
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = controller.sendOtp(phone)) {
                OtpSendResult.Sent ->
                    _state.update { it.copy(isLoading = false, otpSent = true) }
                is OtpSendResult.AutoVerified -> syncAndFinish(result.user)
                is OtpSendResult.Error ->
                    _state.update { it.copy(isLoading = false, error = result.message) }
            }
        }
    }

    fun confirmOtp(controller: SocialAuthController) {
        val code = _state.value.otpCode.trim()
        if (code.isBlank()) {
            _state.update { it.copy(error = "SMS kodini kiriting") }
            return
        }
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = controller.confirmOtp(code)) {
                is SocialAuthResult.Success -> syncAndFinish(result.user)
                SocialAuthResult.Cancelled -> _state.update { it.copy(isLoading = false) }
                is SocialAuthResult.Error ->
                    _state.update { it.copy(isLoading = false, error = result.message) }
            }
        }
    }

    private suspend fun syncAndFinish(external: ExternalAuthUser) {
        when (val synced = syncExternalUserUseCase(external)) {
            is Resource.Success ->
                _state.update { it.copy(isLoading = false, user = synced.data, otpSent = false) }
            is Resource.Error ->
                _state.update { it.copy(isLoading = false, error = synced.message) }
            Resource.Loading -> Unit
        }
    }

    private fun isValidPhone(phone: String): Boolean =
        phone.startsWith("+") && phone.drop(1).all { it.isDigit() } && phone.length in 8..16
}
