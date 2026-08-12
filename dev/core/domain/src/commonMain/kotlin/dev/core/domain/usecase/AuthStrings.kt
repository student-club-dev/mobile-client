package dev.core.domain.usecase

import dev.core.common.locale.AppLocale

/**
 * Kirish/ro'yxatdan o'tish validatsiya matnlari.
 *
 * Validatsiya use-case qatlamida — Compose'dan tashqarida — bajariladi, shuning uchun til
 * [AppLocale] global holatidan o'qiladi.
 */
internal object AuthStrings {
    val invalidLogin: String
        get() = AppLocale.pick(
            en = "Enter a valid phone number or email address",
            ru = "Введите корректный номер телефона или email",
            uz = "Telefon raqami yoki email manzilini to'g'ri kiriting",
        )

    val passwordRequired: String
        get() = AppLocale.pick(
            en = "Enter your password",
            ru = "Введите пароль",
            uz = "Parolni kiriting",
        )

    val googleTokenEmpty: String
        get() = AppLocale.pick(
            en = "Google token is empty",
            ru = "Токен Google пуст",
            uz = "Google token bo'sh",
        )

    val invalidPhone: String
        get() = AppLocale.pick(
            en = "Enter the full 9-digit number",
            ru = "Введите полный 9-значный номер",
            uz = "To'liq 9 xonali raqam kiriting",
        )

    val phoneRequiredForReset: String
        get() = AppLocale.pick(
            en = "Enter the phone number to reset your password",
            ru = "Введите номер телефона для восстановления пароля",
            uz = "Parolni tiklash uchun telefon raqamini kiriting",
        )

    val invalidPhoneShort: String
        get() = AppLocale.pick(
            en = "Enter a valid phone number",
            ru = "Введите корректный номер телефона",
            uz = "Telefon raqamini to'g'ri kiriting",
        )

    val otpLength: String
        get() = AppLocale.pick(
            en = "Enter the $OTP_LENGTH-digit code",
            ru = "Введите $OTP_LENGTH-значный код",
            uz = "$OTP_LENGTH xonali kodni kiriting",
        )

    val passwordTooShort: String
        get() = AppLocale.pick(
            en = "Password must be at least $MIN_PASSWORD_LENGTH characters",
            ru = "Пароль должен содержать не менее $MIN_PASSWORD_LENGTH символов",
            uz = "Parol kamida $MIN_PASSWORD_LENGTH belgidan iborat bo'lsin",
        )
}
