package dev.feature.chat.data.remote

import dev.core.common.Resource
import dev.core.common.network.NetworkConnectivity
import dev.core.network.generated.api.ChatApi
import dev.core.network.generated.model.GifSearchResponseDto
import dev.core.network.generated.model.GifShareDto
import kotlinx.coroutines.CancellationException

/**
 * GIF qidiruvining REST qatlami (`04-GIF-INTEGRATION.md`).
 *
 * Xatolar umumiy `safeCall` bilan emas, [mediaSearchSafeCall] bilan o'raladi — sababi
 * o'sha faylda (ikkita 429 ni ajratish). O'sha o'ram stiker qidiruvida ham ishlatiladi.
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
    ): Resource<GifSearchResponseDto> = mediaSearchSafeCall(connectivity) {
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
            // `share` → `gifsShare`: stiker share qo'shilgach operationId qisqarishi ikkalasini
            // ajratadigan bo'ldi (`GifsController_share` va `StickersController_share`).
            api.gifsShare(id = id, gifShareDto = GifShareDto(q = q?.trim()?.takeIf { it.isNotEmpty() }))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Ataylab jim: kontraktda bor, lekin hech narsani to'sib qo'ymaydi.
        }
    }

    private companion object {
        const val MIN_LIMIT = 1
        const val MAX_LIMIT = 50
    }
}
