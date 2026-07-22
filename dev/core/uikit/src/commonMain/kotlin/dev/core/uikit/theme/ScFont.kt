package dev.core.uikit.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.core.uikit.generated.resources.Res
import dev.core.uikit.generated.resources.plus_jakarta_sans_bold
import dev.core.uikit.generated.resources.plus_jakarta_sans_extrabold
import dev.core.uikit.generated.resources.plus_jakarta_sans_medium
import dev.core.uikit.generated.resources.plus_jakarta_sans_regular
import dev.core.uikit.generated.resources.plus_jakarta_sans_semibold
import org.jetbrains.compose.resources.Font

/**
 * Plus Jakarta Sans — dizayn shrifti (400/500/600/700/800).
 *
 * `Font()` @Composable bo'lgani uchun oila kompozitsiya ichida quriladi va
 * [LocalScFontFamily] orqali tarqatiladi; `AppFontFamily` shu qiymatni o'qiydi,
 * shuning uchun mavjud ekranlarni o'zgartirmasdan hammasi yangi shriftga o'tadi.
 */
@Composable
fun rememberScFontFamily(): FontFamily = FontFamily(
    Font(Res.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(Res.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(Res.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(Res.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(Res.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
    Font(Res.font.plus_jakarta_sans_extrabold, FontWeight.Black),
)

/** [AppTheme] o'rnatadi. Tema tashqarisida — tizim shrifti. */
val LocalScFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Default }
