package dev.core.domain.model

/**
 * Foydalanuvchining qo'shimcha profil ma'lumotlari — Firebase Auth bermaydigan maydonlar.
 * Backendsiz holatda Cloud Firestore'da `users/{uid}` hujjatida saqlanadi.
 */
data class UserProfile(
    val firstName: String? = null,
    val lastName: String? = null,
    val phoneNumber: String? = null,   // E.164, masalan "+998901234567"
    val role: String? = null,          // "STUDENT" | "BUSINESS" | "EMPLOYER" | "UNIVERSITY"
    val universityId: String? = null,
    val universityEmail: String? = null,
    val birthYear: Int? = null,
    val courseYear: String? = null,    // "1".."4" | "MASTER"
)
