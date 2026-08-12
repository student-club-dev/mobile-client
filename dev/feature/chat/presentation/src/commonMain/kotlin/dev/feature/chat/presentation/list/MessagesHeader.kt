package dev.feature.chat.presentation.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import dev.core.uikit.components.ScCircleButton
import dev.core.uikit.components.ScHeaderTitle
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.StatusBarAppearance
import dev.core.uikit.theme.Sc
import dev.feature.chat.presentation.ChatMyProfile
import dev.feature.connections.domain.model.StudentSummary
import dev.feature.stories.presentation.StoriesCell
import dev.feature.stories.presentation.StoriesRow
import dev.feature.stories.presentation.storiesCollapsedWidth
import kotlin.math.roundToInt
import dev.feature.chat.presentation.chatStrings
import dev.core.uikit.locale.uiStrings

/**
 * «Xabarlar» ekranining yig'iluvchi sarlavhasi.
 *
 * Butun harakat bitta qiymatga bog'langan: [collapse] `0f` — panel to'liq ochiq, `1f` —
 * yig'ilgan. Qiymat ro'yxatning surilishidan UZLUKSIZ hisoblanadi va **hech qayerda
 * kompozitsiyada o'qilmaydi** — faqat o'lchash (`Layout`) va chizish (`graphicsLayer`)
 * lambda'larida. Shu sababli surish davomida sarlavha qayta qurilmaydi: har kadrda
 * atigi joylashtirish va chizish qayta bajariladi.
 *
 * Nima bo'ladi:
 * - story lentasi kichrayib, sarlavha yonidagi kichik to'plamga aylanadi
 *   ([StoriesRow] `collapse`) — ikkita alohida ko'rinish emas, BITTA uzluksiz harakat;
 * - panel ekran chetiga "yopishadi": pastki radius to'g'rilanadi va tagida soya chiqadi.
 *
 * Panelda qidiruv MAYDONI yo'q — faqat sarlavhadagi tugma, u klaviatura ustidagi
 * suzuvchi qatlamni ochadi (`ScSearchOverlay`). Papka chiplari ham bu yerda emas:
 * ular panel OSTIDA, ekran fonida turadi.
 */
@Composable
internal fun MessagesHeader(
    collapse: () -> Float,
    title: String,
    onBack: (() -> Unit)?,
    /** Arxiv tugmasi ko'rinsinmi (arxivda suhbat bo'lsa). */
    showArchive: Boolean,
    onArchive: () -> Unit,
    onOpenSearch: () -> Unit,
    /** Lenta chizilsinmi — arxivda u ko'rinmaydi (u yerda ekran boshqa narsani ko'rsatadi). */
    stories: Boolean,
    myProfile: ChatMyProfile,
    onOpenProfile: (StudentSummary) -> Unit,
    /** Yig'ilgan to'plam bosildi — ro'yxat tepasiga qaytadi va lenta ochiladi. */
    onStoriesTop: () -> Unit,
    /**
     * Qidiruv faolmi. Panelda maydon YO'Q — faqat tugma, shuning uchun so'rov yozilganini
     * bildiradigan yagona belgi shu tugmadagi nuqta bo'ladi.
     */
    searchActive: Boolean,
    /**
     * Panel qancha masofada yig'ilishi O'LCHASHda ma'lum bo'ladi (lentaning haqiqiy
     * balandligi). Ekran shu qiymatni surish hisobida ishlatadi.
     */
    onCollapseRange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Ko'k gradient ustida qora status bar belgilari o'qilmaydi.
    StatusBarAppearance(darkIcons = false)

    val brush = Sc.headerBrush
    val statusTop = WindowInsets.statusBars.getTop(LocalDensity.current)
    // Yig'ilgan to'plam sarlavhaning chap yonida turadi — orqaga tugmasidan keyin.
    val collapsedStart = Sc.HeaderPadding + if (onBack != null) BackButton + BackGap else 0.dp
    // To'plam qancha joy egallasa, sarlavha shuncha o'ngga suriladi. Lavhalar soni
    // o'zgargandagina qayta hisoblanadi — animatsiya davomida o'zgarmaydi.
    val stackReserve = if (stories) storiesCollapsedWidth() + StackGap else 0.dp
    val ringCenter = StoriesPadding + StoriesCell / 2

    Box(
        modifier.fillMaxWidth()
            .graphicsLayer {
                val f = collapse().coerceIn(0f, 1f)
                // Yig'ilganda panel ekran chetiga "yopishadi": burchaklar to'g'rilanadi,
                // tagida esa ro'yxatdan ajratib turuvchi soya paydo bo'ladi.
                val radius = HeaderRadius * (1f - f)
                shape = RoundedCornerShape(bottomStart = radius, bottomEnd = radius)
                clip = true
                shadowElevation = lerp(0f, HeaderShadow.toPx(), f)
            }
            .background(brush),
    ) {
        // Dekorativ doiralar — maketdagi `rgba(255,255,255,.10)` / `.08`.
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

        Layout(
            contents = listOf(
                {
                    Toolbar(
                        collapse = collapse,
                        title = title,
                        onBack = onBack,
                        showArchive = showArchive,
                        onArchive = onArchive,
                        onOpenSearch = onOpenSearch,
                        searchActive = searchActive,
                        stackReserve = stackReserve,
                    )
                },
                {
                    if (stories) {
                        StoriesRow(
                            myName = myProfile.name,
                            myAvatarUrl = myProfile.avatarUrl,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(
                                horizontal = Sc.HeaderPadding,
                                vertical = StoriesPadding,
                            ),
                            // Lenta ko'k gradient ustida — yozuvlar va halqalar oq bo'ladi.
                            onHeader = true,
                            // Muallif profili — chatdagi suhbatdosh varag'i bilan AYNAN bir xil.
                            onOpenProfile = onOpenProfile,
                            collapse = collapse,
                            collapsedStart = collapsedStart,
                            onCollapsedClick = onStoriesTop,
                        )
                    }
                },
            ),
            modifier = Modifier.fillMaxWidth(),
        ) { (toolbarM, storiesM), constraints ->
            val width = constraints.maxWidth
            val slot = Constraints(maxWidth = width)
            val toolbar = toolbarM.first().measure(slot)
            val strip = storiesM.firstOrNull()?.measure(slot)

            val storiesGap = if (strip != null) StoriesGap.roundToPx() else 0
            val storiesBlock = storiesGap + (strip?.height ?: 0)
            // Panel shu masofada to'liq yig'iladi — ya'ni lenta balandligicha. Lenta
            // bo'lmasa (arxiv) panel umuman yig'ilmaydi: yig'iladigan narsaning o'zi yo'q.
            onCollapseRange(storiesBlock.toFloat())

            val f = collapse().coerceIn(0f, 1f)
            val storiesLeft = (storiesBlock * (1f - f)).roundToInt()
            val height = statusTop + toolbar.height + storiesLeft + HeaderBottom.roundToPx()

            val ringCenterPx = ringCenter.toPx()

            layout(width, height) {
                // ⚠️ Lenta sarlavha qatoridan OLDIN qo'yiladi, ya'ni u ostda qoladi.
                // Yig'ilganda lenta butun kenglik bo'ylab sarlavha qatorining ustiga
                // chiqadi va agar u tepada tursa, tegishlarni O'ZI yutib qolardi:
                // qidiruv, arxiv va orqaga tugmalari bosilmay qo'yardi (Compose ustki
                // qardoshda tegish topilsa, tagidagilarni umuman tekshirmaydi).
                // Yig'ilgan to'plamning o'z joyi esa bo'sh: u yerda faqat sarlavha uchun
                // ochilgan oraliq turadi, tugmalar emas.
                if (strip != null) {
                    // Lenta o'z balandligini SAQLAB yuqoriga suriladi — siqilib ezilmaydi.
                    // Yig'ilgan holatda avatarlar markazi aynan sarlavha qatorining
                    // o'rtasiga tushadi (kataklar shkalasi ham shu markaz atrofida).
                    val expanded = (statusTop + toolbar.height + storiesGap).toFloat()
                    // Sarlavha qatorining O'RTASI — uning balandligining yarmi emas:
                    // qatorning tepasida bo'shliq bor va yarmi olinsa to'plam
                    // tugmalardan bir necha piksel yuqorida turib qolardi.
                    val rowTop = statusTop + ToolbarTop.toPx()
                    val rowCenter = rowTop + (statusTop + toolbar.height - rowTop) / 2f
                    strip.place(0, lerp(expanded, rowCenter - ringCenterPx, f).roundToInt())
                }

                toolbar.place(0, statusTop)
            }
        }
    }
}

/**
 * Sarlavha qatori: orqaga, yig'ilgan to'plam uchun joy, sarlavha va tugmalar.
 *
 * To'plamning O'ZI bu yerda chizilmaydi — u lentaning yig'ilgan ko'rinishi va panel
 * uni shu qatorning ustiga olib keladi. Bu yerda faqat unga JOY ochiladi, aks holda
 * sarlavha to'plam ostida qolib ketardi.
 */
@Composable
private fun Toolbar(
    collapse: () -> Float,
    title: String,
    onBack: (() -> Unit)?,
    showArchive: Boolean,
    onArchive: () -> Unit,
    onOpenSearch: () -> Unit,
    searchActive: Boolean,
    stackReserve: Dp,
) {
    Row(
        Modifier.fillMaxWidth()
            .padding(start = Sc.HeaderPadding, end = Sc.HeaderPadding, top = ToolbarTop),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            ScCircleButton(ScIcons.ChevronLeft, onBack, contentDescription = uiStrings().back)
            Spacer(Modifier.width(BackGap))
        }
        Spacer(
            Modifier.layout { measurable, _ ->
                val placeable = measurable.measure(Constraints.fixed(0, 0))
                val reserved = (stackReserve.toPx() * collapse().coerceIn(0f, 1f)).roundToInt()
                layout(reserved, 0) { placeable.place(0, 0) }
            },
        )
        ScHeaderTitle(
            title,
            size = 26f,
            modifier = Modifier.weight(1f).graphicsLayer {
                // Sarlavha yig'ilganda bir oz kichrayadi. `fontSize` emas, shkala:
                // o'lcham kompozitsiya parametri bo'lgani uchun har kadrda matn
                // qaytadan o'lchanardi.
                val scale = lerp(1f, CollapsedTitleScale, collapse().coerceIn(0f, 1f))
                transformOrigin = TransformOrigin(0f, 0.5f)
                scaleX = scale
                scaleY = scale
            },
        )
        Spacer(Modifier.width(BackGap))
        // Qidiruv — panelda maydon YO'Q, faqat shu tugma: u har doim ko'rinadi va
        // klaviatura ustidagi suzuvchi qatlamni ochadi. Nuqta — so'rov yozib qo'yilgani
        // va ro'yxat filtrlanib turgani belgisi.
        ScCircleButton(
            ScIcons.Search,
            onOpenSearch,
            contentDescription = chatStrings().search,
            badge = searchActive,
        )
        if (showArchive) {
            Spacer(Modifier.width(8.dp))
            ScCircleButton(ScIcons.Archive, onArchive, contentDescription = chatStrings().archive)
        }
    }
}

/** Orqaga tugmasi va undan keyingi oraliq — to'plamning boshlanish nuqtasi shundan. */
private val BackButton = 42.dp
private val BackGap = 12.dp

/** Yig'ilgan to'plam bilan sarlavha orasidagi oraliq. */
private val StackGap = 12.dp

/** Sarlavha qatorining tepasidagi bo'shliq. */
private val ToolbarTop = 14.dp

/** Lentaning o'z ichki bo'shlig'i — avatar markazi shundan hisoblanadi. */
private val StoriesPadding = 4.dp

/** Sarlavha qatori bilan lenta orasidagi oraliq. */
private val StoriesGap = 10.dp

/** Lenta bilan panelning pastki cheti orasidagi bo'shliq. */
private val HeaderBottom = 14.dp

/** Panelning ochiq holatdagi pastki radiusi va yig'ilgandagi soyasi. */
private val HeaderRadius = 34.dp
private val HeaderShadow = 6.dp

/** Yig'ilgan sarlavhaning o'lchami (26sp → ~22sp). */
private const val CollapsedTitleScale = 0.85f
