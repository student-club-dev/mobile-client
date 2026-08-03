package dev.feature.calls.data.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Opus sozlamalarining SDP'ga yozilishi.
 *
 * Server SDP'ga **tegmaydi** (`handoff/09-CALLS-PROTOCOL.md` §13), ya'ni bu qator
 * o'zgarishsiz peer'ga yetadi — va aynan shu sababdan u to'g'ri bo'lishi shart: xato
 * `fmtp` qatori butun audio yo'lagini kelishuvdan chiqarib yuborardi.
 */
class OpusSdpTest {

    private val sdp = listOf(
        "v=0",
        "m=audio 9 UDP/TLS/RTP/SAVPF 111 103",
        "a=rtpmap:111 opus/48000/2",
        "a=fmtp:111 minptime=10;useinbandfec=0",
        "a=rtpmap:103 ISAC/16000",
    ).joinToString("\r\n")

    @Test
    fun `fec va dtx qo'shiladi`() {
        val tuned = tuneOpus(sdp)
        val line = tuned.split("\r\n").first { it.startsWith("a=fmtp:111 ") }
        assertTrue(line.contains("usedtx=1"), "DTX qo'shilmadi: $line")
        // ⚠️ Parametr allaqachon bor bo'lsa IKKINCHI marta qo'shilmaydi — takrorlangan
        // kalit ba'zi stek'larda butun `fmtp` qatorini yaroqsiz qiladi.
        assertEquals(1, Regex("useinbandfec").findAll(line).count())
    }

    /** Opus topilmasa SDP **o'zgarishsiz** qaytadi — hech narsa buzilmaydi. */
    @Test
    fun `opus bo'lmasa sdp tegilmaydi`() {
        val noOpus = "v=0\r\na=rtpmap:103 ISAC/16000\r\na=fmtp:103 x=1"
        assertEquals(noOpus, tuneOpus(noOpus))
    }

    /** `fmtp` qatori umuman bo'lmasa ham yiqilmaydi. */
    @Test
    fun `fmtp qatori bo'lmasa sdp tegilmaydi`() {
        val noFmtp = "v=0\r\na=rtpmap:111 opus/48000/2"
        assertEquals(noFmtp, tuneOpus(noFmtp))
    }
}
