package dev.feature.chat.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val CDN = "https://cdn.jsdelivr.net/gh/microsoft/fluentui-emoji@" +
    "62ecdc0d7ca5c6df32148c169556bc8d3782fca4/assets"

class FluentEmojiTest {

    @Test
    fun buildsUrlForPlainEmoji() {
        assertEquals("$CDN/Grinning%20face/3D/grinning_face_3d.png", FluentEmoji.urlFor("😀"))
    }

    @Test
    fun skinToneEmojiUsesDefaultVariantPath() {
        // Teri rangi variantlari bor emojilarda yo'l bir bo'g'inga uzun bo'ladi va fayl nomi
        // `_default` bilan tugaydi — bu qoida buzilsa CDN 404 qaytaradi.
        assertEquals(
            "$CDN/Thumbs%20up/Default/3D/thumbs_up_3d_default.png",
            FluentEmoji.urlFor("👍"),
        )
    }

    @Test
    fun encodesNonAsciiAndPunctuationInPath() {
        // `Piñata` — papka nomidagi harf fayl nomiga ham o'zgarishsiz o'tadi, ya'ni ikkala
        // bo'g'in ham kodlanishi kerak.
        assertEquals("$CDN/Pi%C3%B1ata/3D/pi%C3%B1ata_3d.png", FluentEmoji.urlFor("🪅"))
    }

    @Test
    fun variationSelectorIsOptional() {
        // Katalogda to'liq shakl (`U+FE0F` bilan) yotadi, klaviaturadan esa qisqasi kelishi
        // mumkin — ikkalasi ham bitta stikerga olib borishi shart.
        val withSelector = FluentEmoji.urlFor("❤️")
        assertNotNull(withSelector)
        assertEquals(withSelector, FluentEmoji.urlFor("❤"))
    }

    @Test
    fun unknownEmojiHasNoUrl() {
        // Fluent'da davlat bayroqlari umuman chizilmagan — ular tizim emojisi bo'lib qoladi.
        assertNull(FluentEmoji.urlFor("🇺🇿"))
        assertNull(FluentEmoji.urlFor("salom"))
    }

    @Test
    fun packsCoverEveryUnicodeGroup() {
        assertEquals(8, FluentEmoji.packs.size)
        FluentEmoji.packs.forEach { pack ->
            assertTrue(pack.stickers.isNotEmpty(), pack.name)
            assertNotNull(pack.coverUrl, "muqova rasmi: ${pack.name}")
        }
    }

    @Test
    fun everyStickerHasAnImage() {
        // Jadval faqat tekshirilgan papkalardan yig'iladi, ya'ni URL'siz stiker qolishi
        // generatsiyada xatolik borligini bildiradi.
        val withoutUrl = FluentEmoji.packs.flatMap { it.stickers }.filter { it.url == null }
        assertTrue(withoutUrl.isEmpty(), "URL'siz: ${withoutUrl.take(5).map { it.emoji }}")
    }
}

class StickerCatalogTest {

    @Test
    fun studentPackComesFirstAndIsFullyIllustrated() {
        val student = StickerCatalog.packs.first()
        assertEquals("student", student.id)
        val missing = student.stickers.filter { it.url == null }
        assertTrue(missing.isEmpty(), "Fluent'da yo'q: ${missing.map { it.emoji }}")
    }

    @Test
    fun catalogIsLargeEnoughToReplaceTheOldEmojiList() {
        // Eski zaxira 96 ta edi. Aniq son jadval yangilanganda o'zgaradi, shuning uchun
        // faqat kattalik tartibi tekshiriladi.
        assertTrue(StickerCatalog.all.size > 1_000, "jami: ${StickerCatalog.all.size}")
        assertEquals(9, StickerCatalog.packs.size)
    }

    @Test
    fun catalogStickersAreNeverTreatedAsServerStickers() {
        // ⚠️ Bu — eng qimmat xato bo'lardi: bu stikerlarning tasviri bor, lekin server
        // ularni BILMAYDI. `isRemote = true` bo'lib qolsa ular `stickerId` bilan yuborilib
        // har safar `422 STICKER_NOT_FOUND` olardi.
        assertTrue(StickerCatalog.all.none { it.isRemote })
    }

    @Test
    fun findsStickerByEmoji() {
        assertEquals("🎓", StickerCatalog.findByEmoji("🎓")?.emoji)
        // Bo'shliqlar kesiladi — xabar tanasi ko'chirilganda ular qo'shilib qolishi mumkin.
        assertEquals("🎓", StickerCatalog.findByEmoji("  🎓 ")?.emoji)
        assertNull(StickerCatalog.findByEmoji("salom"))
    }

    @Test
    fun everyCatalogEmojiRendersLarge() {
        // Zaxira stikerlari MATN bo'lib yuboriladi. Qaytib kelganda `isLoneEmoji` ularni
        // tanimasa, stiker o'rniga kichkina matn chizilardi — ya'ni panel yolg'on va'da
        // bergan bo'lardi.
        val notDetected = StickerCatalog.all.map { it.emoji }.filterNot(EmojiText::isLoneEmoji)
        assertTrue(notDetected.isEmpty(), "tanilmadi: ${notDetected.take(10)}")
    }

    @Test
    fun ordinaryTextIsStillNotAnEmoji() {
        // Katalog tekshiruvi qo'shilgani oddiy matnni stikerga aylantirib yubormasin.
        assertFalse(EmojiText.isLoneEmoji("salom"))
        assertFalse(EmojiText.isLoneEmoji("😀 salom"))
        assertFalse(EmojiText.isLoneEmoji(""))
    }
}
