package dev.feature.chat.data.mapper

import dev.feature.chat.domain.model.GifErrorKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * GIF xatolarini ajratish (`gif.md` jadvali).
 *
 * Eng muhimi — **ikkita 429**: biri provayder kvotasi (kutish kerak), ikkinchisi bizning
 * chegara (foydalanuvchini sekinlashtirish). Ular bir xil statusda keladi, ya'ni faqat
 * `error.code` ularni ajratadi; adashtirilsa foydalanuvchi teskari maslahat oladi.
 */
class GifErrorTest {

    @Test
    fun `provayder kvotasi va bizning chegara AJRATILADI`() {
        val provider = gifErrorKindOf(429, "GIF_PROVIDER_RATE_LIMITED")
        val ours = gifErrorKindOf(429, "RATE_LIMITED")

        assertEquals(GifErrorKind.PROVIDER_RATE_LIMITED, provider)
        assertEquals(GifErrorKind.RATE_LIMITED, ours)
        assertTrue(provider != ours, "Ikkala 429 bir xil turga tushib qolmasin")
        assertTrue(
            provider.userMessage != ours.userMessage,
            "Foydalanuvchi ko'radigan matn ham har xil bo'lishi kerak",
        )
    }

    @Test
    fun `502 va 503 — provayder ishlamayapti`() {
        assertEquals(GifErrorKind.PROVIDER_UNAVAILABLE, gifErrorKindOf(502, "GIF_PROVIDER_ERROR"))
        assertEquals(GifErrorKind.PROVIDER_UNAVAILABLE, gifErrorKindOf(503, "GIF_PROVIDER_ERROR"))
        // Kod kelmasa ham status yetarli.
        assertEquals(GifErrorKind.PROVIDER_UNAVAILABLE, gifErrorKindOf(502, null))
        assertEquals(GifErrorKind.PROVIDER_UNAVAILABLE, gifErrorKindOf(503, null))
    }

    @Test
    fun `kodsiz 429 — provayder kvotasi deb hisoblanadi`() {
        // Test kaliti global va soatiga 100 ta so'rov beradi: kodsiz 429 larning ko'pchiligi
        // shundan keladi, ya'ni foydalanuvchini bekorga ayblamaymiz.
        assertEquals(GifErrorKind.PROVIDER_RATE_LIMITED, gifErrorKindOf(429, null))
    }

    @Test
    fun `kod statusdan ustun`() {
        // Provayder xatosi boshqa status bilan kelsa ham kod bo'yicha aniqlanadi.
        assertEquals(GifErrorKind.RATE_LIMITED, gifErrorKindOf(400, "RATE_LIMITED"))
        assertEquals(GifErrorKind.PROVIDER_UNAVAILABLE, gifErrorKindOf(500, "GIF_PROVIDER_ERROR"))
    }

    @Test
    fun `notanish xato — UNKNOWN`() {
        assertEquals(GifErrorKind.UNKNOWN, gifErrorKindOf(418, "TEAPOT"))
        assertEquals(GifErrorKind.UNKNOWN, gifErrorKindOf(null, null))
    }

    @Test
    fun `xato kodi konvert tanasidan o'qiladi`() {
        val body = """
            {"success":false,"status":429,"code":null,"message":"Juda ko'p so'rov",
             "result":null,
             "error":{"code":"GIF_PROVIDER_RATE_LIMITED","message":"Kvota tugadi","fields":{}}}
        """.trimIndent()

        assertEquals("GIF_PROVIDER_RATE_LIMITED", apiErrorCodeOf(body))
        assertEquals(GifErrorKind.PROVIDER_RATE_LIMITED, gifErrorKindOf(429, apiErrorCodeOf(body)))
    }

    @Test
    fun `konvert bo'lmagan tana — null, status bo'yicha zaxira`() {
        assertNull(apiErrorCodeOf(""))
        assertNull(apiErrorCodeOf("<html>502 Bad Gateway</html>"))
        assertNull(apiErrorCodeOf("""{"detail":"nope"}"""))
        // Zaxira yo'l: kod yo'q, lekin status bor.
        assertEquals(
            GifErrorKind.PROVIDER_UNAVAILABLE,
            gifErrorKindOf(502, apiErrorCodeOf("<html>502 Bad Gateway</html>")),
        )
    }

    @Test
    fun `har bir xato foydalanuvchiga matn beradi`() {
        GifErrorKind.entries.forEach { kind ->
            assertTrue(kind.userMessage.isNotBlank(), "$kind matnsiz qolgan")
        }
    }
}
