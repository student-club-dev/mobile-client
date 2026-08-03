package dev.core.uikit.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * StudentClub dizayn tokenlari — `StudentClub.dc.html` maketidan **aynan** ko'chirilgan.
 *
 * Bu yerdagi HEX/o'lchamlar dizaynning yagona haqiqat manbai; ekranlarda rang yoki
 * radius "o'ylab topilmaydi", faqat shu obyektdan olinadi.
 *
 * Ranglar MAVZUGA BOG'LIQ: har biri [LocalScColors] dan o'qiydi, ya'ni yorug'/qorong'i
 * rejimda avtomatik almashadi (qiymatlar — [LightScColors] va [DarkScColors]).
 * Shu sababli rang tokenlari `@Composable` — ular faqat kompozitsiya ichida o'qiladi.
 * Agar rang `Canvas { }` yoki boshqa composable bo'lmagan lambda ichida kerak bo'lsa,
 * uni lambdadan TASHQARIDA o'qib, o'zgaruvchiga oling.
 *
 * O'lchamlar va gradient yordamchisi mavzuga bog'liq emas — ular oddiy `val`.
 */
object Sc {
    /** Joriy mavzu to'q (dark) mi — och/to'q uchun turli qiymat kerak bo'lganda. */
    val IsDark: Boolean @Composable @ReadOnlyComposable get() = LocalScColors.current.dark

    // --- Brend -------------------------------------------------------------
    val Brand: Color @Composable @ReadOnlyComposable get() = LocalScColors.current.brand
    val BrandDark: Color @Composable @ReadOnlyComposable get() = LocalScColors.current.brandDark
    val BrandLight: Color @Composable @ReadOnlyComposable get() = LocalScColors.current.brandLight

    // --- Matn --------------------------------------------------------------
    val Ink: Color @Composable @ReadOnlyComposable get() = LocalScColors.current.ink
    val InkSoft: Color @Composable @ReadOnlyComposable get() = LocalScColors.current.inkSoft
    val Muted: Color @Composable @ReadOnlyComposable get() = LocalScColors.current.muted
    val MutedLight: Color @Composable @ReadOnlyComposable get() = LocalScColors.current.mutedLight

    // --- Yuzalar -----------------------------------------------------------
    val Bg: Color @Composable @ReadOnlyComposable get() = LocalScColors.current.bg
    val Card: Color @Composable @ReadOnlyComposable get() = LocalScColors.current.card
    val Border: Color @Composable @ReadOnlyComposable get() = LocalScColors.current.border

    /** Selektor/chip chegarasi — tintBlue fon ustida. */
    val BorderSoft: Color @Composable @ReadOnlyComposable get() = LocalScColors.current.borderSoft

    /** Bottom sheet tutqichi va tanlanmagan radio halqasi. */
    val Handle: Color @Composable @ReadOnlyComposable get() = LocalScColors.current.handle

    /** Neytral chip foni (filtr sheet). */
    val Chip: Color @Composable @ReadOnlyComposable get() = LocalScColors.current.chip
    val ChipInk: Color @Composable @ReadOnlyComposable get() = LocalScColors.current.chipInk

    /** Qidiruv maydoni foni (universitet sheet). */
    val FieldBg: Color @Composable @ReadOnlyComposable get() = LocalScColors.current.fieldBg

    /** Suhbat ekrani foni — nuqtali pattern ostida. */
    val ChatBg: Color @Composable @ReadOnlyComposable get() = LocalScColors.current.chatBg

    /** To'q yuza, ustidagi matn/ikona doim oq (toast, xarita yorlig'i, kamera katagi). */
    val InkSurface: Color @Composable @ReadOnlyComposable get() = LocalScColors.current.inkSurface

    /**
     * Topbar gradientining o'rta rangi — panel USTIDAGI belgi uning fonidan "kesib
     * olingandek" ko'rinishi kerak bo'lganda (masalan avatardagi "onlayn" nuqtasi halqasi).
     */
    val HeaderMid: Color @Composable @ReadOnlyComposable get() = LocalScColors.current.headerMid

    // --- Holat ranglari ----------------------------------------------------
    val Success: Color @Composable @ReadOnlyComposable get() = LocalScColors.current.success
    val Danger: Color @Composable @ReadOnlyComposable get() = LocalScColors.current.danger

    /** Pastki panelda faol bo'lmagan element. */
    val NavIdle: Color @Composable @ReadOnlyComposable get() = LocalScColors.current.navIdle

    // --- Icon plitka fonlari (tint) ---------------------------------------
    val TintBlue: Color @Composable @ReadOnlyComposable get() = LocalScColors.current.tintBlue
    val TintOrange: Color @Composable @ReadOnlyComposable get() = LocalScColors.current.tintOrange
    val TintGreen: Color @Composable @ReadOnlyComposable get() = LocalScColors.current.tintGreen
    val TintGreenDeep: Color @Composable @ReadOnlyComposable get() = LocalScColors.current.tintGreenDeep
    val TintViolet: Color @Composable @ReadOnlyComposable get() = LocalScColors.current.tintViolet
    val TintPink: Color @Composable @ReadOnlyComposable get() = LocalScColors.current.tintPink
    val TintAmber: Color @Composable @ReadOnlyComposable get() = LocalScColors.current.tintAmber

    // --- Accent iconlar ----------------------------------------------------
    val Violet: Color @Composable @ReadOnlyComposable get() = LocalScColors.current.violet
    val Orange: Color @Composable @ReadOnlyComposable get() = LocalScColors.current.orange
    val Pink: Color @Composable @ReadOnlyComposable get() = LocalScColors.current.pink

    /** Kitob ikonasining ochiq yarmi. */
    val PinkSoft: Color @Composable @ReadOnlyComposable get() = LocalScColors.current.pinkSoft

    /** Muddat chipi matni. */
    val PinkDeep: Color @Composable @ReadOnlyComposable get() = LocalScColors.current.pinkDeep
    val Amber: Color @Composable @ReadOnlyComposable get() = LocalScColors.current.amber

    // --- Splash ------------------------------------------------------------
    /** Tizim splash foni bilan bir xil tekis rang — ular orasida chok bo'lmasligi uchun. */
    val SplashSolid: Color @Composable @ReadOnlyComposable get() = LocalScColors.current.splashSolid

    // --- O'lchamlar --------------------------------------------------------
    /** Ekran gorizontal paddingi — barcha ekranlarda bir xil. */
    val ScreenPadding = 20.dp

    /** Gradient topbar gorizontal paddingi. */
    val HeaderPadding = 22.dp

    // --- Gradientlar -------------------------------------------------------
    /**
     * Topbar: `linear-gradient(150deg, #23C3F5 0%, #00ADEE 48%, #0091D8 100%)`.
     *
     * Ranglar brenddan ALOHIDA tokenlardan olinadi ([ScColors.headerTop] va h.k.) —
     * qorong'i rejimda topbar to'q ko'k bo'ladi, ekranning qolgani bilan bir oilada.
     */
    val headerBrush: Brush
        @Composable @ReadOnlyComposable
        get() = with(LocalScColors.current) {
            angledGradient(150f, 0f to headerTop, 0.48f to headerMid, 1f to headerBottom)
        }

    /** Tugma/FAB: `linear-gradient(135deg, #00ADEE, #0091D8)`. */
    val buttonBrush: Brush
        @Composable @ReadOnlyComposable
        get() = with(LocalScColors.current) {
            angledGradient(135f, 0f to brand, 1f to brandDark)
        }

    /** Katta plitka/FAB: `linear-gradient(140deg, #23C3F5, #0091D8)`. */
    val tileBrush: Brush
        @Composable @ReadOnlyComposable
        get() = with(LocalScColors.current) {
            angledGradient(140f, 0f to brandLight, 1f to brandDark)
        }

    /** Chiquvchi chat pufagi: `linear-gradient(135deg, #23C3F5, #0091D8)`. */
    val bubbleBrush: Brush
        @Composable @ReadOnlyComposable
        get() = with(LocalScColors.current) {
            angledGradient(135f, 0f to brandLight, 1f to brandDark)
        }

    /** Splash foni: `linear-gradient(150deg, top 0%, mid 46%, bottom 100%)`. */
    val splashBrush: Brush
        @Composable @ReadOnlyComposable
        get() = with(LocalScColors.current) {
            angledGradient(150f, 0f to splashTop, 0.46f to splashMid, 1f to splashBottom)
        }
}

/**
 * CSS `linear-gradient(<deg>, ...)` ning aniq ekvivalenti.
 *
 * Compose'ning sukutdagi `Brush.linearGradient()` chizilayotgan to'rtburchak
 * diagonali bo'ylab ketadi — keng va past topbar'da bu burchak dizayndagi 150°
 * dan sezilarli farq qiladi. Shuning uchun burchakni o'lchamdan hisoblaymiz:
 * CSS'da 0° — yuqoriga, soat strelkasi bo'yicha o'sadi.
 */
fun angledGradient(degrees: Float, vararg colorStops: Pair<Float, Color>): Brush =
    object : ShaderBrush() {
        override fun createShader(size: Size): Shader {
            val rad = degrees.toDouble() * kotlin.math.PI / 180.0
            // Ekran koordinatalari (y pastga) uchun yo'nalish vektori.
            val dx = sin(rad).toFloat()
            val dy = -cos(rad).toFloat()
            // Gradient chizig'ining CSS bo'yicha uzunligi.
            val len = abs(size.width * dx) + abs(size.height * dy)
            val cx = size.width / 2f
            val cy = size.height / 2f
            return LinearGradientShader(
                from = Offset(cx - dx * len / 2f, cy - dy * len / 2f),
                to = Offset(cx + dx * len / 2f, cy + dy * len / 2f),
                colors = colorStops.map { it.second },
                colorStops = colorStops.map { it.first },
                tileMode = TileMode.Clamp,
            )
        }
    }
