package dev.feature.profile.data.remote

import dev.core.common.Resource
import dev.core.common.map
import dev.core.network.generated.api.ProfileApi
import dev.core.network.generated.model.AddProfilePhotoDto
import dev.core.network.media.ChatMediaKind
import dev.core.network.media.MediaPurpose
import dev.core.network.media.MediaUploader
import dev.core.network.response.safeCall
import dev.feature.profile.data.mapper.toDomain
import dev.feature.profile.data.mapper.toUpdateRequest
import dev.feature.profile.domain.model.ProfilePhoto
import dev.feature.profile.domain.model.UserProfile
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import dev.core.common.locale.AppLocale

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
        Resource.Error(e.message ?: AppLocale.pick(en = "Couldn't load the profile", ru = "Не удалось загрузить профиль", uz = "Profilni yuklab bo'lmadi"), e)
    }

    /**
     * `safeCall` orqali — u 422 javob tanasini o'qib `AppException.Validation.fields` ni
     * to'ldiradi. O'z `try/catch` imiz bo'lsa maydon xatolari yo'qolardi.
     */
    override suspend fun save(profile: UserProfile): Resource<UserProfile> =
        safeCall { api.updateMe(profile.toUpdateRequest()).body() }.map { it.toDomain() }

    override suspend fun exists(): Boolean =
        (fetch() as? Resource.Success)?.data != null

    override suspend fun photos(): Resource<List<ProfilePhoto>> =
        safeCall { api.profilePhotosList().body() }.map { list -> list.items.map { it.toDomain() } }

    /**
     * Ikki qadam: `kind=PROFILE_PHOTO` bilan yuklash → `POST /v1/profile/photos`.
     *
     * `conversationId` **yuborilmaydi** — profil rasmi hech qanday suhbatga tegishli emas
     * (`handoff/08-PROFILE.md` §2). `mediaId` **bir martalik**: ikkinchi marta ishlatilsa
     * `422 MEDIA_ALREADY_USED`.
     */
    override suspend fun addPhoto(
        bytes: ByteArray,
        fileName: String,
        onProgress: ((Float) -> Unit)?,
    ): Resource<ProfilePhoto> =
        safeCall {
            val media = media.chatUpload(
                bytes = bytes,
                fileName = fileName,
                kind = ChatMediaKind.PROFILE_PHOTO,
                onProgress = onProgress,
            )
            api.add(AddProfilePhotoDto(mediaId = media.id)).body()
        }.map { it.toDomain() }

    override suspend fun makeMainPhoto(photoId: String): Resource<Unit> =
        safeCall { api.makeMain(photoId).body() }.map { }

    override suspend fun deletePhoto(photoId: String): Resource<Unit> =
        safeCall { api.profilePhotosRemove(photoId).body() }

    override suspend fun uploadAvatar(bytes: ByteArray, fileName: String): Resource<String> =
        safeCall {
            // Profil rasmi uchun alohida endpoint yo'q — umumiy `POST /v1/media/upload`
            // ishlatiladi, qaytgan URL esa `PUT /profile/me` orqali `avatarUrl` ga yoziladi
            // (buni repository qiladi). Spec'da `purpose` = LOGO | COVER | LISTING; avatar
            // uchun eng yaqini — LOGO.
            media.upload(bytes, fileName, MediaPurpose.LOGO).url
        }
}
