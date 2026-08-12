package dev.feature.calls.domain.model

import dev.core.common.locale.AppLocale

/**
 * Qo'ng'iroq bilan bog'liq foydalanuvchi matnlari — sessiya menejeri, bildirishnoma va
 * xato kodlari uchun. Ular Compose'dan TASHQARIDA (socket ishlovchisi, servis) tug'iladi,
 * shuning uchun til [AppLocale] global holatidan olinadi.
 */
object CallStrings {
    val busy get() = AppLocale.pick("The user is busy right now.", "Пользователь сейчас занят.", "Foydalanuvchi hozir band.")
    val notConnected get() = AppLocale.pick("You aren't connected with this user.", "Вы не связаны с этим пользователем.", "Bu foydalanuvchi bilan bog'lanmagansiz.")
    val blocked get() = AppLocale.pick("The call can't be placed.", "Звонок невозможен.", "Qo'ng'iroq qilib bo'lmaydi.")
    val rateLimited get() = AppLocale.pick("Too many attempts. Try again in a moment.", "Слишком много попыток. Попробуйте позже.", "Juda ko'p urinish. Birozdan so'ng qayta urining.")
    val notAvailable get() = AppLocale.pick("Calls aren't available yet.", "Звонки пока недоступны.", "Qo'ng'iroq hozircha mavjud emas.")
    val sessionExpired get() = AppLocale.pick("Your session has expired. Sign in again.", "Сессия истекла. Войдите снова.", "Sessiya tugagan. Qaytadan kiring.")
    val forbidden get() = AppLocale.pick("You don't have permission for this action.", "У вас нет прав на это действие.", "Bu amal uchun ruxsat yo'q.")
    val answeredElsewhere get() = AppLocale.pick("The call was answered on another device.", "На звонок ответили с другого устройства.", "Qo'ng'iroqqa boshqa qurilmada javob berildi.")
    val callEnded get() = AppLocale.pick("The call has ended.", "Звонок завершён.", "Qo'ng'iroq tugadi.")
    val callFailed get() = AppLocale.pick("Couldn't place the call.", "Не удалось совершить звонок.", "Qo'ng'iroqni amalga oshirib bo'lmadi.")

    val alreadyInCall get() = AppLocale.pick("You're already in a call.", "Вы уже в звонке.", "Siz allaqachon qo'ng'iroqdasiz.")
    val cantStart get() = AppLocale.pick("Couldn't start the call.", "Не удалось начать звонок.", "Qo'ng'iroqni boshlab bo'lmadi.")
    val cantAnswer get() = AppLocale.pick("Couldn't answer the call.", "Не удалось ответить на звонок.", "Qo'ng'iroqqa javob berib bo'lmaydi.")
    val micUnavailable get() = AppLocale.pick(
        "Microphone permission is missing or the device is busy.",
        "Нет разрешения на микрофон или устройство занято.",
        "Mikrofonga ruxsat berilmagan yoki qurilma band.",
    )

    // --- Bildirishnomalar (Android) ---
    val ongoingCall get() = AppLocale.pick("Call", "Звонок", "Qo'ng'iroq")
    val ongoingCallBody get() = AppLocale.pick("Call in progress", "Идёт звонок", "Qo'ng'iroq davom etmoqda")
    val callsChannel get() = AppLocale.pick("Calls", "Звонки", "Qo'ng'iroqlar")
    val incomingCallsChannel get() = AppLocale.pick("Incoming calls", "Входящие звонки", "Kiruvchi qo'ng'iroqlar")
    val incomingCallsChannelBody get() = AppLocale.pick("Calls coming to you", "Звонки, поступающие вам", "Sizga kelayotgan qo'ng'iroqlar")
    val unknownCaller get() = AppLocale.pick("Unknown number", "Неизвестный номер", "Noma'lum raqam")
    val videoCall get() = AppLocale.pick("Video call", "Видеозвонок", "Video qo'ng'iroq")
    val voiceCall get() = AppLocale.pick("Voice call", "Голосовой звонок", "Ovozli qo'ng'iroq")
    val decline get() = AppLocale.pick("Decline", "Отклонить", "Rad etish")
    val answer get() = AppLocale.pick("Answer", "Ответить", "Javob berish")
}
