package dev.core.network.response

import dev.core.common.error.AppException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Qoida:** 200 dan boshqa har qanday javobda foydalanuvchi SERVERNING matnini ko'radi.
 *
 * Backend hamma joyda bir xil konvert qaytarmaydi — bu yerdagi har bir test amalda
 * uchragan (yoki uchrashi mumkin bo'lgan) javob shaklidan olingan. Bittasi buzilsa,
 * foydalanuvchi o'sha ekranda serverning sababini emas, klientning umumiy matnini
 * ko'radi va nima qilishni bilmay qoladi.
 */
class ErrorMessagesTest {

    private fun textOf(body: String, status: Int? = null): String? =
        parseErrorEnvelope(body, status)?.userMessage

    // --- O'z konvertimiz ------------------------------------------------------------

    @Test
    fun ownEnvelopeMessageIsUsed() {
        assertEquals(
            "Telefon raqam yoki parol noto'g'ri",
            textOf("""{"success":false,"status":401,"message":"Telefon raqam yoki parol noto'g'ri"}"""),
        )
    }

    @Test
    fun ownEnvelopeErrorObjectWins() {
        val body = """
            {"success":false,"status":403,
             "message":"umumiy",
             "error":{"code":"NOT_CONNECTED","message":"Avval bog'lanish kerak"}}
        """.trimIndent()
        val parsed = parseErrorEnvelope(body, 403)
        assertEquals("Avval bog'lanish kerak", parsed?.userMessage)
        assertEquals("NOT_CONNECTED", parsed?.errorCode)
    }

    @Test
    fun fieldErrorsReachTheForm() {
        val body = """
            {"success":false,"status":422,
             "error":{"message":"Ma'lumot noto'g'ri","fields":{"phoneNumber":"Noto'g'ri format"}}}
        """.trimIndent()
        val parsed = parseErrorEnvelope(body, 422)
        assertTrue(parsed is AppException.Validation)
        assertEquals("Noto'g'ri format", parsed.fields["phoneNumber"])
    }

    // --- Boshqa freymvorklarning shakllari -------------------------------------------

    @Test
    fun nestJsDefaultShapeIsUnderstood() {
        // ⚠️ `error` bu yerda SATR. Ilgari u obyekt deb o'qilardi va butun konvert
        // yiqilib, ekranda "Ma'lumot topilmadi" degan local matn chiqardi.
        assertEquals(
            "Talaba topilmadi",
            textOf("""{"statusCode":404,"message":"Talaba topilmadi","error":"Not Found"}""", 404),
        )
    }

    @Test
    fun nestJsValidationListIsJoined() {
        // NestJS `class-validator` xatolarni RO'YXAT qilib yuboradi.
        val body = """
            {"statusCode":400,
             "message":["telefon raqam noto'g'ri","parol kamida 8 belgi"],
             "error":"Bad Request"}
        """.trimIndent()
        assertEquals("telefon raqam noto'g'ri · parol kamida 8 belgi", textOf(body, 400))
    }

    @Test
    fun detailKeyIsUnderstood() {
        assertEquals("Ruxsat yo'q", textOf("""{"detail":"Ruxsat yo'q"}""", 403))
    }

    @Test
    fun nestedMessageIsFound() {
        assertEquals(
            "Kvota tugadi",
            textOf("""{"data":null,"meta":{"error":{"message":"Kvota tugadi"}}}""", 429),
        )
    }

    @Test
    fun fieldErrorsWithoutEnvelope() {
        // Laravel/DRF uslubi: `errors` — maydon → xabarlar ro'yxati.
        val parsed = parseErrorEnvelope("""{"errors":{"email":["Band"],"phone":["Xato"]}}""", 422)
        assertTrue(parsed is AppException.Validation)
        assertEquals("Band", parsed.fields["email"])
        assertEquals("Xato", parsed.fields["phone"])
    }

    @Test
    fun plainTextBodyIsUsedAsMessage() {
        assertEquals("Xizmat vaqtincha o'chirilgan", textOf("Xizmat vaqtincha o'chirilgan", 503))
    }

    // --- O'qishga arzimaydigan tanalar ------------------------------------------------

    @Test
    fun htmlPageIsIgnored() {
        // nginx 502 butun sahifani qaytaradi — uni toast'ga chiqarish ma'nosiz.
        assertNull(parseErrorEnvelope("<html><body><h1>502 Bad Gateway</h1></body></html>", 502))
    }

    @Test
    fun bodyWithoutAnyTextIsIgnored() {
        // Faqat raqamlar — ko'rsatadigan xabar yo'q, chaqiruvchi status bo'yicha zaxiraga o'tadi.
        assertNull(parseErrorEnvelope("""{"statusCode":500,"timestamp":1717171717}""", 500))
        assertNull(parseErrorEnvelope("", 500))
    }

    // --- Status bo'yicha turlar --------------------------------------------------------

    @Test
    fun statusDecidesTheType() {
        assertTrue(parseErrorEnvelope("""{"message":"a"}""", 401) is AppException.Unauthorized)
        assertTrue(parseErrorEnvelope("""{"message":"a"}""", 403) is AppException.PermissionDenied)
        assertTrue(parseErrorEnvelope("""{"message":"a"}""", 404) is AppException.NotFound)
        assertTrue(parseErrorEnvelope("""{"message":"a"}""", 500) is AppException.Server)
        // 409 uchun alohida tur yo'q — matn baribir yetib boradi.
        assertEquals("a", textOf("""{"message":"a"}""", 409))
    }

    @Test
    fun envelopeStatusBeatsHttpStatus() {
        // Ba'zi backendlar HTTP 200 qaytarib, xatoni konvert ichida yuboradi.
        val parsed = parseErrorEnvelope("""{"success":false,"status":404,"message":"Yo'q"}""", 200)
        assertTrue(parsed is AppException.NotFound)
        assertEquals("Yo'q", parsed.userMessage)
    }
}
