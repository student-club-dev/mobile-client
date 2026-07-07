package dev.core.domain.usecase

import dev.core.domain.repository.AuthRepository

/**
 * Joriy foydalanuvchida saqlangan profil bor-yo'qligini tekshiradi.
 * Telefon OTP oqimida login (profil bor → HOME) va register (profil yo'q → SignUp)
 * yo'nalishlarini ajratish uchun ishlatiladi.
 */
class HasProfileUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(): Boolean = repository.hasProfile()
}
