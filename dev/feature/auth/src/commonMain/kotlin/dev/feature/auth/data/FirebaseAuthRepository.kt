package dev.feature.auth.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.core.common.Resource
import dev.core.database.sql.StudentClubsDatabase
import dev.core.database.sql.UserEntity
import dev.core.domain.model.ExternalAuthUser
import dev.core.domain.model.User
import dev.core.domain.model.UserProfile
import dev.core.domain.model.UserRole
import dev.core.domain.repository.AuthRepository
import dev.gitlive.firebase.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
class FirebaseAuthRepository(
    /** Local sessiya keshi (SQLDelight) — offline ishlash va avtomatik kirish uchun. */
    private val database: StudentClubsDatabase,
) : AuthRepository {

    private val auth get() = Firebase.auth
    private val db get() = Firebase.firestore
    private val fns get() = Firebase.functions
    private val userQueries get() = database.userQueries

    override suspend fun login(email: String, password: String): Resource<User> = try {
        val fbUser = auth.signInWithEmailAndPassword(email, password).user
            ?: return Resource.Error("Foydalanuvchi topilmadi")
        val profile = loadProfile(fbUser.uid)
        val user = fbUser.toDomainUser(profile)
        cacheUser(fbUser.uid, user, profile)
        Resource.Success(user)
    } catch (e: Exception) {
        Resource.Error(mapError(e), e)
    }

    override suspend fun register(email: String, password: String): Resource<User> = try {
        val fbUser = auth.createUserWithEmailAndPassword(email, password).user
            ?: return Resource.Error("Hisob yaratilmadi")
        val user = fbUser.toDomainUser(null)
        cacheUser(fbUser.uid, user, null)
        Resource.Success(user)
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
        val fbUser = auth.currentUser
            ?: return Resource.Error("Firebase sessiyasi topilmadi")
        val profile = loadProfile(fbUser.uid)
        val user = fbUser.toDomainUser(profile)
        cacheUser(fbUser.uid, user, profile)
        return Resource.Success(user)
    }

    override suspend fun saveProfile(profile: UserProfile): Resource<Unit> {
        val fbUser = auth.currentUser ?: return Resource.Error("Sessiya topilmadi — avval kiring")
        return try {
            db.collection("users").document(fbUser.uid)
                .set(ProfileDto.serializer(), ProfileDto.from(profile), merge = true)
            // Local keshni yangilaymiz — offline'da ham profil ko'rinadi
            cacheUser(fbUser.uid, fbUser.toDomainUser(profile), profile)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(mapError(e), e)
        }
    }

    override suspend fun hasProfile(): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        return try {
            db.collection("users").document(uid).get().exists
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun logout() {
        auth.signOut()
        userQueries.clear() // local sessiya keshini tozalaymiz
    }

    override suspend fun currentUser(): User? {
        val fbUser = auth.currentUser
        if (fbUser == null) {
            userQueries.clear() // sessiya yo'q — eskirgan keshni tozalaymiz
            return null
        }
        // Offline-first: kesh bo'lsa darrov qaytaramiz (tarmoqsiz ishlaydi)
        cachedUser()?.let { return it }
        // Kesh bo'sh — Firebase/Firestore'dan tiklab keshlaymiz
        val profile = loadProfile(fbUser.uid)
        val user = fbUser.toDomainUser(profile)
        cacheUser(fbUser.uid, user, profile)
        return user
    }

    override fun observeCurrentUser(): Flow<User?> =
        userQueries.selectCurrent()
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { it?.toDomainUser() }

    override fun observeProfile(): Flow<UserProfile?> =
        userQueries.selectCurrent()
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { it?.toProfile() }

    // ------------------------------------------------------------------
    // Local kesh (SQLDelight)
    // ------------------------------------------------------------------
    private fun cachedUser(): User? =
        userQueries.selectCurrent().executeAsOneOrNull()?.toDomainUser()

    /** Bitta joriy-foydalanuvchi qatorini yozadi (avval eskisini o'chirib). */
    private fun cacheUser(uid: String, user: User, profile: UserProfile?) {
        userQueries.transaction {
            userQueries.clear()
            userQueries.upsert(
                uid = uid,
                userId = user.id,
                fullName = user.fullName,
                email = user.email,
                role = user.role.name,
                phoneNumber = user.phoneNumber,
                photoUrl = user.photoUrl,
                firstName = profile?.firstName,
                lastName = profile?.lastName,
                universityId = profile?.universityId,
                universityEmail = profile?.universityEmail,
                birthYear = profile?.birthYear?.toLong(),
                courseYear = profile?.courseYear,
                profileRole = profile?.role,
            )
        }
    }

    private fun UserEntity.toDomainUser(): User = User(
        id = userId,
        fullName = fullName,
        email = email,
        role = runCatching { UserRole.valueOf(role) }.getOrDefault(UserRole.STUDENT),
        phoneNumber = phoneNumber,
        photoUrl = photoUrl,
    )

    private fun UserEntity.toProfile(): UserProfile = UserProfile(
        firstName = firstName,
        lastName = lastName,
        phoneNumber = phoneNumber,
        role = profileRole,
        universityId = universityId,
        universityEmail = universityEmail,
        birthYear = birthYear?.toInt(),
        courseYear = courseYear,
    )

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
