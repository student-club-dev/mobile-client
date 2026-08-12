package dev.core.common.format
import dev.core.common.locale.AppLocale

/**
 * Telefon raqami va summaning YAGONA qolipi. Butun ilova shu yerdagi qoidalarga tayanadi:
 * ekranlar, ViewModel'lar va domen validatorlari.
 *
 * Qoida qisqacha:
 * - telefon — doim "+998" va undan keyin 9 xonali raqam "## ### ## ##" ko'rinishida;
 * - summa — uch xonali guruhlar, ajratgich probel: "90 000".
 *
 * Bu fayl Compose'ga bog'liq emas. Maydonlardagi jonli qolip (`VisualTransformation`)
 * `dev.core.uikit.components.AppUtils` da — u shu funksiyalar ustiga qurilgan.
 */

// ===========================================================================
// TELEFON RAQAMI
// ===========================================================================

/** Barcha raqamlarga qo'yiladigan mamlakat kodi. */
const val UZ_PHONE_CODE = "+998"

/** Milliy raqam uzunligi — bundan ortiq raqam yozib bo'lmaydi. */
const val UZ_PHONE_DIGITS = 9

/**
 * Har qanday kiritmadan 9 xonali milliy raqamni ajratadi.
 *
 * "+998 90 123 45 67" → "901234567", "998901234567" → "901234567".
 * Raqam bo'lmagan belgilar tashlanadi, 9 tadan keyingilari kesiladi.
 */
fun String.toUzPhoneDigits(): String {
    var d = filter { it.isDigit() }
    // Mamlakat kodi bilan yozilgan (yoki boshqa joydan nusxalangan) raqam.
    if (d.length > UZ_PHONE_DIGITS && d.startsWith("998")) d = d.drop(3)
    return d.take(UZ_PHONE_DIGITS)
}

/** Raqam to'liq (9 xona) kiritilganmi. */
fun String?.isUzPhoneComplete(): Boolean = orEmpty().toUzPhoneDigits().length == UZ_PHONE_DIGITS

/**
 * Saqlash va API uchun yagona ko'rinish: "90 123 45 67" → "+998901234567".
 * Raqam chala bo'lsa `null` — yarim raqam serverga ketmaydi.
 */
fun String.toUzPhoneE164(): String? =
    toUzPhoneDigits().takeIf { it.length == UZ_PHONE_DIGITS }?.let { "$UZ_PHONE_CODE$it" }

/** 9 xonali milliy raqamni "90 123 45 67" ko'rinishida formatlaydi. */
fun formatUzPhone(digits: String): String {
    val d = digits.filter { it.isDigit() }.take(UZ_PHONE_DIGITS)
    val sb = StringBuilder()
    for (i in d.indices) {
        sb.append(d[i])
        if ((i == 1 || i == 4 || i == 6) && i != d.lastIndex) sb.append(' ')
    }
    return sb.toString()
}

/**
 * Ko'rsatish uchun to'liq qolip: "+998901234567" → "+998 90 123 45 67".
 * Raqam chala/noto'g'ri bo'lsa — kelgan matn o'zgarishsiz qaytadi (eski yozuvlar uchun).
 */
fun formatUzPhoneFull(raw: String?): String {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return ""
    val digits = value.toUzPhoneDigits()
    if (digits.length != UZ_PHONE_DIGITS) return value
    return "$UZ_PHONE_CODE ${formatUzPhone(digits)}"
}

// ===========================================================================
// SUMMA
// ===========================================================================

/** Summadagi maksimal raqamlar soni — bundan ortiq yozib bo'lmaydi. */
const val MAX_AMOUNT_DIGITS = 12

/**
 * Kiritmadan toza summa raqamlarini ajratadi: harf va probellar tashlanadi,
 * boshidagi nollar olib tashlanadi ("090" → "90"), uzunlik [MAX_AMOUNT_DIGITS] gacha.
 */
fun String.toAmountDigits(): String =
    filter { it.isDigit() }.dropWhile { it == '0' }.take(MAX_AMOUNT_DIGITS)

/** "90000" → "90 000". Ajratgich — probel (uch xonali guruhlar). */
fun formatAmount(digits: String): String {
    val d = digits.filter { it.isDigit() }
    if (d.length <= 3) return d
    return d.reversed().chunked(3).joinToString(" ").reversed()
}

/** "55000" → "55 000". Narxni o'qish uchun. */
fun Long.formatAmount(): String = formatAmount(toString())

/** Pul birligi — joriy tilda ("UZS" / "сум" / "so'm"). */
fun currencyUnit(): String = AppLocale.pick(en = "UZS", ru = "сум", uz = "so'm")

/** Summani birligi bilan: "90 000 UZS". Summa bo'sh bo'lsa — bo'sh satr. */
fun formatAmountWithUnit(digits: String, unit: String = currencyUnit()): String {
    val formatted = formatAmount(digits)
    return if (formatted.isEmpty()) "" else "$formatted $unit"
}

/**
 * Tor joy (xarita markeri, kichik nishon) uchun qisqartma:
 * 21000 → "21k", 890000 → "890k", 6500000 → "6.5M".
 */
fun Long.formatAmountShort(): String = when {
    this >= 1_000_000 -> {
        val whole = this / 1_000_000
        val frac = (this % 1_000_000) / 100_000
        if (frac == 0L) "${whole}M" else "$whole.${frac}M"
    }
    this >= 1_000 -> "${this / 1_000}k"
    else -> "$this"
}
