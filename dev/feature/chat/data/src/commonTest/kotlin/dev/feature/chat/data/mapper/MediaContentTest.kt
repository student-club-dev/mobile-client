package dev.feature.chat.data.mapper

import dev.feature.chat.domain.model.MessageType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Backend tipli xabarni qo'llab-quvvatlamagani uchun rasm va stiker xabar **tanasidan**
 * aniqlanadi (`CHAT_MEDIA_AND_CALLS_BACKEND.md` §0). Bu evristika juda ehtiyotkor bo'lishi
 * kerak: oddiy matnni rasm deb o'ylab qolsa, foydalanuvchining xabari ekranda yo'qoladi.
 */
class MediaContentTest {

    @Test
    fun bareImageUrlIsAnImage() {
        listOf(
            "https://api.studentclub.uz/uploads/LISTING/a1.jpg",
            "https://cdn.example.uz/x.jpeg",
            "http://example.uz/y.PNG",
            "https://example.uz/z.webp",
            "https://example.uz/w.heic",
            // So'rov parametri kengaytmani yashirmasin.
            "https://example.uz/a.jpg?v=2",
        ).forEach { url ->
            assertEquals(MessageType.IMAGE, MediaContent.detect(url), url)
            assertEquals(url, MediaContent.imageUrlOrNull(url), url)
        }
    }

    @Test
    fun textAroundUrlStaysText() {
        // Foydalanuvchi havolaga izoh yozgan — bu rasm emas, oddiy xabar.
        val withComment = "manabu rasm https://example.uz/a.jpg"
        assertEquals(MessageType.TEXT, MediaContent.detect(withComment))
        assertNull(MediaContent.imageUrlOrNull(withComment))
    }

    @Test
    fun nonImageUrlStaysText() {
        listOf(
            "https://example.uz/hujjat.pdf",
            "https://example.uz/video.mp4",
            // Kengaytmasiz havola — oddiy matn bo'lib qoladi (bosiladigan havola).
            "https://studentclub.uz",
            "ftp://example.uz/a.jpg",
        ).forEach { text ->
            assertEquals(MessageType.TEXT, MediaContent.detect(text), text)
            assertNull(MediaContent.imageUrlOrNull(text), text)
        }
    }

    @Test
    fun loneEmojiIsASticker() {
        listOf(
            "😄",
            "🎓",
            "❤️", // variatsiya selektori bilan
            "👍🏽", // teri rangi modifikatori bilan
            "😀😀", // ikkitasi ham stiker sanaladi
            "🎉🎊🎁", // uchtagacha
            " 😅 ", // atrofdagi bo'shliqlar
        ).forEach { text ->
            assertEquals(MessageType.STICKER, MediaContent.detect(text), text)
        }
    }

    @Test
    fun emojiWithTextStaysText() {
        listOf(
            "😄 salom",
            "salom 😄",
            "ok",
            "",
            "   ",
            // To'rttadan ko'p bo'lsa oddiy matn — aks holda uzun qator ekranni egallardi.
            "😀😀😀😀",
        ).forEach { text ->
            assertEquals(MessageType.TEXT, MediaContent.detect(text), "«$text»")
        }
    }

    @Test
    fun stickerCatalogEmojisAreAllDetected() {
        // Panel har qanday stikerni yuborishi mumkin — hammasi qaytib kelganda ham
        // stiker bo'lib tanilishi shart, aks holda ular matn bo'lib ko'rinardi.
        dev.feature.chat.domain.model.StickerCatalog.all.forEach { sticker ->
            assertEquals(MessageType.STICKER, MediaContent.detect(sticker.emoji), sticker.emoji)
        }
    }
}
