package dev.feature.chat.data.repository

import dev.core.common.Resource
import dev.core.common.error.AppException
import dev.feature.chat.data.mapper.toDomain
import dev.feature.chat.data.remote.StickerRemoteDataSource
import dev.feature.chat.domain.model.StickerCatalog
import dev.feature.chat.domain.model.StickerCatalogState
import dev.feature.chat.domain.repository.StickerRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Stiker katalogi repository'si — **xotiradagi kesh + `ETag`**.
 *
 * Katalog bitta javobda keladi va kamdan-kam o'zgaradi, shuning uchun ilova ishlagan davomida
 * bir marta o'qiladi. Keyingi so'rov `If-None-Match` bilan ketadi: o'zgarmagan katalog
 * **304** va tanasiz javob beradi, ya'ni trafik ham, parse ham yo'q.
 *
 * Nega bazada emas: SQLDelight sxemasiga tegmaslik kelishuvi bor, va katalogsiz qolish
 * halokat emas — pastda emoji zaxirasi turibdi.
 */
class StickerRepositoryImpl(
    private val remote: StickerRemoteDataSource,
) : StickerRepository {

    private val lock = Mutex()
    private var etag: String? = null
    private var cached: StickerCatalogState? = null

    override suspend fun catalog(forceRefresh: Boolean): StickerCatalogState = lock.withLock {
        val current = cached
        // Serverdan olingan katalog bo'lsa — qayta so'ramaymiz. Zaxira ko'rsatilayotgan
        // bo'lsa har ochilishda yana bir imkoniyat beramiz (server oralig'da to'lgan
        // bo'lishi mumkin — stiker tasvirlari hali ishlanmoqda, `PENDING_ACTIONS.md` §6).
        if (!forceRefresh && current != null && current.fromServer) return current

        when (val result = remote.packs(etag = etag)) {
            is Resource.Success -> {
                val packs = result.data
                if (packs == null) {
                    // 304 — keshdagi katalog hali ham to'g'ri.
                    current ?: fallback()
                } else {
                    etag = packs.etag ?: etag
                    val domain = packs.dto.toDomain()
                    val state = if (domain.isEmpty()) {
                        // Server javob berdi, lekin stiker YO'Q — hozirgi holat aynan shu:
                        // sxema va endpoint tayyor, tasvirlar esa hali ishlab chiqilmagan.
                        fallback(version = packs.dto.version)
                    } else {
                        StickerCatalogState(
                            packs = domain,
                            fromServer = true,
                            version = packs.dto.version,
                        )
                    }
                    state.also { cached = it }
                }
            }

            is Resource.Error -> {
                // Panel baribir ochilishi kerak — emoji katalogi hech narsaga bog'liq emas.
                val state = fallback(error = result.error ?: AppException.Unknown(result.message))
                // Xatoli natija keshlanadi, lekin `fromServer = false` bo'lgani uchun
                // keyingi ochilishda yana urinib ko'riladi.
                state.also { cached = it }
            }

            Resource.Loading -> current ?: fallback()
        }
    }

    private fun fallback(version: Int? = null, error: AppException? = null) = StickerCatalogState(
        packs = StickerCatalog.packs,
        fromServer = false,
        version = version,
        error = error,
    )
}
