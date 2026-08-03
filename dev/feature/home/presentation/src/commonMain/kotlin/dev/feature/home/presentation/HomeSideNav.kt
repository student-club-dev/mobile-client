package dev.feature.home.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import dev.core.uikit.components.AppIcons
import dev.core.uikit.components.ScAvatar
import dev.core.uikit.components.ScIconTile
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScText
import dev.core.uikit.components.scSoftShadow
import dev.core.uikit.theme.Sc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Bosh ekranning yon navigatsiyasi: chap chetdagi tutqichdan yoki **barmoq bilan chetdan
 * o'ngga surib** ochiladigan panel.
 *
 * Nega kerak — pastki panelda atigi to'rtta tab bor (Home / Universitet / E'lonlar /
 * Takliflar), qolgan bo'limlar (Xabarlar, Do'stlar, So'rovlar, Sozlamalar) esa ekran
 * ichidagi tugmalarga tarqalib ketgan edi. Yon panel ularning HAMMASI uchun bitta
 * ro'yxat beradi; mavjud tugmalar joyida qoladi — bu qo'shimcha yo'l, almashtiruv emas.
 *
 * Panel Home ekrani ichida chiziladi, ya'ni pastki navigatsiya paneli uning USTIDA
 * qoladi. Shu sababli ro'yxat ostiga [BottomBarGap] bo'shligi qo'yiladi — oxirgi
 * qator panel tagida yashirinib qolmasin.
 */
private val SideNavWidth = 288.dp

/** Pastki navigatsiya paneli uchun bo'sh joy — ro'yxatning oxirgi qatori u bilan to'qnashmasin. */
private val BottomBarGap = 96.dp

/** Ochilish/yopilish davomiyligi — topbar siqilishi bilan bir xil egri ([ScEasing]). */
private const val SideNavDuration = 280

/**
 * Chap chetning shuncha qismidan boshlangan surish panelni ochadi.
 *
 * Atayin keng: Android'ning tizim "orqaga" imo-ishorasi eng chetdagi ~20dp ni o'zi olib
 * qo'yadi, tor zona qoldirsak surish deyarli hech qachon bizga yetib kelmaydi.
 */
private val EdgeZone = 44.dp

/**
 * Barmoq uzilganda yo'nalishni hal qiluvchi tezlik (px/soniya). Bundan tez surilgan
 * bo'lsa masofaga qaralmaydi — qisqa, lekin keskin harakat ham panelni ochadi/yopadi.
 */
private const val FlingVelocity = 450f

/**
 * Panelning holati: `0` — yopiq, `1` — to'liq ochiq. Oralig'idagi qiymat — barmoq
 * bilan surish payti.
 *
 * Holat `Animatable` da: bitta qiymat ham animatsiyani (tugma bosilganda), ham
 * barmoqni (surilganda) boshqaradi — panel surish o'rtasida barmoqdan "uzilib"
 * qolmaydi.
 */
@Stable
internal class SideNavState(
    private val widthPx: Float,
    private val scope: CoroutineScope,
) {
    val progress = Animatable(0f)

    /**
     * Surish paytidagi maqsad qiymati. `progress.value` dan o'qib bo'lmaydi: har bir
     * `snapTo` alohida korutinada bajariladi va ular bir-birini bekor qiladi — oraliq
     * qiymatlar yo'qolib, panel barmoqdan orqada qolardi.
     */
    private var pending = 0f

    /** Panel chizilishi kerakmi — to'liq yopilganda kompozitsiyadan chiqadi. */
    val visible: Boolean get() = progress.value > 0f

    fun open() {
        pending = 1f
        scope.launch { progress.animateTo(1f, tween(SideNavDuration, easing = ScEasing)) }
    }

    fun close() {
        pending = 0f
        scope.launch { progress.animateTo(0f, tween(SideNavDuration, easing = ScEasing)) }
    }

    /** Surish boshlandi — hisobni panelning JORIY holatidan davom ettiramiz. */
    fun startDrag() {
        pending = progress.value
    }

    /** Barmoq siljishi (px). O'ngga — musbat. */
    fun drag(deltaPx: Float) {
        pending = (pending + deltaPx / widthPx).coerceIn(0f, 1f)
        val target = pending
        scope.launch { progress.snapTo(target) }
    }

    /** Barmoq uzildi: tezlikka, u past bo'lsa — bosib o'tilgan yo'lga qarab hal qilinadi. */
    fun settle(velocityPx: Float) {
        val shouldOpen = when {
            velocityPx > FlingVelocity -> true
            velocityPx < -FlingVelocity -> false
            else -> progress.value > 0.5f
        }
        if (shouldOpen) open() else close()
    }
}

@Composable
internal fun rememberSideNavState(): SideNavState {
    val widthPx = with(LocalDensity.current) { SideNavWidth.toPx() }
    val scope = rememberCoroutineScope()
    return remember(widthPx, scope) { SideNavState(widthPx, scope) }
}

/**
 * Chap chetdan o'ngga surish — panelni ochadi. Butun ekranga qo'yiladi.
 *
 * Nega oddiy `draggable` bilan ko'rinmas tasma emas: tasma o'sha joyni butunlay
 * egallaydi va uning ostidagi kartalar bosilmay, gorizontal lentalar surilmay qolardi.
 *
 * Bu yerda hodisalar [PointerEventPass.Initial] bosqichida kuzatiladi — ya'ni
 * bolalardan OLDIN, lekin harakat aniq gorizontal bo'lgunicha hech narsa YUTILMAYDI:
 * bosish ham, vertikal scroll ham o'z egasiga o'tadi. Faqat chetdan boshlangan
 * gorizontal surish "tortib olinadi" — shundagina ichkaridagi `LazyRow` ustun keladi.
 */
@Composable
internal fun Modifier.sideNavEdgeDrag(nav: SideNavState): Modifier {
    val edgePx = with(LocalDensity.current) { EdgeZone.toPx() }
    return pointerInput(nav, edgePx) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // Panel allaqachon ochiq bo'lsa — uni o'zining surish ishlovchisi boshqaradi.
            if (nav.progress.value > 0f || down.position.x > edgePx) return@awaitEachGesture

            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var dx = 0f
            var dy = 0f
            var captured = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                tracker.addPosition(change.uptimeMillis, change.position)
                if (!change.pressed) {
                    if (captured) nav.settle(tracker.calculateVelocity().x)
                    return@awaitEachGesture
                }
                val move = change.positionChange()
                if (captured) {
                    change.consume()
                    nav.drag(move.x)
                    continue
                }
                dx += move.x
                dy += move.y
                // Vertikal harakat ustun — bu ro'yxat scroll'i, aralashmaymiz.
                if (abs(dy) > slop && abs(dy) >= abs(dx)) return@awaitEachGesture
                if (dx > slop) {
                    captured = true
                    nav.startDrag()
                    change.consume()
                    nav.drag(dx)
                }
            }
        }
    }
}

/**
 * Panelni ochadigan tutqich — chap chetda, topbar bilan story lentasi ORASIDA.
 *
 * Rangi KULRANG (`Sc.Chip` + `Sc.ChipInk`), brend ko'ki emas: tutqich kontentning bir
 * qismi emas, boshqaruv elementi — neytral rang uni kartalar bilan aralashtirmaydi.
 *
 * Foni QISMAN shaffof ([HandleAlpha]) — to'liq shaffof emas: ekran foni sezilib turadi,
 * lekin `»` belgisi har qanday yuzada o'qiladi. To'liq shaffof bo'lganda tutqich rangli
 * fon ustiga tushib qolsa ko'rinmay ketardi.
 *
 * Yumshoq soya uni yuzadan ajratib turadi. Ichida `»` — ikkita ustma-ust surilgan `›`
 * ikonasi (to'plamda tayyor juft chevron yo'q).
 */
@Composable
internal fun SideNavHandle(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(17.dp)
    val chevron = ScIcons.ChevronRight
    Box(
        modifier
            .padding(start = 8.dp)
            .size(HandleSize)
            .scSoftShadow(12.dp, shape)
            .clip(shape)
            .background(Sc.Chip.copy(alpha = HandleAlpha))
            .border(1.dp, Sc.Handle.copy(alpha = HandleAlpha), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // `Arrangement.spacedBy` manfiy oraliqni qabul qilmaydi, shuning uchun ikkala
        // chevron markazdan teng masofaga suriladi — natijada zich `»` chiqadi.
        Icon(chevron, "Yon menyu", tint = Sc.ChipInk, modifier = Modifier.size(21.dp).offset(x = (-5).dp))
        Icon(chevron, null, tint = Sc.ChipInk, modifier = Modifier.size(21.dp).offset(x = 5.dp))
    }
}

/**
 * Tutqichni ustunda JOY EGALLAMAYDIGAN qilib qo'yadi: balandligi 0 deb o'lchanadi va
 * o'zining yarim bo'yi qadar tepaga suriladi.
 *
 * Natijada tugma topbar bilan pastdagi kontent chegarasida "suzadi" — story lentasini
 * ham, undan keyingi bo'limlarni ham pastga surmaydi. [zIndex] kerak: ustunda keyingi
 * bolalar kechroq chiziladi, ya'ni usiz tutqich lentaning ORQASIDA qolib ketardi.
 */
internal fun Modifier.sideNavHandleSeam(): Modifier = this
    .zIndex(1f)
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, 0) { placeable.place(0, -placeable.height / 2) }
    }

/** Tutqich kvadrat — eni va bo'yi teng. */
private val HandleSize = 52.dp

/** Fon shaffofligi: 0 — ko'rinmas, 1 — to'liq to'q. Tagidagi kontent sal sezilib tursin. */
private const val HandleAlpha = 0.82f

/**
 * Yon panelning o'zi: qorong'ilashtiruvchi fon + chapdan siljib chiqadigan ro'yxat.
 *
 * Panel to'liq yopilganda hech narsa chizilmaydi — ya'ni yopiq panel Home ekranidagi
 * bosishlarni ushlab qolmaydi.
 *
 * Ochiq panelning USTIDA gorizontal surish ishlaydi: o'ngdan chapga surilsa yopiladi.
 * Surish butun ekranga qo'yilgan (panel ham, qorayish qatlami ham) — modal holatda
 * tagidagi ekran baribir bloklangan, ya'ni hech narsa yo'qotilmaydi.
 *
 * Har bir qator avval panelni yopadi, keyin o'z ekraniga o'tadi: qaytib kelinganda
 * panel ochiq holda qolib ketmasin.
 */
@Composable
internal fun HomeSideNav(
    nav: SideNavState,
    state: HomeUiState,
    onOpenProfile: () -> Unit,
    onOpenUniversity: () -> Unit,
    onOpenListings: () -> Unit,
    onOpenOffers: () -> Unit,
    onOpenRentals: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenStudents: () -> Unit,
    onOpenStudentSearch: () -> Unit,
    onOpenStudentRequests: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    if (!nav.visible) return
    val p = nav.progress.value

    /** Qator bosilganda: avval yopish, keyin o'tish. */
    val go: (() -> Unit) -> () -> Unit = { action -> { nav.close(); action() } }

    val drag = rememberDraggableState { delta -> nav.drag(delta) }
    Box(
        Modifier.fillMaxSize().draggable(
            state = drag,
            orientation = Orientation.Horizontal,
            onDragStarted = { nav.startDrag() },
            onDragStopped = { velocity -> nav.settle(velocity) },
        ),
    ) {
        // Fon — bosilsa panel yopiladi. Shaffofligi siljish bilan birga o'sadi.
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f * p))
                .clickable(onClick = nav::close),
        )

        Column(
            Modifier.width(SideNavWidth)
                .fillMaxHeight()
                .offset(x = lerp(-SideNavWidth, 0.dp, p))
                .scSoftShadow(18.dp, PanelShape)
                .clip(PanelShape)
                .background(Sc.Card),
        ) {
            SideNavHeader(state, onClick = go(onOpenProfile), onClose = nav::close)

            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 12.dp),
            ) {
                SideNavLabel("Bo'limlar")
                SideNavItem("Universitetim", ScIcons.Cap, Sc.TintViolet, Sc.Violet, go(onOpenUniversity))
                SideNavItem("E'lonlar", ScIcons.FileText, Sc.TintAmber, Sc.Amber, go(onOpenListings))
                SideNavItem("Takliflar", ScIcons.DiscountTag, Sc.TintPink, Sc.Pink, go(onOpenOffers))
                SideNavItem("Ijara kvartiralar", ScIcons.Map, Sc.TintOrange, Sc.Orange, go(onOpenRentals))

                SideNavLabel("Aloqa")
                SideNavItem("Xabarlar", ScIcons.ChatRound, Sc.TintBlue, Sc.Brand, go(onOpenChat))
                SideNavItem(
                    "Bildirishnomalar", ScIcons.Bell, Sc.TintAmber, Sc.Amber,
                    go(onOpenNotifications), badge = state.hasUnreadNotifications,
                )
                SideNavItem("Do'stlar", ScIcons.Users, Sc.TintGreen, Sc.Success, go(onOpenStudents))
                SideNavItem("So'rovlar", ScIcons.MessageLines, Sc.TintViolet, Sc.Violet, go(onOpenStudentRequests))
                SideNavItem("Talaba qidirish", ScIcons.Search, Sc.TintBlue, Sc.Brand, go(onOpenStudentSearch))

                // "Profilim" qatori YO'Q — panel sarlavhasining o'zi profilga olib boradi.
                SideNavLabel("Hisob")
                // Ikona profildagi "Sozlamalar" bilan AYNAN bir xil (`AppIcons.Settings`):
                // bir bo'lim ikki joyda ikki xil belgi bilan turmasin.
                SideNavItem("Sozlamalar", AppIcons.Settings, Sc.Chip, Sc.ChipInk, go(onOpenSettings))

                // Pastki navigatsiya paneli panelning USTIDA turadi.
                Spacer(Modifier.navigationBarsPadding().height(BottomBarGap))
            }
        }
    }
}

/** O'ng qirralari yumaloq — panel chap chetdan chiqadi. */
private val PanelShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)

/**
 * Panel sarlavhasi — Home topbari bilan bir xil gradient: avatar, ism va universitet chipi.
 * Butun blok profilga olib boradi, o'ngdagi `×` esa faqat panelni yopadi.
 */
@Composable
private fun SideNavHeader(state: HomeUiState, onClick: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(Sc.headerBrush)
            .clickable(onClick = onClick)
            .statusBarsPadding()
            .padding(start = 18.dp, end = 12.dp, top = 16.dp, bottom = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScAvatar(
                name = state.userName,
                size = 54.dp,
                avatarUrl = state.avatarUrl,
                background = Color.White.copy(alpha = 0.22f),
                initialColor = Color.White,
                shape = RoundedCornerShape(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            ScText(
                state.userName, 17f, FontWeight.ExtraBold, Color.White,
                Modifier.weight(1f), letterSpacing = -0.3f, maxLines = 1,
            )
            Box(
                Modifier.size(34.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(ScIcons.Close, "Yopish", tint = Color.White, modifier = Modifier.size(15.dp))
            }
        }
        val badge = listOfNotNull(state.universityMonogram, state.courseLabel).joinToString(" · ")
        if (badge.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.2f))
                    .padding(horizontal = 11.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(ScIcons.CapBadge, null, tint = Color.White, modifier = Modifier.size(13.dp))
                ScText(badge, 12f, FontWeight.Bold, Color.White, maxLines = 1)
            }
        }
    }
}

/** Guruh sarlavhasi ("BO'LIMLAR") — qatorlarni ma'no bo'yicha ajratadi. */
@Composable
private fun SideNavLabel(text: String) {
    ScText(
        text.uppercase(), 10.5f, FontWeight.ExtraBold, Sc.MutedLight,
        Modifier.padding(start = 14.dp, top = 14.dp, bottom = 6.dp),
        letterSpacing = 0.8f, maxLines = 1,
    )
}

/** Paneldagi bitta qator: rangli plitka + yorliq + `›` (yoki o'qilmagan nuqtasi). */
@Composable
private fun SideNavItem(
    label: String,
    icon: ImageVector,
    tint: Color,
    accent: Color,
    onClick: () -> Unit,
    badge: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScIconTile(tint, size = 38.dp, radius = 14.dp) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(19.dp))
        }
        ScText(label, 14.5f, FontWeight.Bold, Sc.Ink, Modifier.weight(1f), maxLines = 1)
        if (badge) {
            Box(Modifier.size(8.dp).background(Sc.Danger, CircleShape))
        } else {
            Icon(ScIcons.ChevronRight, null, tint = Sc.MutedLight, modifier = Modifier.size(14.dp))
        }
    }
}
