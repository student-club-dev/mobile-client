package dev.feature.auth.data

import dev.core.common.Resource
import dev.core.domain.model.ExternalAuthUser
import dev.core.domain.model.User
import dev.core.domain.model.UserProfile
import dev.core.domain.model.UserRole
import dev.core.domain.repository.AuthRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuthInvalidCredentialsException
import dev.gitlive.firebase.auth.FirebaseAuthInvalidUserException
import dev.gitlive.firebase.auth.FirebaseAuthUserCollisionException
import dev.gitlive.firebase.auth.FirebaseAuthWeakPasswordException
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.functions.functions

/**
 * [AuthRepository] ning GitLive Firebase (backendsiz) implementatsiyasi.
 *
 * - Email/parol, ro'yxat, parolni tiklash → **Firebase Auth** (commonMain).
 * - Profil (rol, universitet, kurs...) → **Cloud Firestore** `users/{uid}`.
 * - Google/Telefon → platformaga bog'liq `SocialAuthController` Firebase'ga kiritadi,
 *   bu yerdagi [syncExternalUser] esa o'sha sessiyani o'qib domen [User] qaytaradi.
 *
 * GitLive `Firebase.auth`/`Firebase.firestore` platformadagi bir xil Firebase
 * singleton ustida ishlaydi — shu sabab native controller kiritgan sessiya bu yerda
 * ham ko'rinadi.
 */
class FirebaseAuthRepository : AuthRepository {

    private val auth get() = Firebase.auth
    private val db get() = Firebase.firestore
    private val fns get() = Firebase.functions

    override suspend fun login(email: String, password: String): Resource<User> = try {
        val user = auth.signInWithEmailAndPassword(email, password).user
            ?: return Resource.Error("Foydalanuvchi topilmadi")
        Resource.Success(user.toDomainUser(loadProfile(user.uid)))
    } catch (e: Exception) {
        Resource.Error(mapError(e), e)
    }

    override suspend fun register(email: String, password: String): Resource<User> = try {
        val user = auth.createUserWithEmailAndPassword(email, password).user
            ?: return Resource.Error("Hisob yaratilmadi")
        Resource.Success(user.toDomainUser(null))
    } catch (e: Exception) {
        Resource.Error(mapError(e), e)
    }

    override suspend fun sendPasswordReset(email: String): Resource<Unit> = try {
        auth.sendPasswordResetEmail(email)
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(mapError(e), e)
    }

    override suspend fun requestEmailSignup(email: String): Resource<Unit> = try {
        fns.httpsCallable("requestEmailSignup").invoke(mapOf("email" to email))
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.message ?: "Kod yuborilmadi", e)
    }

    override suspend fun confirmEmailSignup(email: String, code: String, password: String): Resource<Unit> = try {
        // Server kodni tekshirib akkaunt yaratadi (emailVerified=true)
        fns.httpsCallable("confirmEmailSignup")
            .invoke(mapOf("email" to email, "code" to code, "password" to password))
        // Endi email/parol bilan kiramiz
        auth.signInWithEmailAndPassword(email, password)
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.message ?: "Tasdiqlash amalga oshmadi", e)
    }

    override suspend fun syncExternalUser(external: ExternalAuthUser): Resource<User> {
        val user = auth.currentUser
            ?: return Resource.Error("Firebase sessiyasi topilmadi")
        return Resource.Success(user.toDomainUser(loadProfile(user.uid)))
    }

    override suspend fun saveProfile(profile: UserProfile): Resource<Unit> {
        val uid = auth.currentUser?.uid ?: return Resource.Error("Sessiya topilmadi — avval kiring")
        return try {
            db.collection("users").document(uid)
                .set(ProfileDto.serializer(), ProfileDto.from(profile), merge = true)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(mapError(e), e)
        }
    }

    override suspend fun logout() {
        auth.signOut()
    }

    override suspend fun currentUser(): User? {
        val user = auth.currentUser ?: return null
        return user.toDomainUser(loadProfile(user.uid))
    }

    // ------------------------------------------------------------------
    private suspend fun loadProfile(uid: String): UserProfile? = try {
        val snapshot = db.collection("users").document(uid).get()
        if (snapshot.exists) snapshot.data(ProfileDto.serializer()).toDomain() else null
    } catch (e: Exception) {
        null
    }

    private fun FirebaseUser.toDomainUser(profile: UserProfile?): User {
        val profileName = listOfNotNull(profile?.firstName, profile?.lastName)
            .joinToString(" ")
            .ifBlank { null }
        return User(
            id = uid.hashCode().toLong() and 0xffffffffL,
            fullName = profileName
                ?: displayName
                ?: email?.substringBefore('@')
                ?: phoneNumber
                ?: "Foydalanuvchi",
            email = email.orEmpty(),
            role = UserRole.STUDENT,
            phoneNumber = phoneNumber,
            photoUrl = photoURL,
        )
    }

    private fun mapError(e: Throwable): String = when (e) {
        is FirebaseAuthWeakPasswordException -> "Parol juda oddiy (kamida 6 belgi)."
        is FirebaseAuthInvalidCredentialsException -> "Email yoki parol noto‘g‘ri."
        is FirebaseAuthInvalidUserException -> "Bunday foydalanuvchi topilmadi."
        is FirebaseAuthUserCollisionException -> "Bu email allaqachon ro‘yxatdan o‘tgan."
        else -> e.message ?: "Xatolik yuz berdi."
    }
}
