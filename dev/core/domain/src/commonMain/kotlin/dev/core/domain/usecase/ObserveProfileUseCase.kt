package dev.core.domain.usecase

import dev.core.domain.model.UserProfile
import dev.core.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow

/** Local keshdagi joriy foydalanuvchi profilini reaktiv kuzatadi (Home header va h.k.). */
class ObserveProfileUseCase(private val repository: AuthRepository) {
    operator fun invoke(): Flow<UserProfile?> = repository.observeProfile()
}
