package dev.core.network.response

import dev.core.common.error.AppException
import dev.core.network.appJson
import kotlinx.serialization.json.JsonElement

/**
 * **Checker** — javob konvertini bir joyda tekshiradi (IYM-business naqshi).
 *
 * Muvaffaqiyat bo'lsa [BaseResponse.payload] ni qaytaradi; aks holda status/xato bo'yicha
 * typed [AppException] tashlaydi. Shu sabab har bir data-source'da `if (response.status...)`
 * takrorlanmaydi — bitta checker hammasini hal qiladi.
 */
object ResponseChecker {

    fun <T> check(response: BaseResponse<T>): T {
        if (response.isSuccessful) {
            return response.payload
                ?: throw AppException.Server(response.status) // 2xx, lekin tana bo'sh
        }
        throw response.toAppException()
    }
}

/** Qisqartma: `response.check()` — muvaffaqiyatli payload yoki [AppException] tashlaydi. */
fun <T> BaseResponse<T>.check(): T = ResponseChecker.check(this)

/**
 * Konvertdagi status/xatoni typed [AppException] ga aylantiradi.
 *
 * [ApiError.fields] bo'lsa u [AppException.Validation.fields] ga o'tadi — shu sabab 422
 * validatsiya xatolari formagacha yetib boradi va maydon ostida ko'rsatiladi.
 *
 * [httpStatus] — konvertda `status` bo'lmaganda ishlatiladigan zaxira (HTTP javob kodi).
 */
fun BaseResponse<*>.toAppException(httpStatus: Int? = null): AppException =
    statusException(
        status = status ?: httpStatus,
        text = errorText,
        fields = error?.fields.orEmpty(),
    )
        // `error.code` — HTTP statusi ayta olmaydigan yagona narsa (403 «bog'lanmagan» mi
        // yoki «bloklangan» mi, 503 «server yiqildi» mi yoki «xususiyat o'chirilgan» mi).
        .withCode(error?.code ?: code)

/**
 * Status + serverdan olingan matn → typed [AppException].
 *
 * ⚠️ QOIDA: **200 dan boshqa har qanday kodda** foydalanuvchi serverning matnini ko'radi.
 * Status bo'yicha yozilgan local matn faqat ZAXIRA — server hech nima demaganda ishlatiladi
 * va o'shanda ham kodning o'zi qavs ichida qoladi, aks holda foydalanuvchi ham, biz ham
 * nima bo'lganini bilmay qolardik.
 *
 * [fields] bo'lsa u [AppException.Validation.fields] ga o'tadi — shu sabab validatsiya
 * xatolari formagacha yetib boradi va maydon ostida ko'rsatiladi.
 */
internal fun statusException(
    status: Int?,
    text: String?,
    fields: Map<String, String>,
): AppException {
    // Maydon xatolari bor bo'lsa status qanday bo'lishidan qat'i nazar bu — validatsiya.
    if (fields.isNotEmpty()) return AppException.Validation(text ?: validationFallback(status), fields)
    return when (status) {
        // Kirish so'rovi ham 401 beradi ("parol noto'g'ri") — matn saqlanadi.
        401 -> AppException.Unauthorized(reason = text)
        // 403/404 da backendning o'zbekcha `message` i umumiy matndan aniqroq
        // (masalan `NOT_CONNECTED` → "Avval bog'lanish kerak").
        403 -> AppException.PermissionDenied(reason = text)
        404 -> AppException.NotFound(reason = text)
        408 -> AppException.Timeout(reason = text)
        in 500..599 -> AppException.Server(status, reason = text)
        // Status noma'lum (konvertda ham, HTTP'da ham yo'q) yoki 4xx/boshqa — matn bo'lsa
        // o'zi, bo'lmasa kod bilan umumiy xabar.
        else -> AppException.Validation(text ?: validationFallback(status), fields)
    }
}

/**
 * Server hech qanday matn bermaganda ko'rsatiladigan zaxira.
 *
 * Kod qavs ichida qoladi: "So'rov qabul qilinmadi (409)" — foydalanuvchi uni skrinshotda
 * yuborsa, muammoni topish uchun shuning o'zi yetadi.
 */
private fun validationFallback(status: Int?): String =
    if (status == null) "Noma'lum xatolik yuz berdi." else "So'rov qabul qilinmadi ($status)."

/**
 * Xato javobining **tanasidan** typed xato quradi — 2xx BO'LMAGAN har qanday javob uchun.
 *
 * Non-2xx javoblar [EnvelopeUnwrapPlugin] gacha yetmaydi (Ktor `expectSuccess` ularni
 * `ResponseException` bilan tashlaydi), shuning uchun tana shu yerda — xom matndan — o'qiladi.
 *
 * Qidiruv uch bosqichda va **hech qanday shaklga bog'lanmagan**:
 * 1. o'z konvertimiz (`error.message` / `message`) — kod va maydon xatolari ham shundan;
 * 2. konvert emas, lekin JSON — matn kalitlar bo'yicha ichma-ich qidiriladi
 *    ([extractErrorText]): `detail`, `errors`, NestJS'ning ro'yxatli `message` i va h.k.;
 * 3. umuman JSON emas — tananing o'zi xabar deb olinadi ([plainErrorText]).
 *
 * `null` faqat tanada **o'qishga arziydigan hech nima** bo'lmaganda qaytadi (bo'sh tana,
 * HTML sahifa, faqat raqamlardan iborat javob) — o'shanda chaqiruvchi HTTP status bo'yicha
 * zaxira matnga o'tadi.
 */
fun parseErrorEnvelope(body: String, httpStatus: Int? = null): AppException? {
    if (body.isBlank()) return null
    val root = runCatching { appJson.parseToJsonElement(body) }.getOrNull()
        // JSON emas — oddiy matnli javob ("Ruxsat yo'q").
        ?: return plainErrorText(body)?.let { text ->
            statusException(httpStatus, text, emptyMap())
        }

    val envelope = runCatching {
        appJson.decodeFromJsonElement(BaseResponse.serializer(JsonElement.serializer()), root)
    }.getOrNull()

    // Konvertdagi qiymatlar birinchi navbatda; bo'lmasa butun daraxt bo'ylab qidiriladi.
    val text = envelope?.errorText ?: root.extractErrorText()
    val fields = envelope?.error?.fields?.takeIf { it.isNotEmpty() }
        ?: root.extractFieldErrors()
    val code = envelope?.error?.code ?: envelope?.code

    if (text == null && fields.isEmpty() && code == null) return null
    return statusException(envelope?.status ?: httpStatus, text, fields).withCode(code)
}
