package dev.feature.chat.data.mapper

import dev.feature.chat.domain.model.MessageType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * `handoff/03-WEBSOCKET.md` dagi jadval — turga xos qoidalar.
 *
 * Nega klientda tekshiriladi: server `422` qaytarsa xabar ekranda "yuborilmadi" bo'lib
 * qolardi va foydalanuvchi sababini bilmasdan qayta urinaverardi.
 */
class SendPayloadTest {

    @Test
    fun textRequiresBody() {
        assertNotNull(SendPayload(MessageType.TEXT, body = "").validate())
        assertNotNull(SendPayload(MessageType.TEXT, body = "   ").validate())
        assertNull(SendPayload(MessageType.TEXT, body = "salom").validate())
        // 1–4000
        assertNull(SendPayload(MessageType.TEXT, body = "a".repeat(4000)).validate())
        assertNotNull(SendPayload(MessageType.TEXT, body = "a".repeat(4001)).validate())
    }

    @Test
    fun textCannotCarryAnAttachment() {
        assertNotNull(SendPayload(MessageType.TEXT, body = "salom", mediaId = "med_1").validate())
        assertNotNull(SendPayload(MessageType.TEXT, body = "salom", stickerId = "st_1").validate())
    }

    @Test
    fun imageVideoAndFileNeedMediaAndAllowACaption() {
        listOf(MessageType.IMAGE, MessageType.VIDEO, MessageType.FILE).forEach { type ->
            assertNotNull(SendPayload(type).validate(), "$type: mediaId'siz o'tib ketdi")
            assertNull(SendPayload(type, mediaId = "med_1").validate(), "$type")
            assertNull(SendPayload(type, mediaId = "med_1", body = "izoh").validate(), "$type")
            // Izoh ≤ 1024 — matnli xabardagi 4000 dan boshqa chegara.
            assertNull(SendPayload(type, mediaId = "med_1", body = "a".repeat(1024)).validate(), "$type")
            assertNotNull(SendPayload(type, mediaId = "med_1", body = "a".repeat(1025)).validate(), "$type")
        }
    }

    @Test
    fun voiceAndStickerRejectACaption() {
        // Izoh ataylab rad etiladi — uni chizadigan joy yo'q, ya'ni qabul qilsak
        // foydalanuvchining matni jimgina yo'qolardi.
        assertEquals(
            SendPayload.CAPTION_FORBIDDEN,
            SendPayload(MessageType.VOICE, mediaId = "med_1", body = "izoh").validate(),
        )
        assertEquals(
            SendPayload.CAPTION_FORBIDDEN,
            SendPayload(MessageType.STICKER, stickerId = "st_1", body = "izoh").validate(),
        )
        assertNull(SendPayload(MessageType.VOICE, mediaId = "med_1").validate())
        assertNull(SendPayload(MessageType.STICKER, stickerId = "st_1").validate())
        assertNotNull(SendPayload(MessageType.VOICE).validate())
        assertNotNull(SendPayload(MessageType.STICKER).validate())
    }

    @Test
    fun gifTakesMediaOrSearchResultButNotBoth() {
        val gif = gifRef()
        assertNull(SendPayload(MessageType.GIF, mediaId = "med_1").validate())
        assertNull(SendPayload(MessageType.GIF, gif = gif).validate())
        // Ikkalasi ham — xato; hech biri ham — xato.
        assertNotNull(SendPayload(MessageType.GIF, mediaId = "med_1", gif = gif).validate())
        assertNotNull(SendPayload(MessageType.GIF).validate())
        assertEquals(
            SendPayload.CAPTION_FORBIDDEN,
            SendPayload(MessageType.GIF, gif = gif, body = "izoh").validate(),
        )
    }

    @Test
    fun stickerTakesCatalogIdOrSearchResultButNotBoth() {
        val sticker = stickerRef()
        assertNull(SendPayload(MessageType.STICKER, stickerId = "st_1").validate())
        assertNull(SendPayload(MessageType.STICKER, sticker = sticker).validate())
        // Ikkalasi birga — server buni `422 STICKER_SOURCE_AMBIGUOUS` deb rad etadi,
        // shuning uchun tarmoqqa umuman chiqmaydi.
        assertEquals(
            SendPayload.STICKER_SOURCE_AMBIGUOUS,
            SendPayload(MessageType.STICKER, stickerId = "st_1", sticker = sticker).validate(),
        )
        assertEquals(
            SendPayload.CAPTION_FORBIDDEN,
            SendPayload(MessageType.STICKER, sticker = sticker, body = "izoh").validate(),
        )
    }

    @Test
    fun textCannotCarryASearchedSticker() {
        assertNotNull(SendPayload(MessageType.TEXT, body = "salom", sticker = stickerRef()).validate())
    }

    @Test
    fun systemAndCallCannotBeSent() {
        // `SYSTEM` ni faqat server yozadi, `CALL` esa hali umuman yo'q.
        assertNotNull(SendPayload(MessageType.SYSTEM, body = "x").validate())
        assertNotNull(SendPayload(MessageType.CALL, body = "x").validate())
    }

    @Test
    fun wireBodyDropsEmptyText() {
        // Bo'sh satr ham izoh sifatida rad etilishi mumkin — uni umuman yubormaymiz.
        assertNull(SendPayload(MessageType.IMAGE, mediaId = "med_1", body = "  ").wireBody)
        assertEquals("izoh", SendPayload(MessageType.IMAGE, mediaId = "med_1", body = " izoh ").wireBody)
    }

    private fun gifRef() = kotlinx.serialization.json.buildJsonObject {
        put("provider", kotlinx.serialization.json.JsonPrimitive("KLIPY"))
        put("externalId", kotlinx.serialization.json.JsonPrimitive("gif_1"))
        put("url", kotlinx.serialization.json.JsonPrimitive("https://media.klipy.com/a.mp4"))
        put("thumbUrl", kotlinx.serialization.json.JsonPrimitive("https://media.klipy.com/a.png"))
        put("width", kotlinx.serialization.json.JsonPrimitive(498))
        put("height", kotlinx.serialization.json.JsonPrimitive(280))
    }

    private fun stickerRef() = kotlinx.serialization.json.buildJsonObject {
        put("provider", kotlinx.serialization.json.JsonPrimitive("KLIPY"))
        put("externalId", kotlinx.serialization.json.JsonPrimitive("st_klipy_1"))
        put("url", kotlinx.serialization.json.JsonPrimitive("https://static.klipy.com/a.webp"))
        put("thumbUrl", kotlinx.serialization.json.JsonPrimitive("https://static.klipy.com/a_s.webp"))
        put("width", kotlinx.serialization.json.JsonPrimitive(512))
        put("height", kotlinx.serialization.json.JsonPrimitive(512))
    }
}
