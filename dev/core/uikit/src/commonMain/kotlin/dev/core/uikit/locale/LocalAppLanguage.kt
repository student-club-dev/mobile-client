package dev.core.uikit.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import dev.core.common.locale.AppLanguage

/**
 * Joriy interfeys tili — butun Compose daraxti bo'ylab tarqaladi.
 *
 * Sukut [AppLanguage.Default] (= EN): til hali o'qilmagan (birinchi kadr) yoki Composable
 * preview'da ham ekran ingliz tilida chiziladi. Til ildizda (`App`) beriladi;
 * `staticCompositionLocalOf` — qiymat kamdan-kam o'zgaradi, lekin o'zgarganda BUTUN
 * daraxt qayta chiziladi (aynan shu kerak: hamma matn almashadi).
 */
val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.Default }

/**
 * Bitta matnning uch tarjimasidan mosini beradi — alohida `Strings` obyekti ortiqcha
 * bo'ladigan joylar uchun (bitta-ikkita satr).
 *
 * Ko'p matnli ekranlar buning o'rniga o'z `…Strings` interfeysini yozadi va uni
 * [rememberStrings] orqali oladi.
 */
@Composable
@ReadOnlyComposable
fun localized(en: String, ru: String, uz: String): String = when (LocalAppLanguage.current) {
    AppLanguage.EN -> en
    AppLanguage.RU -> ru
    AppLanguage.UZ -> uz
}

/** Modulning `…En` / `…Ru` / `…Uz` obyektlaridan joriy tilga mosini tanlaydi. */
@Composable
@ReadOnlyComposable
fun <T> rememberStrings(en: T, ru: T, uz: T): T = when (LocalAppLanguage.current) {
    AppLanguage.EN -> en
    AppLanguage.RU -> ru
    AppLanguage.UZ -> uz
}
