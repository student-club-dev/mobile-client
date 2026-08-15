package dev.core.uikit.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.core.uikit.theme.Sc

/**
 * Sevimlilar tugmasi — **yurak**, to'lish animatsiyasi bilan.
 *
 * Ilgari bu arxiv qutisi ikonasi edi ([AppIcons.Bookmark]) va nima qilishini ko'rinishidan
 * bilib bo'lmasdi — foydalanuvchi uni "o'chirish" yoki "arxivlash" deb o'qirdi (bug
 * hisoboti #36). Yurak — saqlashning universal belgisi va tushuntirish talab qilmaydi.
 *
 * Animatsiya ikki qatlamdan: kontur doim turadi, to'ldirilgan yurak esa ustidan
 * shaffoflik bilan chiqadi va prujina bilan "urib" oladi. Ikkalasi bir vaqtda —
 * shuning uchun holat o'zgarishi sezilarli, lekin sakramaydi.
 */
@Composable
fun ScFavoriteButton(
    saved: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    /** Kontur rangi — karta rasmi ustida oq, oddiy fonda so'nik siyoh. */
    idleTint: Color = Sc.Muted,
    contentDescription: String? = null,
) {
    val fill by animateFloatAsState(if (saved) 1f else 0f, tween(220), label = "favFill")
    // "Urish" — FAQAT belgilanganda: bekor qilishda sakrash ortiqcha e'tibor tortadi.
    val pop = remember { Animatable(1f) }
    LaunchedEffect(saved) {
        if (!saved) return@LaunchedEffect
        pop.snapTo(0.72f)
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }

    Box(
        modifier
            .size(size + TOUCH_PADDING)
            // Rippl yo'q: ikona kichkina va halqa uni butunlay yopib qo'yardi.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onToggle(saved) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            AppIcons.Heart,
            contentDescription,
            tint = idleTint.copy(alpha = 1f - fill),
            modifier = Modifier.size(size),
        )
        Icon(
            AppIcons.HeartFilled,
            null,
            tint = Sc.Danger,
            modifier = Modifier.size(size).alpha(fill).scale(pop.value),
        )
    }
}

/** Ikonaning atrofidagi tegish maydoni — 22dp yurak yolg'iz o'zi juda kichik nishon. */
private val TOUCH_PADDING = 16.dp
