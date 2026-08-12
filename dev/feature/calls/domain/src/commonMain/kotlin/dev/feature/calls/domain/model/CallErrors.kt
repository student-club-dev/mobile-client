package dev.feature.calls.domain.model

/**
 * Qo'ng'iroq xato kodlari — **REST va WS uchun bitta to'plam**
 * (`handoff/09-CALLS-PROTOCOL.md` §9). Klientda bitta xato yo'li yetadi.
 *
 * Bu yerda `String` konstantalari, enum emas: kod serverdan keladi va ro'yxat kengayishi
 * mumkin — noma'lum kod hech qachon `when` ni yiqitmasligi kerak.
 */
object CallErrorCode {
    /** Socket handshake'ni o'tmadi. */
    const val UNAUTHORIZED = "UNAUTHORIZED"

    /** Faqat `call:invite` va `call:accept` da — token yangilanishi kerak. */
    const val TOKEN_EXPIRED = "TOKEN_EXPIRED"

    /** Ishtirokchi emassiz **yoki** bu hodisa uchun rol noto'g'ri. */
    const val FORBIDDEN = "FORBIDDEN"

    /** Siz bilan chaqirilgan bog'lanmagan (`call:invite`). */
    const val NOT_CONNECTED = "NOT_CONNECTED"

    /** Biri ikkinchisini bloklagan. Spec'dagi `BLOCKED` emas — mavjud kod qayta ishlatiladi. */
    const val USER_BLOCKED = "USER_BLOCKED"

    const val STUDENT_NOT_FOUND = "STUDENT_NOT_FOUND"

    /**
     * Bunday `callId` yo'q.
     *
     * ⚠️ **Eng ko'p uchraydigan sababi — qo'ng'iroq allaqachon tugagan.** Ya'ni bu xato
     * emas, «qo'ng'iroq tugadi» degani: UI shunda jimgina yopiladi.
     */
    const val CALL_NOT_FOUND = "CALL_NOT_FOUND"

    /** Chaqirilgan (yoki siz) allaqachon qo'ng'iroqda — `call:invite` ack'ida. */
    const val CALL_BUSY = "CALL_BUSY"

    /** Boshqa qurilmangiz avvalroq javob berdi — `call:accept`. */
    const val INVALID_CALL_STATE = "INVALID_CALL_STATE"

    const val VALIDATION_ERROR = "VALIDATION_ERROR"

    /** Chegaradan oshdi — **darhol qayta urinmang**, eksponensial pauza bilan. */
    const val RATE_LIMITED = "RATE_LIMITED"

    /**
     * Qo'ng'iroqlar xususiyati bu joylashtirishda o'chirilgan (`CALLS_ENABLED=false`) yoki
     * TURN sozlanmagan.
     *
     * ⚠️ Bu **kutilgan holat**, «server ishlamayapti» emas: `GET /v1/calls/ice-servers`
     * 503 qaytaradi va `call:invite` rad etiladi, qolgan hamma narsa (jonli qo'ng'iroq
     * hodisalari, `GET /v1/calls`) ishlashda davom etadi.
     */
    const val NOT_IMPLEMENTED = "NOT_IMPLEMENTED"

    const val INTERNAL_ERROR = "INTERNAL_ERROR"

    /** Foydalanuvchiga ko'rsatiladigan zaxira matn (server o'z `message` ini bermasa). */
    fun message(code: String?): String = when (code) {
        CALL_BUSY -> CallStrings.busy
        NOT_CONNECTED -> CallStrings.notConnected
        USER_BLOCKED -> CallStrings.blocked
        RATE_LIMITED -> CallStrings.rateLimited
        NOT_IMPLEMENTED -> CallStrings.notAvailable
        TOKEN_EXPIRED, UNAUTHORIZED -> CallStrings.sessionExpired
        FORBIDDEN -> CallStrings.forbidden
        INVALID_CALL_STATE -> CallStrings.answeredElsewhere
        CALL_NOT_FOUND -> CallStrings.callEnded
        else -> CallStrings.callFailed
    }
}
