package dev.core.network.response

import dev.core.common.locale.AppLocale

/**
 * Tarmoq qatlamining zaxira xato matnlari — server hech qanday o'qishga arziydigan matn
 * bermaganda ishlatiladi. Serverdan kelgan matn har doim ustun turadi (u allaqachon
 * foydalanuvchi tilida bo'lishi kutiladi).
 *
 * Compose'dan tashqarida, shuning uchun til [AppLocale] dan olinadi.
 */
internal object NetworkStrings {
    val unknown: String
        get() = AppLocale.pick(
            en = "Something went wrong.",
            ru = "Произошла неизвестная ошибка.",
            uz = "Noma'lum xatolik yuz berdi.",
        )

    /** Status kodi qavs ichida qoladi — skrinshotdan muammoni topish uchun. */
    fun rejected(status: Int): String = AppLocale.pick(
        en = "Request was rejected ($status).",
        ru = "Запрос отклонён ($status).",
        uz = "So'rov qabul qilinmadi ($status).",
    )

    fun serverError(label: String, code: Int): String = AppLocale.pick(
        en = "Server error — $label ($code). Please try again in a moment.",
        ru = "Ошибка сервера — $label ($code). Попробуйте через некоторое время.",
        uz = "Serverda xatolik — $label ($code). Birozdan so'ng qayta urining.",
    )

    fun rejectedWithLabel(label: String, code: Int): String = AppLocale.pick(
        en = "Request was rejected — $label ($code).",
        ru = "Запрос отклонён — $label ($code).",
        uz = "So'rov qabul qilinmadi — $label ($code).",
    )
}
