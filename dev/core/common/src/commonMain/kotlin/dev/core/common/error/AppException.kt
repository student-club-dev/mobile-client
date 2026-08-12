package dev.core.common.error

/**
 * Ilova bo'ylab yagona **typed xato** ierarxiyasi — oddiy `String` xabar o'rniga.
 *
 * Har bir xato [userMessage] beradi (foydalanuvchiga joriy tilda ko'rsatiladigan matn) va
 * asl [cause] ni saqlaydi (log/telemetriya uchun). Repository/UseCase qatlamlari istalgan
 * `Throwable` ni [toAppException] orqali shu turlarga aylantiradi — UI esa faqat shu
 * cheklangan to'plam bilan ishlaydi (retry ko'rsatishmi, login'ga yuborishmi va h.k.).
 */
sealed class AppException(
    val userMessage: String,
    cause: Throwable? = null,
) : Exception(userMessage, cause) {

    /**
     * Backend konvertidagi **mashina o'qiydigan** kod (`error.code`): `NOT_CONNECTED`,
     * `USER_BLOCKED`, `CALL_BUSY`, `NOT_IMPLEMENTED`, `RATE_LIMITED`…
     *
     * HTTP statusi bir xil bo'lgan holatlarni ajratish uchun kerak va **matnga qarab
     * tekshirishning o'rnini bosadi**: `403` ning o'zi «bog'lanmagansiz» mi yoki
     * «bloklangansiz» mi ekanini aytmaydi, `503` esa «server yiqildi» mi yoki
     * «qo'ng'iroq xususiyati o'chirilgan» mi ekanini (`handoff/09-CALLS-REST.md` §1).
     *
     * `null` — kod kelmadi (tarmoq xatosi, konvertsiz javob). Kod bo'yicha shoxlanganda
     * doim zaxira yo'l qoldiring: u kelmasligi normal holat.
     */
    var errorCode: String? = null
        private set

    /** Konvertdan o'qilgan kodni biriktiradi (`toAppException` ichida chaqiriladi). */
    fun withCode(value: String?): AppException = apply { if (value != null) errorCode = value }

    /** Internet yo'q — retry mazmunli. */
    class NoInternet(cause: Throwable? = null) :
        AppException(ErrorStrings.noInternet, cause)

    /** So'rov muddati tugadi. [reason] — konvertdagi `message` (bo'lsa). */
    class Timeout(cause: Throwable? = null, val reason: String? = null) :
        AppException(reason ?: ErrorStrings.timeout, cause)

    /**
     * Sessiya tugagan yoki kirilmagan — login kerak.
     *
     * [reason] — backend konvertidagi `message`. ⚠️ **Kirish** so'rovi ham 401 qaytaradi:
     * parol xato bo'lganda server "Telefon raqam yoki parol noto'g'ri" deydi va aynan
     * shuni ko'rsatish kerak. Ilgari bu matn tashlanib, hamma joyda "Sessiya tugagan"
     * chiqardi — foydalanuvchi parolini emas, sessiyasini ayblardi.
     */
    class Unauthorized(cause: Throwable? = null, val reason: String? = null) :
        AppException(reason ?: ErrorStrings.unauthorized, cause)

    /**
     * Ruxsat yo'q (403).
     *
     * [reason] — backend konvertidagi `message`. Berilsa foydalanuvchi aynan shuni ko'radi:
     * masalan chat'da `403 NOT_CONNECTED` uchun "Bu foydalanuvchi bilan yozisha olmaysiz"
     * umumiy "ruxsat yo'q" dan ancha aniqroq.
     */
    class PermissionDenied(cause: Throwable? = null, val reason: String? = null) :
        AppException(reason ?: ErrorStrings.permissionDenied, cause)

    /** Ma'lumot topilmadi (404). [reason] — backend bergan aniqroq matn (bo'lsa). */
    class NotFound(cause: Throwable? = null, val reason: String? = null) :
        AppException(reason ?: ErrorStrings.notFound, cause)

    /**
     * Server xatosi (5xx).
     *
     * [reason] — konvertdagi `message` (bo'lsa). Server o'zi tushuntirsa (masalan
     * "Xizmat vaqtincha o'chirilgan") foydalanuvchi umumiy matn o'rniga o'shani ko'radi.
     */
    class Server(val code: Int? = null, cause: Throwable? = null, val reason: String? = null) :
        AppException(reason ?: ErrorStrings.server, cause)

    /**
     * Kiritilgan ma'lumot noto'g'ri (validatsiya / 4xx).
     *
     * [fields] — backend qaytargan **maydonga bog'langan** xatolar: `{"phoneNumber": "Noto'g'ri
     * format"}`. Kalit — so'rov tanasidagi maydon nomi (masalan `UpdateProfileDto` maydoni),
     * qiymat — foydalanuvchiga ko'rsatiladigan matn. Forma ularni aynan shu maydon ostida
     * ko'rsatadi, [userMessage] esa umumiy xabar bo'lib qoladi. Bo'sh bo'lishi normal —
     * hamma 4xx ham maydon-darajali emas.
     */
    class Validation(
        val reason: String,
        val fields: Map<String, String> = emptyMap(),
        cause: Throwable? = null,
    ) : AppException(reason, cause)

    /** Boshqa/noma'lum xato. */
    class Unknown(message: String = ErrorStrings.unknown, cause: Throwable? = null) :
        AppException(message, cause)
}

/**
 * Maydonga bog'langan validatsiya xatolari — faqat [AppException.Validation] da bo'ladi,
 * qolgan turlarda bo'sh. Chaqiruvchi `is Validation` tekshiruvini takrorlamasligi uchun.
 */
val AppException.fieldErrors: Map<String, String>
    get() = (this as? AppException.Validation)?.fields.orEmpty()

/**
 * Istalgan [Throwable] ni [AppException] ga aylantiradi.
 *
 * [isOnline] — chaqiruvchi (repository) internet holatini bilsa uzatadi: offline bo'lsa
 * xato aniq [AppException.NoInternet] bo'ladi. Aks holda xato matni/turi bo'yicha taxmin
 * qilinadi (Ktor va platforma istisnolarining umumiy so'zlari).
 */
fun Throwable.toAppException(isOnline: Boolean = true): AppException {
    if (this is AppException) return this
    val msg = message?.lowercase() ?: ""
    return when {
        !isOnline -> AppException.NoInternet(this)
        msg.containsAny("permission", "denied", "permission_denied") -> AppException.PermissionDenied(this)
        msg.containsAny("unauthenticated", "unauthorized", "not authenticated", "sign in") ->
            AppException.Unauthorized(this)
        msg.containsAny("not found", "no document", "not_found") -> AppException.NotFound(this)
        msg.containsAny("timeout", "timed out", "deadline") -> AppException.Timeout(this)
        msg.containsAny("unavailable", "network", "host", "connection", "internet", "offline", "resolve") ->
            AppException.NoInternet(this)
        msg.containsAny("internal", "server", "unknown error") -> AppException.Server(cause = this)
        else -> AppException.Unknown(message ?: ErrorStrings.unknown, this)
    }
}

private fun String.containsAny(vararg needles: String): Boolean =
    needles.any { this.contains(it) }
