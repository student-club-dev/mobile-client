package dev.core.uikit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.core.uikit.theme.AppPalette
import dev.core.uikit.theme.LocalScFontFamily
import dev.core.uikit.theme.appPalette
import androidx.compose.foundation.layout.imePadding
import dev.core.uikit.locale.uiStrings

/**
 * Ilovaning yagona shrift oilasi — Plus Jakarta Sans (dizayn spetsifikatsiyasi).
 *
 * `AppTheme` uni [dev.core.uikit.theme.LocalScFontFamily] ga qo'yadi, shuning uchun
 * barcha `fontFamily = AppFontFamily` yozuvlari avtomatik yangi shriftga o'tadi.
 */
val AppFontFamily: FontFamily
    @Composable
    @ReadOnlyComposable
    get() = LocalScFontFamily.current

// ---------------------------------------------------------------------------
// Fon — gradient + dekorativ bloblar + ekran paddingi
// ---------------------------------------------------------------------------

/**
 * Auth/forma ekranlarining karkasi.
 *
 * [bottomBar] — scroll maydonidan TASHQARIDA, ekranning pastiga mixlanadigan blok
 * (asosiy tugma va uning xatosi). Klaviatura ochilganda ham ko'rinib turadi, ya'ni
 * "tugmani klaviatura to'sib qo'ydi" holati bo'lishi mumkin emas.
 */
@Composable
fun AppScreenScaffold(
    modifier: Modifier = Modifier,
    scroll: Boolean = false,
    horizontalPadding: Int = 22,
    // Status bar balandligi `statusBarsPadding()` dan olinadi; bu — undan keyingi bo'shliq.
    topPadding: Int = 14,
    palette: AppPalette = appPalette,
    bottomBar: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().background(palette.bgBrush)) {
        // Yuqori-o'ng primary blob
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(0.dp)
                .size(210.dp)
                .graphicsLayer { translationX = 60f; translationY = -80f }
                .background(
                    Brush.radialGradient(listOf(palette.blobPrimary, Color.Transparent)),
                    RoundedCornerShape(999.dp),
                ),
        )
        // Pastki-chap cyan blob
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .size(200.dp)
                .graphicsLayer { translationX = -70f; translationY = 40f }
                .background(
                    Brush.radialGradient(listOf(palette.blobCyan, Color.Transparent)),
                    RoundedCornerShape(999.dp),
                ),
        )
        Column(
            Modifier.fillMaxSize()
                .statusBarsPadding()
                // ⚠️ Klaviatura balandligi paddingdan OLDIN: shundan keyin ustun faqat
                // klaviatura ustidagi bo'shliqni oladi.
                .imePadding()
                .padding(
                    start = horizontalPadding.dp,
                    end = horizontalPadding.dp,
                    top = topPadding.dp,
                    bottom = 26.dp,
                ),
        ) {
            // ⚠️ Scroll FAQAT [scroll] bo'yicha yoqiladi, klaviatura holatiga qarab EMAS:
            // scroll qilinadigan ustun ichida `weight(1f)` bergan bolalar (masalan
            // universitet tanlash ekranidagi `LazyColumn`) cheksiz balandlikda o'lchanib,
            // ilovani yiqitardi. Klaviaturani [bottomBar] hal qiladi — asosiy tugma
            // scroll maydonidan tashqarida turadi.
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = bottomBar == null)
                    .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                content = content,
            )
            // Mixlangan blok — scroll maydonidan tashqarida, ya'ni doim ko'rinadi.
            bottomBar?.invoke(this)
        }
    }
}

// ---------------------------------------------------------------------------
// Matn uslublari
// ---------------------------------------------------------------------------

@Composable
fun ScreenTitle(text: String, size: Int = 24, palette: AppPalette = appPalette) {
    Text(
        text,
        style = TextStyle(
            fontFamily = AppFontFamily,
            fontSize = size.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.5).sp,
            color = palette.ink,
        ),
    )
}

@Composable
fun ScreenSubtitle(text: String, palette: AppPalette = appPalette) {
    Text(
        text,
        style = TextStyle(
            fontFamily = AppFontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = 19.sp,
            color = palette.inkMuted,
        ),
    )
}

@Composable
fun FieldLabel(text: String, palette: AppPalette = appPalette) {
    Text(
        text,
        style = TextStyle(
            fontFamily = AppFontFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = palette.label,
        ),
    )
}

@Composable
fun HintText(text: String, palette: AppPalette = appPalette) {
    Text(
        text,
        style = TextStyle(
            fontFamily = AppFontFamily,
            fontSize = 11.5f.sp,
            color = palette.inkFaint,
            lineHeight = 15.sp,
        ),
    )
}

// ---------------------------------------------------------------------------
// Logo tile
// ---------------------------------------------------------------------------

@Composable
fun LogoTile(
    size: Int = 52,
    radius: Int = 16,
    iconSize: Int = 28,
    palette: AppPalette = appPalette,
) {
    Box(
        Modifier
            .size(size.dp)
            .shadow(18.dp, RoundedCornerShape(radius.dp), spotColor = palette.primary, ambientColor = palette.primary)
            .background(palette.primaryBrush, RoundedCornerShape(radius.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons.GraduationCap, null, tint = Color.White, modifier = Modifier.size(iconSize.dp))
    }
}

// ---------------------------------------------------------------------------
// Back / icon buttons
// ---------------------------------------------------------------------------

@Composable
fun BackButton(onClick: () -> Unit, icon: ImageVector = AppIcons.ArrowLeft, palette: AppPalette = appPalette) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.glass)
            .border(1.dp, palette.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = palette.ink, modifier = Modifier.size(19.dp))
    }
}

// ---------------------------------------------------------------------------
// Primary / outline tugmalar
// ---------------------------------------------------------------------------

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingIcon: ImageVector? = null,
    palette: AppPalette = appPalette,
) {
    val shape = RoundedCornerShape(15.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .shadow(if (enabled) 20.dp else 0.dp, shape, spotColor = palette.primary, ambientColor = palette.primary)
            .clip(shape)
            .background(if (enabled) palette.primaryBrush else SolidColor(palette.primary.copy(alpha = 0.4f)))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text,
                style = TextStyle(
                    fontFamily = AppFontFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.onPrimary,
                ),
            )
            if (trailingIcon != null) {
                Icon(trailingIcon, null, tint = palette.onPrimary, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    palette: AppPalette = appPalette,
) {
    val shape = RoundedCornerShape(15.dp)
    val contentAlpha = if (enabled) 1f else 0.4f
    Row(
        modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(shape)
            .background(palette.glass)
            .border(1.dp, palette.border, shape)
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, null, tint = palette.primary.copy(alpha = contentAlpha), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(9.dp))
        }
        Text(
            text,
            style = TextStyle(
                fontFamily = AppFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = (if (palette.dark) palette.ink else Color(0xFF4A3F86)).copy(alpha = contentAlpha),
            ),
        )
    }
}

// ---------------------------------------------------------------------------
// Glass text field
// ---------------------------------------------------------------------------

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    focused: Boolean = false,
    height: Int = 50,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    textLetterSpacing: Float = 0f,
    palette: AppPalette = appPalette,
) {
    val shape = RoundedCornerShape(14.dp)
    val borderColor = if (focused) palette.borderStrong else palette.border
    val borderWidth = if (focused) 1.5.dp else 1.dp
    var box = modifier
        .fillMaxWidth()
        .height(height.dp)
    if (focused) box = box.shadow(0.dp, shape, spotColor = palette.fieldFocusGlow)
    Row(
        box
            .clip(shape)
            .background(palette.fieldBg)
            .border(borderWidth, borderColor, shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        if (leadingContent != null) {
            leadingContent()
        } else if (leading != null) {
            Icon(leading, null, tint = palette.inkFaint, modifier = Modifier.size(17.dp))
        }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    style = TextStyle(fontFamily = AppFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = palette.inkFaint),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = AppFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.ink,
                    letterSpacing = textLetterSpacing.sp,
                ),
                cursorBrush = SolidColor(palette.primary),
                keyboardOptions = keyboardOptions,
                visualTransformation = visualTransformation,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (trailing != null) trailing()
    }
}

// ---------------------------------------------------------------------------
// Divider "yoki"
// ---------------------------------------------------------------------------

@Composable
fun OrDivider(text: String = uiStrings().or, modifier: Modifier = Modifier, palette: AppPalette = appPalette) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f).height(1.dp).background(palette.border))
        Text(text, style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.5f.sp, fontWeight = FontWeight.Medium, color = palette.inkFaint))
        Box(Modifier.weight(1f).height(1.dp).background(palette.border))
    }
}

// ---------------------------------------------------------------------------
// Social row (Google / Apple / Telegram)
// ---------------------------------------------------------------------------

/**
 * Ijtimoiy kirish tugmalari. [onApple] / [onTelegram] `null` bo'lsa o'sha tugma **chizilmaydi** —
 * ishlamaydigan tugmani ko'rsatishdan ko'ra yo'q qilgan tuzuk. Hozir backend faqat Google'ni
 * qo'llab-quvvatlaydi (`POST /v1/auth/student/oauth/google`).
 */
@Composable
fun SocialRow(
    onGoogle: () -> Unit,
    onApple: (() -> Unit)? = null,
    onTelegram: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    palette: AppPalette = appPalette,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        SocialButton(Modifier.weight(1f), onGoogle, palette) {
            androidx.compose.foundation.Image(AppIcons.Google, null, modifier = Modifier.size(21.dp))
        }
        if (onApple != null) {
            SocialButton(Modifier.weight(1f), onApple, palette) {
                Icon(AppIcons.Apple, null, tint = palette.ink, modifier = Modifier.size(19.dp))
            }
        }
        if (onTelegram != null) {
            SocialButton(Modifier.weight(1f), onTelegram, palette) {
                androidx.compose.foundation.Image(AppIcons.Telegram, null, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun SocialButton(modifier: Modifier, onClick: () -> Unit, palette: AppPalette, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .height(50.dp)
            .clip(shape)
            .background(palette.glass)
            .border(1.dp, palette.border, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

// ---------------------------------------------------------------------------
// Footer link ("Hisobingiz yo'qmi? Ro'yxatdan o'tish")
// ---------------------------------------------------------------------------

/** Xato xabari — bo'sh bo'lmasa qizil matn ko'rsatadi. */
@Composable
fun ColumnScope.ErrorText(message: String?) {
    if (message.isNullOrBlank()) return
    Spacer(Modifier.height(12.dp))
    Text(
        message,
        style = TextStyle(
            fontFamily = AppFontFamily,
            fontSize = 12.5f.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFDC2626),
            lineHeight = 17.sp,
        ),
    )
}

@Composable
fun FooterLink(prefix: String, action: String, onClick: () -> Unit, palette: AppPalette = appPalette) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            "$prefix ",
            style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.5f.sp, color = palette.inkMuted),
        )
        Text(
            action,
            style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.5f.sp, fontWeight = FontWeight.ExtraBold, color = palette.primary),
        )
    }
}
