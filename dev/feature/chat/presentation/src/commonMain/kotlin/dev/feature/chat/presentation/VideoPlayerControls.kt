package dev.feature.chat.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.core.uikit.components.ScText
import dev.core.uikit.media.ScVideoState

/**
 * To'liq ekrandagi videoning **o'z** boshqaruv paneli — Telegramdagidek.
 *
 * Nega tizimning tayyor paneli emas: u har platformada boshqacha ko'rinadi (Android'da
 * Media3'ning kulrang paneli, iOS'da AVKit'niki) va uni loyihaning dizayniga bo'yab
 * bo'lmaydi. Telegram ham o'zinikini chizadi va foydalanuvchi aynan o'sha ko'rinishga
 * o'rgangan: markazda katta pauza, pastda izoh, ko'chirgich va `00:01 / 00:38`.
 *
 * Panel videoga **bosilganda** ko'rinadi/yashirinadi (`visible`) — ko'rish paytida ekranni
 * to'sib turmasin.
 */
@Composable
internal fun BoxScope.VideoPlayerControls(
    state: ScVideoState,
    visible: Boolean,
    /** Video ostidagi izoh (`caption`). Bo'sh bo'lsa qator umuman chizilmaydi. */
    caption: String?,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    // Markazdagi play/pauza.
    Box(
        Modifier.align(Alignment.Center)
            .size(64.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(Color.Black.copy(alpha = 0.35f))
            .pointerInput(state) { detectTapGestures { state.togglePlay() } },
        contentAlignment = Alignment.Center,
    ) {
        PlayPauseGlyph(playing = state.isPlaying, size = 26.dp)
    }

    // Pastdagi qatlam: izoh + ko'chirgich + vaqt.
    Column(
        modifier.align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))),
            )
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!caption.isNullOrBlank()) {
            ScText(caption, 14f, FontWeight.Medium, Color.White, maxLines = 3)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SeekBar(state, Modifier.weight(1f))
            ScText(
                "${formatClock(state.positionMs)} / ${formatClock(state.durationMs)}",
                13f,
                FontWeight.Bold,
                Color.White,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

/**
 * Ko'chirgich — chiziq va dumaloq tutqich.
 *
 * ⚠️ Barmoq tortilayotganda pozitsiya **local** holatda turadi va pleyerga faqat barmoq
 * qo'yib yuborilganda beriladi: har piksel uchun `seekTo` chaqirilsa dekoder uzluksiz
 * qayta yuklanib, tortish sakrab-sakrab ketardi.
 */
@Composable
private fun SeekBar(state: ScVideoState, modifier: Modifier = Modifier) {
    var widthPx by remember { mutableStateOf(1) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }

    val duration = state.durationMs.coerceAtLeast(1L)
    val fraction = dragFraction ?: (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)

    fun seekAt(x: Float) {
        dragFraction = (x / widthPx).coerceIn(0f, 1f)
    }

    Box(
        modifier.height(28.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(state, widthPx) {
                detectTapGestures { offset ->
                    val target = (offset.x / widthPx).coerceIn(0f, 1f)
                    state.seekTo((target * duration).toLong())
                }
            }
            .pointerInput(state, widthPx) {
                detectHorizontalDragGestures(
                    onDragStart = { offset: Offset -> seekAt(offset.x) },
                    onDragEnd = {
                        dragFraction?.let { state.seekTo((it * duration).toLong()) }
                        dragFraction = null
                    },
                    onDragCancel = { dragFraction = null },
                    onHorizontalDrag = { change, _ -> seekAt(change.position.x) },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // O'tilmagan qism.
        Box(
            Modifier.fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.3f)),
        )
        // O'tilgan qism.
        Box(
            Modifier.fillMaxWidth(fraction)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White),
        )
        // Tutqich — chiziqning oxirida.
        Box(Modifier.fillMaxWidth(fraction), contentAlignment = Alignment.CenterEnd) {
            Box(
                Modifier.size(13.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.White),
            )
        }
    }
}

/**
 * O'ynatish uchburchagi / pauza tayoqchalari — **chizib** beriladi.
 *
 * Loyihaning ikonalar to'plamida bu ikkalasi yo'q va video pufagida ularning o'rniga
 * `ChevronRight` (`>`) turardi — u "o'ynatish" emas, "keyingisi" degan ma'noni beradi.
 * Shakl shu qadar oddiyki, uni SVG qilib qo'shishning ma'nosi yo'q.
 */
@Composable
internal fun PlayPauseGlyph(playing: Boolean, size: Dp, color: Color = Color.White) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        if (playing) {
            // Pauza: ikki tayoqcha, orasi tayoqcha kengligining yarmicha.
            val bar = w * 0.28f
            val gap = w * 0.16f
            val left = (w - (bar * 2 + gap)) / 2f
            val top = h * 0.12f
            val barHeight = h * 0.76f
            drawRoundRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(bar, barHeight),
                cornerRadius = CornerRadius(bar * 0.25f),
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(left + bar + gap, top),
                size = Size(bar, barHeight),
                cornerRadius = CornerRadius(bar * 0.25f),
            )
        } else {
            // O'ynatish: uchburchak. Optik markazga surilgan — geometrik markazda tursa
            // ko'zga chapga og'gandek ko'rinadi.
            val path = Path().apply {
                moveTo(w * 0.30f, h * 0.14f)
                lineTo(w * 0.84f, h * 0.50f)
                lineTo(w * 0.30f, h * 0.86f)
                close()
            }
            drawPath(path, color)
        }
    }
}

/**
 * `00:38` yoki soatli videoda `1:05:20`.
 *
 * Davomiylik hali noma'lum bo'lsa (`0`) — `00:00`, chunki `--:--` ko'chirgich ostida
 * xatodek ko'rinardi.
 */
internal fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        "$hours:${minutes.pad()}:${seconds.pad()}"
    } else {
        "${minutes.pad()}:${seconds.pad()}"
    }
}

private fun Long.pad(): String = if (this < 10) "0$this" else "$this"
