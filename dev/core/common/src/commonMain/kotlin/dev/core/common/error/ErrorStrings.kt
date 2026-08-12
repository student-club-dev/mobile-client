package dev.core.common.error

import dev.core.common.locale.AppLocale

/**
 * Xato matnlari — Compose'dan TASHQARIDA (repository, use-case, tarmoq qatlami) tug'iladi,
 * shuning uchun til [AppLocale] global holatidan olinadi.
 *
 * Xato obyekti yaratilgan paytdagi til bilan matn oladi. Bu yetarli: istisno tug'iladi va
 * darhol ko'rsatiladi — orada til almashishi amalda uchramaydi.
 */
internal object ErrorStrings {
    val noInternet: String
        get() = AppLocale.pick(
            en = "No internet connection. Check your network and try again.",
            ru = "Нет подключения к интернету. Проверьте сеть и попробуйте снова.",
            uz = "Internet aloqasi yo'q. Ulanishni tekshirib, qayta urining.",
        )

    val timeout: String
        get() = AppLocale.pick(
            en = "The request timed out. Please try again.",
            ru = "Время ожидания запроса истекло. Попробуйте снова.",
            uz = "So'rov vaqti tugadi. Qayta urining.",
        )

    val unauthorized: String
        get() = AppLocale.pick(
            en = "Your session has expired. Please sign in again.",
            ru = "Сессия истекла. Пожалуйста, войдите снова.",
            uz = "Sessiya tugagan. Iltimos, qaytadan kiring.",
        )

    val permissionDenied: String
        get() = AppLocale.pick(
            en = "You don't have permission for this action.",
            ru = "У вас нет прав на это действие.",
            uz = "Bu amal uchun ruxsat yo'q.",
        )

    val notFound: String
        get() = AppLocale.pick(
            en = "Not found.",
            ru = "Данные не найдены.",
            uz = "Ma'lumot topilmadi.",
        )

    val server: String
        get() = AppLocale.pick(
            en = "Server error. Please try again in a moment.",
            ru = "Ошибка сервера. Попробуйте через некоторое время.",
            uz = "Serverda xatolik. Birozdan so'ng qayta urining.",
        )

    val unknown: String
        get() = AppLocale.pick(
            en = "Something went wrong.",
            ru = "Произошла неизвестная ошибка.",
            uz = "Noma'lum xatolik yuz berdi.",
        )
}
