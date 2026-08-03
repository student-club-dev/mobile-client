package dev.feature.university.domain.repository

import dev.core.common.Resource
import dev.feature.university.domain.model.University
import kotlinx.coroutines.flow.Flow

/** Universitetlar (local DB — profil to'ldirish va studentlar bo'limi uchun). */
interface UniversityRepository {
    fun observeUniversities(): Flow<List<University>>

    /** Backend'dan sinxronlab local DB'ni yangilaydi (offline-first). Xatoda cache saqlanadi. */
    suspend fun refresh(): Resource<Unit>

    /** Tanlash uchun universitetlar ro'yxatini prof-emis'dan oladi (local DB'ga yozmaydi). */
    suspend fun fetchSelectableUniversities(): Resource<List<University>>

    /** Tanlangan universitetni local DB'ga qo'shadi. */
    suspend fun addUniversity(university: University)

    /** Universitetlar ro'yxatini prof-emis'dan bir marta local DB'ga yuklaydi. */
    suspend fun ensureRemoteUniversities()
}
