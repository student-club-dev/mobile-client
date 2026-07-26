package dev.feature.profile.data.remote

import dev.core.common.Resource
import dev.core.common.map
import dev.core.network.generated.api.ProfileApi
import dev.core.network.media.MediaPurpose
import dev.core.network.media.MediaUploader
import dev.core.network.response.safeCall
import dev.feature.profile.data.mapper.toDomain
import dev.feature.profile.data.mapper.toUpdateRequest
import dev.feature.profile.domain.model.UserProfile
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode

/**
 * Real backend (`GET/PUT /v1/profile/me`) — spetsifikatsiya:
 * `dev/api-client-generator/student-club.json`, klient shundan generatsiya qilingan.
 *
 * API klientiga ilovaning umumiy Ktor klienti uzatiladi, shuning uchun har so'rovga sessiya
 * tokeni `Authorization: Bearer ...` sifatida avtomatik qo'shiladi va muddati tugasa
 * avtomatik yangilanadi.
 */
class ApiProfileRemoteDataSource(
    private val api: ProfileApi,
    private val media: MediaUploader,
) : ProfileRemoteDataSource {

    override suspend fun fetch(): Resource<UserProfile?> = try {
        Resource.Success(api.getMe().body().toDomain())
    } catch (e: ClientRequestException) {
        // 404 — profil hali yaratilmagan; bu xato emas, shunchaki bo'sh profil.
        if (e.response.status == HttpStatusCode.NotFound) Resource.Success(null)
        else Resource.Error(e.message, e)
    } catch (e: Exception) {
        Resource.Error(e.message ?: "Profilni yuklab bo'lmadi", e)
    }

    /**
     * `safeCall` orqali — u 422 javob tanasini o'qib `AppException.Validation.fields` ni
     * to'ldiradi. O'z `try/catch` imiz bo'lsa maydon xatolari yo'qolardi.
     */
    override suspend fun save(profile: UserProfile): Resource<UserProfile> =
        safeCall { api.updateMe(profile.toUpdateRequest()).body() }.map { it.toDomain() }

    override suspend fun exists(): Boolean =
        (fetch() as? Resource.Success)?.data != null

    override suspend fun uploadAvatar(bytes: ByteArray, fileName: String): Resource<String> =
        safeCall {
            // Profil rasmi uchun alohida endpoint yo'q — umumiy `POST /v1/media/upload`
            // ishlatiladi, qaytgan URL esa `PUT /profile/me` orqali `avatarUrl` ga yoziladi
            // (buni repository qiladi). Spec'da `purpose` = LOGO | COVER | LISTING; avatar
            // uchun eng yaqini — LOGO.
            media.upload(bytes, fileName, MediaPurpose.LOGO).url
        }
}
