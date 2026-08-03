package dev.feature.chat.data.mapper

import dev.feature.chat.data.realtime.WsAttachment
import dev.feature.chat.data.realtime.WsMessage
import dev.feature.chat.data.realtime.WsSticker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * WS `message:new` endi to'liq `MessageDto` shaklida keladi (`handoff/03-WEBSOCKET.md`) — biriktirma,
 * stiker, `albumId` va `deletedAt` bilan. Bu test aynan shu maydonlarning keshga tushishini
 * tekshiradi: ular yo'qolsa ovozli xabarning to'lqin shakli va rasm nisbati oflayn rejimda
 * qayta ochilganda g'oyib bo'lardi.
 */
class ChatMessageMappingTest {

    @Test
    fun voiceMessageKeepsWaveformAndDuration() {
        val row = WsMessage(
            id = "msg_1",
            conversationId = "cnv_1",
            senderId = "std_a",
            seq = 148,
            type = "VOICE",
            body = null,
            clientMsgId = "cmid-1",
            createdAt = "2026-07-28T09:14:22.531Z",
            attachment = WsAttachment(
                id = "med_1",
                kind = "VOICE",
                status = "READY",
                url = "/v1/media/med_1/raw",
                mimeType = "audio/mp4",
                sizeBytes = 284_100,
                durationMs = 12_400,
                waveform = listOf(12, 40, 88),
            ),
        ).toRow()

        assertEquals("VOICE", row.type)
        assertEquals("", row.body)
        assertEquals("med_1", row.attachmentId)
        assertEquals("/v1/media/med_1/raw", row.attachmentUrl)
        assertEquals(12_400L, row.attachmentDurationMs)
        assertEquals("12,40,88", row.attachmentWaveform)
        assertEquals(284_100L, row.attachmentSizeBytes)
        assertEquals(listOf(12, 40, 88), decodeWaveform(row.attachmentWaveform))
    }

    @Test
    fun albumIdComesFromTheServer() {
        // Ilgari `albumId` ni faqat jo'natuvchi bilardi va qabul qiluvchi tomonda to'r
        // qo'shni xabarlardan taxmin qilinardi. Endi u serverdan keladi.
        val row = wsImage(albumId = "alb_1").toRow()
        assertEquals("alb_1", row.albumId)
        assertEquals(1920L, row.attachmentWidth)
        assertEquals(1080L, row.attachmentHeight)
    }

    @Test
    fun deletedMessageKeepsSeqAndLosesBody() {
        val row = WsMessage(
            id = "msg_9",
            conversationId = "cnv_1",
            senderId = "std_a",
            seq = 148,
            type = "TEXT",
            body = null,
            deletedAt = "2026-07-29T10:02:11.000Z",
            createdAt = "2026-07-28T09:14:22.531Z",
        ).toRow()

        assertEquals(148L, row.seq)
        assertEquals("", row.body)
        // Vaqti pars qilingan bo'lishi shart — aks holda tombstone chizilmasdi.
        assertEquals(true, (row.deletedAt ?: 0L) > 0L)
    }

    @Test
    fun stickerMessageCarriesItsEmoji() {
        // `STICKER` da tana TAQIQLANGAN, ya'ni ko'rsatiladigan hamma narsa `sticker` da.
        val row = WsMessage(
            id = "msg_2",
            conversationId = "cnv_1",
            senderId = "std_a",
            seq = 5,
            type = "STICKER",
            body = null,
            createdAt = "2026-07-28T09:14:22.531Z",
            sticker = WsSticker(id = "st_1", packId = "pk_1", emoji = "🎓", url = "/v1/media/st_1/raw"),
        ).toRow()

        assertEquals("st_1", row.stickerId)
        assertEquals("🎓", row.stickerEmoji)
        assertEquals("/v1/media/st_1/raw", row.stickerUrl)
        assertNull(row.attachmentUrl)
    }

    @Test
    fun waveformSurvivesARoundTrip() {
        val values = (1..48).map { it * 2 }
        assertEquals(values, decodeWaveform(encodeWaveform(values)))
        // Bo'sh ro'yxat ustunni band qilmasin.
        assertNull(encodeWaveform(emptyList()))
        assertEquals(emptyList(), decodeWaveform(null))
    }

    private fun wsImage(albumId: String?) = WsMessage(
        id = "msg_3",
        conversationId = "cnv_1",
        senderId = "std_a",
        seq = 7,
        type = "IMAGE",
        body = null,
        albumId = albumId,
        createdAt = "2026-07-28T09:14:22.531Z",
        attachment = WsAttachment(
            id = "med_2",
            kind = "IMAGE",
            status = "READY",
            url = "/v1/media/med_2/raw",
            thumbUrl = "/v1/media/med_2/raw?variant=thumb",
            width = 1920,
            height = 1080,
        ),
    )
}
