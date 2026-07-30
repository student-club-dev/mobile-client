package dev.feature.chat.data.remote

import dev.core.common.Resource
import dev.core.common.error.AppException
import dev.core.common.errorOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Media yo'lidagi xato matni.
 *
 * Muhimi — **faqat 5xx** almashtiriladi. 4xx da server aniq sabab beradi (hajm chegarasi,
 * `NOT_CONNECTED`, `MEDIA_ALREADY_USED`); uni umumiy "media xizmati ishlamayapti" bilan
 * almashtirish foydalanuvchini adashtirardi — u kutishni boshlardi, aslida rasmni
 * kichraytirishi yoki bog'lanishni tiklashi kerak edi.
 */
class MediaServerMessageTest {

    @Test
    fun `5xx aniq matnga almashadi`() {
        val res = errorOf(AppException.Server(500)).withMediaServerMessage()

        assertTrue(res is Resource.Error)
        assertEquals(MEDIA_SERVER_MESSAGE, res.message)
        // Typed xato saqlanadi — UI retry ko'rsatishni shunga qarab hal qiladi.
        assertTrue(res.error is AppException.Server)
    }

    @Test
    fun `4xx matni TEGILMAYDI`() {
        val notConnected = errorOf(
            AppException.PermissionDenied(reason = "Bu foydalanuvchi bilan yozisha olmaysiz"),
        )
        val tooBig = errorOf(AppException.Validation("Rasm hajmi 10 MB dan oshmasin."))

        assertEquals(notConnected.message, notConnected.withMediaServerMessage().messageOf())
        assertEquals(tooBig.message, tooBig.withMediaServerMessage().messageOf())
    }

    @Test
    fun `internet yo'q — o'z matni qoladi`() {
        val offline = errorOf(AppException.NoInternet())
        assertEquals(offline.message, offline.withMediaServerMessage().messageOf())
    }

    @Test
    fun `muvaffaqiyatli natija o'zgarmaydi`() {
        val ok = Resource.Success("mediaId")
        assertSame(ok, ok.withMediaServerMessage())
    }

    private fun <T> Resource<T>.messageOf(): String = (this as Resource.Error).message
}
