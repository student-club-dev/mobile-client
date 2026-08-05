package dev.feature.notifications.data.remote

import dev.core.common.Resource
import dev.core.common.error.AppException
import dev.core.common.error.toAppException
import dev.core.common.errorOf
import dev.core.common.network.NetworkConnectivity
import dev.core.network.response.toAppException
import dev.feature.notifications.data.dto.MarkNotificationsReadDto
import dev.feature.notifications.data.dto.NotificationPageDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

/** Bildirishnomalar ro'yxati serverdan (`NOTIFICATIONS_BACKEND.md`). */
interface NotificationRemoteDataSource {
    suspend fun fetch(limit: Int): Resource<NotificationPageDto>
    suspend fun markRead(ids: List<String>): Resource<Unit>
    suspend fun markAllRead(): Resource<Unit>
}

/**
 * Ktor implementatsiyasi.
 *
 * `safeCall` ATAYLAB ishlatilmagan: u har xatoni [dev.core.common.error.AppMessageBus] ga
 * yuboradi va ildizdagi toast'ni chiqaradi. Bildirishnoma ro'yxati esa ekran ochilishida va
 * bosh ekranda JIMGINA yangilanadi — bunday fon so'rovi tarmoq yo'qligida toast chiqarsa,
 * foydalanuvchi hech so'ramagan xatoni ko'rardi. Xato typed holda yuqoriga qaytadi, ko'rsatish
 * qarori esa ekranniki (kesh bo'sh bo'lsagina inline xato ko'rinadi).
 */
class KtorNotificationRemoteDataSource(
    private val client: HttpClient,
    private val connectivity: NetworkConnectivity? = null,
) : NotificationRemoteDataSource {

    override suspend fun fetch(limit: Int): Resource<NotificationPageDto> = call {
        client.get(PATH) { parameter("limit", limit) }.body()
    }

    /**
     * Bir nechta id bitta so'rovda: ekran ochilib yopilgunicha bir necha bildirishnoma
     * o'qilishi mumkin va har biri uchun alohida so'rov yuborish keraksiz.
     */
    override suspend fun markRead(ids: List<String>): Resource<Unit> {
        if (ids.isEmpty()) return Resource.Success(Unit)
        return call { client.post(READ_PATH) { jsonBody(MarkNotificationsReadDto(ids = ids)) } }
            .toUnit()
    }

    override suspend fun markAllRead(): Resource<Unit> =
        call { client.post(READ_PATH) { jsonBody(MarkNotificationsReadDto(all = true)) } }.toUnit()

    private fun HttpRequestBuilder.jsonBody(body: Any) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private fun Resource<Any>.toUnit(): Resource<Unit> = when (this) {
        is Resource.Success -> Resource.Success(Unit)
        is Resource.Error -> this
        Resource.Loading -> Resource.Loading
    }

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
        errorOf(e.response.status.toAppException(e))
    } catch (e: Throwable) {
        errorOf(e.toAppException(connectivity?.isOnline() ?: true))
    }

    private companion object {
        const val PATH = "notifications"
        const val READ_PATH = "notifications/read"
    }
}
