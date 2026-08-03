package dev.core.uikit.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * UI-kit'ning rang palitrasi — yorug' va qorong'i rejim uchun ikkita to'liq to'plam.
 *
 * Ekranlar bu klassni to'g'ridan-to'g'ri ishlatmaydi: ular [Sc] orqali murojaat qiladi
 * (`Sc.Brand`, `Sc.Card`, ...), [Sc] esa joriy rejimga mos to'plamni [LocalScColors]
 * dan oladi. Shu tufayli 300+ chaqiruv joyini o'zgartirmasdan dark rejim yoqildi.
 *
 * Yangi rang qo'shilganda IKKALA to'plamga ham qiymat berish SHART — Kotlin data klassi
 * buni kompilyatsiya vaqtida majburlaydi.
 */
@Immutable
data class ScColors(
    val dark: Boolean,
    // --- Brend ---
    val brand: Color,
    val brandDark: Color,
    val brandLight: Color,
    // --- Gradient topbar (`Sc.headerBrush`) --------------------------------
    /**
     * Topbar gradientining uch to'xtash nuqtasi. Brend rangidan ALOHIDA: yorug' rejimda
     * ular aynan brend ko'ki, qorong'ida esa bir necha pog'ona to'q variant. Ilgari topbar
     * to'g'ridan-to'g'ri `brandLight/brand/brandDark` dan qurilardi va dark rejimda
     * o'zgarishsiz — ekranning qolgan qismi to'q, tepasi esa yorqin ko'k bo'lib qolardi.
     */
    val headerTop: Color,
    val headerMid: Color,
    val headerBottom: Color,
    // --- Matn ---
    val ink: Color,
    val inkSoft: Color,
    val muted: Color,
    val mutedLight: Color,
    // --- Yuzalar ---
    val bg: Color,
    val card: Color,
    val border: Color,
    val borderSoft: Color,
    val handle: Color,
    val chip: Color,
    val chipInk: Color,
    val fieldBg: Color,
    val chatBg: Color,
    /**
     * To'q yuza — ustidagi matn/ikona DOIM oq: bir martalik xabar (toast), xaritadagi
     * natija yorlig'i, galereyadagi kamera katagi.
     *
     * [ink] dan ALOHIDA: to'q rejimda matn rangi oqarib ketadi va uni fon sifatida
     * ishlatgan yuzalar oq matn bilan birga ko'rinmay qolardi.
     */
    val inkSurface: Color,
    // --- Holat ---
    val success: Color,
    val danger: Color,
    val navIdle: Color,
    // --- Ikon plitka fonlari ---
    val tintBlue: Color,
    val tintOrange: Color,
    val tintGreen: Color,
    val tintGreenDeep: Color,
    val tintViolet: Color,
    val tintPink: Color,
    val tintAmber: Color,
    // --- Accent ikonlar ---
    val violet: Color,
    val orange: Color,
    val pink: Color,
    val pinkSoft: Color,
    val pinkDeep: Color,
    val amber: Color,
    // --- Splash ---
    /** Tizim splash foni (`R.color.splash_background`) bilan bir xil bo'lishi SHART. */
    val splashSolid: Color,
    val splashTop: Color,
    val splashMid: Color,
    val splashBottom: Color,
    /** "Club" so'zining ochroq tusi. */
    val splashWordmarkAccent: Color,
    // --- Splash nishoni (shishasimon kvadrat + byust) ---
    val emblemGlassTop: Color,
    val emblemGlassBottom: Color,
    val emblemGlassBorder: Color,
    val emblemBustLight: Color,
    val emblemBustShade: Color,
    val emblemCapLight: Color,
    val emblemCapShade: Color,
    val emblemTie: Color,
    val emblemTieDark: Color,
    val emblemGold: Color,
    val emblemGoldDark: Color,
)

/** Yorug' rejim — `StudentClub.dc.html` maketidan aynan ko'chirilgan qiymatlar. */
val LightScColors = ScColors(
    dark = false,
    brand = Color(0xFF00ADEE),
    brandDark = Color(0xFF0091D8),
    brandLight = Color(0xFF23C3F5),
    // Maketdagi `linear-gradient(150deg, #23C3F5, #00ADEE 48%, #0091D8)`.
    headerTop = Color(0xFF23C3F5),
    headerMid = Color(0xFF00ADEE),
    headerBottom = Color(0xFF0091D8),
    ink = Color(0xFF0F2A43),
    inkSoft = Color(0xFF6B7A8D),
    muted = Color(0xFF8A98A8),
    mutedLight = Color(0xFFA6B2BF),
    bg = Color(0xFFEEF3F8),
    card = Color(0xFFFFFFFF),
    border = Color(0xFFE4EBF2),
    borderSoft = Color(0xFFCDEBF8),
    handle = Color(0xFFD4DEE8),
    chip = Color(0xFFF0F4F8),
    chipInk = Color(0xFF4A5A6A),
    fieldBg = Color(0xFFF6F9FC),
    chatBg = Color(0xFFE8EEF4),
    inkSurface = Color(0xFF0F2A43),
    success = Color(0xFF16B364),
    danger = Color(0xFFFF5A5A),
    navIdle = Color(0xFF93A2B2),
    tintBlue = Color(0xFFEAF7FD),
    tintOrange = Color(0xFFFFF1E8),
    tintGreen = Color(0xFFE9F8F0),
    tintGreenDeep = Color(0xFFE5F7EF),
    tintViolet = Color(0xFFEEEBFB),
    tintPink = Color(0xFFFDECEF),
    tintAmber = Color(0xFFFFF6E5),
    violet = Color(0xFF7C6BE0),
    orange = Color(0xFFF0842B),
    pink = Color(0xFFF0567C),
    pinkSoft = Color(0xFFF98FA9),
    pinkDeep = Color(0xFFE23E63),
    amber = Color(0xFFE0A020),
    splashSolid = Color(0xFF00ADEE),
    splashTop = Color(0xFF23C3F5),
    splashMid = Color(0xFF00ADEE),
    splashBottom = Color(0xFF0091D8),
    splashWordmarkAccent = Color(0xFFEAF9FF),
    emblemGlassTop = Color(0x57FFFFFF),
    emblemGlassBottom = Color(0x24FFFFFF),
    emblemGlassBorder = Color(0x73FFFFFF),
    emblemBustLight = Color(0xFFFFFFFF),
    emblemBustShade = Color(0xFFD3E4F1),
    emblemCapLight = Color(0xFFF4FAFD),
    emblemCapShade = Color(0xFFC3D9E9),
    emblemTie = Color(0xFF4A8CC2),
    emblemTieDark = Color(0xFF2B5E8C),
    emblemGold = Color(0xFFE7C34A),
    emblemGoldDark = Color(0xFFC79E1F),
)

/**
 * Qorong'i rejim.
 *
 * Ikki qoida bo'yicha olingan:
 *  - Brend ko'ki bir pog'ona OCHROQ bo'ladi (to'q fonda `#00ADEE` yetarli kontrast bermaydi) —
 *    bu `AppPalette`dagi `PrimaryDark` bilan bir xil yondashuv.
 *  - Yuzalar va tint'lar to'q ko'k-kulrang oilada qoladi (`SurfaceDark` atrofida), toza qora
 *    ishlatilmaydi — aks holda brend gradientlari "kesilgandek" ko'rinadi.
 */
val DarkScColors = ScColors(
    dark = true,
    brand = Color(0xFF23C3F5),
    brandDark = Color(0xFF00ADEE),
    brandLight = Color(0xFF4FD3FA),
    // Topbar to'q rejimda YORQIN ko'k bo'lib qolmaydi: gradient splash foni bilan bir
    // oilada (`splashTop/Mid/Bottom`) — to'q ko'k-kobalt. Ustidagi oq matn va oq aylana
    // tugmalar bu fonda ham to'liq kontrastda qoladi.
    headerTop = Color(0xFF17688F),
    headerMid = Color(0xFF0F4C6B),
    headerBottom = Color(0xFF0A374F),
    ink = Color(0xFFEAF3FA),
    inkSoft = Color(0xFF9DAFBF),
    muted = Color(0xFF8394A5),
    mutedLight = Color(0xFF6E7F90),
    bg = Color(0xFF0B1622),
    card = Color(0xFF16212E),
    border = Color(0xFF223040),
    borderSoft = Color(0xFF1E3A4C),
    handle = Color(0xFF2A3846),
    chip = Color(0xFF1A2632),
    chipInk = Color(0xFFA8B8C8),
    fieldBg = Color(0xFF131F2B),
    chatBg = Color(0xFF0D1823),
    // Fondan bir pog'ona OCHROQ (yorug' rejimda esa to'q) — ikkala holatda ham oq matn o'qiladi.
    inkSurface = Color(0xFF2A3846),
    success = Color(0xFF22C97A),
    danger = Color(0xFFFF6B6B),
    navIdle = Color(0xFF6B7C8D),
    tintBlue = Color(0xFF123040),
    tintOrange = Color(0xFF3A2A1E),
    tintGreen = Color(0xFF16332A),
    tintGreenDeep = Color(0xFF143026),
    tintViolet = Color(0xFF232043),
    tintPink = Color(0xFF3A2028),
    tintAmber = Color(0xFF3A3020),
    violet = Color(0xFF9385F0),
    orange = Color(0xFFFF9A4D),
    pink = Color(0xFFFF7396),
    pinkSoft = Color(0xFFFFA8BE),
    pinkDeep = Color(0xFFFF5C80),
    amber = Color(0xFFF0B840),
    splashSolid = Color(0xFF0F4C6B),
    splashTop = Color(0xFF17688F),
    splashMid = Color(0xFF0F4C6B),
    splashBottom = Color(0xFF0A374F),
    splashWordmarkAccent = Color(0xFFCDE9F7),
    // To'q fonda shisha va byust bir oz SO'NIQROQ — aks holda ular ko'zni qamashtiradi.
    emblemGlassTop = Color(0x3DFFFFFF),
    emblemGlassBottom = Color(0x1AFFFFFF),
    emblemGlassBorder = Color(0x59FFFFFF),
    emblemBustLight = Color(0xFFE8F1F8),
    emblemBustShade = Color(0xFFAFC6D8),
    emblemCapLight = Color(0xFFDCEAF4),
    emblemCapShade = Color(0xFF9FBBD0),
    emblemTie = Color(0xFF5C9BCE),
    emblemTieDark = Color(0xFF32688F),
    emblemGold = Color(0xFFEDCB5C),
    emblemGoldDark = Color(0xFFC9A32A),
)

/**
 * Joriy palitra. [AppTheme] o'rnatadi; sukut bo'yicha yorug' rejim (Preview'lar uchun).
 */
val LocalScColors = staticCompositionLocalOf { LightScColors }
