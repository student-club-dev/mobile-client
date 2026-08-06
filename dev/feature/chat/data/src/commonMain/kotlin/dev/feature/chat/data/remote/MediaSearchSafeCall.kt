package dev.feature.chat.data.remote

import dev.core.common.Resource
import dev.core.common.error.AppException
import dev.core.common.error.toAppException
import dev.core.common.errorOf
import dev.core.common.network.NetworkConnectivity
import dev.core.network.response.parseErrorEnvelope
import dev.core.network.response.toAppException
import dev.feature.chat.data.mapper.apiErrorCodeOf
import dev.feature.chat.data.mapper.gifErrorKindOf
import dev.feature.chat.domain.model.GifErrorKind
import dev.feature.chat.domain.model.GifException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException

/**
 * Provayder qidiruvlari (GIF **va** stiker) uchun xavfsiz o'ram.
 *
 * Nega umumiy `safeCall` emas: u xato **kodini** tashlab yuboradi va faqat HTTP statusni
 * ko'radi. Bu yerda esa aynan kod muhim — `429 GIF_PROVIDER_RATE_LIMITED` bilan
 * `429 RATE_LIMITED` bir xil status ostida turadi, lekin foydalanuvchiga beriladigan
 * maslahat teskari. Shuning uchun 4xx/5xx tanasi shu yerda o'qiladi.
 *
 * Nega ikkala qidiruv uchun bitta funksiya: `GET /v1/stickers/search` — `GET /v1/gifs/search`
 * ning **aynan nusxasi** (parametrlar, sahifalash, xato kodlari —
 * `handoff/06-STICKER-SEARCH.md` §1). Ikki nusxa yozilsa ular albatta bir-biridan ajralib
 * ketardi.
 */
internal suspend fun <T> mediaSearchSafeCall(
    connectivity: NetworkConnectivity,
    block: suspend () -> T,
): Resource<T> {
    if (connectivity.isOnline().not()) {
        return mediaSearchError(GifErrorKind.NETWORK, AppException.NoInternet())
    }
    return try {
        Resource.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: ResponseException) {
        val status = e.response.status.value
        val body = runCatching { e.response.bodyAsText() }.getOrNull().orEmpty()
        // Tanadagi matn ham olinadi: tur `UNKNOWN` bo'lib chiqsa foydalanuvchi
        // "GIF qidiruvida xatolik" o'rniga serverning O'Z sababini ko'radi.
        val typed = parseErrorEnvelope(body, status) ?: e.response.status.toAppException(e)
        mediaSearchError(gifErrorKindOf(status, apiErrorCodeOf(body)), typed, e)
    } catch (e: AppException) {
        // Konvert ichidagi xato (`success=false`) — status 2xx bo'lishi ham mumkin.
        mediaSearchError(GifErrorKind.UNKNOWN, e, e)
    } catch (e: Throwable) {
        val online = connectivity.isOnline()
        val kind = if (online) GifErrorKind.UNKNOWN else GifErrorKind.NETWORK
        mediaSearchError(kind, e.toAppException(online), e)
    }
}

/**
 * Xatoni [GifErrorKind] ga aylantirib, `Resource.Error.cause` ichida [GifException] bilan
 * uzatadi.
 *
 * Matn tanlash qoidasi: tur ANIQ bo'lsa ([GifErrorKind.NETWORK], `RATE_LIMITED`…) uning
 * maslahati serverning texnik matnidan foydaliroq — "keyinroq urinib ko'ring" nima
 * qilishni aytadi. [GifErrorKind.UNKNOWN] da esa aksincha: bizda aytadigan gap yo'q,
 * shuning uchun serverning O'Z xabari ko'rsatiladi.
 */
private fun mediaSearchError(
    kind: GifErrorKind,
    appException: AppException,
    cause: Throwable? = null,
): Resource.Error = errorOf(appException).copy(
    message = if (kind == GifErrorKind.UNKNOWN) appException.userMessage else kind.userMessage,
    cause = GifException(kind, cause),
)
