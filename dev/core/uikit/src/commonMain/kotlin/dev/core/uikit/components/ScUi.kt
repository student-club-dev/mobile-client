package dev.core.uikit.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.core.uikit.theme.Sc
import dev.core.uikit.locale.uiStrings

// ---------------------------------------------------------------------------
// Matn
// ---------------------------------------------------------------------------

/**
 * Dizayndagi `font-size / font-weight / color` uchligini bitta joyda yig'adi.
 * `sp` qiymatlari maketdagi `px` bilan bir xil — 1:1 nusxa.
 */
@Composable
fun scStyle(
    size: Float,
    weight: FontWeight = FontWeight.Medium,
    color: Color = Sc.Ink,
    lineHeight: Float = 0f,
    letterSpacing: Float = 0f,
): TextStyle = TextStyle(
    fontFamily = AppFontFamily,
    fontSize = size.sp,
    fontWeight = weight,
    color = color,
    lineHeight = if (lineHeight > 0f) lineHeight.sp else TextStyle.Default.lineHeight,
    letterSpacing = letterSpacing.sp,
)

@Composable
fun ScText(
    text: String,
    size: Float,
    weight: FontWeight = FontWeight.Medium,
    color: Color = Sc.Ink,
    modifier: Modifier = Modifier,
    lineHeight: Float = 0f,
    letterSpacing: Float = 0f,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text,
        modifier = modifier,
        style = scStyle(size, weight, color, lineHeight, letterSpacing),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

// ---------------------------------------------------------------------------
// Soya — dizaynda hamma soya juda yumshoq (spread manfiy, blur katta)
// ---------------------------------------------------------------------------

/** Karta soyasi: `0 10px 24px -18px rgba(15,42,67,0.3)`. */
// `Sc.Ink` mavzuga bog'liq bo'lgani uchun `@Composable` (yonidagi `scCard` kabi).
@Composable
fun Modifier.scCardShadow(elevation: Dp = 8.dp, shape: Shape): Modifier =
    shadow(elevation, shape, ambientColor = Sc.Ink.copy(alpha = 0.22f), spotColor = Sc.Ink.copy(alpha = 0.30f))

/**
 * Aylana tugmalar soyasi: `0 8px 20px -6px rgba(11,22,34,0.35)`.
 *
 * Rang SHAFFOF bo'lishi shart. To'liq to'q rang berilsa Android soyani to'yingan qora
 * dog' qilib chizadi va oq doira atrofida iflos halqa paydo bo'ladi.
 */
fun Modifier.scSoftShadow(elevation: Dp = 6.dp, shape: Shape): Modifier =
    shadow(
        elevation, shape,
        ambientColor = Color(0xFF0B1622).copy(alpha = 0.25f),
        spotColor = Color(0xFF0B1622).copy(alpha = 0.35f),
    )

/** Brend rangidagi yorqin soya — gradient tugma/FAB ostida. */
@Composable
fun Modifier.scBrandShadow(elevation: Dp = 14.dp, shape: Shape): Modifier =
    shadow(elevation, shape, ambientColor = Sc.Brand.copy(alpha = 0.5f), spotColor = Sc.Brand.copy(alpha = 0.7f))

/**
 * Oq karta: `background #FFF`, `1px solid #E4EBF2`, radius + yumshoq soya.
 * `onClick` berilsa bosiladigan bo'ladi (ripple'siz — dizaynda ripple yo'q).
 */
@Composable
fun Modifier.scCard(
    radius: Dp = 24.dp,
    elevation: Dp = 8.dp,
    background: Color = Sc.Card,
    borderColor: Color = Sc.Border,
    borderWidth: Dp = 1.dp,
    onClick: (() -> Unit)? = null,
): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .scCardShadow(elevation, shape)
        .clip(shape)
        .background(background)
        .border(borderWidth, borderColor, shape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
}

/**
 * Ekran tepasidagi bo'shliq — **haqiqiy** status bar inseti + dizayndagi qo'shimcha oraliq.
 *
 * Ilgari ekranlarda `padding(top = 54.dp)` yozilardi: bu status bar balandligini taxmin
 * qilingan qiymat bilan almashtiradi va o'yiq (notch), planshet yoki landshaft rejimida
 * kontentni status bar ostiga tiqib qo'yadi yoki ortiqcha bo'shliq qoldiradi.
 */
fun Modifier.scTopInset(extra: Dp = 14.dp): Modifier = statusBarsPadding().padding(top = extra)

// ---------------------------------------------------------------------------
// Gradient topbar
// ---------------------------------------------------------------------------

/**
 * Har bir ekranning tepasidagi gradient panel: 150° gradient, pastki burchaklari
 * yumaloq va ichida ikkita shaffof dekorativ doira.
 *
 * Tizim status bar'i ostidan o'tadi (edge-to-edge), shuning uchun ichki kontent
 * `statusBarsPadding()` bilan suriladi — maketdagi soxta status bar qatorining o'rniga.
 */
@Composable
fun ScHeader(
    modifier: Modifier = Modifier,
    bottomRadius: Dp = 34.dp,
    horizontalPadding: Dp = Sc.HeaderPadding,
    bottomPadding: Dp = 22.dp,
    // Status bar'dan keyingi bo'shliq. Maketdagi `padding-top: 14px` soxta status bar
    // qatoridan OLDIN turadi — haqiqiy status bar uni allaqachon egallaydi, shuning uchun 0.
    topPadding: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Ko'k gradient ustida qora status bar belgilari o'qilmaydi.
    StatusBarAppearance(darkIcons = false)
    val shape = RoundedCornerShape(bottomStart = bottomRadius, bottomEnd = bottomRadius)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Sc.headerBrush),
    ) {
        // Dekorativ doiralar — maketdagi `rgba(255,255,255,.10)` / `.08`.
        //
        // MUHIM: `matchParentSize()` ichida turishi shart. Aks holda 196dp'lik doira
        // Box'ning o'lchamiga qo'shiladi va panel HAMISHA kamida 196dp bo'lib qoladi —
        // kontent kichrayganda ham fon kichraymaydi. CSS'da ular `position: absolute`,
        // ya'ni oqimdan tashqarida; `matchParentSize` — shuning aynan ekvivalenti.
        Box(Modifier.matchParentSize()) {
            Box(
                Modifier.align(Alignment.TopEnd)
                    .offset(x = 40.dp, y = (-60).dp)
                    .size(196.dp)
                    .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(percent = 50)),
            )
            Box(
                Modifier.align(Alignment.BottomStart)
                    .offset(x = (-30).dp, y = 70.dp)
                    .size(156.dp)
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(percent = 50)),
            )
        }
        Column(
            Modifier
                .statusBarsPadding()
                .padding(start = horizontalPadding, end = horizontalPadding, top = topPadding, bottom = bottomPadding),
            content = content,
        )
    }
}

/** Topbar sarlavhasi — 24sp/800 oq. */
@Composable
fun ScHeaderTitle(text: String, size: Float = 24f, modifier: Modifier = Modifier) {
    ScText(text, size, FontWeight.ExtraBold, Color.White, modifier, letterSpacing = -0.5f, maxLines = 1)
}

/** Topbar ostidagi izoh matni. */
@Composable
fun ScHeaderSubtitle(text: String, modifier: Modifier = Modifier) {
    ScText(text, 13.5f, FontWeight.Medium, Color.White.copy(alpha = 0.9f), modifier, lineHeight = 19.5f)
}

/**
 * **Orqaga tugmasining yagona o'lchami** — ilovadagi HAMMA ekranda bir xil.
 *
 * Ilgari har ekran o'zicha berardi: 40, 42, 44, 46 dp va bir nechta "kvadrat" variant.
 * Ekrandan ekranga o'tganda tugma sezilarli sakrardi, ba'zilari esa sarlavhadan kattaroq
 * bo'lib ko'zga urilardi (bug hisoboti #28, #35). 38 dp — Material'ning 48 dp tegish
 * maydonidan kichik emas (tugma atrofidagi bo'sh joy tegishga kiradi), lekin vizual
 * jihatdan sarlavha bilan muvozanatda.
 */
val ScBackButtonSize = 38.dp

/** [ScBackButtonSize] ichidagi belgi o'lchami. */
val ScBackButtonIconSize = 17.dp

/**
 * Orqaga qaytish tugmasi — ilovadagi YAGONA ko'rinish.
 *
 * Ekranlar `ScCircleButton` ni o'z o'lchami bilan chaqirish o'rniga shuni ishlatadi,
 * shunda o'lcham bitta joydan boshqariladi.
 */
@Composable
fun ScBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    icon: ImageVector = ScIcons.ChevronLeft,
) {
    ScCircleButton(
        icon = icon,
        onClick = onClick,
        modifier = modifier,
        size = ScBackButtonSize,
        iconSize = ScBackButtonIconSize,
        contentDescription = contentDescription,
    )
}

/**
 * Topbar'dagi aylana tugma. Orqaga qaytish uchun hamisha `‹` ([ScIcons.ChevronLeft])
 * ishlatiladi — X emas.
 *
 * Rangi MAVZUGA bog'liq ([scGlassButtonColors]): yorug'da oq fon + `brandDark` ikona
 * (maketdagidek), to'q rejimda esa shaffof oq fon + OQ ikona. To'q rejimda eski qiymatlar
 * qolib ketsa, gradient topbar to'qlashgani holda uning ustidagi tugmalar yorqin oq
 * doiralar, ichidagi belgilar esa och ko'k bo'lib "yorug' rejimdan qolib ketgandek"
 * ko'rinardi.
 */
@Composable
fun ScCircleButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    iconSize: Dp = 20.dp,
    background: Color = scGlassButtonColors().first,
    tint: Color = scGlassButtonColors().second,
    contentDescription: String? = null,
    badge: Boolean = false,
    badgeColor: Color = Sc.Danger,
) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier
            .size(size)
            .scSoftShadow(6.dp, shape)
            .clip(shape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(iconSize))
        if (badge) {
            Box(
                Modifier.align(Alignment.TopEnd)
                    .padding(top = 7.dp, end = 8.dp)
                    .size(8.dp)
                    .background(badgeColor, RoundedCornerShape(percent = 50))
                    .border(1.5.dp, background, RoundedCornerShape(percent = 50)),
            )
        }
    }
}

/**
 * Gradient topbar ustidagi tugmaning foni va ikona rangi — `fon to ikona`.
 *
 * Yagona joyda, chunki bir xil qoida uchta joyda kerak: [ScCircleButton], bosh ekranning
 * o'z aylana tugmasi va profil sarlavhasidagi tugma.
 */
@Composable
@ReadOnlyComposable
fun scGlassButtonColors(): Pair<Color, Color> =
    if (Sc.IsDark) Color.White.copy(alpha = 0.18f) to Color.White else Color.White to Sc.BrandDark

// ---------------------------------------------------------------------------
// Bo'lim sarlavhasi
// ---------------------------------------------------------------------------

/**
 * "Siz uchun … Barchasi ›" qatori. [subtitle] berilsa sarlavha ostida kulrang izoh
 * chiqadi (dizaynda sarlavha bilan izoh oralig'i 3px, izohdan keyin 13px).
 */
@Composable
fun ScSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: String? = "Barchasi",
    actionIcon: ImageVector? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScText(
                title, 19f, FontWeight.ExtraBold, Sc.Ink,
                Modifier.weight(1f), letterSpacing = -0.3f, maxLines = 2,
            )
            if (action != null) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .then(if (onAction != null) Modifier.clickable(onClick = onAction) else Modifier)
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (actionIcon != null) {
                        Icon(actionIcon, null, tint = Sc.Brand, modifier = Modifier.size(15.dp))
                    }
                    ScText(action, 13f, FontWeight.Bold, Sc.Brand, maxLines = 1)
                    if (actionIcon == null) {
                        Icon(ScIcons.ChevronRight, null, tint = Sc.Brand, modifier = Modifier.size(13.dp))
                    }
                }
            }
        }
        if (subtitle != null) {
            Spacer(Modifier.height(3.dp))
            ScText(subtitle, 13f, FontWeight.Medium, Sc.Muted)
        }
    }
}

@Composable
fun ScIconTile(
    background: Color,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    radius: Dp = 18.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier.size(size).background(background, RoundedCornerShape(radius)),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * Kvadrat belgi — universitet qisqartmasi (`TATU`, `SamDVU`), ism bosh harflari va h.k.
 *
 * [fontSize] — **uch belgili** matn uchun o'lchov: qisqartmalar 2 dan 6 belgigacha bo'ladi
 * (`IT` … `SamDVU`) va bitta o'lchov bilan uzunlari kvadratdan chiqib ketardi. Shuning uchun
 * o'lcham matn uzunligiga qarab kichrayadi — kesib tashlash yoki uch nuqta chiqarishdan
 * ko'ra shu yaxshi: qisqartmaning oxirgi harfi (muassasa turi) ma'no tashiydi.
 */
@Composable
fun ScMonogramTile(
    text: String,
    background: Color,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 62.dp,
    radius: Dp = 24.dp,
    fontSize: Float = 24f,
) {
    val scaled = when (text.length) {
        0, 1, 2, 3 -> fontSize
        4 -> fontSize * 0.84f
        5 -> fontSize * 0.70f
        else -> fontSize * 0.60f
    }
    ScIconTile(background, modifier, size, radius) {
        ScText(text, scaled, FontWeight.ExtraBold, color, maxLines = 1)
    }
}

@Composable
fun ScGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    radius: Dp = 18.dp,
    verticalPadding: Dp = 15.dp,
    fontSize: Float = 15.5f,
    weight: FontWeight = FontWeight.ExtraBold,
    brush: Brush = Sc.buttonBrush,
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .fillMaxWidth()
            .scBrandShadow(12.dp, shape)
            .clip(shape)
            .background(brush)
            .clickable(onClick = onClick)
            .padding(vertical = verticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        ScText(text, fontSize, weight, Color.White, maxLines = 1)
    }
}

@Composable
fun ScSoftButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    radius: Dp = 16.dp,
    verticalPadding: Dp = 15.dp,
    fontSize: Float = 15f,
    background: Color = Sc.Chip,
    color: Color = Sc.ChipInk,
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = verticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        ScText(text, fontSize, FontWeight.Bold, color, maxLines = 1)
    }
}

@Composable
fun ScSheetHandle(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.padding(top = 12.dp, bottom = 6.dp)
                .width(44.dp).height(5.dp)
                .background(Sc.Handle, RoundedCornerShape(5.dp)),
        )
    }
}

@Composable
fun ScSheetClose(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(34.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(Sc.Chip)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(ScIcons.Close, uiStrings().close, tint = Sc.InkSoft, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun ScGlyph(image: ImageVector, size: Dp, modifier: Modifier = Modifier) {
    Image(image, null, modifier = modifier.size(size))
}
