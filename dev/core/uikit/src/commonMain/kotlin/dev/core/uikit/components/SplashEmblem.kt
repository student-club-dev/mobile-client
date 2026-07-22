package dev.core.uikit.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import dev.core.uikit.theme.LocalScColors
import dev.core.uikit.theme.ScColors

/**
 * Splash nishoni — shishasimon (glassmorphic) kvadrat ichida bitiruvchining byusti,
 * ortida yumshoq halo halqa. Wordmark ustida turadi.
 *
 * To'liq kodda chiziladi (rasm asseti yo'q) va [EmblemRef] mos yozuvlar tizimidan
 * Canvas o'lchamiga masshtablanadi — shuning uchun istalgan o'lchamda o'tkir chiqadi.
 */
internal const val EmblemRef = 120f

/** Bir vaqtning o'zida tarqalayotgan halqalar soni. */
private const val RippleCount = 2


/**
 * @param sweep 0f..1f — shisha bo'ylab sirpanuvchi yaltirash chizig'ining o'rni.
 * @param halo 0f..1f — tarqalayotgan halqalarning bosqichi. Chiziqli va uzluksiz
 *   takrorlanishi kerak (0 -> 1 -> 0 emas), aks holda halqalar orqaga qaytadi.
 * @param tasselDegrees popukning tebranish burchagi (tanadagi popuk bilan bir xil manba).
 */
@Composable
internal fun SplashEmblem(
    sweep: Float,
    halo: Float,
    tasselDegrees: Float,
    modifier: Modifier = Modifier,
) {
    // Ranglar Canvas lambdasidan TASHQARIDA o'qiladi — lambda `@Composable` emas.
    val colors = LocalScColors.current
    Canvas(modifier) { drawEmblem(sweep, halo, tasselDegrees, colors) }
}

private fun DrawScope.drawEmblem(
    sweep: Float,
    halo: Float,
    tasselDegrees: Float,
    colors: ScColors,
) {
    val s = size.width / EmblemRef
    fun at(x: Float, y: Float) = Offset(x * s, y * s)
    val center = at(60f, 60f)

    // ---- Ortdagi halo ----
    // Doimiy yumshoq nur — nishonga chuqurlik beradi.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.12f), Color.Transparent),
            center = center,
            radius = 56f * s,
        ),
        radius = 56f * s,
        center = center,
    )
    // Kengayib so'nuvchi halqalar: nishon chetidan chiqib, tarqalib yo'qoladi.
    // Ikkitasi yarim sikl farq bilan ketadi — to'lqin uzluksiz ko'rinadi.
    repeat(RippleCount) { index ->
        val progress = (halo + index.toFloat() / RippleCount) % 1f
        // Boshida tez ochiladi, keyin butun yo'l davomida so'nadi — keskin paydo bo'lmaydi.
        val alpha = (1f - progress) * minOf(1f, progress * 6f) * 0.42f
        drawCircle(
            color = Color.White.copy(alpha = alpha),
            radius = (44f + 26f * progress) * s,
            center = center,
            style = Stroke(width = (1.8f - 1.1f * progress) * s),
        )
    }

    // ---- Shisha kvadrat (squircle) ----
    val tile = Rect(at(18f, 18f), Size(84f * s, 84f * s))
    val corner = CornerRadius(30f * s, 30f * s)
    val tilePath = Path().apply { addRoundRect(RoundRect(tile, corner)) }
    drawPath(
        path = tilePath,
        brush = Brush.verticalGradient(
            listOf(colors.emblemGlassTop, colors.emblemGlassBottom),
            startY = tile.top,
            endY = tile.bottom,
        ),
    )

    // ---- Byust: shisha ichida qirqiladi ----
    clipPath(tilePath) {
        // Yelkalar (oq ko'ylak)
        val shoulders = Path().apply {
            moveTo(34f * s, 100f * s)
            lineTo(34f * s, 86f * s)
            cubicTo(34f * s, 76f * s, 46f * s, 71f * s, 60f * s, 71f * s)
            cubicTo(74f * s, 71f * s, 86f * s, 76f * s, 86f * s, 86f * s)
            lineTo(86f * s, 100f * s)
            close()
        }
        drawPath(
            shoulders,
            Brush.horizontalGradient(listOf(colors.emblemBustLight, colors.emblemBustShade), startX = 34f * s, endX = 86f * s),
        )
        // Galstuk
        val tie = Path().apply {
            moveTo(56f * s, 72f * s)
            lineTo(64f * s, 72f * s)
            lineTo(61f * s, 96f * s)
            lineTo(60f * s, 99f * s)
            lineTo(59f * s, 96f * s)
            close()
        }
        drawPath(tie, Brush.verticalGradient(listOf(colors.emblemTie, colors.emblemTieDark), startY = 72f * s, endY = 99f * s))
        // Bosh
        drawCircle(
            brush = Brush.radialGradient(
                listOf(colors.emblemBustLight, colors.emblemBustShade),
                center = at(55f, 52f),
                radius = 22f * s,
            ),
            radius = 14f * s,
            center = at(60f, 58f),
        )
        // Shapkaning boshni quchgan qismi
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(colors.emblemCapLight, colors.emblemCapShade),
                startY = 44f * s,
                endY = 56f * s,
            ),
            topLeft = at(47f, 44f),
            size = Size(26f * s, 12f * s),
            cornerRadius = CornerRadius(6f * s, 6f * s),
        )
        // Shapka taxtasi
        val board = Path().apply {
            moveTo(60f * s, 33f * s)
            lineTo(87f * s, 44f * s)
            lineTo(60f * s, 55f * s)
            lineTo(33f * s, 44f * s)
            close()
        }
        drawPath(
            board,
            Brush.horizontalGradient(listOf(colors.emblemCapLight, colors.emblemCapShade), startX = 33f * s, endX = 87f * s),
        )
        // Popuk — tanadagi popuk bilan bir xil ritmda chayqaladi
        rotate(degrees = tasselDegrees, pivot = at(87f, 44f)) {
            drawLine(
                brush = Brush.verticalGradient(
                    listOf(colors.emblemGold, colors.emblemGoldDark),
                    startY = 44f * s,
                    endY = 60f * s,
                ),
                start = at(87f, 44f),
                end = at(88f, 58f),
                strokeWidth = 1.8f * s,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(colors.emblemGold, colors.emblemGoldDark),
                    center = at(87f, 60f),
                    radius = 6f * s,
                ),
                radius = 4f * s,
                center = at(88f, 61f),
            )
        }

        // ---- Shisha bo'ylab sirpanuvchi yaltirash ----
        // sweep 0..1 -> chapdan o'ngga; 0.55 dan keyin ko'rinmaydi (qisqa "chaqnash").
        if (sweep < 0.62f) {
            val progress = sweep / 0.62f
            val bandWidth = 26f * s
            val x = tile.left - bandWidth + (tile.width + bandWidth * 2f) * progress
            rotate(degrees = -20f, pivot = center) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.30f),
                            Color.Transparent,
                        ),
                        startX = x,
                        endX = x + bandWidth,
                    ),
                    topLeft = Offset(x, tile.top - tile.height),
                    size = Size(bandWidth, tile.height * 3f),
                )
            }
        }
    }

    // ---- Shisha chegarasi (byust ustidan chiziladi — chekkasi toza chiqadi) ----
    drawPath(
        path = tilePath,
        color = colors.emblemGlassBorder,
        style = Stroke(width = 1.4f * s),
    )
}
