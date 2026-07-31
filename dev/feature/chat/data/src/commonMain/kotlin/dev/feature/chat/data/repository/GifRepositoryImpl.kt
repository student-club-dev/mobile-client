package dev.feature.chat.data.repository

import dev.core.common.Resource
import dev.core.common.map
import dev.feature.chat.data.mapper.toDomain
import dev.feature.chat.data.mapper.toDto
import dev.feature.chat.data.remote.GifRemoteDataSource
import dev.feature.chat.domain.model.GifItem
import dev.feature.chat.domain.model.GifLocale
import dev.feature.chat.domain.model.GifPage
import dev.feature.chat.domain.repository.GifRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

/**
 * GIF repository'si — qidiruv + **xotiradagi kesh**.
 *
 * Nega kesh shart: hozirgi kalit **soatiga 100 ta so'rov beradi va u GLOBAL** — ya'ni bitta
 * foydalanuvchining ortiqcha so'rovi butun ilovaning kvotasidan yeydi (`04-GIF-INTEGRATION.md`). Panelni
 * ochib-yopish, orqaga qaytish yoki bir xil so'zni qayta yozish **yangi so'rov qilmasligi**
 * kerak.
 *
 * Nega bazada emas: GIF katalogi tez eskiradi va ma'lumot bazasi sxemasiga tegmaslik kerak.
 * Kesh ilova ishlagan davomida yashaydi va [CACHE_TTL_MS] dan keyin eskiradi.
 */
class GifRepositoryImpl(
    private val remote: GifRemoteDataSource,
    private val clock: Clock = Clock.System,
) : GifRepository {

    private data class Key(
        val query: String,
        val cursor: String?,
        val locale: GifLocale,
        val limit: Int,
    )

    private data class Entry(val page: GifPage, val atMs: Long)

    // `LinkedHashMap` — kirish tartibini saqlaydi, ya'ni eng eskisini o'chirish arzon.
    private val cache = LinkedHashMap<Key, Entry>()
    private val lock = Mutex()

    override suspend fun search(
        query: String,
        cursor: String?,
        locale: GifLocale,
        limit: Int,
    ): Resource<GifPage> {
        val key = Key(
            // Kesh kaliti registrga bog'liq emas: "Salom" va "salom" — bitta so'rov.
            query = query.trim().lowercase(),
            cursor = cursor,
            locale = locale,
            limit = limit,
        )
        cached(key)?.let { return Resource.Success(it) }

        val result = remote.search(
            q = query,
            limit = limit,
            pos = cursor,
            locale = locale.toDto(),
        ).map { it.toDomain() }

        if (result is Resource.Success) put(key, result.data)
        return result
    }

    override suspend fun share(gif: GifItem, query: String?) {
        remote.share(id = gif.id, q = query)
    }

    private suspend fun cached(key: Key): GifPage? = lock.withLock {
        val entry = cache[key] ?: return null
        if (clock.now().toEpochMilliseconds() - entry.atMs > CACHE_TTL_MS) {
            cache.remove(key)
            return null
        }
        entry.page
    }

    private suspend fun put(key: Key, page: GifPage) = lock.withLock {
        cache[key] = Entry(page, clock.now().toEpochMilliseconds())
        // Chegaradan oshsa eng eski yozuv chiqariladi — xotira cheksiz o'smasin.
        while (cache.size > MAX_CACHED_PAGES) {
            val oldest = cache.keys.firstOrNull() ?: break
            cache.remove(oldest)
        }
    }

    private companion object {
        /** 10 daqiqa: trending shu vaqt ichida sezilarli o'zgarmaydi. */
        const val CACHE_TTL_MS = 10 * 60 * 1000L

        /** ~40 sahifa — bir necha qidiruvning bir necha sahifasi uchun yetarli. */
        const val MAX_CACHED_PAGES = 40
    }
}

/**
 * Domen `GifRef` ini serverga yuboriladigan DTO ga aylantiradi.
 *
 * Bu **yuborish nuqtasi uchun** ochib qo'yilgan: `message:send` ni quruvchi kod (`ChatRepository`)
 * shu funksiyani chaqirib `SendMessageDto.gif` ni to'ldiradi. Bu yerda ataylab qayta
 * ishlanmaydi — maydonlar o'zgarmasligi shart (`422 GIF_URL_NOT_ALLOWED`).
 */
fun gifRefDtoOf(gif: GifItem) = gif.toRef().toDto()
