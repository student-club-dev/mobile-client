package dev.feature.notifications.data.remote

import dev.core.common.Resource
import dev.core.common.error.AppException
import dev.core.common.error.toAppException
import dev.core.common.errorOf
import dev.core.common.network.NetworkConnectivity
import dev.core.network.generated.api.NotificationsApi
import dev.core.network.generated.model.MarkNotificationsReadDto
import dev.core.network.generated.model.NotificationListDto
import dev.core.network.response.toAppExceptionWithFields
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException

/** Bildirishnomalar ro'yxati serverdan (`01-NOTIFICATIONS_BACKEND.md`). */
interface NotificationRemoteDataSource {
    suspend fun fetch(limit: Int): Resource<NotificationListDto>
    suspend fun markRead(ids: List<String>): Resource<Unit>
    suspend fun markAllRead(): Resource<Unit>
}

/**
 * Generatsiya qilingan [NotificationsApi] ustidagi implementatsiya.
 *
 * `safeCall` ATAYLAB ishlatilmagan: u har xatoni [dev.core.common.error.AppMessageBus] ga
 * yuboradi va ildizdagi toast'ni chiqaradi. Bildirishnoma ro'yxati esa ekran ochilishida va
 * bosh ekranda JIMGINA yangilanadi — bunday fon so'rovi tarmoq yo'qligida toast chiqarsa,
 * foydalanuvchi hech so'ramagan xatoni ko'rardi. Xato typed holda yuqoriga qaytadi, ko'rsatish
 * qarori esa ekranniki (kesh bo'sh bo'lsagina inline xato ko'rinadi).
 */
class ApiNotificationRemoteDataSource(
    private val api: NotificationsApi,
    private val connectivity: NetworkConnectivity? = null,
) : NotificationRemoteDataSource {

    override suspend fun fetch(limit: Int): Resource<NotificationListDto> =
        call { api.notificationsList(limit = limit).body() }

    /**
     * Bir nechta id bitta so'rovda: ekran ochilib yopilgunicha bir necha bildirishnoma
     * o'qilishi mumkin va har biri uchun alohida so'rov yuborish keraksiz.
     */
    override suspend fun markRead(ids: List<String>): Resource<Unit> {
        if (ids.isEmpty()) return Resource.Success(Unit)
        return call { api.markRead(MarkNotificationsReadDto(ids = ids)); Unit }
    }

    /**
     * ⚠️ `all = true` YUBORILADI, `all = false` emas: server `{all: false}` ni ham `422` bilan
     * rad etadi (§2) — u hech narsani tanlamaydi va deyarli har doim klient xatosi.
     */
    override suspend fun markAllRead(): Resource<Unit> =
        call { api.markRead(MarkNotificationsReadDto(all = true)); Unit }

    private suspend fun <T> call(block: suspend () -> T): Resource<T> = try {
        // Tarmoq yo'qligi so'rovdan OLDIN aniqlanadi — aks holda xato matni platformaga
        // qarab har xil ("Unable to resolve host…") bo'lib chiqardi.
        if (connectivity?.isOnline() == false) {
            errorOf(AppException.NoInternet())
        } else {
            Resource.Success(block())
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: ResponseException) {
        // ⚠️ Tana O'QILADI (`toAppExceptionWithFields`), faqat statusdan xato qurilmaydi:
        // server nima deganini ekran inline ko'rsatishi kerak. Toast baribir chiqmaydi —
        // u `safeCall` ichidagi `failure()` da yuboriladi, bu yerda esa u ishlatilmaydi.
        errorOf(e.toAppExceptionWithFields())
    } catch (e: Throwable) {
        errorOf(e.toAppException(connectivity?.isOnline() ?: true))
    }
}
