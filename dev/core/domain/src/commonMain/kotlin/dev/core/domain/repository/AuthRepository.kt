package dev.core.domain.repository

import dev.core.common.Resource
import dev.core.domain.model.ExternalAuthUser
import dev.core.domain.model.User
import dev.core.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    /** Email + parol bilan kirish (Firebase Auth). */
    suspend fun login(email: String, password: String): Resource<User>

    /** Email + parol bilan yangi hisob yaratish (Firebase Auth). */
    suspend fun register(email: String, password: String): Resource<User>

    /** Parolni tiklash havolasini emailga yuboradi (Firebase Auth). */
    suspend fun sendPasswordReset(email: String): Resource<Unit>

    /** Email ro'yxatdan o'tish 1-qadam: emailga 6 xonali kod yuboradi (akkaunt hali yaratilmaydi). */
    suspend fun requestEmailSignup(email: String): Resource<Unit>

    /** 2-qadam: kod to'g'ri bo'lsa akkaunt yaratiladi va tizimga kiriladi. */
    suspend fun confirmEmailSignup(email: String, code: String, password: String): Resource<Unit>

    /**
     * Firebase (Google/Telefon) orqali autentifikatsiyadan o'tgan foydalanuvchini
     * domen [User] ga aylantiradi (joriy Firebase sessiyasidan).
     */
    suspend fun syncExternalUser(external: ExternalAuthUser): Resource<User>

    /** Joriy foydalanuvchining profilini Firestore'ga saqlaydi (`users/{uid}`). */
    suspend fun saveProfile(profile: UserProfile): Resource<Unit>

    /**
     * Joriy Firebase sessiyasi uchun Firestore'da profil hujjati (`users/{uid}`)
     * mavjudmi — telefon OTP'dan keyin login (profil bor) va register (profil yo'q)
     * oqimlarini ajratish uchun ishlatiladi.
     */
    suspend fun hasProfile(): Boolean

    suspend fun logout()

    /** Joriy foydalanuvchi (local keshdan, offline-first; sessiya bo'lmasa null). */
    suspend fun currentUser(): User?

    /**
     * Local kesh (SQLDelight) ustidan joriy foydalanuvchini reaktiv kuzatadi —
     * kesh yangilanganda/ tozalanganda avtomatik yangi qiymat chiqaradi.
     * Ilova ochilishida avtomatik kirish (session restore) uchun ishlatiladi.
     */
    fun observeCurrentUser(): Flow<User?>

    /** Local keshdagi joriy foydalanuvchi profili (universitet, kurs, rol...). */
    fun observeProfile(): Flow<UserProfile?>
}
