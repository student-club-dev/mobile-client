package dev.core.domain.repository

import dev.core.common.Resource
import dev.core.domain.model.AuthIdentifier
import dev.core.domain.model.DeviceSession
import dev.core.domain.model.OtpChallenge
import dev.core.domain.model.User
import kotlinx.coroutines.flow.Flow

/**
 * Sessiya (autentifikatsiya) repository'si — backend `/v1/auth/student/…` endpoint'lari.
 *
 * Oqim spec'ga to'liq mos:
 * - **kirish/ro'yxat** — telefon yoki email + parol → access/refresh juftligi;
 * - **SMS kod** — kirish usuli EMAS: u faqat raqamni tasdiqlash ([requestPhoneOtp]) va
 *   parolni tiklash ([forgotPassword] → [resetPassword]) uchun;
 * - **token yangilash** — avtomatik, tarmoq qatlamida (`createHttpClient`).
 *
 * Profil ma'lumoti (universitet, kurs, rol...) bu yerda EMAS — unga
 * `dev.feature.profile.domain.repository.ProfileRepository` egalik qiladi.
 */
interface AuthRepository {

    /** Telefon yoki email + parol bilan kirish (`POST /auth/student/login`). */
    suspend fun login(identifier: AuthIdentifier, password: String): Resource<User>

    /**
     * Ro'yxatdan o'tish uchun raqamga SMS kod (`POST /auth/student/register/otp`).
     *
     * ⚠️ Bu [requestPhoneOtp] EMAS va ikkalasining kodi bir-biriga yaramaydi (server
     * ularni Redis'da alohida fazoda saqlaydi):
     *
     * | | `otp/request` | `register/otp` |
     * |---|---|---|
     * | Token | kerak | **kerak emas** |
     * | Qachon | hisob BOR, raqamni tasdiqlash | hisob YO'Q, ro'yxatdan oldin |
     *
     * Chaqiruv tartibi shu sabab teskari: avval kod, keyin [register]. Hisob kod
     * tekshirilmaguncha UMUMAN yaratilmaydi.
     */
    suspend fun requestRegistrationOtp(phone: String): Resource<OtpChallenge>

    /**
     * Yangi talaba hisobi (`POST /auth/student/register`).
     *
     * [otpCode] — [requestRegistrationOtp] bergan kod. **`phoneNumber` yuborilganda
     * majburiy** (aks holda `422`): raqam bazada `@unique`, ya'ni uni tasdiqsiz band qilgan
     * ro'yxat raqamning haqiqiy egasini butunlay tashqarida qoldirardi. Email bilan
     * ro'yxatdan o'tishda kerak emas.
     *
     * Sessiya **kutilmoqda** holatida ochiladi: tokenlar saqlanadi, lekin local
     * foydalanuvchi qatori YOZILMAYDI. Ya'ni ilova o'chib qayta ochilsa kirish ekraniga
     * tushadi. Profil saqlangach [completeRegistration] chaqiriladi.
     */
    suspend fun register(
        identifier: AuthIdentifier,
        password: String,
        otpCode: String? = null,
    ): Resource<User>

    /**
     * Ro'yxatni yakunlaydi — [register] muvaffaqiyatli o'tgandan KEYIN chaqiriladi:
     * local sessiya qatori yoziladi (endi foydalanuvchi kirgan hisoblanadi).
     */
    suspend fun completeRegistration(): Resource<User>

    /**
     * Yarim qolgan ro'yxatni bekor qiladi (foydalanuvchi kod ekranidan chiqib ketdi) —
     * tokenlar bekor qilinadi va o'chiriladi.
     *
     * Kod ekranidan chiqilganda serverda hech narsa qolmaydi: hisob endi kod
     * tekshirilgandan KEYIN yaratiladi. Bu chaqiruv esa [register] o'tib, lekin profil
     * saqlash yoki [completeRegistration] yiqilgan holat uchun kerak.
     */
    suspend fun cancelPendingRegistration()

    /**
     * Google bilan kirish (`POST /auth/student/oauth/google`). [idToken] — Google Sign-In
     * bergan ID token; backend uni tekshirib access/refresh juftligini qaytaradi.
     */
    suspend fun loginWithGoogle(idToken: String): Resource<User>

    /** Chiqish — refresh token bekor qilinadi, local sessiya va profil keshi tozalanadi. */
    suspend fun logout()

    /** Joriy foydalanuvchi (local keshdan, offline-first; sessiya bo'lmasa `null`). */
    suspend fun currentUser(): User?

    /**
     * Local kesh ustidan joriy foydalanuvchini reaktiv kuzatadi — kesh yangilanganda/
     * tozalanganda avtomatik yangi qiymat chiqadi. Ilova ochilishida avtomatik kirish
     * (session restore) uchun ishlatiladi.
     */
    fun observeCurrentUser(): Flow<User?>

    // --- Telefon raqamini tasdiqlash (hisob ALLAQACHON bor) --------------------------
    //
    // Ro'yxatdan o'tish oqimi bu ikkisini ISHLATMAYDI — u `register/otp` dan foydalanadi.
    // Bular email/Google bilan ochilgan hisobga keyinchalik raqam qo'shish uchun.

    /** Raqamga SMS kod yuboradi (`POST /auth/student/otp/request`, Bearer talab qiladi). */
    suspend fun requestPhoneOtp(phone: String): Resource<OtpChallenge>

    /** Kodni tekshiradi va raqamni tasdiqlangan deb belgilaydi. */
    suspend fun verifyPhoneOtp(phone: String, code: String): Resource<Unit>

    // --- Parol ----------------------------------------------------------------------

    /** Parolni tiklash uchun SMS kod so'raydi (`POST /auth/student/password/forgot`). */
    suspend fun forgotPassword(phone: String): Resource<Unit>

    /** SMS kod bilan yangi parol o'rnatadi (`POST /auth/student/password/reset`). */
    suspend fun resetPassword(phone: String, code: String, newPassword: String): Resource<Unit>

    /**
     * Kirgan holatda parolni o'rnatadi/almashtiradi (`POST /auth/student/password/set`).
     * [currentPassword] faqat mavjud parolni almashtirishda kerak.
     */
    suspend fun setPassword(currentPassword: String?, newPassword: String): Resource<Unit>

    // --- Faol qurilmalar -------------------------------------------------------------

    /** Hisobning faol sessiyalari (`GET /auth/student/sessions`). */
    suspend fun sessions(): Resource<List<DeviceSession>>

    /** Bitta qurilma sessiyasini bekor qiladi. */
    suspend fun revokeSession(id: String): Resource<Unit>

    /** Barcha qurilmalardan chiqadi (joriysi ham). */
    suspend fun logoutAllDevices(): Resource<Unit>
}
