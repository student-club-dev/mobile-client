package dev.feature.calls.data

import dev.feature.calls.data.realtime.CallAck
import dev.feature.calls.data.realtime.CallsSocket
import dev.feature.calls.data.realtime.WsCallEnded
import dev.feature.calls.data.realtime.WsCallIncoming
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `/calls` payload'larining shakli — `handoff/09-CALLS-PROTOCOL.md` §4 va §7.
 *
 * Nega test: WS xato ack'i **qaysi maydon buzilganini aytmaydi** (`message` doimo umumiy
 * «Ma'lumotlar noto'g'ri»). Ya'ni payload xatosi ishlab chiqarishda faqat «qo'ng'iroq
 * ishlamayapti» bo'lib ko'rinadi va sababini topib bo'lmaydi.
 */
class CallsSocketPayloadTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `ack xatosi kod va matnni o'qiydi`() {
        val ack = json.decodeFromString(
            CallAck.serializer(),
            """{"status":"error","error":{"code":"CALL_BUSY","message":"Foydalanuvchi hozir band"}}""",
        )
        assertFalse(ack.isOk)
        assertEquals("CALL_BUSY", ack.error?.code)
        assertEquals("Foydalanuvchi hozir band", ack.error?.text)
    }

    @Test
    fun `invite ack'idagi callId va relayOnly o'qiladi`() {
        val ack = json.decodeFromString(
            CallAck.serializer(),
            """{"status":"ok","callId":"3fa85f64-5717-4562-b3fc-2c963f66afa6",
               "expiresAt":"2026-08-01T09:15:07.000Z","relayOnly":true}""",
        )
        assertTrue(ack.isOk)
        assertEquals("3fa85f64-5717-4562-b3fc-2c963f66afa6", ack.callId)
        assertEquals(true, ack.relayOnly)
    }

    /**
     * `call:ended` da `endedBy` **nullable** va `durationMs` **doim son** — taymer yopgan
     * qo'ng'iroqda hech kim tugatmagan, javob berilmaganida esa davomiylik `0`.
     */
    @Test
    fun `ended hodisasida endedBy null bo'lishi mumkin`() {
        val ended = json.decodeFromString(
            WsCallEnded.serializer(),
            """{"callId":"3fa85f64-5717-4562-b3fc-2c963f66afa6","reason":"TIMEOUT","durationMs":0}""",
        )
        assertNull(ended.endedBy)
        assertEquals(0, ended.durationMs)
        assertEquals("TIMEOUT", ended.reason)
    }

    /** `caller.fullName` bo'sh satr bo'lishi mumkin — `username` ga tushiladi. */
    @Test
    fun `incoming hodisasida caller maydonlari ixtiyoriy`() {
        val incoming = json.decodeFromString(
            WsCallIncoming.serializer(),
            """{"callId":"3fa85f64-5717-4562-b3fc-2c963f66afa6","conversationId":"clx1",
               "caller":{"id":"clx2","fullName":"","username":"ali"},
               "media":"VIDEO","sdp":"v=0","relayOnly":false,
               "expiresAt":"2026-08-01T09:15:07.000Z"}""",
        )
        assertEquals("", incoming.caller.fullName)
        assertEquals("ali", incoming.caller.username)
        assertNull(incoming.caller.avatarUrl)
        assertFalse(incoming.relayOnly)
    }

    /**
     * `sdpMid` bo'sh bo'lgan nomzod **umuman yuborilmaydi**: bu nomzodlarni tugatish
     * markeri va server uni `null` sifatida qabul qilmaydi (`VALIDATION_ERROR`).
     */
    @Test
    fun `sdpMid bo'sh nomzod payloadga aylanmaydi`() {
        assertNull(CallsSocket.icePayloadOrNull("call-1", "candidate:1", sdpMid = null, sdpMLineIndex = 0))
        assertNull(CallsSocket.icePayloadOrNull("call-1", "candidate:1", sdpMid = "", sdpMLineIndex = 0))
    }

    /** Chegaradan oshgan nomzod ham yuborilmaydi — server uni baribir rad etardi. */
    @Test
    fun `chegaradan oshgan nomzod payloadga aylanmaydi`() {
        val tooLong = "a".repeat(CallsSocket.MAX_CANDIDATE + 1)
        assertNull(CallsSocket.icePayloadOrNull("call-1", tooLong, sdpMid = "0", sdpMLineIndex = 0))
        assertNull(CallsSocket.icePayloadOrNull("call-1", "candidate:1", sdpMid = "0", sdpMLineIndex = -1))
        assertNull(
            CallsSocket.icePayloadOrNull(
                "call-1",
                "candidate:1",
                sdpMid = "0",
                sdpMLineIndex = CallsSocket.MAX_MLINE_INDEX + 1,
            ),
        )
    }

    /**
     * Nomzod obyektida **aynan uchta kalit** bo'ladi.
     *
     * `RTCIceCandidate.toJSON()` ko'p platformada `usernameFragment` ni ham qo'shadi va
     * server `forbidNonWhitelisted` rejimida uni `VALIDATION_ERROR` bilan rad etadi.
     */
    @Test
    fun `nomzod obyektida faqat uchta kalit bo'ladi`() {
        val payload = CallsSocket.icePayloadOrNull("call-1", "candidate:1", "0", 0)
        val candidate = payload?.get("candidate") as? JsonObject
        assertEquals(setOf("candidate", "sdpMid", "sdpMLineIndex"), candidate?.keys)
        assertEquals(setOf("callId", "candidate"), payload?.keys)
    }
}
