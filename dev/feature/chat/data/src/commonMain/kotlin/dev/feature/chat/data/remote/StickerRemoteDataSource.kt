package dev.feature.chat.data.remote

import dev.core.common.Resource
import dev.core.common.network.NetworkConnectivity
import dev.core.network.generated.api.ChatApi
import dev.core.network.generated.model.StickerPacksDto
import dev.core.network.response.safeCall
import io.ktor.client.plugins.ResponseException

/**
 * Stiker katalogi (`GET /v1/stickers/packs`).
 *
 * Butun katalog **bitta javobda** keladi (~200 KB), shuning uchun u keshlanadi va keyingi
 * so'rovda `If-None-Match` bilan tekshiriladi: o'zgarmagan katalog **304** va **tanasiz**
 * javob beradi (`02-API-CHANGES.md` §4c).
 */
class StickerRemoteDataSource(
    private val api: ChatApi,
    private val connectivity: NetworkConnectivity,
) {

    /** Server javobi: katalog + keyingi safar yuboriladigan `ETag`. */
    data class Packs(val dto: StickerPacksDto, val etag: String?)

    /**
     * [etag] berilsa `If-None-Match` bo'lib ketadi.
     *
     * `Resource.Success(null)` — **304**, ya'ni keshdagi katalog hali ham to'g'ri.
     * Nega istisno orqali: klientda `expectSuccess = true`, ya'ni 3xx javob ham
     * `ResponseException` bo'lib keladi va uni bu yerda "xato emas" deb ushlaymiz.
     */
    suspend fun packs(etag: String? = null): Resource<Packs?> = safeCall(connectivity) {
        try {
            val response = api.packs(ifNoneMatch = etag)
            Packs(dto = response.body(), etag = response.headers.etag())
        } catch (e: ResponseException) {
            if (e.response.status.value == HTTP_NOT_MODIFIED) null else throw e
        }
    }

    /** Sarlavhalar registrga bog'liq emas — `ETag` / `etag` ikkalasi ham uchraydi. */
    private fun Map<String, List<String>>.etag(): String? =
        entries.firstOrNull { it.key.equals("ETag", ignoreCase = true) }
            ?.value?.firstOrNull()
            ?.takeIf { it.isNotBlank() }

    private companion object {
        const val HTTP_NOT_MODIFIED = 304
    }
}
