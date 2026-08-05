package dev.feature.chat.presentation.list

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.core.uikit.components.ScText
import dev.core.uikit.components.scCard
import dev.core.uikit.theme.Sc
import kotlin.math.abs

/** Bitta bo'lim: belgisi, yozuvi va yonidagi son. */
@Immutable
internal data class ChatSegment(
    val label: String,
    val icon: ImageVector,
    /** Nofaol holatdagi belgi rangi. */
    val accent: Color,
    val badge: Int = 0,
    /** `true` — son o'qilmaganlarni bildiradi (qizil), `false` — shunchaki miqdor. */
    val unread: Boolean = true,
)

/**
 * Papkalar — E'lonlar ekranidagi bo'limlar (Yordam / Ijara / Xizmat / Ish) bilan **bir xil**
 * segment boshqaruvi: oq kartochka ichida teng kenglikdagi bo'limlar, faoli gradient bilan.
 *
 * Farqi bittada: bu yerda faol bo'lim SAKRAB emas, SILJIB o'tadi. Gradient — alohida
 * element emas, chiziladigan shakl; uning o'rni [indicator] (sahifa raqami + surilish
 * ulushi) bo'yicha interpolatsiya qilinadi, ya'ni `HorizontalPager` ni barmoq bilan
 * surganda u barmoq ortidan yuradi.
 *
 * ⚠️ [indicator] — lambda: qiymat faqat CHIZISH bosqichida o'qiladi, ya'ni surish
 * davomida bo'limlar qayta kompozitsiya qilinmaydi.
 */
@Composable
internal fun MessagesSegments(
    segments: List<ChatSegment>,
    indicator: () -> Float,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Chizish lambda'si ichida `Sc.*` ni o'qib bo'lmaydi (u `@Composable`) — oldindan olamiz.
    val activeFill = Sc.buttonBrush
    val count = segments.size

    Row(
        modifier.fillMaxWidth()
            // Kartochka TO'LIQ yumaloq (kapsula) — radius balandligining yarmidan katta
            // bo'lsa Compose uni balandlikka qisqartiradi, ya'ni bu «CircleShape» bilan
            // bir xil natija beradi va o'lchamga bog'liq emas.
            .scCard(radius = PillRadius, elevation = 6.dp)
            .padding(SegmentInset)
            .drawBehind {
                if (count == 0) return@drawBehind
                val gap = SegmentGap.toPx()
                val width = (size.width - gap * (count - 1)) / count
                val position = indicator().coerceIn(0f, (count - 1).toFloat())
                drawRoundRect(
                    brush = activeFill,
                    topLeft = Offset(position * (width + gap), 0f),
                    size = Size(width, size.height),
                    // Faol bo'lim ham kapsula: kartochka bilan bir xil qavariqlikda
                    // bo'lmasa ichkarida burchakli to'rtburchak bo'lib turib qolardi.
                    cornerRadius = CornerRadius(size.height / 2f),
                )
            },
        horizontalArrangement = Arrangement.spacedBy(SegmentGap),
    ) {
        segments.forEachIndexed { index, segment ->
            Segment(
                segment = segment,
                active = { proximity(indicator(), index) },
                onClick = { onSelect(index) },
            )
        }
    }
}

/**
 * Bo'limning ichi — fonsiz: gradient qatorning O'ZIDA chiziladi.
 *
 * Ikki ko'rinish (faol va nofaol) ustma-ust turadi va shaffofligi bilan almashadi.
 * Nega rangni oddiy `lerp` bilan bermaymiz: matn va belgi rangi KOMPOZITSIYA
 * parametri, ya'ni surishning har kadrida bo'lim qaytadan quriladi. Shaffoflik esa
 * `graphicsLayer` da — faqat chizish.
 */
@Composable
private fun RowScope.Segment(segment: ChatSegment, active: () -> Float, onClick: () -> Unit) {
    Box(
        Modifier.weight(1f)
            // Bosish sohasi ham kapsula — to'lqinlanish (ripple) burchaklardan chiqmasin.
            .clip(RoundedCornerShape(percent = 50))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        SegmentFace(segment, selected = false, Modifier.graphicsLayer { alpha = 1f - active() })
        SegmentFace(segment, selected = true, Modifier.graphicsLayer { alpha = active() })
    }
}

@Composable
private fun SegmentFace(segment: ChatSegment, selected: Boolean, modifier: Modifier) {
    Row(
        modifier.padding(horizontal = 2.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            segment.icon, null,
            tint = if (selected) Color.White else segment.accent,
            modifier = Modifier.size(17.dp),
        )
        ScText(
            segment.label, 12.5f, FontWeight.Bold,
            if (selected) Color.White else Sc.InkSoft, maxLines = 1,
        )
        if (segment.badge > 0) {
            val background = when {
                // Gradient ustida belgicha "o'yilgandek": shaffof oq.
                selected -> Color.White.copy(alpha = 0.26f)
                // O'qilmaganlar har doim diqqat tortadi, oddiy miqdor esa xira.
                segment.unread -> Sc.Danger
                else -> Sc.Brand.copy(alpha = 0.14f)
            }
            val ink = when {
                selected -> Color.White
                segment.unread -> Color.White
                else -> Sc.Brand
            }
            AnimatedContent(
                targetState = segment.badge,
                transitionSpec = {
                    // Yangi son "irg'ib" chiqadi — o'qilmagan xabar kelgani sezilsin.
                    (scaleIn(spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium)) + fadeIn())
                        .togetherWith(scaleOut(tween(120)) + fadeOut(tween(120)))
                },
                label = "segment-badge",
            ) { value ->
                Box(
                    Modifier.size(18.dp).background(background, RoundedCornerShape(percent = 50)),
                    contentAlignment = Alignment.Center,
                ) { ScText("$value", 10f, FontWeight.ExtraBold, ink) }
            }
        }
    }
}

/** Bo'lim indikatorga qanchalik yaqin: `1f` — aynan ustida, `0f` — qo'shnisidan narida. */
private fun proximity(indicator: Float, index: Int): Float =
    (1f - abs(indicator - index)).coerceIn(0f, 1f)

/** Kartochka bilan bo'limlar orasidagi ramka va bo'limlar orasidagi tirqish. */
private val SegmentInset = 4.dp
private val SegmentGap = 4.dp

/**
 * "To'liq yumaloq" radius.
 *
 * Compose burchak radiusini shaklning yarim o'lchamiga QISQARTIRADI, ya'ni bu qiymat
 * balandlikdan katta bo'lgani uchun natija aynan kapsula bo'ladi — panel balandligi
 * o'zgarsa ham shakl to'g'ri qoladi.
 */
private val PillRadius = 999.dp
