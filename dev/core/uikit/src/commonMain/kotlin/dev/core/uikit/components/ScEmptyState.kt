package dev.core.uikit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.core.uikit.theme.Sc

/**
 * Bo'sh ro'yxatning YAGONA ko'rinishi: markazda ikona, ostida sarlavha va (ixtiyoriy)
 * bir qatorlik izoh.
 *
 * Nega alohida komponent: ilgari har ekran bo'sh holatni o'zicha, quruq matn bilan
 * ko'rsatardi ("Hozircha topshiriq e'loni yo'q.") — ekran buzilganday ko'rinardi va
 * har joyda boshqacha edi. Endi hamma joy shu komponentni chaqiradi.
 *
 * Sarlavha UNIVERSAL: [ScEmptyTitle] ("Hozircha bo'sh") har qanday ro'yxatga tushadi,
 * shuning uchun chaqiruvchi uni berishi SHART emas. Nima yo'qligini aniqlashtirish
 * kerak bo'lsa — [message] ga yoziladi, sarlavha esa bir xilligicha qoladi.
 *
 * @param compact ro'yxat ichidagi kichik bo'lim uchun (ekran o'rtasi emas): ikona va
 *   matn kichrayadi, tepa-past bo'sh joyi qisqaradi.
 * @param glyph plitka ichidagi tasvirni butunlay o'zi chizadigan bo'limlar uchun
 *   (masalan e'lon turining ko'p rangli ikonasi); berilsa [icon]/[emoji] e'tiborsiz.
 */
@Composable
fun ScEmptyState(
    modifier: Modifier = Modifier,
    title: String = ScEmptyTitle,
    message: String? = null,
    icon: ImageVector? = null,
    emoji: String? = null,
    tint: Color = Sc.Chip,
    iconColor: Color = Sc.Muted,
    titleColor: Color = Sc.Ink,
    messageColor: Color = Sc.Muted,
    compact: Boolean = false,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    glyph: (@Composable BoxScope.() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(
            horizontal = Sc.ScreenPadding,
            vertical = if (compact) 18.dp else 34.dp,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ScIconTile(
            tint,
            size = if (compact) 64.dp else 96.dp,
            radius = if (compact) 22.dp else 30.dp,
        ) {
            // Belgi uch xil bo'lishi mumkin: chaqiruvchining o'z tasviri, emoji (bo'limning
            // belgisi) yoki hech biri berilmasa — universal "bo'sh quti" ikonasi.
            when {
                glyph != null -> glyph()
                emoji != null -> Text(emoji, style = TextStyle(fontSize = if (compact) 22.sp else 34.sp))
                else -> Icon(
                    icon ?: ScIcons.Empty,
                    null,
                    tint = iconColor,
                    modifier = Modifier.size(if (compact) 26.dp else 40.dp),
                )
            }
        }
        Spacer(Modifier.height(if (compact) 12.dp else 18.dp))
        Text(
            title,
            style = scStyle(
                if (compact) 15.5f else 19f,
                FontWeight.ExtraBold,
                titleColor,
            ).copy(textAlign = TextAlign.Center),
        )
        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(if (compact) 4.dp else 6.dp))
            Text(
                message,
                style = scStyle(
                    if (compact) 13f else 14f,
                    FontWeight.Medium,
                    messageColor,
                    lineHeight = if (compact) 19f else 21f,
                ).copy(textAlign = TextAlign.Center),
            )
        }
        // Tugma faqat foydalanuvchi haqiqatan biror narsa qila olsa ("E'lon joylash").
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(if (compact) 12.dp else 18.dp))
            ScSoftButton(actionText, onAction, Modifier.width(200.dp))
        }
    }
}

/**
 * Ekranning butun bo'sh joyini egallaydigan variant — ro'yxat o'rniga turadi va
 * markazda qoladi (`LazyColumn` yo'q bo'lgan sahifalarda).
 */
@Composable
fun ScEmptyStateBox(
    modifier: Modifier = Modifier,
    title: String = ScEmptyTitle,
    message: String? = null,
    icon: ImageVector? = null,
    emoji: String? = null,
    tint: Color = Sc.Chip,
    iconColor: Color = Sc.Muted,
    compact: Boolean = false,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        ScEmptyState(
            title = title,
            message = message,
            icon = icon,
            emoji = emoji,
            tint = tint,
            iconColor = iconColor,
            compact = compact,
            actionText = actionText,
            onAction = onAction,
        )
    }
}

/**
 * Universal sarlavha: "bu yerda hozircha hech nima yo'q" ma'nosini beradi va har qanday
 * ro'yxatga (e'lon, talaba, suhbat, fayl) mos tushadi.
 */
const val ScEmptyTitle: String = "Hozircha bo'sh"

/** Qidiruv/filtr natijasi bo'sh bo'lganda — "yo'q" emas, "topilmadi". */
const val ScNotFoundTitle: String = "Hech narsa topilmadi"
