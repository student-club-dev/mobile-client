package dev.core.uikit.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.core.uikit.theme.Sc

/**
 * "Skelet" (shimmer) yuklanish effekti — ilovadagi BARCHA yuklanish holatlari uchun yagona
 * ko'rinish. Aylanma indikator o'rniga kelajakdagi kontentning kulrang shakli chiziladi va
 * ustidan yorug' to'lqin o'tadi.
 *
 * Nega aylanma emas: skelet ekranning tuzilishini oldindan ko'rsatadi — ma'lumot kelganda
 * kontent joyidan sakramaydi va kutish qisqaroq tuyuladi.
 *
 * ### Ishlatish
 * ```
 * ScShimmerBox(Modifier.fillMaxWidth().height(120.dp))   // bitta blok
 * Modifier.scShimmer(RoundedCornerShape(12.dp))          // mavjud o'lchamli elementga
 * ScShimmerList(rows = 4)                                // ro'yxat skeleti
 * ScShimmerGrid(columns = 3, rows = 3)                   // to'r (GIF/stiker) skeleti
 * ```
 */

/** To'lqinning bir marta o'tish vaqti. */
private const val ShimmerDurationMs = 1400

/** To'lqin kengligi elementning kengligiga nisbatan. */
private const val ShimmerBandRatio = 0.6f

/**
 * Yuklanayotgan joyni skelet sifatida bo'yaydi: kulrang fon + ustidan yuruvchi yorug'
 * to'lqin. O'lcham chaqiruvchidan keladi ([Modifier.size], [Modifier.height]...).
 *
 * @param shape burchak shakli — kontentning haqiqiy shakliga mos bo'lsin.
 */
@Composable
fun Modifier.scShimmer(shape: Shape = RoundedCornerShape(12.dp)): Modifier =
    clip(shape).background(ScShimmerBase).shimmerSweep(ScShimmerHighlight)

/**
 * Skelet foni — SOF KULRANG (mavzu ranglaridan olinmaydi: `Sc.Border` oq karta ustida
 * deyarli ko'rinmasdi va shimmer "qo'shilmagandek" tuyulardi, brend ranglari esa
 * skeletni ko'kartirib yuborardi).
 */
private val ScShimmerBase: Color
    @Composable @ReadOnlyComposable get() =
        if (Sc.IsDark) Color(0xFF2B3138) else Color(0xFFDDE1E6)

/** To'lqin — kulrang fonni yoritib o'tadi. */
private val ScShimmerHighlight: Color
    @Composable @ReadOnlyComposable get() =
        if (Sc.IsDark) Color(0xFF3E464F) else Color(0xFFF3F5F7)

/**
 * Faqat yorug' to'lqin — foni YO'Q, ya'ni ostidagi kontent ko'rinib turadi.
 *
 * Haqiqiy kontent allaqachon ekranda bo'lgan, lekin ustida ish ketayotgan holatlar uchun:
 * masalan yuborilayotgan rasmning local nusxasi ko'rinib turadi, yuklash esa davom etadi.
 */
@Composable
fun Modifier.scShimmerSweep(
    shape: Shape = RoundedCornerShape(0.dp),
    highlight: Color = Color.White.copy(alpha = 0.35f),
): Modifier = clip(shape).shimmerSweep(highlight)

/** Ikkala variantning umumiy qismi — yuruvchi gradient chizig'i. */
@Composable
private fun Modifier.shimmerSweep(highlight: Color): Modifier {
    // Bitta cheksiz animatsiya har bir skelet uchun: ekrandagi bloklar bir maromda yuradi.
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(ShimmerDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )
    // `drawWithContent` (keshlanadigan variant emas): `progress` HAR bir kadrda o'qiladi,
    // shuning uchun to'lqin haqiqatan harakatlanadi.
    return drawWithContent {
        drawContent()
        val band = size.width * ShimmerBandRatio
        // To'lqin chapdan o'ngga: boshida ham, oxirida ham element tashqarisida bo'ladi.
        val start = -band + progress * (size.width + 2 * band)
        drawRect(
            Brush.linearGradient(
                colors = listOf(Color.Transparent, highlight, Color.Transparent),
                start = Offset(start, 0f),
                end = Offset(start + band, size.height),
            ),
        )
    }
}

/** Bitta skelet blok — matn qatori, karta yoki avatar o'rniga. */
@Composable
fun ScShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
) = Box(modifier.scShimmer(shape))

/** Matn qatori skeleti — [widthFraction] bilan qatorlar turli uzunlikda ko'rinadi. */
@Composable
fun ScShimmerLine(
    widthFraction: Float = 1f,
    height: Dp = 12.dp,
    modifier: Modifier = Modifier,
) = ScShimmerBox(
    modifier.fillMaxWidth(widthFraction).height(height),
    RoundedCornerShape(percent = 50),
)

/**
 * Ro'yxat skeleti: chapda plitka, o'ngda ikki qator matn. Suhbatlar, talabalar, viloyatlar,
 * universitetlar — ilovadagi ro'yxatlarning aksariyati shu shaklda.
 *
 * @param rows nechta qator chizilsin (ekranga sig'adigan taxminiy son).
 * @param leading chapdagi plitka kerakmi (avatar/ikonasiz ro'yxatlarda `false`).
 */
@Composable
fun ScShimmerList(
    rows: Int = 5,
    leading: Boolean = true,
    modifier: Modifier = Modifier,
    rowHeight: Dp = 52.dp,
    spacing: Dp = 12.dp,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing)) {
        repeat(rows) { index ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading) {
                    ScShimmerBox(Modifier.size(rowHeight), RoundedCornerShape(rowHeight / 4))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    // Qatorlar bir xil uzunlikda bo'lsa "jadval" bo'lib ko'rinadi — navbat
                    // bilan qisqartiriladi, haqiqiy ro'yxatga o'xshaydi.
                    ScShimmerLine(if (index % 2 == 0) 0.62f else 0.48f, 13.dp)
                    ScShimmerLine(if (index % 2 == 0) 0.34f else 0.42f, 10.dp)
                }
            }
        }
    }
}

/** Katta karta skeleti — rasm + ikki qator matn ("Siz uchun" feed'i, e'lon tafsiloti). */
@Composable
fun ScShimmerCard(
    modifier: Modifier = Modifier,
    imageHeight: Dp = 150.dp,
    radius: Dp = 18.dp,
) {
    Column(modifier.fillMaxWidth().background(Sc.Card, RoundedCornerShape(radius))) {
        ScShimmerBox(
            Modifier.fillMaxWidth().height(imageHeight),
            RoundedCornerShape(topStart = radius, topEnd = radius),
        )
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            ScShimmerLine(0.7f, 14.dp)
            ScShimmerLine(0.4f, 12.dp)
        }
    }
}

/** To'r (grid) skeleti — GIF va stiker panellari uchun. */
@Composable
fun ScShimmerGrid(
    columns: Int = 3,
    rows: Int = 3,
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    cellHeight: Dp = 96.dp,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        repeat(rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                repeat(columns) {
                    ScShimmerBox(Modifier.weight(1f).height(cellHeight), RoundedCornerShape(12.dp))
                }
            }
        }
    }
}

/**
 * Ro'yxat OXIRIDAGI "yana yuklanmoqda" qatori (cheksiz scroll) — butun ekran skeleti emas,
 * bitta past blok.
 */
@Composable
fun ScShimmerFooter(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        ScShimmerBox(Modifier.fillMaxWidth(0.42f).height(12.dp), RoundedCornerShape(percent = 50))
    }
}
