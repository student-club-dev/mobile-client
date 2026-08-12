package dev.feature.profile.domain.usecase

import dev.core.common.Resource
import dev.feature.profile.domain.model.ProfilePhoto
import dev.feature.profile.domain.model.UserProfile
import dev.feature.profile.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import dev.core.common.locale.AppLocale

/** Local keshdagi joriy profilni reaktiv kuzatadi (Home sarlavhasi, Profil ekrani...). */
class ObserveProfileUseCase(private val repository: ProfileRepository) {
    operator fun invoke(): Flow<UserProfile?> = repository.observeProfile()
}

/** Profilni saqlaydi (masofaviy manba + local kesh). */
class SaveProfileUseCase(private val repository: ProfileRepository) {
    suspend operator fun invoke(profile: UserProfile): Resource<Unit> =
        repository.saveProfile(profile)
}

/** Joriy sessiyada profil bor-yo'qligini tekshiradi (login/register yo'nalishini ajratish). */
class HasProfileUseCase(private val repository: ProfileRepository) {
    suspend operator fun invoke(): Boolean = repository.hasProfile()
}

/** Profilni masofaviy manbadan qayta yuklaydi (ekran ochilganda fon rejimida). */
class RefreshProfileUseCase(private val repository: ProfileRepository) {
    suspend operator fun invoke(): Resource<Unit> = repository.refresh()
}

/** Profil rasmlari to'plami — `GET /v1/profile/photos` (`handoff/08-PROFILE.md` §2). */
class ProfilePhotosUseCase(private val repository: ProfileRepository) {
    suspend operator fun invoke(): Resource<List<ProfilePhoto>> = repository.photos()
}

/**
 * Rasm qo'shadi. Yangi rasm **doim birinchi o'ringa** tushadi va avatar bo'ladi, ya'ni
 * "rasmni almashtirish" ham shu chaqiruv.
 *
 * Chegara serverdagi bilan bir xil — 12 MB (`kind=PROFILE_PHOTO`).
 */
class AddProfilePhotoUseCase(private val repository: ProfileRepository) {

    suspend operator fun invoke(
        bytes: ByteArray,
        fileName: String,
        /** Yuklash foizi (`0f..1f`) — ekrandagi halqa uchun. */
        onProgress: ((Float) -> Unit)? = null,
    ): Resource<ProfilePhoto> {
        if (bytes.isEmpty()) return Resource.Error(AppLocale.pick(en = "The image is empty", ru = "Изображение пустое", uz = "Rasm bo'sh"))
        if (bytes.size > MAX_PHOTO_BYTES) return Resource.Error(AppLocale.pick(en = "The image is too large (max 12 MB)", ru = "Изображение слишком большое (макс. 12 МБ)", uz = "Rasm juda katta (maks. 12 MB)"))
        return repository.addPhoto(bytes, fileName, onProgress)
    }

    companion object {
        /** `handoff/08-PROFILE.md` §2 — serverdagi chegara. */
        const val MAX_PHOTO_BYTES = 12 * 1024 * 1024
    }
}

/** Mavjud rasmni asosiy (avatar) qiladi. */
class SetMainProfilePhotoUseCase(private val repository: ProfileRepository) {
    suspend operator fun invoke(photoId: String): Resource<Unit> = repository.makeMainPhoto(photoId)
}

/** Rasmni o'chiradi; birinchisi o'chirilsa keyingisi avtomatik avatar bo'ladi. */
class DeleteProfilePhotoUseCase(private val repository: ProfileRepository) {
    suspend operator fun invoke(photoId: String): Resource<Unit> = repository.deletePhoto(photoId)
}

/**
 * Profil rasmini yuklaydi. Rasm hajmi [MAX_AVATAR_BYTES] dan oshsa server'ga bormaydi —
 * xatoni darrov qaytaradi (spec ham 5 MB chegara qo'yadi).
 *
 * ⚠️ **Eskirgan yo'l.** Profil rasmlari endi to'plam bo'lib boshqariladi
 * ([AddProfilePhotoUseCase]) va `avatarUrl` — hosila maydon. Bu klass faqat eski
 * chaqiruvlar uchun qoldirilgan.
 */
class UploadAvatarUseCase(private val repository: ProfileRepository) {

    suspend operator fun invoke(bytes: ByteArray, fileName: String): Resource<String> {
        if (bytes.isEmpty()) return Resource.Error(AppLocale.pick(en = "The image is empty", ru = "Изображение пустое", uz = "Rasm bo'sh"))
        if (bytes.size > MAX_AVATAR_BYTES) {
            return Resource.Error(AppLocale.pick(en = "The image is too large (max 5 MB)", ru = "Изображение слишком большое (макс. 5 МБ)", uz = "Rasm juda katta (maks. 5 MB)"))
        }
        return repository.uploadAvatar(bytes, fileName)
    }

    companion object {
        const val MAX_AVATAR_BYTES = 5 * 1024 * 1024
    }
}
