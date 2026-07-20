package dev.feature.clubs.domain.repository

import dev.core.common.Resource
import dev.feature.clubs.domain.model.Club
import kotlinx.coroutines.flow.Flow

/** Klublar — ro'yxat, qo'shilish/chiqish. */
interface ClubRepository {
    fun observeClubs(): Flow<List<Club>>
    suspend fun refreshClubs(): Resource<List<Club>>

    /** Klubga qo'shilish / chiqish (local). */
    suspend fun setJoined(id: Long, joined: Boolean)
}
