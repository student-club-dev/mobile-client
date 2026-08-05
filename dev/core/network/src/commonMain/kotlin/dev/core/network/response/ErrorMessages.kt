package dev.core.network.response

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * **Xato matnini javob tanasidan qazib oluvchi** — 2xx BO'LMAGAN har qanday javob uchun.
 *
 * Nega kerak: backend hamma joyda bir xil konvert qaytarmaydi. Amalda uchraydigan shakllar:
 *
 * ```
 * {"success":false,"message":"Parol noto'g'ri"}          // o'z konvertimiz
 * {"error":{"message":"...","fields":{...}}}             // o'z konvertimiz
 * {"statusCode":404,"message":"Not Found","error":"Not Found"}   // NestJS default
 * {"statusCode":400,"message":["telefon noto'g'ri","..."],"error":"Bad Request"} // NestJS validatsiya
 * {"detail":"Ruxsat yo'q"}                               // FastAPI/DRF
 * {"errors":{"phone":["Band"]}}                          // Laravel/DRF
 * Ruxsat yo'q                                            // oddiy matn
 * ```
 *
 * Ilgari faqat birinchi ikkitasi tushunilardi, qolganlarida klient o'zining umumiy
 * matnini ("So'rov noto'g'ri", "Serverda xatolik") ko'rsatardi va serverning haqiqiy
 * javobi yo'qolib ketardi. Endi qidiruv **kalitlar bo'yicha, ichma-ich** ketadi va
 * topilgan birinchi mazmunli matn foydalanuvchiga chiqadi.
 */
internal fun JsonElement.extractErrorText(): String? =
    // Ildizning o'zi satr bo'lsa (`"Ruxsat yo'q"`) — o'sha javob.
    if (this is JsonPrimitive) flattenText() else findByKeys(MESSAGE_KEYS, depth = 0)

/**
 * Maydonga bog'langan xatolar (`{"phone": "Band"}` yoki `{"phone": ["Band"]}`).
 *
 * Forma ularni maydon ostida ko'rsatadi ([dev.core.common.error.AppException.Validation]),
 * shuning uchun ular umumiy matndan alohida qidiriladi.
 */
internal fun JsonElement.extractFieldErrors(): Map<String, String> {
    val holder = findObjectByKeys(FIELD_KEYS, depth = 0) ?: return emptyMap()
    return holder.entries
        .mapNotNull { (key, value) -> value.flattenText()?.let { key to it } }
        .toMap()
}

/**
 * JSON bo'lmagan tana — o'zini xabar deb olamiz.
 *
 * ⚠️ HTML **olinmaydi**: nginx/proxy 502'da butun sahifani qaytaradi va uni toast'ga
 * chiqarish foydalanuvchiga hech nima bermaydi. Uzun matn ham kesiladi.
 */
internal fun plainErrorText(body: String): String? {
    val text = body.trim()
    if (text.isEmpty()) return null
    if (text.startsWith("<")) return null
    if (text.length > MAX_PLAIN_LENGTH) return null
    return text
}

// ---------------------------------------------------------------------------
// Ichki qidiruv
// ---------------------------------------------------------------------------

/**
 * [keys] dagi kalitlardan birini **kenglik bo'yicha** qidiradi: avval joriy obyektning
 * o'zida, keyin ichki obyektlarda.
 *
 * Kenglik bo'yicha, chunki eng ma'noli matn odatda yuqori darajada turadi
 * (`{"message":"...","meta":{"message":"debug"}}` da birinchisi kerak).
 */
private fun JsonElement.findByKeys(keys: List<String>, depth: Int): String? {
    if (depth > MAX_DEPTH) return null
    when (this) {
        is JsonObject -> {
            for (key in keys) {
                val text = this[key]?.flattenText()
                if (text != null) return text
            }
            for (value in values) {
                value.findByKeys(keys, depth + 1)?.let { return it }
            }
        }
        is JsonArray -> for (item in this) {
            item.findByKeys(keys, depth + 1)?.let { return it }
        }
        else -> Unit
    }
    return null
}

/** [findByKeys] ning obyekt qaytaradigan varianti — maydon xatolari uchun. */
private fun JsonElement.findObjectByKeys(keys: List<String>, depth: Int): JsonObject? {
    if (depth > MAX_DEPTH) return null
    when (this) {
        is JsonObject -> {
            for (key in keys) {
                val holder = this[key] as? JsonObject ?: continue
                // Faqat "maydon → matn" ko'rinishidagi obyekt: ichida yana obyektlar
                // bo'lsa bu boshqa narsa (masalan `error` konverti).
                if (holder.values.any { it is JsonPrimitive || it is JsonArray }) return holder
            }
            for (value in values) {
                value.findObjectByKeys(keys, depth + 1)?.let { return it }
            }
        }
        is JsonArray -> for (item in this) {
            item.findObjectByKeys(keys, depth + 1)?.let { return it }
        }
        else -> Unit
    }
    return null
}

/**
 * Istalgan JSON qiymatini bitta satrga keltiradi:
 *
 * - satr → o'zi;
 * - ro'yxat → elementlar `, ` bilan qo'shiladi (NestJS validatsiya xatolari shunday keladi);
 * - obyekt → ichidan xabar kaliti izlanadi;
 * - son/`true`/`null` → `null` (bular xabar emas: `{"statusCode":404}` ni ko'rsatib
 *   bo'lmaydi).
 */
internal fun JsonElement.flattenText(): String? = when (this) {
    JsonNull -> null
    is JsonPrimitive -> content.takeIf { isString && it.isNotBlank() }
    is JsonArray -> mapNotNull { it.flattenText() }
        .takeIf { it.isNotEmpty() }
        ?.joinToString(SEPARATOR)
    is JsonObject -> MESSAGE_KEYS.firstNotNullOfOrNull { key -> this[key]?.flattenText() }
}

/**
 * Xabar saqlanadigan kalitlar — eng aniqrog'idan umumiyiga qarab.
 *
 * `error` ro'yxatda: NestJS uni satr sifatida yuboradi (`"error":"Bad Request"`), o'z
 * konvertimizda esa u obyekt — [flattenText] ikkalasini ham eplaydi.
 */
private val MESSAGE_KEYS = listOf(
    "message",
    "userMessage",
    "detail",
    "details",
    "description",
    "error_description",
    "reason",
    "msg",
    "title",
    "error",
    "errors",
)

/** Maydonga bog'langan xatolar saqlanadigan kalitlar. */
private val FIELD_KEYS = listOf("fields", "errors", "validation", "fieldErrors")

/**
 * Ro'yxatdagi bir nechta xato bitta qatorga qo'shiladi.
 *
 * Nuqta emas, `·`: bular tugallangan jumlalar emas, alohida qoidalar
 * ("telefon raqam noto'g'ri · parol kamida 8 belgi").
 */
private const val SEPARATOR = " · "

/**
 * Ichma-ich qidiruv chuqurligi. Cheklov kerak: javob tanasi ixtiyoriy chuqurlikda
 * bo'lishi mumkin va u yerdan olingan matn allaqachon xabar emas, ichki tafsilot bo'ladi.
 */
private const val MAX_DEPTH = 4

/** JSON bo'lmagan tanadan shundan uzun matn olinmaydi (bu — xabar emas, sahifa). */
private const val MAX_PLAIN_LENGTH = 400
