package dev.feature.notifications.domain.repository

import dev.core.common.Resource
import dev.feature.notifications.domain.model.AppNotification
import kotlinx.coroutines.flow.Flow

/**
 * Bildirishnomalar — offline-first: o'qish DOIM local keshdan ([observeAll]), yangilanish
 * esa [refresh] orqali serverdan (`GET /v1/notifications`).
 *
 * Kesh faqat MUVAFFAQIYATLI javobda almashtiriladi: tarmoq yo'q bo'lsa ekranda oxirgi
 * ko'rilgan ro'yxat qoladi, bo'sh ekran emas.
 */
interface NotificationRepository {
    fun observeAll(): Flow<List<AppNotification>>
    fun observeUnreadCount(): Flow<Int>

    /** Serverdan qayta o'qiydi va keshni almashtiradi. Xato bo'lsa kesh tegilmaydi. */
    suspend fun refresh(): Resource<Unit>

    /**
     * O'qilgan deb belgilaydi — avval localda (UI darrov javob beradi), keyin serverda.
     *
     * Server so'rovi yiqilsa local qiymat qoladi va keyingi [refresh] uni server holatiga
     * qaytaradi — ya'ni desinxron o'zini o'zi tuzatadi.
     */
    suspend fun markRead(id: String)
    suspend fun markAllRead()
}
