package dev.core.uikit.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import dev.core.uikit.locale.uiStrings

/**
 * Yuklash foizi — Telegramdagi halqa.
 *
 * [progress] — `0f..1f`. **`null` bo'lsa** jarayon noma'lum (server hajmni bilmaydi yoki
 * fayl allaqachon ketib bo'lgan, javob kutilmoqda) va halqa aylanma bo'lib chiziladi:
 * to'xtab qolgan `0%` "osilib qoldi" degan taassurot berardi.
 *
 * Foiz **yaxlitlanadi** va `99%` da to'xtab turadi — `100%` faqat server javob berganda
 * ma'noga ega, undan oldin ko'rsatilsa foydalanuvchi tugadi deb o'ylab ekranni yopardi
 * (`MediaUploader` ham aynan shu sababga ko'ra `0.99` dan oshirmaydi).
 */
@Composable
fun ScUploadRing(
    progress: Float?,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    stroke: Dp = 3.dp,
    color: Color = Color.White,
    /** Halqa ortidagi qora doira — rangli rasm ustida foiz o'qilishi uchun. */
    scrim: Color = Color.Black.copy(alpha = 0.45f),
    showPercent: Boolean = true,
) {
    Box(
        modifier.size(size).clip(CircleShape).background(scrim),
        contentAlignment = Alignment.Center,
    ) {
        if (progress == null) {
            IndeterminateRing(size, stroke, color)
        } else {
            DeterminateRing(progress, size, stroke, color)
            if (showPercent) {
                ScText(
                    scUploadPercent(progress),
                    percentTextSize(size),
                    FontWeight.ExtraBold,
                    color,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Rasm/video ustidagi to'liq qoplama — kontent ko'rinib turadi, ustidan xira parda va
 * o'rtada halqa.
 *
 * Ataylab **kontentni yashirmaydi**: foydalanuvchi qaysi rasm ketayotganini ko'rib turishi
 * kerak (Telegram ham shunday qiladi).
 */
@Composable
fun ScUploadOverlay(
    progress: Float?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(0.dp),
    ringSize: Dp = 46.dp,
    /**
     * Berilsa — halqaning **ichida** bekor qilish belgisi chiziladi va foiz o'rniga o'sha
     * ko'rinadi (Telegramdagi kabi).
     *
     * Faqat uzoq ketadigan yuklashlar uchun: bir soniyada tugaydigan rasmga bosib ulgurib
     * bo'lmaydi, ya'ni belgi faqat xalaqit berardi.
     */
    onCancel: (() -> Unit)? = null,
) {
    Box(
        modifier.fillMaxSize().clip(shape).background(Color.Black.copy(alpha = 0.28f)),
        contentAlignment = Alignment.Center,
    ) {
        ScUploadRing(progress = progress, size = ringSize, showPercent = onCancel == null)
        if (onCancel != null) {
            Icon(
                ScIcons.Close,
                uiStrings().cancelUpload,
                tint = Color.White,
                modifier = Modifier.size(ringSize * CANCEL_ICON_RATIO).clickable(onClick = onCancel),
            )
        }
    }
}

/** Bekor qilish belgisi halqaning ichiga sig'sin — yoyga tegib ketmasin. */
private const val CANCEL_ICON_RATIO = 0.42f

/** `0.42f` → `"42%"`. Chegaradan chiqqan qiymat qisiladi — chaqiruvchi tekshirmasin. */
fun scUploadPercent(progress: Float): String =
    "${(progress.coerceIn(0f, 1f) * 100).roundToInt()}%"

/** To'lgan yoyi bilan halqa. Qiymat sakramasin uchun yumshoq animatsiya bilan. */
@Composable
private fun DeterminateRing(progress: Float, size: Dp, stroke: Dp, color: Color) {
    // Yuklash bo'lak-bo'lak keladi (soket buferi bo'shaganda) — animatsiyasiz halqa
    // sakrab-sakrab to'lardi.
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(PROGRESS_ANIM_MS, easing = LinearEasing),
        label = "uploadProgress",
    )
    Canvas(Modifier.size(size)) {
        val width = stroke.toPx()
        val inset = width / 2f + ringPadding.toPx()
        val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
        drawArc(
            color = color.copy(alpha = 0.3f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = width, cap = StrokeCap.Round),
        )
        drawArc(
            color = color,
            // Tepadan boshlanadi (soat mili kabi) — `0f` o'ng tomon bo'lardi.
            startAngle = -90f,
            sweepAngle = 360f * animated,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = width, cap = StrokeCap.Round),
        )
    }
}

/** Hajm noma'lum — aylanib turuvchi chorak yoy. */
@Composable
private fun IndeterminateRing(size: Dp, stroke: Dp, color: Color) {
    val transition = rememberInfiniteTransition(label = "uploadSpin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(SPIN_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "uploadSpinAngle",
    )
    Canvas(Modifier.size(size)) {
        val width = stroke.toPx()
        val inset = width / 2f + ringPadding.toPx()
        val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
        drawArc(
            color = color.copy(alpha = 0.3f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = width, cap = StrokeCap.Round),
        )
        drawArc(
            color = color,
            startAngle = angle,
            sweepAngle = SPIN_SWEEP,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = width, cap = StrokeCap.Round),
        )
    }
}

/**
 * Foiz matni halqa ichiga sig'ishi kerak: kichkina halqada (fayl pufagidagi 34dp) katta
 * shrift chetlarga urilardi.
 */
private fun percentTextSize(ring: Dp): Float = when {
    ring >= 52.dp -> 13f
    ring >= 40.dp -> 11.5f
    else -> 9.5f
}

/** Halqa bilan doira cheti orasidagi nafas. */
private val ringPadding = 3.dp

private const val PROGRESS_ANIM_MS = 220
private const val SPIN_DURATION_MS = 900
private const val SPIN_SWEEP = 90f
