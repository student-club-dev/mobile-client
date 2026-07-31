package dev.feature.profile.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.core.common.AppDispatchers
import dev.core.common.Resource
import dev.core.common.auth.TokenStore
import dev.core.database.sql.StudentClubDatabase
import dev.feature.profile.data.mapper.toDomain
import dev.feature.profile.data.remote.ProfileRemoteDataSource
import dev.feature.profile.domain.model.ProfilePhoto
import dev.feature.profile.domain.model.UserProfile
import dev.feature.profile.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Offline-first profil repository'si.
 *
 * UI **faqat** local DB'ni (`ProfileEntity`) kuzatadi — shu sabab tarmoq bo'lmasa ham
 * profil ko'rinadi. [refresh]/[saveProfile] masofaviy manba bilan gaplashib keshni yangilaydi
 * ([ProfileRemoteDataSource] — hozir backend REST'i).
 */
class ProfileRepositoryImpl(
    private val db: StudentClubDatabase,
    private val dispatchers: AppDispatchers,
    private val remote: ProfileRemoteDataSource,
    private val tokenStore: TokenStore,
) : ProfileRepository {

    private val q get() = db.profileQueries

    /** Kesh kaliti — backend sessiyasidagi uid (JWT `sub`, [TokenStore] da saqlanadi). */
    private val currentUid: String? get() = tokenStore.userId()

    override fun observeProfile(): Flow<UserProfile?> =
        q.selectCurrent()
            .asFlow()
            .mapToOneOrNull(dispatchers.io)
            .map { it?.toDomain() }

    override suspend fun refresh(): Resource<Unit> {
        val uid = currentUid ?: return Resource.Error("Sessiya topilmadi")
        return when (val res = remote.fetch()) {
            is Resource.Success -> {
                // Masofada profil yo'q bo'lsa keshni o'zgartirmaymiz (mavjud local
                // ma'lumotni o'chirib yubormaslik uchun).
                res.data?.let { cache(uid, it) }
                Resource.Success(Unit)
            }
            is Resource.Error -> res
            Resource.Loading -> Resource.Success(Unit)
        }
    }

    override suspend fun saveProfile(profile: UserProfile): Resource<Unit> {
        // Offline-first: avval local keshga yozamiz — UI (profil/universitet) darrov yangilanadi
        // va backend bo'lmasa ham saqlanadi. Uid sessiyadan yoki mavjud kesh qatoridan.
        val uid = currentUid ?: cachedUid() ?: return Resource.Error("Sessiya topilmadi — avval kiring")
        cache(uid, profile)
        // Fon: masofaviy manbaga ham yozishga urinamiz; muvaffaqiyatli bo'lsa server qiymatini keshlaymiz.
        return when (val res = remote.save(profile)) {
            is Resource.Success -> { cache(uid, res.data); Resource.Success(Unit) }
            is Resource.Error -> Resource.Success(Unit)   // local saqlandi — offline-first
            Resource.Loading -> Resource.Success(Unit)
        }
    }

    override suspend fun uploadAvatar(bytes: ByteArray, fileName: String): Resource<String> {
        val uid = currentUid ?: return Resource.Error("Sessiya topilmadi — avval kiring")

        val url = when (val uploaded = remote.uploadAvatar(bytes, fileName)) {
            is Resource.Success -> uploaded.data
            is Resource.Error -> return uploaded
            Resource.Loading -> return Resource.Error("Rasmni yuklab bo'lmadi")
        }

        // Rasm yuklandi — endi uning manzilini profilga yozamiz, aks holda URL yo'qoladi.
        val updated = (cachedProfile() ?: UserProfile()).copy(avatarUrl = url)
        return when (val saved = remote.save(updated)) {
            is Resource.Success -> {
                cache(uid, saved.data)
                Resource.Success(url)
            }
            is Resource.Error -> saved
            Resource.Loading -> Resource.Success(url)
        }
    }

    // --- Profil rasmlari (`handoff/08-PROFILE.md` §2) --------------------------------------

    override suspend fun photos(): Resource<List<ProfilePhoto>> = remote.photos()

    /**
     * Rasm qo'shilgach profil ham **yangilanadi**: `avatarUrl` — hosila maydon va server
     * uni o'zi almashtiradi, ya'ni local keshdagi eski avatar noto'g'ri bo'lib qolardi.
     * Shuning uchun muvaffaqiyatdan keyin `refresh()` chaqiriladi (bitta arzon so'rov).
     */
    override suspend fun addPhoto(
        bytes: ByteArray,
        fileName: String,
        onProgress: ((Float) -> Unit)?,
    ): Resource<ProfilePhoto> =
        remote.addPhoto(bytes, fileName, onProgress).also { if (it is Resource.Success) refresh() }

    override suspend fun makeMainPhoto(photoId: String): Resource<Unit> =
        remote.makeMainPhoto(photoId).also { if (it is Resource.Success) refresh() }

    override suspend fun deletePhoto(photoId: String): Resource<Unit> =
        remote.deletePhoto(photoId).also { if (it is Resource.Success) refresh() }

    override suspend fun hasProfile(): Boolean {
        // Offline-first: kesh bo'lsa tarmoqni kutmaymiz.
        if (cachedProfile() != null) return true
        return remote.exists()
    }

    private suspend fun cachedProfile(): UserProfile? = withContext(dispatchers.io) {
        q.selectCurrent().executeAsOneOrNull()?.toDomain()
    }

    /** Kesh qatoridagi uid (currentUid null bo'lsa — offline saqlash uchun). */
    private suspend fun cachedUid(): String? = withContext(dispatchers.io) {
        q.selectCurrent().executeAsOneOrNull()?.uid
    }

    /** Bitta joriy-profil qatorini yozadi (avval eskisini o'chirib). */
    private suspend fun cache(uid: String, p: UserProfile) = withContext(dispatchers.io) {
        q.transaction {
            q.clear()
            q.upsert(
                uid = uid,
                firstName = p.firstName,
                lastName = p.lastName,
                phoneNumber = p.phoneNumber,
                role = p.role,
                universityId = p.universityId,
                universityEmail = p.universityEmail,
                birthYear = p.birthYear?.toLong(),
                courseYear = p.courseYear,
                gender = p.gender,
                lastSeenVisibility = p.lastSeenVisibility,
                avatarUrl = p.avatarUrl,
                businessName = p.businessName,
                businessType = p.businessType,
                email = p.email,
                bio = p.bio,
                phoneVisibility = p.phoneVisibility,
            )
        }
    }
}
