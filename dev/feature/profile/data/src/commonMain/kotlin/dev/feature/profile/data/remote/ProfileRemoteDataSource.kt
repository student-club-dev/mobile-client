package dev.feature.profile.data.remote

import dev.core.common.Resource
import dev.feature.profile.domain.model.UserProfile

/**
 * Profilning masofaviy manbasi. Yagona implementatsiyasi — [ApiProfileRemoteDataSource]
 * (real backend `/v1/profile/me`, OpenAPI'dan generatsiya qilingan klient).
 *
 * Repository qaysi manba ulanganini bilmaydi — u faqat shu interfeys bilan ishlaydi.
 */
interface ProfileRemoteDataSource {

    /** Profilni olib keladi. Profil hali yaratilmagan bo'lsa `Success(null)`. */
    suspend fun fetch(): Resource<UserProfile?>

    /** Profilni saqlaydi (upsert) va saqlangan holatini qaytaradi. */
    suspend fun save(profile: UserProfile): Resource<UserProfile>

    /** Masofaviy manbada profil mavjudmi (tarmoq xatosida `false`). */
    suspend fun exists(): Boolean

    /** Rasm faylini yuklab, uning ochiq URL manzilini qaytaradi (`POST /v1/media/upload`). */
    suspend fun uploadAvatar(bytes: ByteArray, fileName: String): Resource<String>
}
