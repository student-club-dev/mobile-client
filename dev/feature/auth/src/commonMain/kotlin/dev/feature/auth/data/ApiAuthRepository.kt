package dev.feature.auth.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.core.common.Resource
import dev.core.common.auth.AuthTokens
import dev.core.common.auth.TokenStore
import dev.core.common.deviceName
import dev.core.common.error.AppException
import dev.core.common.errorOf
import dev.core.common.network.NetworkConnectivity
import dev.core.common.platformName
import dev.core.common.push.PushRegistrar
import dev.core.database.sql.StudentClubDatabase
import dev.core.database.sql.UserEntity
import dev.core.domain.model.AuthIdentifier
import dev.core.domain.model.DeviceSession
import dev.core.domain.model.OtpChallenge
import dev.core.domain.model.User
import dev.core.domain.model.UserRole
import dev.core.domain.repository.AuthRepository
import dev.core.network.generated.api.AuthStudentApi
import dev.core.network.generated.model.AuthTokensDto
import dev.core.network.generated.model.ForgotPasswordDto
import dev.core.network.generated.model.LoginDto
import dev.core.network.generated.model.LogoutDto
import dev.core.network.generated.model.OAuthLoginDto
import dev.core.network.generated.model.OtpRequestDto
import dev.core.network.generated.model.OtpVerifyDto
import dev.core.network.generated.model.RegisterDto
import dev.core.network.generated.model.ResetPasswordDto
import dev.core.network.generated.model.SessionDto
import dev.core.network.generated.model.SetPasswordDto
import dev.core.network.resetAuthTokenCache
import dev.core.network.response.safeCall
import dev.feature.profile.domain.repository.ProfileRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * [AuthRepository] ning **backend** implementatsiyasi — `/v1/auth/student/…`.
 *
 * Sessiya oqimi:
 * 1. `login`/`register` access + refresh juftligini qaytaradi;
 * 2. juftlik [TokenStore] ga yoziladi va Ktor'ning token keshi tozalanadi
 *    ([resetAuthTokenCache]) — aks holda klient eski (yoki yo'q) token bilan ishlayveradi;
 * 3. access-token JWT'sining `sub` maydoni — foydalanuvchi id'si;
 * 4. profil (`GET /profile/me`) tortiladi — ism/telefon/rasm shundan keladi;
 * 5. hammasi local `UserEntity` ga yoziladi, UI esa uni **reaktiv** kuzatadi (avtomatik kirish).
 *
 * Token muddati tugasa `refresh` avtomatik, tarmoq qatlamida bo'ladi — bu klass unga aralashmaydi.
 */
class ApiAuthRepository(
    private val api: AuthStudentApi,
    private val tokenStore: TokenStore,
    private val database: StudentClubDatabase,
    private val httpClient: HttpClient,
    private val connectivity: NetworkConnectivity,
    private val profileRepository: ProfileRepository,
    /**
     * Qurilma push tokenini sessiya bilan bog'laydi (`POST/DELETE /v1/devices`).
     * Auth qatlami push tafsilotlarini bilmaydi — faqat "sessiya ochildi/yopilmoqda"
     * signalini beradi. Implementatsiya `:dev:feature:notifications:data` da.
     */
    private val pushRegistrar: PushRegistrar = PushRegistrar.None,
) : AuthRepository {

    private val userQueries get() = database.userQueries

    /**
     * Ro'yxatdan o'tish boshlangan, lekin raqam hali tasdiqlanmagan foydalanuvchi
     * identifikatori. `completeRegistration()` uni sessiya qatoriga yozishda ishlatadi.
     */
    private var pendingIdentifier: AuthIdentifier? = null

    // ------------------------------------------------------------------
    // Kirish / ro'yxatdan o'tish
    // ------------------------------------------------------------------

    override suspend fun login(identifier: AuthIdentifier, password: String): Resource<User> =
        authenticate(identifier) {
            api.login(
                LoginDto(
                    password = password,
                    email = (identifier as? AuthIdentifier.Email)?.value,
                    phoneNumber = (identifier as? AuthIdentifier.Phone)?.value,
                    deviceName = deviceName,
                    platform = platformName,
                ),
            ).body()
        }

    /**
     * Hisob backendda darhol ochiladi (spec shunday), lekin ILOVA uchun sessiya hali
     * ochilmaydi: [authenticate] ga `persistSession = false` beramiz — tokenlar saqlanadi
     * (OTP so'rovlari uchun), local `UserEntity` esa YOZILMAYDI. Shuning uchun tasdiqlanmagan
     * foydalanuvchi ilovaga kira olmaydi ([completeRegistration] ni kuting).
     */
    override suspend fun register(identifier: AuthIdentifier, password: String): Resource<User> =
        authenticate(identifier, persistSession = false) {
            api.studentAuthRegister(
                RegisterDto(
                    password = password,
                    email = (identifier as? AuthIdentifier.Email)?.value,
                    phoneNumber = (identifier as? AuthIdentifier.Phone)?.value,
                    deviceName = deviceName,
                    platform = platformName,
                ),
            ).body()
        }

    override suspend fun completeRegistration(): Resource<User> {
        val uid = tokenStore.userId()
            ?: return errorOf(AppException.Server(cause = IllegalStateException("Kutilayotgan sessiya yo'q")))
        val user = cacheSession(uid, pendingIdentifier ?: AuthIdentifier.Phone(""))
        pendingIdentifier = null
        return Resource.Success(user)
    }

    override suspend fun cancelPendingRegistration() {
        pendingIdentifier = null
        logout()
    }

    override suspend fun loginWithGoogle(idToken: String): Resource<User> =
        // Identifikator Google'dan emas, backenddan (token → profil) keladi; shuning uchun
        // bo'sh email beramiz — profil refresh haqiqiy email/nomni to'ldiradi.
        authenticate(AuthIdentifier.Email("")) {
            api.googleOAuth(
                OAuthLoginDto(
                    idToken = idToken,
                    deviceName = deviceName,
                    platform = platformName,
                ),
            ).body()
        }

    /**
     * Umumiy qism: tokenlarni saqlash → profil → local sessiya.
     *
     * [persistSession] `false` bo'lsa oxirgi qadam (local `UserEntity`) BAJARILMAYDI —
     * ro'yxatdan o'tish oqimida sessiya SMS kod tasdiqlanguncha "kutilmoqda" holatida turadi.
     */
    private suspend fun authenticate(
        identifier: AuthIdentifier,
        persistSession: Boolean = true,
        call: suspend () -> AuthTokensDto,
    ): Resource<User> = when (val tokens = safeCall(connectivity) { call() }) {
        is Resource.Error -> tokens
        Resource.Loading -> errorOf(AppException.Unknown())
        is Resource.Success -> {
            val uid = JwtClaims.subject(tokens.data.accessToken)
            if (uid == null) {
                errorOf(AppException.Server(cause = IllegalStateException("Tokenda `sub` yo'q")))
            } else {
                tokenStore.save(
                    AuthTokens(tokens.data.accessToken, tokens.data.refreshToken),
                    userId = uid,
                )
                httpClient.resetAuthTokenCache()
                if (persistSession) {
                    val user = cacheSession(uid, identifier)
                    // Sessiya tayyor — endi push tokenini bog'lash mumkin (so'rov `Bearer`
                    // talab qiladi). Xato bo'lsa ham kirish davom etadi.
                    runCatching { pushRegistrar.onSessionStarted() }
                    Resource.Success(user)
                } else {
                    // Kutilayotgan ro'yxat — identifikatorni eslab qolamiz, local sessiya
                    // faqat `completeRegistration()` da yoziladi.
                    pendingIdentifier = identifier
                    Resource.Success(pendingUser(uid, identifier))
                }
            }
        }
    }

    /** Hali keshga yozilmagan (tasdiqlanmagan) foydalanuvchi — faqat oqim davomida ishlatiladi. */
    private fun pendingUser(uid: String, identifier: AuthIdentifier) = User(
        id = uid,
        fullName = "",
        email = (identifier as? AuthIdentifier.Email)?.value.orEmpty(),
        role = UserRole.STUDENT,
        phoneNumber = (identifier as? AuthIdentifier.Phone)?.value,
        photoUrl = null,
    )

    /**
     * Profilni backenddan tortib local sessiya qatorini yozadi. Profil kelmasa (yangi hisob
     * yoki tarmoq muammosi) sessiya baribir ochiladi — foydalanuvchi ilovaga kiradi va
     * profilni keyin to'ldiradi.
     */
    private suspend fun cacheSession(uid: String, identifier: AuthIdentifier): User {
        runCatching { profileRepository.refresh() }
        val profile = runCatching { profileRepository.observeProfile().first() }.getOrNull()

        val user = User(
            id = uid,
            fullName = profile?.displayName.orEmpty(),
            email = profile?.email
                ?: (identifier as? AuthIdentifier.Email)?.value.orEmpty(),
            role = profile?.role?.let { role -> UserRole.entries.firstOrNull { it.name == role } }
                ?: UserRole.STUDENT,
            phoneNumber = profile?.phoneNumber ?: (identifier as? AuthIdentifier.Phone)?.value,
            photoUrl = profile?.avatarUrl,
        )
        userQueries.transaction {
            userQueries.clear()
            userQueries.upsert(
                uid = user.id,
                fullName = user.fullName,
                email = user.email,
                role = user.role.name,
                phoneNumber = user.phoneNumber,
                photoUrl = user.photoUrl,
            )
        }
        return user
    }

    // ------------------------------------------------------------------
    // Chiqish va sessiya holati
    // ------------------------------------------------------------------

    override suspend fun logout() {
        // Push tokenini AVVAL uzamiz — tokenlar tozalangandan keyin so'rov `401` bo'lardi
        // va qurilma serverda "faol" bo'lib qolib, chiqqan foydalanuvchiga push kelaverardi.
        runCatching { pushRegistrar.onSessionEnding() }
        // Refresh tokenni serverda bekor qilamiz. Tarmoq bo'lmasa ham local sessiya tozalanadi:
        // foydalanuvchi "chiqdim" degan bo'lsa, ilova uni ushlab turmasligi kerak.
        tokenStore.tokens()?.refreshToken?.let { refresh ->
            runCatching { api.logout(LogoutDto(refreshToken = refresh)) }
        }
        clearLocalSession()
    }

    /**
     * Sessiyaga tegishli hamma narsani o'chiradi. Ilova sozlamalari (mavzu, tanishtiruv
     * ko'rilgani) SAQLANADI — chiqishdan keyin foydalanuvchi tanishtiruvga emas, kirish
     * ekraniga tushishi kerak.
     */
    private fun clearLocalSession() {
        tokenStore.clear()
        httpClient.resetAuthTokenCache()
        userQueries.clear()
        database.profileQueries.clear()
    }

    override suspend fun currentUser(): User? =
        userQueries.selectCurrent().executeAsOneOrNull()?.toDomainUser()

    override fun observeCurrentUser(): Flow<User?> =
        userQueries.selectCurrent()
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { it?.toDomainUser() }

    // ------------------------------------------------------------------
    // Telefonni tasdiqlash
    // ------------------------------------------------------------------

    override suspend fun requestPhoneOtp(phone: String): Resource<OtpChallenge> =
        safeCall(connectivity) {
            val result = api.request(OtpRequestDto(phoneNumber = phone)).body()
            OtpChallenge(
                expiresInSeconds = result.expiresInSeconds,
                resendCooldownSeconds = result.resendCooldownSeconds,
            )
        }

    override suspend fun verifyPhoneOtp(phone: String, code: String): Resource<Unit> =
        when (
            val result = safeCall(connectivity) {
                api.verify(OtpVerifyDto(phoneNumber = phone, code = code)).body()
            }
        ) {
            is Resource.Error -> result
            Resource.Loading -> errorOf(AppException.Unknown())
            is Resource.Success ->
                if (result.data.verified) Resource.Success(Unit)
                else errorOf(AppException.Validation("Kod noto'g'ri yoki muddati o'tgan."))
        }

    // ------------------------------------------------------------------
    // Parol
    // ------------------------------------------------------------------

    override suspend fun forgotPassword(phone: String): Resource<Unit> = safeCall(connectivity) {
        api.forgot(ForgotPasswordDto(phoneNumber = phone))
        Unit
    }

    override suspend fun resetPassword(
        phone: String,
        code: String,
        newPassword: String,
    ): Resource<Unit> = when (
        val result = safeCall(connectivity) {
            api.reset(
                ResetPasswordDto(phoneNumber = phone, code = code, newPassword = newPassword),
            ).body()
        }
    ) {
        is Resource.Error -> result
        Resource.Loading -> errorOf(AppException.Unknown())
        is Resource.Success ->
            if (result.data.reset) Resource.Success(Unit)
            else errorOf(AppException.Validation("Parolni tiklab bo'lmadi. Kodni tekshiring."))
    }

    override suspend fun setPassword(currentPassword: String?, newPassword: String): Resource<Unit> =
        safeCall(connectivity) {
            api.set(
                SetPasswordDto(newPassword = newPassword, currentPassword = currentPassword),
            )
            Unit
        }

    // ------------------------------------------------------------------
    // Faol qurilmalar
    // ------------------------------------------------------------------

    override suspend fun sessions(): Resource<List<DeviceSession>> = safeCall(connectivity) {
        api.studentSessionsList().body().map { it.toDomain() }
    }

    override suspend fun revokeSession(id: String): Resource<Unit> = safeCall(connectivity) {
        api.revoke(id)
        Unit
    }

    override suspend fun logoutAllDevices(): Resource<Unit> {
        val result = safeCall(connectivity) {
            api.logoutAll()
            Unit
        }
        // Joriy qurilma ham chiqarildi — local sessiyani ushlab turishning ma'nosi yo'q.
        if (result is Resource.Success) clearLocalSession()
        return result
    }
}

// ---------------------------------------------------------------------------
// Mapper'lar
// ---------------------------------------------------------------------------

private fun UserEntity.toDomainUser(): User = User(
    id = uid,
    fullName = fullName,
    email = email,
    role = runCatching { UserRole.valueOf(role) }.getOrDefault(UserRole.STUDENT),
    phoneNumber = phoneNumber,
    photoUrl = photoUrl,
)

private fun SessionDto.toDomain() = DeviceSession(
    id = id,
    deviceName = deviceName,
    platform = platform,
    ipAddress = ipAddress,
    lastUsedAtMillis = lastUsedAt?.toEpochMilliseconds(),
    createdAtMillis = createdAt.toEpochMilliseconds(),
)
