package dev.feature.chat.data.mapper

import dev.core.network.generated.model.GifDto
import dev.core.network.generated.model.GifSearchResponseDto
import dev.core.network.generated.model.MediaProviderDto
import dev.core.network.generated.model.StickerDto
import dev.core.network.generated.model.StickerPackDto
import dev.core.network.generated.model.StickerPacksDto
import dev.feature.chat.domain.model.GifProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GifMapperTest {

    private val gif = GifDto(
        id = "1938481",
        url = "https://static.klipy.com/ii/abc/IlnkvSyF.mp4",
        thumbUrl = "https://static.klipy.com/ii/abc/M7ThMWi7.gif",
        width = 220,
        height = 230,
        durationMs = null,
    )

    @Test
    fun `qidiruv javobi domenga o'giriladi`() {
        val page = GifSearchResponseDto(
            items = listOf(gif),
            next = "2",
            provider = MediaProviderDto.KLIPY,
        ).toDomain()

        assertEquals(1, page.items.size)
        assertEquals(GifProvider.KLIPY, page.provider)
        assertEquals("2", page.next)
        assertTrue(page.hasMore)
        // `url` — MP4, `thumbUrl` — statik kadr. Ular ADASHTIRILMASLIGI kerak.
        assertTrue(page.items[0].url.endsWith(".mp4"))
        assertTrue(page.items[0].thumbUrl.endsWith(".gif"))
    }

    @Test
    fun `bo'sh kursor — oxiri`() {
        val page = GifSearchResponseDto(listOf(gif), next = "", provider = MediaProviderDto.KLIPY).toDomain()
        assertNull(page.next)
        assertTrue(!page.hasMore)
    }

    @Test
    fun `tanlangan GIF serverga O'ZGARTIRILMASDAN qaytadi`() {
        // `gif.url` domen oq ro'yxatidan o'tadi — havolaning bir belgisi o'zgarsa ham
        // `422 GIF_URL_NOT_ALLOWED` keladi.
        val item = gif.toDomain(GifProvider.KLIPY)
        val dto = item.toRef().toDto()

        assertEquals(gif.url, dto.url)
        assertEquals(gif.thumbUrl, dto.thumbUrl)
        assertEquals(gif.id, dto.externalId)
        assertEquals(gif.width, dto.width)
        assertEquals(gif.height, dto.height)
        assertEquals(MediaProviderDto.KLIPY, dto.provider)
    }

    @Test
    fun `noma'lum provayder atributsiz qolmaydi`() {
        // Atribut belgisi shartnomaviy: provayder almashsa ham panel bo'sh joy ko'rsatmasin.
        assertEquals(GifProvider.UNKNOWN, GifProvider.of("SOMETHING_NEW"))
        assertTrue(GifProvider.UNKNOWN.attribution.isNotBlank())
        assertEquals(GifProvider.KLIPY, GifProvider.of("klipy"))
    }

    @Test
    fun `stiker katalogi — bo'sh paketlar tashlanadi`() {
        val dto = StickerPacksDto(
            packs = listOf(
                StickerPackDto(
                    id = "pk_1", key = "student", name = "Talaba",
                    coverUrl = "https://cdn.example/c.webp", isDefault = true,
                    stickers = listOf(
                        StickerDto(
                            id = "st_1", packId = "pk_1", emoji = "🎓",
                            url = "https://cdn.example/1.webp", width = 512, height = 512,
                        ),
                    ),
                ),
                // Tasvirlar hali ishlab chiqilmagan paket — panelda bo'sh to'r bo'lib
                // ko'rinmasligi kerak.
                StickerPackDto(
                    id = "pk_2", key = "empty", name = "Bo'sh",
                    coverUrl = "", isDefault = false, stickers = emptyList(),
                ),
            ),
            version = 7,
        )

        val packs = dto.toDomain()
        assertEquals(1, packs.size)
        assertEquals("Talaba", packs[0].name)
        assertEquals("🎓", packs[0].cover)
        assertEquals("https://cdn.example/c.webp", packs[0].coverUrl)
        assertTrue(packs[0].stickers.single().isRemote)
    }
}
