package dev.feature.auth.data

import dev.core.domain.model.UserProfile
import kotlinx.serialization.Serializable

/** Firestore `users/{uid}` hujjati uchun serializatsiya modeli. */
@Serializable
data class ProfileDto(
    val firstName: String? = null,
    val lastName: String? = null,
    val phoneNumber: String? = null,
    val role: String? = null,
    val universityId: String? = null,
    val universityEmail: String? = null,
    val birthYear: Int? = null,
    val courseYear: String? = null,
) {
    fun toDomain(): UserProfile = UserProfile(
        firstName = firstName,
        lastName = lastName,
        phoneNumber = phoneNumber,
        role = role,
        universityId = universityId,
        universityEmail = universityEmail,
        birthYear = birthYear,
        courseYear = courseYear,
    )

    companion object {
        fun from(p: UserProfile): ProfileDto = ProfileDto(
            firstName = p.firstName,
            lastName = p.lastName,
            phoneNumber = p.phoneNumber,
            role = p.role,
            universityId = p.universityId,
            universityEmail = p.universityEmail,
            birthYear = p.birthYear,
            courseYear = p.courseYear,
        )
    }
}
