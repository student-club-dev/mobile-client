package dev.core.domain.usecase

import dev.core.common.Resource
import dev.core.domain.model.UserProfile
import dev.core.domain.repository.AuthRepository

/** Joriy foydalanuvchining profilini saqlash (Firestore). */
class SaveProfileUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(profile: UserProfile): Resource<Unit> =
        repository.saveProfile(profile)
}
