package dev.feature.chat.data.remote

import dev.core.common.Resource
import dev.core.common.error.AppException
import dev.core.common.error.toAppException
import dev.core.common.errorOf
import dev.core.common.network.NetworkConnectivity
import dev.core.network.generated.api.ChatApi
import dev.core.network.generated.model.GifSearchResponseDto
import dev.core.network.generated.model.GifShareDto
import dev.feature.chat.data.mapper.apiErrorCodeOf
import dev.feature.chat.data.mapper.gifErrorKindOf
import dev.feature.chat.domain.model.GifErrorKind
import dev.feature.chat.domain.model.GifException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException

/**
 * GIF qidiruvining REST qatlami (`gif.md`).
 *
 * Nega `safeCall` emas: umumiy o'ram xato **kodini** tashlab yuboradi va faqat HTTP statusni
 * ko'radi. Bu yerda esa aynan kod muhim — `429 GIF_PROVIDER_RATE_LIMITED` bilan
 * `429 RATE_LIMITED` bir xil status ostida turadi, lekin foydalanuvchiga beriladigan
 * maslahat teskari. Shuning uchun 4xx/5xx tanasi shu yerda o'qiladi.
 */
class GifRemoteDataSource(
    private val api: ChatApi,
    private val connectivity: NetworkConnectivity,
) {

    /**
     * `GET /v1/gifs/search`. [q] bo'sh bo'lsa — trending (parametr umuman yuborilmaydi).
     * [pos] — oldingi sahifadagi `next`; **shaffof**, o'zgartirilmaydi.
     */
    suspend fun search(
        q: String?,
        limit: Int,
        pos: String?,
        locale: ChatApi.LocaleGifsSearch,
    ): Resource<GifSearchResponseDto> = gifSafeCall {
        api.gifsSearch(
            q = q?.trim()?.takeIf { it.isNotEmpty() },
            limit = limit.coerceIn(MIN_LIMIT, MAX_LIMIT),
            pos = pos,
            locale = locale,
        ).body()
    }

    /**
     * `POST /v1/gifs/{id}/share` — **eng yaxshi harakat**.
     *
     * Xatosi yutiladi: bu provayder reytingi uchun signal, foydalanuvchining GIF yuborishi
     * esa unga bog'liq bo'lmasligi kerak (hozirgi provayderda umuman no-op).
     */
    suspend fun share(id: String, q: String?) {
        try {
            api.share(id = id, gifShareDto = GifShareDto(q = q?.trim()?.takeIf { it.isNotEmpty() }))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Ataylab jim: kontraktda bor, lekin hech narsani to'sib qo'ymaydi.
        }
    }

    /**
     * GIF so'rovlari uchun xavfsiz o'ram: xatoni [GifErrorKind] ga aylantirib,
     * `Resource.Error.cause` ichida [GifException] bo'lib uzatadi.
     */
    private suspend fun <T> gifSafeCall(block: suspend () -> T): Resource<T> {
        if (connectivity.isOnline().not()) return gifError(GifErrorKind.NETWORK, AppException.NoInternet())
        return try {
            Resource.Success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: ResponseException) {
            val status = e.response.status.value
            val body = runCatching { e.response.bodyAsText() }.getOrNull().orEmpty()
            gifError(gifErrorKindOf(status, apiErrorCodeOf(body)), e.toAppException(), e)
        } catch (e: AppException) {
            // Konvert ichidagi xato (`success=false`) — status 2xx bo'lishi ham mumkin.
            gifError(GifErrorKind.UNKNOWN, e, e)
        } catch (e: Throwable) {
            val online = connectivity.isOnline()
            val kind = if (online) GifErrorKind.UNKNOWN else GifErrorKind.NETWORK
            gifError(kind, e.toAppException(online), e)
        }
    }

    private fun gifError(
        kind: GifErrorKind,
        appException: AppException,
        cause: Throwable? = null,
    ): Resource.Error = errorOf(appException).copy(
        message = kind.userMessage,
        cause = GifException(kind, cause),
    )

    private companion object {
        const val MIN_LIMIT = 1
        const val MAX_LIMIT = 50
    }
}
