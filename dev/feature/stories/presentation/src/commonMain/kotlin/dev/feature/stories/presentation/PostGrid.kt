package dev.feature.stories.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.core.uikit.components.AppIcons
import dev.core.uikit.components.ScNetworkImage
import dev.core.uikit.components.ScText
import dev.core.uikit.theme.Sc
import dev.feature.stories.domain.model.Story
import dev.feature.stories.domain.model.StoryKind
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Uch ustunli to'r. `LazyVerticalGrid` **ataylab ishlatilmadi**: bo'lim profilning
 * `verticalScroll` ustunida yashaydi va ichki aylanuvchi to'rning balandligini qo'lda
 * hisoblash kerak bo'lardi (qator soni × katak balandligi) — qatorlarga bo'lish esa
 * hech narsa hisoblamaydi va sig'imi ham yetarli (bir sahifada 30 ta post).
 */
@Composable
internal fun PostGrid(
    stories: List<Story>,
    archived: Boolean,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(GRID_GAP)) {
        stories.chunked(GRID_COLUMNS).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(GRID_GAP)) {
                row.forEachIndexed { columnIndex, story ->
                    PostCell(
                        story = story,
                        archived = archived,
                        modifier = Modifier.weight(1f),
                        onClick = { onOpen(rowIndex * GRID_COLUMNS + columnIndex) },
                    )
                }
                // Oxirgi qator to'lmasa qolgan joy bo'sh qoladi — aks holda ikkita post
                // butun kenglikka cho'zilib, katak o'lchamlari qatorma-qator o'zgarardi.
                repeat(GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Bitta post — poster va uning ustidagi ma'lumot.
 *
 * Faol postda **ko'rishlar soni** (u faqat o'z postingda keladi), arxivda esa **sana**:
 * arxiv oyma-oy o'sadi va u yerda "qachon qo'ygandim" degan savol asosiysi.
 */
@Composable
private fun PostCell(
    story: Story,
    archived: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier.aspectRatio(CELL_RATIO)
            .clip(RoundedCornerShape(12.dp))
            .background(Sc.Chip)
            .clickable(onClick = onClick),
    ) {
        // Rasm uchun — telefondagi nusxa (bo'lsa), aks holda serverdagi poster.
        //
        // ⚠️ Videoda local fayl ISHLATILMAYDI: rasm yuklovchi videodan kadr ajratmaydi
        // (buning uchun alohida dekoder kerak), ya'ni katak bo'sh qolardi. Video posteri
        // baribir yengil — u serverdan keladi.
        //
        // Saqlash muddati o'tgan arxiv postida serverga umuman so'rov yuborilmaydi: fayl
        // qaytarib olingan (`url` → 404) va yuklovchi buzilgan rasm belgisini chizardi.
        // Telefonda nusxa qolgan bo'lsa u baribir ko'rsatiladi.
        val purged = story.mediaPurged
        ScNetworkImage(
            url = when {
                story.kind == StoryKind.VIDEO -> (story.thumbUrl ?: story.url).takeIf { !purged }
                purged -> story.localUri
                else -> story.localUri ?: story.thumbUrl ?: story.url
            },
            modifier = Modifier.fillMaxSize(),
            contentDescription = if (story.kind == StoryKind.VIDEO) "Video post" else "Post",
            fallback = {
                // Post o'chmaydi — sanasi va o'rni qoladi, faqat mediasi yo'q.
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        AppIcons.ImageIcon,
                        contentDescription = "Media saqlanmagan",
                        tint = Sc.MutedLight,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
        )

        // Pastdagi qorayish — oq matn har qanday rasmda o'qilsin.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.55f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.6f),
                ),
            ),
        )

        if (story.kind == StoryKind.VIDEO) {
            Icon(
                AppIcons.Video,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(15.dp),
            )
        }

        Row(
            Modifier.align(Alignment.BottomStart).padding(horizontal = 7.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (archived) {
                ScText(shortDate(story), 11f, FontWeight.Bold, Color.White, maxLines = 1)
            } else {
                Icon(
                    AppIcons.Eye,
                    contentDescription = "Ko'rishlar",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp),
                )
                ScText("${story.viewsCount ?: 0}", 11f, FontWeight.Bold, Color.White, maxLines = 1)
            }
        }
    }
}

/**
 * Postlar ro'yxati hali kelmagan.
 *
 * Bo'sh holat plitkasi bu yerda YO'Q: post yo'q bo'lsa bo'lim umuman chizilmaydi. Faqat
 * yuklanish ko'rsatiladi — "bo'sh" deb yozish ro'yxat kelmasidan oldin yolg'on bo'lardi.
 */
@Composable
internal fun PostsLoadingNote() {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Sc.Card),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.padding(horizontal = 20.dp, vertical = 30.dp)) {
            ScText("Yuklanmoqda…", 16f, FontWeight.ExtraBold, Sc.Ink, maxLines = 1)
        }
    }
}

/** «12 iyul», o'tgan yildagi post uchun yil bilan: «12 iyul 2025». */
private fun shortDate(story: Story): String {
    val date = story.createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).date
    val thisYear = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
    val month = UZ_MONTHS.getOrNull(date.monthNumber - 1).orEmpty()
    return if (date.year == thisYear) "${date.dayOfMonth} $month" else "${date.dayOfMonth} $month ${date.year}"
}

private val UZ_MONTHS = listOf(
    "yanvar", "fevral", "mart", "aprel", "may", "iyun",
    "iyul", "avgust", "sentabr", "oktabr", "noyabr", "dekabr",
)

private const val GRID_COLUMNS = 3
private val GRID_GAP = 5.dp

/** Katak nisbati — story kadrining o'zi kabi tik (Telegramdagidek). */
private const val CELL_RATIO = 0.72f
