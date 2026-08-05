package dev.core.network.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Backend'ning **standart javob konverti** — har bir API so'rovi shu bitta shaklda qaytadi
 * (IYM-business / Ipak Yo'li naqshi). Klient hech qachon "yalang'och" JSON kutmaydi:
 * status/xato bir joyda — [ResponseChecker] — tekshiriladi.
 *
 * Backend'lar turlicha nomlaydi, shuning uchun konvert moslashuvchan:
 * - muvaffaqiyat: [success] `true` **yoki** [status] 2xx,
 * - foydali yuk: [result] **yoki** [data] (qaysi biri kelsa — [payload]),
 * - xato: [error] yoki [message].
 *
 * ⚠️ [message] va [error] **xom `JsonElement`** bo'lib o'qiladi va faqat o'qish paytida
 * satrga keltiriladi. Sabab: bir xil maydon turli shaklda keladi —
 * `"message":"Xato"`, `"message":["Xato 1","Xato 2"]` (NestJS validatsiyasi),
 * `"error":"Bad Request"` (satr) yoki `"error":{"message":…}` (obyekt). Ilgari bular
 * qat'iy `String?` / `ApiError?` deb o'qilardi va mos kelmagan shakl butun konvertni
 * yiqitardi — natijada serverning haqiqiy xabari yo'qolib, ekranda klientning umumiy
 * matni ("So'rov noto'g'ri") chiqardi.
 */
@Serializable
data class BaseResponse<T>(
    val success: Boolean = false,
    val status: Int? = null,
    val code: String? = null,
    @SerialName("message") val messageRaw: JsonElement? = null,
    val result: T? = null,
    @SerialName("data") val data: T? = null,
    @SerialName("error") val errorRaw: JsonElement? = null,
) {
    /** `result` yoki `data` — backend qaysi maydonda yuborsa. */
    val payload: T? get() = result ?: data

    /** Konvertdagi xabar — qanday shaklda kelishidan qat'i nazar bitta satr. */
    val message: String? get() = messageRaw?.flattenText()

    /**
     * Xato bloki — obyekt bo'lsa to'liq o'qiladi, satr bo'lsa faqat matni bo'lgan
     * [ApiError] quriladi.
     *
     * `null` — xato bloki umuman yo'q (yoki `null` kelgan).
     */
    val error: ApiError?
        get() = when (val raw = errorRaw) {
            null, JsonNull -> null
            is JsonObject -> ApiError(
                code = (raw["code"] as? JsonPrimitive)?.takeIf { it.isString }?.content,
                message = raw.extractErrorText(),
                fields = raw.extractFieldErrors(),
            )
            // `"error":"Bad Request"` — matnli shakl.
            else -> raw.flattenText()?.let { ApiError(message = it) }
        }

    /**
     * Foydalanuvchiga ko'rsatiladigan xato matni — [message] va [error] dan qaysi biri
     * **aniqroq** bo'lsa o'sha.
     *
     * ⚠️ Tartib `error` ning shakliga bog'liq:
     * - `"error": {...}` (bizning konvert) — u aniqroq, ustun turadi;
     * - `"error": "Not Found"` (NestJS) — bu shunchaki status nomi, konkret sabab esa
     *   `message` da (`"Talaba topilmadi"`), ya'ni o'shanda `message` ustun.
     *
     * Ilgari tartib doim `error` foydasiga edi va NestJS javoblarida foydalanuvchi
     * "Talaba topilmadi" o'rniga inglizcha "Not Found" ni ko'rardi.
     */
    val errorText: String?
        get() {
            val fromError = error?.message?.takeIf { it.isNotBlank() }
            val fromMessage = message?.takeIf { it.isNotBlank() }
            return if (errorRaw is JsonObject) fromError ?: fromMessage else fromMessage ?: fromError
        }

    /**
     * Muvaffaqiyatli javobmi. `status` bor bo'lsa — 2xx bo'yicha; yo'q bo'lsa — [success]
     * bayrog'i bo'yicha (shunda `{"success": false, "message": ...}` xato deb hisoblanadi).
     */
    val isSuccessful: Boolean
        get() = error == null && (if (status != null) status in 200..299 else success)
}

/** Standart xato tanasi — kod, matn va (validatsiya) maydon-xatolari. */
data class ApiError(
    val code: String? = null,
    val message: String? = null,
    /** Maydonga bog'langan validatsiya xatolari: {"phone": "Noto'g'ri format"}. */
    val fields: Map<String, String> = emptyMap(),
)
