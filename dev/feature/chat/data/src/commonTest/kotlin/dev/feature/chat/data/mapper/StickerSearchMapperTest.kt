package dev.feature.chat.data.mapper

import dev.core.network.generated.model.MediaProviderDto
import dev.core.network.generated.model.ProviderStickerDto
import dev.core.network.generated.model.StickerSearchResponseDto
import dev.feature.chat.domain.model.GifProvider
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Stiker qidiruvi (`handoff/06-STICKER-SEARCH.md`).
 *
 * DTO'lar spec'dan generatsiya qilinadi, ya'ni maydon **nomlarini** generator ushlab turadi.
 * Bu testlar esa o'girishning o'zini qotiradi: `thumbUrl` ning zaxirasi, kursorning bo'sh
 * satri va yuborish shaklidagi `externalId` — bularning har biri ilgari xatoga sabab
 * bo'lgan naqshlar.
 */
class StickerSearchMapperTest {

    private val sticker = ProviderStickerDto(
        id = "8471021",
        url = "https://static.klipy.com/sticker/xY3k.webp",
        thumbUrl = "https://static.klipy.com/sticker/xY3k_s.webp",
        width = 512,
        height = 512,
        isAnimated = true,
    )

    @Test
    fun `qidiruv javobi domenga o'giriladi`() {
        val page = StickerSearchResponseDto(
            items = listOf(sticker),
            next = "2",
            provider = MediaProviderDto.KLIPY,
        ).toDomain()

        assertEquals(1, page.items.size)
        assertEquals(GifProvider.KLIPY, page.provider)
        assertEquals("2", page.next)
        assertTrue(page.hasMore)
        // ⚠️ WebP — MP4 EMAS: stikerda shaffof fon shart.
        assertTrue(page.items[0].url.endsWith(".webp"))
        assertTrue(page.items[0].isAnimated)
    }

    @Test
    fun `bo'sh kursor oxirini bildiradi`() {
        val page = StickerSearchResponseDto(
            items = listOf(sticker),
            provider = MediaProviderDto.KLIPY,
            next = "  ",
        ).toDomain()
        assertNull(page.next)
        assertTrue(!page.hasMore)
    }

    @Test
    fun `kichik nusxa bo'lmasa to'liq tasvir ishlatiladi`() {
        val page = StickerSearchResponseDto(
            items = listOf(sticker.copy(thumbUrl = "  ")),
            provider = MediaProviderDto.KLIPY,
        ).toDomain()
        assertEquals(sticker.url, page.items[0].thumbUrl)
    }

    @Test
    fun `yuborish shakli maydonlarni o'zgartirmaydi`() {
        val json = StickerSearchResponseDto(items = listOf(sticker), provider = MediaProviderDto.KLIPY)
            .toDomain().items[0].toRef().toJson()

        // Serverdagi domen oq ro'yxati aynan shu `url` ni tekshiradi — bitta o'zgargan
        // belgi ham `422 STICKER_URL_NOT_ALLOWED` bo'lib qaytadi.
        assertEquals(JsonPrimitive("KLIPY"), json["provider"])
        assertEquals(JsonPrimitive("8471021"), json["externalId"])
        assertEquals(JsonPrimitive(sticker.url), json["url"])
        assertEquals(JsonPrimitive(sticker.thumbUrl), json["thumbUrl"])
        assertEquals(JsonPrimitive(512), json["width"])
        assertEquals(JsonPrimitive(512), json["height"])
        // `id` yuborilmaydi — serverda bu stiker yo'q, uning kaliti `externalId`.
        assertNull(json["id"])
    }
}
