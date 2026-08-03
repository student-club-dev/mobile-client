package dev.feature.chat.data.remote

import dev.core.common.Resource
import dev.core.common.network.NetworkConnectivity
import dev.core.network.generated.api.ChatApi
import dev.core.network.generated.model.StickerSearchResponseDto
import dev.core.network.generated.model.StickerShareDto
import kotlinx.coroutines.CancellationException

/**
 * Stiker qidiruvi (`GET /v1/stickers/search`, `POST /v1/stickers/{id}/share`) —
 * `handoff/06-STICKER-SEARCH.md`.
 *
 * Endpoint `GET /v1/gifs/search` ning aynan nusxasi, shuning uchun bu klass ham
 * [GifRemoteDataSource] ning aynan nusxasi: o'sha [mediaSearchSafeCall] o'rami (ikkita 429 ni
 * ajratish uchun), o'sha "share — eng yaxshi harakat" qoidasi.
 */
class StickerSearchRemoteDataSource(
    private val api: ChatApi,
    private val connectivity: NetworkConnectivity,
) {

    /**
     * [q] bo'sh bo'lsa — trending (parametr umuman yuborilmaydi).
     * [pos] — oldingi sahifadagi `next`; **shaffof**, o'zgartirilmaydi.
     */
    suspend fun search(
        q: String?,
        limit: Int,
        pos: String?,
        locale: ChatApi.LocaleStickersSearch,
    ): Resource<StickerSearchResponseDto> = mediaSearchSafeCall(connectivity) {
        api.stickersSearch(
            q = q?.trim()?.takeIf { it.isNotEmpty() },
            limit = limit.coerceIn(MIN_LIMIT, MAX_LIMIT),
            pos = pos,
            locale = locale,
        ).body()
    }

    /**
     * `POST /v1/stickers/{id}/share` — **eng yaxshi harakat**.
     *
     * Xatosi yutiladi: bu provayder reytingi uchun signal, foydalanuvchining stiker yuborishi
     * esa unga bog'liq bo'lmasligi kerak.
     */
    suspend fun share(id: String, q: String?) {
        try {
            api.stickersShare(
                id = id,
                stickerShareDto = StickerShareDto(q = q?.trim()?.takeIf { it.isNotEmpty() }),
            )
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
