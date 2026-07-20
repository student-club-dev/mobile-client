package dev.core.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder

/**
 * Navigatsiyaning "hech qayerda osilib qolmasin / qotmasin / otvormasin" qatlami.
 *
 * Uchta haqiqiy nosozlik manbasini yopadi:
 *
 * 1. **Dublikat ekran (ikki marta bosish).** Foydalanuvchi tugmani tez ikki marta bossa,
 *    Compose Navigation ikkita bir xil destination'ni stack'ka qo'yadi — natijada "Orqaga"
 *    bir marta bosilganda ekran o'zgarmaydi (ilova qotib qolgandek tuyuladi).
 *
 * 2. **"Destination not found" crash'i.** Eskirgan (allaqachon pop qilingan) ekrandan kech
 *    kelgan callback `navigate()` chaqirsa yoki argument maxsus belgi (`/ ? & #`) saqlasa,
 *    route mos kelmay `IllegalArgumentException` bilan ilova yopiladi.
 *
 * 3. **Bo'sh stack'da pop.** Start destination'da `popBackStack()` — no-op bo'lib, ekran
 *    qotgandek ko'rinadi; chaqiruvchi buni bilishi kerak.
 *
 * Yechim: har bir navigatsiya faqat manba ekran HAQIQATDA ko'rinib turganda (`RESUMED`)
 * bajariladi. Birinchi bosishdan keyin manba `RESUMED` bo'lmay qoladi, shuning uchun
 * keyingi bosishlar jimgina e'tiborsiz qoldiriladi.
 */

/**
 * Faqat joriy ekran ko'rinib turganda navigatsiya qiladi (ikki marta bosish + eskirgan
 * callback himoyasi). Sukut bo'yicha [NavOptionsBuilder.launchSingleTop] yoqilgan.
 *
 * @return `true` — navigatsiya bajarildi; `false` — e'tiborsiz qoldirildi.
 */
fun NavController.navigateSafe(
    route: String,
    builder: NavOptionsBuilder.() -> Unit = {},
): Boolean {
    if (!isCurrentEntryResumed()) return false
    navigate(route) {
        launchSingleTop = true
        builder()
    }
    return true
}

/**
 * Xavfsiz orqaga qaytish — stack bo'sh bo'lsa hech narsa qilmaydi (crash ham, qotish ham yo'q).
 * Ekran ko'rinmayotgan bo'lsa (allaqachon pop qilingan) ham e'tiborsiz qoldiradi.
 *
 * @return `true` — chindan ham orqaga qaytildi; `false` — qaytadigan ekran yo'q edi.
 *   Chaqiruvchi `false` da muqobil yo'l tanlashi mumkin (masalan ilovadan chiqish).
 */
fun NavController.popSafe(): Boolean {
    if (!isCurrentEntryResumed()) return false
    if (previousBackStackEntry == null) return false
    return popBackStack()
}

/** Joriy ekran haqiqatan ko'rinib turibdimi (o'tish animatsiyasi tugaganmi). */
private fun NavController.isCurrentEntryResumed(): Boolean =
    currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) ?: false

/**
 * Route argumenti uchun xavfsiz kodlash. `/ ? & # % + bo'sh joy` kabi belgilar route
 * shablonini buzadi va `navigate()` da crash keltirib chiqaradi — ularni foizli kodlaymiz.
 *
 * `null` yoki bo'sh qiymat uchun `null` qaytaradi (chaqiruvchi argumentni umuman qo'shmasligi
 * mumkin).
 */
fun encodeArg(value: String?): String? {
    if (value.isNullOrEmpty()) return null
    val out = StringBuilder(value.length)
    for (ch in value) {
        val safe = ch.isLetterOrDigit() || ch == '-' || ch == '_' || ch == '.' || ch == '~'
        if (safe) {
            out.append(ch)
        } else {
            // Kotlin `Char` — ASCII bo'lmaganlar uchun UTF-8 bayt ketma-ketligini kodlaymiz.
            for (byte in ch.toString().encodeToByteArray()) {
                out.append('%').append(HEX[(byte.toInt() shr 4) and 0x0F]).append(HEX[byte.toInt() and 0x0F])
            }
        }
    }
    return out.toString()
}

private const val HEX = "0123456789ABCDEF"
