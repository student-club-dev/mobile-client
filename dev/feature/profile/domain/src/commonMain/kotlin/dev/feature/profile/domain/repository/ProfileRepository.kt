package dev.feature.profile.domain.repository

import dev.core.common.Resource
import dev.feature.profile.domain.model.ProfilePhoto
import dev.feature.profile.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * Profil ma'lumotiga egalik qiluvchi repository (offline-first).
 *
 * UI **har doim** [observeProfile] (local DB) ni kuzatadi; [refresh] va [saveProfile]
 * masofaviy manbadan olib/unga yozib, local keshni yangilaydi. Shu sabab tarmoq
 * bo'lmasa ham profil ko'rinadi.
 */
interface ProfileRepository {

    /** Local keshdagi joriy profil (sessiya yo'q bo'lsa `null`). */
    fun observeProfile(): Flow<UserProfile?>

    /** Masofaviy manbadan profilni olib local keshga yozadi. */
    suspend fun refresh(): Resource<Unit>

    /** Profilni masofaviy manbaga va local keshga saqlaydi. */
    suspend fun saveProfile(profile: UserProfile): Resource<Unit>

    /**
     * Profil rasmini yuklaydi va uning URL manzilini profilga yozib, keshni yangilaydi.
     * Qaytaradi: yuklangan rasmning URL manzili.
     *
     * @param bytes rasm fayli (JPEG/PNG)
     * @param fileName original fayl nomi (kengaytmani aniqlash uchun)
     */
    suspend fun uploadAvatar(bytes: ByteArray, fileName: String): Resource<String>

    /**
     * Profil rasmlari — `GET /v1/profile/photos`, tartib bo'yicha.
     *
     * Birinchi element **doim** avatar (`UserProfile.avatarUrl` bilan bir xil).
     */
    suspend fun photos(): Resource<List<ProfilePhoto>>

    /**
     * Rasm qo'shadi: `POST /v1/media/chat-upload?kind=PROFILE_PHOTO` → `POST /v1/profile/photos`.
     *
     * Yangi rasm **doim birinchi o'ringa** tushadi, ya'ni "rasmni almashtirish" — bitta
     * chaqiruv, alohida "asosiy qilish" kerak emas (`handoff/08-PROFILE.md` §2).
     *
     * 6 tadan oshsa `422 PHOTO_LIMIT_REACHED`.
     *
     * [onProgress] — yuklash foizi (`0f..1f`). Faqat fayl ketayotgan qismni qamraydi:
     * undan keyingi `POST /v1/profile/photos` va profilni yangilash foizsiz o'tadi.
     */
    suspend fun addPhoto(
        bytes: ByteArray,
        fileName: String,
        onProgress: ((Float) -> Unit)? = null,
    ): Resource<ProfilePhoto>

    /** Mavjud rasmni asosiy (avatar) qiladi — `PUT /v1/profile/photos/{id}/main`. */
    suspend fun makeMainPhoto(photoId: String): Resource<Unit>

    /**
     * O'chiradi. Birinchisi o'chirilsa **keyingisi avtomatik avatar** bo'ladi; oxirgisi
     * o'chirilsa `avatarUrl = null` va bosh harfga tushiladi.
     */
    suspend fun deletePhoto(photoId: String): Resource<Unit>

    /**
     * Joriy sessiyada saqlangan profil bormi. Telefon OTP oqimida login
     * (profil bor → HOME) va register (profil yo'q → SignUp) yo'nalishlarini ajratadi.
     */
    suspend fun hasProfile(): Boolean
}
