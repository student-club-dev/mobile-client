package dev.feature.chat.domain.model

import dev.core.common.locale.AppLocale

/**
 * Chat domen/data qatlamining matnlari — validatsiya va xato xabarlari.
 *
 * Bu qatlam Compose'ni ko'rmaydi, shuning uchun til [AppLocale] global holatidan olinadi.
 */
object ChatDomainStrings {
    // --- Yuborish validatsiyasi (`SendPayload`) ---
    val emptyMessage get() = AppLocale.pick("The message is empty.", "Сообщение пустое.", "Xabar bo'sh.")
    fun bodyTooLong(max: Int) = AppLocale.pick(
        "The message must be no longer than $max characters.",
        "Сообщение не должно быть длиннее $max символов.",
        "Xabar $max belgidan uzun bo'lmasin.",
    )
    val textCantHaveAttachment get() = AppLocale.pick(
        "A text message can't carry an attachment.",
        "К текстовому сообщению нельзя приложить файл.",
        "Matnli xabarga biriktirma qo'shib bo'lmaydi.",
    )
    val fileNotUploaded get() = AppLocale.pick("The file wasn't uploaded.", "Файл не загружен.", "Fayl yuklanmadi.")
    fun captionTooLong(max: Int) = AppLocale.pick(
        "The caption must be no longer than $max characters.",
        "Подпись не должна быть длиннее $max символов.",
        "Izoh $max belgidan uzun bo'lmasin.",
    )
    val gifNeedsSource get() = AppLocale.pick(
        "A GIF needs either an uploaded file or a search result.",
        "Для GIF нужен загруженный файл или результат поиска.",
        "GIF uchun yo yuklangan fayl, yo qidiruv natijasi kerak.",
    )
    val voiceNotUploaded get() = AppLocale.pick("The voice message wasn't uploaded.", "Голосовое сообщение не загружено.", "Ovozli xabar yuklanmadi.")
    val stickerNotFound get() = AppLocale.pick("Sticker not found.", "Стикер не найден.", "Stiker topilmadi.")
    val videoNoteNotUploaded get() = AppLocale.pick("The video message wasn't uploaded.", "Видеосообщение не загружено.", "Video xabar yuklanmadi.")
    val unsupportedKind get() = AppLocale.pick(
        "This kind of message can't be sent.",
        "Сообщение такого типа отправить нельзя.",
        "Bu turdagi xabarni yuborib bo'lmaydi.",
    )
    val quoteNeedsReply get() = AppLocale.pick(
        "A quote can't be sent without the message it replies to.",
        "Цитату нельзя отправить без исходного сообщения.",
        "Sitata javob xabarisiz yuborilmaydi.",
    )
    val quoteEmpty get() = AppLocale.pick("The quote is empty.", "Цитата пустая.", "Sitata bo'sh.")
    fun quoteTooLong(max: Int) = AppLocale.pick(
        "The quote must be no longer than $max characters.",
        "Цитата не должна быть длиннее $max символов.",
        "Sitata $max belgidan uzun bo'lmasin.",
    )
    val quoteBadRange get() = AppLocale.pick("The quote range is invalid.", "Неверный диапазон цитаты.", "Sitataning o'rni noto'g'ri.")
    val captionNotAllowed get() = AppLocale.pick(
        "This kind of message can't have a caption.",
        "К такому сообщению нельзя добавить подпись.",
        "Bu turdagi xabarga izoh qo'shib bo'lmaydi.",
    )
    val stickerSourceUnclear get() = AppLocale.pick("The sticker source is unclear.", "Источник стикера не определён.", "Stiker manbasi noaniq.")

    // --- Repozitoriy xatolari ---
    fun albumTooLarge(max: Int) = AppLocale.pick(
        "Up to $max photos can be sent at once.",
        "За раз можно отправить до $max фото.",
        "Bir martada $max tagacha rasm yuboriladi.",
    )
    val videoUnsupported get() = AppLocale.pick(
        "Couldn't send the video — it's too large or the format isn't supported.",
        "Не удалось отправить видео — оно слишком большое или формат не поддерживается.",
        "Videoni yuborib bo'lmadi — u juda katta yoki formati qo'llab-quvvatlanmaydi.",
    )
    val cantResend get() = AppLocale.pick("This message can't be resent.", "Это сообщение нельзя отправить повторно.", "Bu xabarni qayta yuborib bo'lmaydi.")
    val imageMissing get() = AppLocale.pick("Image not found — pick it again", "Изображение не найдено — выберите заново", "Rasm topilmadi — uni qaytadan tanlang")
    val videoMissing get() = AppLocale.pick("Video not found — pick it again", "Видео не найдено — выберите заново", "Video topilmadi — uni qaytadan tanlang")
    val sendFailed get() = AppLocale.pick("The message wasn't sent", "Сообщение не отправлено", "Xabar yuborilmadi")
    val mediaServiceDown get() = AppLocale.pick(
        "Couldn't send the attachment — the media service isn't responding. Try again in a moment.",
        "Не удалось отправить вложение — медиасервис не отвечает. Попробуйте позже.",
        "Biriktirmani yuborib bo'lmadi — serverdagi media xizmati javob bermayapti. Birozdan so'ng qayta urining.",
    )

    // --- GIF qidiruvi ---
    val gifBusy get() = AppLocale.pick(
        "The GIF service is busy. Try again in a minute or two.",
        "Сервис GIF занят. Попробуйте через минуту-другую.",
        "GIF xizmati hozir band. Bir-ikki daqiqadan so'ng qayta urining.",
    )
    val searchTooFast get() = AppLocale.pick(
        "You're searching too fast. Slow down a little.",
        "Слишком частый поиск. Попробуйте помедленнее.",
        "Juda tez qidiryapsiz. Biroz sekinroq urinib ko'ring.",
    )
    val gifUnavailable get() = AppLocale.pick(
        "GIF search isn't working right now. Try again later.",
        "Поиск GIF сейчас не работает. Попробуйте позже.",
        "GIF qidiruvi hozir ishlamayapti. Keyinroq urinib ko'ring.",
    )
    val gifNotConfigured get() = AppLocale.pick(
        "GIF search isn't set up in this app.",
        "Поиск GIF не настроен в этом приложении.",
        "GIF qidiruvi bu ilovada sozlanmagan.",
    )
    val noInternet get() = AppLocale.pick(
        "No internet connection. Check your network and try again.",
        "Нет подключения к интернету. Проверьте сеть и попробуйте снова.",
        "Internet aloqasi yo'q. Ulanishni tekshirib, qayta urining.",
    )
    val gifError get() = AppLocale.pick("GIF search failed. Try again.", "Ошибка поиска GIF. Попробуйте снова.", "GIF qidiruvida xatolik. Qayta urining.")

    // --- Stiker qidiruvi ---
    val stickerBusy get() = AppLocale.pick(
        "The sticker service is busy. Try again in a minute or two.",
        "Сервис стикеров занят. Попробуйте через минуту-другую.",
        "Stiker xizmati hozir band. Bir-ikki daqiqadan so'ng qayta urining.",
    )
    val stickerUnavailable get() = AppLocale.pick(
        "Sticker search isn't working right now. You can pick from the packs below.",
        "Поиск стикеров сейчас не работает. Выберите из наборов ниже.",
        "Stiker qidiruvi hozir ishlamayapti. Pastdagi paketlardan tanlashingiz mumkin.",
    )
    val stickerNotConfigured get() = AppLocale.pick(
        "Sticker search isn't set up in this app.",
        "Поиск стикеров не настроен в этом приложении.",
        "Stiker qidiruvi bu ilovada sozlanmagan.",
    )
    val stickerError get() = AppLocale.pick("Sticker search failed. Try again.", "Ошибка поиска стикеров. Попробуйте снова.", "Stiker qidiruvida xatolik. Qayta urining.")

    // --- Emoji toifalari ---
    val emojiMood get() = AppLocale.pick("Smileys", "Смайлы", "Kayfiyat")
    val emojiPeople get() = AppLocale.pick("People", "Люди", "Odamlar")
    val emojiNature get() = AppLocale.pick("Nature", "Природа", "Tabiat")
    val emojiFood get() = AppLocale.pick("Food", "Еда", "Ovqat")
    val emojiActivity get() = AppLocale.pick("Activity", "Активность", "Faoliyat")
    val emojiTravel get() = AppLocale.pick("Travel", "Путешествия", "Sayohat")
    val emojiObjects get() = AppLocale.pick("Objects", "Объекты", "Buyumlar")
    val emojiSymbols get() = AppLocale.pick("Symbols", "Символы", "Belgilar")

    val student get() = AppLocale.pick("Student", "Студент", "Talaba")
}
