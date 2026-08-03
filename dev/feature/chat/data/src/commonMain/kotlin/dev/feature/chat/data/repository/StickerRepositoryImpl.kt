package dev.feature.chat.data.repository

import dev.core.common.Resource
import dev.core.common.error.AppException
import dev.core.common.map
import dev.feature.chat.data.mapper.toDomain
import dev.feature.chat.data.mapper.toStickerDto
import dev.feature.chat.data.remote.StickerRemoteDataSource
import dev.feature.chat.data.remote.StickerSearchRemoteDataSource
import dev.feature.chat.domain.model.GifLocale
import dev.feature.chat.domain.model.StickerCatalog
import dev.feature.chat.domain.model.StickerCatalogState
import dev.feature.chat.domain.model.StickerSearchItem
import dev.feature.chat.domain.model.StickerSearchPage
import dev.feature.chat.domain.repository.StickerRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

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
    private val search: StickerSearchRemoteDataSource,
    private val clock: Clock = Clock.System,
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

    // --- Qidiruv (KLIPY) -------------------------------------------------------------------

    private data class SearchKey(
        val query: String,
        val cursor: String?,
        val locale: GifLocale,
        val limit: Int,
    )

    private data class SearchEntry(val page: StickerSearchPage, val atMs: Long)

    /**
     * Qidiruv keshi — GIF'dagi bilan **bir xil sabab**: test kaliti soatiga 100 ta so'rov
     * beradi va u GLOBAL. Endi kvotani GIF bilan stiker **bo'lishadi**, ya'ni u ikki barobar
     * tez tugaydi (`handoff/06-STICKER-SEARCH.md` §3) — panelni ochib-yopish yangi so'rov
     * qilmasligi kerak.
     *
     * `LinkedHashMap` — kirish tartibini saqlaydi, eng eskisini o'chirish arzon.
     */
    private val searchCache = LinkedHashMap<SearchKey, SearchEntry>()
    private val searchLock = Mutex()

    override suspend fun search(
        query: String,
        cursor: String?,
        locale: GifLocale,
        limit: Int,
    ): Resource<StickerSearchPage> {
        val key = SearchKey(
            // Kesh kaliti registrga bog'liq emas: "Salom" va "salom" — bitta so'rov.
            query = query.trim().lowercase(),
            cursor = cursor,
            locale = locale,
            limit = limit,
        )
        cachedSearch(key)?.let { return Resource.Success(it) }

        val result = search.search(
            q = query,
            limit = limit,
            pos = cursor,
            locale = locale.toStickerDto(),
        ).map { it.toDomain() }

        if (result is Resource.Success) putSearch(key, result.data)
        return result
    }

    override suspend fun share(item: StickerSearchItem, query: String?) {
        search.share(id = item.id, q = query)
    }

    private suspend fun cachedSearch(key: SearchKey): StickerSearchPage? = searchLock.withLock {
        val entry = searchCache[key] ?: return null
        if (clock.now().toEpochMilliseconds() - entry.atMs > SEARCH_CACHE_TTL_MS) {
            searchCache.remove(key)
            return null
        }
        entry.page
    }

    private suspend fun putSearch(key: SearchKey, page: StickerSearchPage) = searchLock.withLock {
        searchCache[key] = SearchEntry(page, clock.now().toEpochMilliseconds())
        while (searchCache.size > MAX_CACHED_SEARCH_PAGES) {
            val oldest = searchCache.keys.firstOrNull() ?: break
            searchCache.remove(oldest)
        }
    }

    private companion object {
        /** 10 daqiqa — GIF keshi bilan bir xil: trending shu vaqtda sezilarli o'zgarmaydi. */
        const val SEARCH_CACHE_TTL_MS = 10 * 60 * 1000L

        /** ~40 sahifa — bir necha qidiruvning bir necha sahifasi uchun yetarli. */
        const val MAX_CACHED_SEARCH_PAGES = 40
    }
}
