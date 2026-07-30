package dev.feature.chat.domain.model

import dev.core.common.Resource

/**
 * GIF qidiruvining **ajratilishi shart** bo'lgan xatolari (`gif.md` jadvali).
 *
 * Nega alohida tur, `AppException` emas: `AppException` — `sealed`, ya'ni unga boshqa moduldan
 * yangi voris qo'shib bo'lmaydi. Ikkalasi ham 429 bo'lgan ikki xatoni esa **farqlash shart**:
 * biri "kuting", ikkinchisi "sekinlashing" — foydalanuvchiga beriladigan maslahat teskari.
 *
 * [retriable] — o'zi o'tib ketadigan xatomi (panel "Qayta urinish" tugmasini ko'rsatadi).
 */
enum class GifErrorKind(val userMessage: String, val retriable: Boolean) {

    /**
     * `429 GIF_PROVIDER_RATE_LIMITED` — **provayder** kvotasi tugadi (hozirgi test kaliti:
     * soatiga 100 ta so'rov, GLOBAL). Foydalanuvchi hech narsa qilmagan, kutishdan boshqa
     * yo'l yo'q — shuning uchun uni "sekinroq harakat qiling" deb ayblamaymiz.
     */
    PROVIDER_RATE_LIMITED(
        "GIF xizmati hozir band. Bir-ikki daqiqadan so'ng qayta urining.",
        retriable = true,
    ),

    /**
     * `429 RATE_LIMITED` — **bizning** chegara (daqiqasiga 60 ta qidiruv). Bu haqiqatan
     * foydalanuvchiga bog'liq: yozishni sekinlashtirsa o'tib ketadi.
     */
    RATE_LIMITED(
        "Juda tez qidiryapsiz. Biroz sekinroq urinib ko'ring.",
        retriable = true,
    ),

    /**
     * `502/503 GIF_PROVIDER_ERROR` — provayder javob bermadi yoki bu deploymentda GIF
     * qidiruvi umuman sozlanmagan (kalit yo'q). Ikkalasida ham panel ochilmaydi va
     * foydalanuvchi buni tushunarli ko'rishi kerak — bo'sh ekran emas.
     */
    PROVIDER_UNAVAILABLE(
        "GIF qidiruvi hozir ishlamayapti. Keyinroq urinib ko'ring.",
        retriable = true,
    ),

    /** Internet yo'q / so'rov uzildi. */
    NETWORK(
        "Internet aloqasi yo'q. Ulanishni tekshirib, qayta urining.",
        retriable = true,
    ),

    /** Qolgan hammasi (401 ham shu yerga tushmaydi — u umumiy oqimda hal bo'ladi). */
    UNKNOWN(
        "GIF qidiruvida xatolik. Qayta urining.",
        retriable = true,
    ),
}

/**
 * [GifErrorKind] ni `Resource.Error` ichida olib yuruvchi istisno.
 *
 * `Resource.Error.cause` da uzatiladi, chunki `AppException` ierarxiyasini kengaytirib
 * bo'lmaydi (yuqoridagi izoh). UI uni [gifErrorKind] orqali o'qiydi.
 */
class GifException(
    val kind: GifErrorKind,
    cause: Throwable? = null,
) : Exception(kind.userMessage, cause)

/** Xato GIF paneliga xosmi — bo'lmasa `null` (umumiy xato matni ishlatiladi). */
val Resource.Error.gifErrorKind: GifErrorKind?
    get() = (cause as? GifException)?.kind
