package dev.core.uikit.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import dev.core.uikit.theme.LocalDarkTheme
import dev.core.uikit.theme.BgBottomDark
import dev.core.uikit.theme.BgBottomLight
import dev.core.uikit.theme.BgMidDark
import dev.core.uikit.theme.BgMidLight
import dev.core.uikit.theme.BgTopDark
import dev.core.uikit.theme.BgTopLight
import dev.core.uikit.theme.Ink
import dev.core.uikit.theme.InkFaint
import dev.core.uikit.theme.InkMuted
import dev.core.uikit.theme.LabelInk
import dev.core.uikit.theme.Primary
import dev.core.uikit.theme.PrimaryAccent
import dev.core.uikit.theme.PrimaryDark
import dev.core.uikit.theme.PrimaryGradientEnd
import dev.core.uikit.theme.Success
import dev.core.uikit.theme.SuccessDeep
import dev.core.uikit.theme.TextDark
import dev.core.uikit.theme.TextFaintDark
import dev.core.uikit.theme.TextMutedDark

/**
 * Auth ekranlariga xos "liquid glass" tokenlar — Material sxemasi ushlab turmaydigan
 * gradient, shisha (glass) yuza va blob ranglar. Yorug'/qorong'i rejim bo'yicha tanlanadi.
 */
@Immutable
data class AppPalette(
    val dark: Boolean,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val label: Color,
    val primary: Color,
    val primaryGradient: List<Color>,
    val bgGradient: List<Color>,
    val blobPrimary: Color,
    val blobCyan: Color,
    val glass: Color,
    val glassStrong: Color,
    val border: Color,
    val borderStrong: Color,
    val tabTrack: Color,
    val fieldBg: Color,
    val fieldFocusGlow: Color,
    val chevron: Color,
    val success: Color,
    val successDeep: Color,
    val successBg: Color,
    val onPrimary: Color,
) {
    /**
     * Bo'sh maydondagi namuna matn rangi.
     *
     * [inkFaint] dan ATAYLAB pastroq kontrastda: ilgari placeholder kiritilgan qiymat
     * bilan deyarli bir xil yorug'likda edi va maydon "to'ldirilgan" ko'rinardi
     * (`aziz@tuit.uz` namunasi haqiqiy emaildek o'qilardi).
     */
    val placeholder: Color get() = inkFaint.copy(alpha = if (dark) 0.50f else 0.60f)

    /** 135° primary gradient — tugmalar va logo uchun. */
    val primaryBrush: Brush get() = Brush.linearGradient(primaryGradient)

    /** 168° fon gradienti. */
    val bgBrush: Brush get() = Brush.linearGradient(bgGradient)
}

private val LightAppPalette = AppPalette(
    dark = false,
    ink = Ink,
    inkMuted = InkMuted,
    inkFaint = InkFaint,
    label = LabelInk,
    primary = Primary,
    primaryGradient = listOf(Primary, PrimaryGradientEnd),
    bgGradient = listOf(BgTopLight, BgMidLight, BgBottomLight),
    blobPrimary = Primary.copy(alpha = 0.28f),
    blobCyan = Color(0xFF22D3EE).copy(alpha = 0.22f),
    glass = Color.White,
    glassStrong = Color.White,
    border = Color(0xFFE4EBF2),
    borderStrong = Primary,
    tabTrack = Color(0xFFEAF7FD),
    fieldBg = Color(0xFFF6F9FC),
    fieldFocusGlow = Primary.copy(alpha = 0.10f),
    chevron = Color(0xFF93A2B2),
    success = Success,
    successDeep = SuccessDeep,
    successBg = SuccessDeep.copy(alpha = 0.06f),
    onPrimary = Color.White,
)

private val DarkAppPalette = AppPalette(
    dark = true,
    ink = TextDark,
    inkMuted = TextMutedDark,
    inkFaint = TextFaintDark,
    label = Color(0xFFB4AECD),
    primary = PrimaryDark,
    primaryGradient = listOf(PrimaryDark, PrimaryAccent),
    bgGradient = listOf(BgTopDark, BgMidDark, BgBottomDark),
    blobPrimary = PrimaryDark.copy(alpha = 0.40f),
    blobCyan = Color(0xFF22D3EE).copy(alpha = 0.20f),
    glass = Color.White.copy(alpha = 0.06f),
    glassStrong = Color.White.copy(alpha = 0.08f),
    border = Color.White.copy(alpha = 0.10f),
    borderStrong = PrimaryDark,
    tabTrack = Color.White.copy(alpha = 0.06f),
    fieldBg = Color.White.copy(alpha = 0.06f),
    fieldFocusGlow = PrimaryDark.copy(alpha = 0.18f),
    chevron = TextFaintDark,
    success = Success,
    successDeep = Success,
    successBg = Success.copy(alpha = 0.10f),
    onPrimary = Color.White,
)

/** Joriy rejimga mos auth palitrasi (foydalanuvchi mavzu tanloviga ergashadi, aks holda tizim). */
val appPalette: AppPalette
    @Composable
    @ReadOnlyComposable
    get() {
        val dark = LocalDarkTheme.current ?: isSystemInDarkTheme()
        return if (dark) DarkAppPalette else LightAppPalette
    }
