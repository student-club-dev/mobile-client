package dev.feature.notifications.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import dev.core.common.AppDispatchers
import dev.core.common.Resource
import dev.core.database.sql.StudentClubDatabase
import dev.feature.notifications.data.mapper.toDomain
import dev.feature.notifications.data.mapper.toEntity
import dev.feature.notifications.data.remote.NotificationRemoteDataSource
import dev.feature.notifications.domain.model.AppNotification
import dev.feature.notifications.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * [NotificationRepository] — offline-first: SQLDelight keshi + `GET /v1/notifications`.
 *
 * [remoteEnabled] `false` bo'lganda (endpoint hali joylanmagan bo'lsa) hamma narsa localda
 * ishlaydi: ro'yxat keshdan o'qiladi, o'qildi belgisi faqat bazaga yoziladi, tarmoqqa esa
 * BIRORTA so'rov ketmaydi — aks holda har ochilishda 404 keladi va u xato holatiga aylanardi.
 */
class NotificationRepositoryImpl(
    private val db: StudentClubDatabase,
    private val dispatchers: AppDispatchers,
    private val remote: NotificationRemoteDataSource,
    private val remoteEnabled: Boolean,
) : NotificationRepository {

    private val q get() = db.notificationQueries

    override fun observeAll(): Flow<List<AppNotification>> =
        q.selectAll().asFlow().mapToList(dispatchers.io).map { rows -> rows.map { it.toDomain() } }

    override fun observeUnreadCount(): Flow<Int> =
        q.countUnread().asFlow().mapToOne(dispatchers.io).map { it.toInt() }

    /**
     * Server ro'yxatini olib keshni ALMASHTIRADI.
     *
     * "Almashtirish" (tozalash + yozish) — qo'shish emas: serverda o'chirilgan yoki
     * boshqa qurilmadan o'qilgan bildirishnoma shundagina yo'qoladi/o'qilgan bo'ladi.
     * Tranzaksiya ichida, ya'ni ro'yxatni kuzatayotgan ekran hech qachon oraliq
     * "bo'sh" holatni ko'rmaydi.
     */
    override suspend fun refresh(): Resource<Unit> {
        if (!remoteEnabled) return Resource.Success(Unit)
        return when (val result = remote.fetch(limit = PAGE_SIZE)) {
            is Resource.Success -> {
                val rows = result.data.items.map { it.toEntity() }
                withContext(dispatchers.io) {
                    q.transaction {
                        q.clear()
                        rows.forEach {
                            q.upsert(
                                id = it.id,
                                title = it.title,
                                body = it.body,
                                type = it.type,
                                createdAt = it.createdAt,
                                targetType = it.targetType,
                                targetId = it.targetId,
                                read = it.read,
                            )
                        }
                    }
                }
                Resource.Success(Unit)
            }
            is Resource.Error -> result
            Resource.Loading -> Resource.Success(Unit)
        }
    }

    /**
     * Avval local, keyin server — UI kutmaydi.
     *
     * Server so'rovi yiqilsa qaytarib olinmaydi: keyingi [refresh] serverning haqiqiy
     * holatini yozadi va belgi o'zi tiklanadi. Teskarisi (avval server, keyin local)
     * bildirishnomani bosgandan keyin ekran ochilguncha nuqta o'chmay turishini bildirardi.
     */
    override suspend fun markRead(id: String) {
        withContext(dispatchers.io) { q.markRead(id) }
        if (remoteEnabled) remote.markRead(listOf(id))
    }

    override suspend fun markAllRead() {
        withContext(dispatchers.io) { q.markAllRead() }
        if (remoteEnabled) remote.markAllRead()
    }

    private companion object {
        /**
         * Bir so'rovda olinadigan bildirishnomalar soni.
         *
         * Sahifalash YO'Q va ataylab: bildirishnoma — qisqa umrli ro'yxat, foydalanuvchi
         * uni oxirigacha aylantirmaydi. Yuzinchisidan pastdagilar allaqachon ahamiyatsiz.
         */
        const val PAGE_SIZE = 100
    }
}
