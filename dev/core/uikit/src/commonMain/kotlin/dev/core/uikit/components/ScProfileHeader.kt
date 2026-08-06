package dev.core.uikit.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.material3.Icon
import dev.core.uikit.theme.Sc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Yig'iluvchi profil sarlavhasining holati (collapsing toolbar) — Telegramdagidek: ro'yxat
 * tepasida turib **pastga tortilsa** avatar butun kenglikni egallaydigan rasmga aylanadi.
 *
 * Balandlik **pikselda** saqlanadi va barmoq bilan bir kadrda o'zgaradi. `Animatable`
 * ishlatilmadi: uning `snapTo` si suspend, ya'ni har bir surish korutinaga tushib bir kadr
 * kechikardi va sarlavha barmoqdan orqada sudralardi.
 */
@Stable
class ScCollapsingHeaderState internal constructor(
    /** Eng kichik balandlik — faqat topbar (orqaga tugmasi, ism va holat bir qatorda). */
    private val minPx: Float,
    /** Avatar, ism va holat sig'adigan «odatiy» balandlik — ekran shundan ochiladi. */
    private val midPx: Float,
    private val maxPx: Float,
    private val scope: CoroutineScope,
) {
    /**
     * Yoyish mumkinmi — rasm umuman bo'lmasa sarlavha faqat yig'ilgan holatda turadi.
     *
     * ⚠️ Konstruktor parametri EMAS, o'zgaruvchan holat. Rasm ro'yxati serverdan **kechroq**
     * keladi, ya'ni bu qiymat `false` dan `true` ga o'zgaradi. Ilgari u `remember(...)`
     * kaliti edi va o'zgargan zahoti butun holat qayta qurilardi: `heightPx` sukut
     * qiymatiga (`midPx`) **sakrab** qaytardi — animatsiyasiz, barmoq ostida.
     */
    var expandable by mutableStateOf(true)
        internal set

    var heightPx by mutableFloatStateOf(midPx)
        private set

    private var snapJob: Job? = null

    /** `0f` — avatarli holat, `1f` — to'liq yoyilgan (butun kenglikdagi rasm). */
    val progress: Float
        get() = if (maxPx > midPx) ((heightPx - midPx) / (maxPx - midPx)).coerceIn(0f, 1f) else 0f

    /**
     * Pastki bosqich: `1f` — avatar to'liq ko'rinadi, `0f` — sarlavha **topbargacha**
     * yig'ilgan (Telegramdagidek: faqat orqaga tugmasi, ism va holat bir qatorda).
     *
     * `progress` dan alohida, chunki bu ikki BOSHQA harakat: tepasi rasmni ochadi, pastki
     * qismi esa ro'yxatga joy bo'shatadi. Bittasiga jamlansa avatar rasm ochilayotganda
     * ham so'nib ketardi.
     */
    val shrink: Float
        get() = if (midPx > minPx) ((heightPx - minPx) / (midPx - minPx)).coerceIn(0f, 1f) else 1f

    val expanded: Boolean get() = progress > EXPANDED_THRESHOLD

    /** Sarlavha topbargacha yig'ilganmi — ism/holat endi yuqori qatorda ko'rinadi. */
    val collapsedToToolbar: Boolean get() = shrink < TOOLBAR_THRESHOLD

    /** Kontent bilan birga aylanadigan qismga ulanadi — `Modifier.nestedScroll(...)`. */
    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val delta = available.y
            // Yuqoriga surish — avval SARLAVHA yig'iladi, kontent keyin aylanadi.
            if (delta < 0 && heightPx > minPx) {
                snapJob?.cancel()
                val next = (heightPx + delta).coerceAtLeast(minPx)
                val used = next - heightPx
                heightPx = next
                return Offset(0f, used)
            }
            return Offset.Zero
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            val delta = available.y
            // `available` — kontent yutmagan qoldiq, ya'ni ro'yxat allaqachon tepasida.
            // Shundagina sarlavha ochila boshlaydi.
            //
            // ⚠️ Yuqori chegara [expandable] ga qarab O'ZGARADI, lekin qaytish yo'li
            // **har doim ochiq**:
            //
            //   toolbar → odatiy   — doim (bu shunchaki yig'ilgan sarlavhaning qaytishi);
            //   odatiy  → rasm     — faqat rasm bo'lsa.
            //
            // Ilgari butun shart `expandable` ostida edi va rasmi yo'q profilda sarlavha
            // bir marta yig'ilgach **abadiy topbar bo'lib qolardi**: pastga qancha
            // tortmang, ochilmasdi. Foydalanuvchi buni "animatsiya ishlamayapti" deb
            // ko'radi — va haq, chunki qaytish yo'li umuman yo'q edi.
            val ceiling = if (expandable) maxPx else midPx
            if (delta > 0 && heightPx < ceiling) {
                snapJob?.cancel()
                val next = (heightPx + delta).coerceAtMost(ceiling)
                val used = next - heightPx
                heightPx = next
                return Offset(0f, used)
            }
            return Offset.Zero
        }

        // Barmoq ko'tarilganda oraliq holatda qolmasin — eng yaqiniga tushadi.
        override suspend fun onPreFling(available: Velocity): Velocity {
            // Faqat RASM oralig'ida (mid…max) yaqiniga tushiriladi: yarim ochilgan rasm
            // ma'nosiz holat. Pastki oraliq (toolbar…mid) esa ro'yxat bilan birga
            // suriladi va u yerda «yopishtirish» aylanishni sakratib yuborardi.
            if (heightPx > midPx && heightPx < maxPx) {
                val half = midPx + (maxPx - midPx) * SNAP_POINT
                animateTo(if (heightPx >= half) maxPx else midPx)
            }
            return Velocity.Zero
        }
    }

    /** Avatar bosilganda — yoyish (barmoq bilan tortish shart emas). */
    fun expand() {
        if (expandable) animateTo(maxPx)
    }

    fun collapse() = animateTo(midPx)

    private fun animateTo(target: Float) {
        snapJob?.cancel()
        snapJob = scope.launch {
            animate(heightPx, target, animationSpec = tween(SNAP_DURATION_MS)) { value, _ ->
                heightPx = value
            }
        }
    }

    private companion object {
        /** Barmoq ko'tarilganda shu ulushdan oshgan sarlavha to'liq ochiladi. */
        const val SNAP_POINT = 0.4f
        const val SNAP_DURATION_MS = 220

        /** Yoyilgan deb hisoblash chegarasi — animatsiya oxiridagi kasr qoldiq uchun. */
        const val EXPANDED_THRESHOLD = 0.99f

        /** Shundan pastda ism topbarga ko'chgan deb hisoblanadi. */
        const val TOOLBAR_THRESHOLD = 0.5f
    }
}

/**
 * [collapsedHeight] — avatar, ism va holat sig'adigan «odatiy» balandlik (ekran shundan
 * ochiladi); [expandedHeight] — odatda ekran kengligi (kvadrat rasm).
 *
 * Eng kichik balandlik — **topbar**: `56dp` + status bar. Ro'yxat surilganda sarlavha shu
 * yergacha yig'iladi va ism topbarga ko'chadi (Telegramdagidek), ya'ni kontent uchun butun
 * ekran bo'shaydi.
 */
@Composable
fun rememberScCollapsingHeaderState(
    collapsedHeight: Dp,
    expandedHeight: Dp,
    expandable: Boolean = true,
): ScCollapsingHeaderState {
    val density = LocalDensity.current
    val statusBarPx = WindowInsets.statusBars.getTop(density)
    val minPx = with(density) { TOOLBAR_HEIGHT.toPx() } + statusBarPx
    val midPx = with(density) { collapsedHeight.toPx() }
    val maxPx = with(density) { expandedHeight.toPx() }
    val scope = rememberCoroutineScope()
    // Kalitlarda [expandable] YO'Q: u rasm kelishi bilan o'zgaradi va holatni qayta qurish
    // sarlavhani animatsiyasiz sakratardi (qarang [ScCollapsingHeaderState.expandable]).
    val state = remember(minPx, midPx, maxPx, scope) {
        ScCollapsingHeaderState(minPx, midPx, maxPx, scope)
    }
    state.expandable = expandable
    // Yoyish imkoni yo'qolsa (oxirgi rasm o'chirildi) sarlavha odatiy balandligiga
    // **animatsiya bilan** qaytadi — to'satdan kesilib qolmasin.
    LaunchedEffect(expandable) { if (!expandable) state.collapse() }
    return state
}

/** Yig'ilgan topbar balandligi — status bar'dan pastda (Material'dagi standart). */
private val TOOLBAR_HEIGHT = 56.dp

/**
 * Profil sarlavhasi — bitta ko'rinish, ikki chekka holat orasida **uzluksiz** o'zgaradi:
 *
 * - `progress = 0` — brend gradienti, markazda dumaloq avatar, ostida ism va holat;
 * - `progress = 1` — rasm butun kenglikni egallaydi, ustida gradient va chap pastda ism.
 *
 * Avatar **o'sib boradi**: o'lchami va burchak radiusi oralatib hisoblanadi, shuning uchun
 * doira asta-sekin to'rtburchak rasmga aylanadi. Matnlar esa bir-birini almashtiradi
 * (shaffoflik bo'yicha) — ikki xil joylashuvni piksel bo'yicha oralatishdan ko'ra
 * ishonchliroq va sakramaydi.
 *
 * [photoUrls] bir nechta bo'lsa yoyilgan rasmning chap/o'ng yarmiga tegish keyingisiga
 * o'tkazadi (Telegramdagidek). Surish (`swipe`) ataylab ishlatilmadi: sarlavhaning o'zi
 * vertikal `nestedScroll` da yashaydi va ikkala jest to'qnashardi.
 */
@Composable
fun ScProfileHeader(
    state: ScCollapsingHeaderState,
    name: String,
    status: String,
    photoUrls: List<String>,
    photoIndex: Int,
    onAvatarClick: () -> Unit,
    onStep: (forward: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    collapsedAvatarSize: Dp = COLLAPSED_AVATAR,
    collapsedAvatarTop: Dp = COLLAPSED_AVATAR_TOP,
    initialColor: Color = Sc.Brand,
    /** Topbarning **chap** qismi — odatda orqaga tugmasi. */
    topBar: @Composable RowScope.() -> Unit = {},
    /**
     * Topbarning **o'ng** qismi (sozlamalar, «⋮» …).
     *
     * Chap va o'ng alohida: yig'ilganda ular orasida ism turadi va bitta slotda bo'lsa uni
     * o'rtaga qo'yib bo'lmasdi.
     */
    trailing: @Composable RowScope.() -> Unit = {},
    /** Avatar ustidagi qatlam — masalan yuklash halqasi. Avatar o'lchamini oladi. */
    avatarOverlay: @Composable BoxScope.() -> Unit = {},
) {
    // Ko'k gradient va rasm ustida qora status bar belgilari o'qilmaydi.
    StatusBarAppearance(darkIcons = false)

    val progress = state.progress
    val shrink = state.shrink
    val height = with(LocalDensity.current) { state.heightPx.toDp() }

    // `clipToBounds` — yig'ilganda avatar sarlavhadan tashqariga chiqib ketmasin: u
    // so'nib boradi, lekin bir necha kadr davomida balandlikdan kattaroq qoladi.
    Box(modifier.fillMaxWidth().height(height).clipToBounds()) {
        // Brend gradienti — rasm yoyilgani sari so'nadi.
        Box(Modifier.fillMaxSize().alpha(1f - progress).background(Sc.headerBrush))

        // Avatar: markazdagi doiradan butun sarlavhagacha.
        val avatarSize = lerp(collapsedAvatarSize, height, progress)
        val corner = lerp(collapsedAvatarSize / 2, 0.dp, progress)
        Box(
            Modifier.align(Alignment.TopCenter)
                .padding(top = lerp(collapsedAvatarTop, 0.dp, progress))
                // Topbargacha yig'ilganda avatar so'nadi — o'sha joyni ism egallaydi.
                .alpha(shrink)
                .clickable(enabled = shrink > 0f, onClick = onAvatarClick),
            contentAlignment = Alignment.Center,
        ) {
            ScAvatar(
                name = name,
                size = avatarSize,
                avatarUrl = photoUrls.getOrNull(photoIndex),
                // Bosh harf avatar bilan birga kattalashmasin — yoyilganda u ekran
                // bo'yi harfga aylanardi.
                fontSize = AVATAR_INITIAL_SIZE,
                // Gradient ustida oq doira — maketdagi kabi kontrast.
                background = Color.White.copy(alpha = 0.9f),
                initialColor = initialColor,
                shape = RoundedCornerShape(corner),
            )
            avatarOverlay()
        }

        // Yoyilgan rasmda chap/o'ng tegish zonalari. Faqat bir nechta rasm bo'lganda va
        // faqat to'liq yoyilganda: yig'ilgan holatda avatar kichkina va bosish uni
        // yoyishi kerak ([onAvatarClick]).
        if (photoUrls.size > 1 && state.expanded) {
            Row(Modifier.fillMaxSize()) {
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .clickable(indication = null, interactionSource = null) { onStep(false) },
                )
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .clickable(indication = null, interactionSource = null) { onStep(true) },
                )
            }
        }

        // Rasm ustidagi gradient — tugmalar va ism har qanday rasmda o'qilsin.
        if (progress > 0f) {
            Box(
                Modifier.fillMaxSize().alpha(progress).background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.45f),
                        0.3f to Color.Transparent,
                        0.6f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.75f),
                    ),
                ),
            )
        }

        Column(Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp)) {
            if (photoUrls.size > 1) {
                ScPhotoDashes(count = photoUrls.size, current = photoIndex, alpha = progress)
                Spacer(Modifier.height(8.dp))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Chapdagi tugma(lar) — orqaga.
                topBar()

                // Ism va holat topbarga **ko'chadi**: sarlavha yig'ilganda markazdagi
                // katta ism yo'qoladi va u shu yerda, orqaga tugmasining yonida paydo
                // bo'ladi (Telegramdagi kabi). Ikkalasi bir vaqtda ko'rinmaydi —
                // shaffofliklari bir-birining teskarisi.
                if (shrink < 1f) {
                    Column(
                        Modifier.weight(1f)
                            .alpha(1f - shrink)
                            .padding(start = 6.dp, end = 6.dp),
                    ) {
                        ScText(name, 17f, FontWeight.ExtraBold, Color.White, maxLines = 1)
                        ScText(
                            status,
                            12f,
                            FontWeight.Medium,
                            Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                        )
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }

                trailing()
            }
        }

        // Yig'ilgan holatdagi ism — markazda, avatar ostida.
        if (progress < 1f && shrink > 0f) {
            Column(
                Modifier.align(Alignment.BottomCenter)
                    .alpha((1f - progress) * shrink)
                    .padding(start = 16.dp, end = 16.dp, bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ScText(name, 21f, FontWeight.ExtraBold, Color.White, letterSpacing = -0.3f, maxLines = 2)
                Spacer(Modifier.height(3.dp))
                ScText(status, 13.5f, FontWeight.Medium, Color.White.copy(alpha = 0.85f), maxLines = 1)
            }
        }

        // Yoyilgan holatdagi ism — chap pastda, kattaroq.
        if (progress > 0f) {
            Column(
                Modifier.align(Alignment.BottomStart)
                    .alpha(progress)
                    .padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
            ) {
                ScText(name, 27f, FontWeight.ExtraBold, Color.White, letterSpacing = -0.5f, maxLines = 2)
                Spacer(Modifier.height(2.dp))
                ScText(status, 14f, FontWeight.Medium, Color.White.copy(alpha = 0.85f), maxLines = 1)
            }
        }
    }
}

/** Telegram'dagidek: rasm soniga qarab yuqoridagi chiziqchalar. */
@Composable
fun ScPhotoDashes(count: Int, current: Int, alpha: Float) {
    Row(Modifier.fillMaxWidth().alpha(alpha), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(count) { index ->
            Box(
                Modifier.weight(1f)
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = if (index == current) 1f else 0.35f)),
            )
        }
    }
}

/** Rasm ustida turadigan yarim shaffof dumaloq tugma. */
@Composable
fun ScGlassButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
) {
    Box(
        Modifier.size(40.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(Color.White.copy(alpha = 0.2f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(20.dp)) }
}

/** Yig'ilgan holatdagi avatar diametri va uning tepadan chekinishi. */
private val COLLAPSED_AVATAR = 96.dp
private val COLLAPSED_AVATAR_TOP = 62.dp

/** Bosh harf o'lchami — avatar kattalashganda ham o'zgarmaydi. */
private const val AVATAR_INITIAL_SIZE = 34f
