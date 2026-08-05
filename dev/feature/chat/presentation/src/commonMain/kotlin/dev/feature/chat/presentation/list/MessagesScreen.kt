package dev.feature.chat.presentation.list

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import dev.core.uikit.components.ScBackHandler
import dev.core.uikit.components.ScEmptyStateBox
import dev.core.uikit.components.ScEmptyTitle
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScNotFoundTitle
import dev.core.uikit.components.ScSearchOverlay
import dev.core.uikit.components.ScText
import dev.core.uikit.components.scBrandShadow
import dev.core.uikit.components.scStyle
import dev.core.uikit.theme.Sc
import dev.feature.chat.domain.model.ConversationItem
import dev.feature.chat.presentation.ActionRow
import dev.feature.chat.presentation.ChatFolder
import dev.feature.chat.presentation.ChatMyProfile
import dev.feature.chat.presentation.ConfirmDialog
import dev.feature.chat.presentation.ReportDialog
import dev.feature.chat.presentation.rememberPeerProfileSections
import dev.feature.clubs.domain.model.Club
import dev.feature.connections.domain.model.ReportReason
import dev.feature.connections.domain.model.StudentSummary
import dev.feature.connections.presentation.StudentProfileSheet
import kotlinx.coroutines.launch

/**
 * «Xabarlar» — suhbatlar ro'yxati.
 *
 * Butun harakat bitta qiymat atrofida qurilgan: `collapseFraction` (0 — panel ochiq,
 * 1 — yig'ilgan). U ro'yxatning surilishidan `NestedScrollConnection` orqali UZLUKSIZ
 * hisoblanadi va **hech qachon kompozitsiyada o'qilmaydi** — pastga lambda bo'lib
 * uzatiladi va faqat `Layout`/`graphicsLayer` ichida ochiladi. Shu sababli surish
 * davomida ekran qayta qurilmaydi: har kadrda faqat joylashtirish va chizish ketadi.
 *
 * Papkalar `HorizontalPager` bilan bog'langan: segment indikatori sahifa surilishi bilan
 * BIRGA yuradi, barmoq uzilganda esa pager uni o'zi joyiga o'tqazadi.
 */
@Composable
internal fun MessagesScreen(
    modifier: Modifier = Modifier,
    conversations: List<ConversationItem>,
    archivedConversations: List<ConversationItem>,
    clubs: List<Club>,
    folder: ChatFolder,
    onFolder: (ChatFolder) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    /** Ayni paytda yozayotgan suhbatlar (WS `typing`). */
    typing: Set<String>,
    myProfile: ChatMyProfile,
    onToggleJoin: (Club) -> Unit,
    onClubSoon: () -> Unit,
    onBack: (() -> Unit)?,
    /** Yangi suhbat — «Do'stlar» ro'yxatiga olib boradi. */
    onNewChat: (() -> Unit)?,
    onOpen: (ConversationItem) -> Unit,
    onArchive: (ConversationItem) -> Unit,
    onUnarchive: (ConversationItem) -> Unit,
    onBlock: (ConversationItem) -> Unit,
    onReport: (ConversationItem, ReportReason, String?) -> Unit,
) {
    var showArchived by remember { mutableStateOf(false) }
    var actionFor by remember { mutableStateOf<ConversationItem?>(null) }
    var blockFor by remember { mutableStateOf<ConversationItem?>(null) }
    var reportFor by remember { mutableStateOf<ConversationItem?>(null) }

    /** Hikoya muallifi bosildi — uning profili (bosh ekrandagi bilan bir xil varaq). */
    var profileStudent by remember { mutableStateOf<StudentSummary?>(null) }

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // --- Yig'ilish holati ---------------------------------------------------
    // `offset` — panel qancha "yeyilgani" (manfiy). Sakrab o'zgarmasin uchun u
    // snapshot holati, lekin FAQAT o'lchash/chizishda o'qiladi.
    var offset by remember { mutableFloatStateOf(0f) }
    // Yig'ilish masofasi panelning O'ZIDAN keladi (lenta + qidiruv maydonining haqiqiy
    // balandligi). Boshlang'ich qiymat — birinchi o'lchashgacha ishlatiladigan taxmin.
    //
    // Snapshot holati EMAS: uni panel O'LCHASH paytida yozadi va o'sha o'lchashning
    // o'zida o'qiydi. Holat bo'lganda bu "o'lchash ichida o'zgargan qiymatni o'qish"
    // bo'lardi va har o'zgarishda ortiqcha qayta o'lchash zanjirini boshlab yuborardi.
    val range = remember { floatArrayOf(with(density) { DefaultCollapseRange.toPx() }) }
    val collapse: () -> Float = remember {
        { if (range[0] <= 0f) 0f else (-offset / range[0]).coerceIn(0f, 1f) }
    }

    // FAB pastga surilganda kichrayadi. Yo'nalish `available.y` ishorasidan olinadi —
    // ro'yxat holatini kuzatishdan aniqroq (u element chegarasida "sakrab" o'zgaradi).
    var scrollingDown by remember { mutableStateOf(false) }

    val nested = remember {
        object : NestedScrollConnection {
            /** Yuqoriga surish — avval PANEL yig'iladi, ro'yxat undan keyin qimirlaydi. */
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta < -DirectionSlop) scrollingDown = true
                if (delta > DirectionSlop) scrollingDown = false
                if (delta >= 0f) return Offset.Zero
                val old = offset
                offset = (old + delta).coerceIn(-range[0], 0f)
                return Offset(0f, offset - old)
            }

            /**
             * Pastga surish — panel FAQAT ro'yxat tepasiga yetgach ochiladi. Aks holda
             * ro'yxat o'rtasida turib pastga tortganda sarlavha "o'zidan-o'zi" ochilardi.
             */
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val delta = available.y
                if (delta <= 0f) return Offset.Zero
                val old = offset
                offset = (old + delta).coerceIn(-range[0], 0f)
                return Offset(0f, offset - old)
            }

            /** Barmoq uzilgach panel oraliq holatda qolmaydi — eng yaqin chekkaga boradi. */
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val distance = range[0]
                if (distance <= 0f) return Velocity.Zero
                val fraction = -offset / distance
                if (fraction <= 0f || fraction >= 1f) return Velocity.Zero
                val target = if (fraction > SnapThreshold) -distance else 0f
                animate(
                    initialValue = offset,
                    targetValue = target,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) { value, _ -> offset = value }
                return Velocity.Zero
            }
        }
    }

    // --- Qidiruv ------------------------------------------------------------
    // Qatlam KLAVIATURA USTIDA suzadi (`ScSearchOverlay`) — ilovadagi barcha ekranlarda
    // shunday. Natijalar esa uning ORQASIDAGI ro'yxatda ko'rinadi: qidiruv alohida
    // ekran emas, shu ro'yxatning filtri.
    var searchOpen by remember { mutableStateOf(false) }

    // --- Papkalar -----------------------------------------------------------
    val pager = rememberPagerState(initialPage = folder.ordinal) { ChatFolder.entries.size }
    LaunchedEffect(pager.settledPage) {
        ChatFolder.entries.getOrNull(pager.settledPage)?.let(onFolder)
    }
    val conversationsState = rememberLazyListState()
    val clubsState = rememberLazyListState()
    val archivedState = rememberLazyListState()

    // Lenta arxivda ko'rinmaydi: u yerda ekran BOSHQA narsani ko'rsatyapti va lenta
    // faqat joy egallardi.
    val storiesVisible = !showArchived

    // Bo'limlar ARXIVDA chizilmaydi: u yerda tanlanadigan papka yo'q va sarlavha
    // allaqachon "Arxiv" deb turibdi.
    val segments = listOf(
        ChatSegment(
            label = ChatFolder.PERSONAL.label,
            icon = ScIcons.ChatRound,
            accent = Sc.Brand,
            badge = conversations.sumOf { it.unreadCount },
        ),
        ChatSegment(
            label = ChatFolder.CLUBS.label,
            icon = ScIcons.Users,
            accent = Sc.Violet,
            badge = clubs.size,
            unread = false,
        ),
    )

    // Qidiruv butun ro'yxat bo'yicha: ism ham, oxirgi xabar matni ham. Server so'rovi yo'q.
    val foundChats = remember(conversations, query) { conversations.matchingConversations(query) }
    val foundClubs = remember(clubs, query) { clubs.matchingClubs(query) }
    val foundArchived = remember(archivedConversations, query) {
        archivedConversations.matchingConversations(query)
    }

    Box(modifier.fillMaxSize().background(Sc.Bg)) {
        Column(Modifier.fillMaxSize().nestedScroll(nested)) {
            MessagesHeader(
                collapse = collapse,
                title = if (showArchived) "Arxiv" else "Xabarlar",
                onBack = when {
                    showArchived -> ({ showArchived = false })
                    else -> onBack
                },
                showArchive = !showArchived && archivedConversations.isNotEmpty(),
                onArchive = { showArchived = true },
                onOpenSearch = { searchOpen = true },
                searchActive = query.isNotBlank(),
                stories = storiesVisible,
                myProfile = myProfile,
                onOpenProfile = { profileStudent = it },
                onStoriesTop = {
                    scope.launch {
                        // Ko'rinib turgan ro'yxat tepaga qaytadi — papka almashtirilgan
                        // bo'lsa boshqasini surish foydalanuvchiga hech nima ko'rsatmasdi.
                        val visible = when {
                            showArchived -> archivedState
                            pager.currentPage == ChatFolder.CLUBS.ordinal -> clubsState
                            else -> conversationsState
                        }
                        visible.animateScrollToItem(0)
                        animate(offset, 0f, animationSpec = tween(280, easing = FastOutSlowInEasing)) {
                                value, _ ->
                            offset = value
                        }
                    }
                },
                onCollapseRange = { value ->
                    if (range[0] != value) {
                        range[0] = value
                        offset = offset.coerceIn(-value, 0f)
                    }
                },
            )

            // Bo'limlar panel ICHIDA emas, uning OSTIDA — ekran fonida. Ular ro'yxat bilan
            // surilmaydi: panel yig'ilganda ham joyida qoladi va papkani almashtirish
            // hamisha bir bosishda bo'ladi.
            if (!showArchived) {
                MessagesSegments(
                    segments = segments,
                    indicator = { pager.currentPage + pager.currentPageOffsetFraction },
                    onSelect = { index -> scope.launch { pager.animateScrollToPage(index) } },
                    modifier = Modifier.padding(
                        start = Sc.ScreenPadding,
                        end = Sc.ScreenPadding,
                        top = 14.dp,
                        bottom = 14.dp,
                    ),
                )
            }

            val listPadding = PaddingValues(
                start = Sc.ScreenPadding, end = Sc.ScreenPadding,
                top = if (showArchived) 16.dp else 4.dp,
                bottom = if (onNewChat != null) 96.dp else 24.dp,
            )
            val notFound = "«${query.trim()}» bo'yicha hech nima topilmadi"

            if (showArchived) {
                ConversationsPage(
                    items = foundArchived,
                    state = archivedState,
                    typing = typing,
                    contentPadding = listPadding,
                    empty = if (query.isBlank()) {
                        "Arxivga ko'chirilgan suhbat yo'q."
                    } else {
                        notFound
                    },
                    emptyTitle = if (query.isBlank()) ScEmptyTitle else ScNotFoundTitle,
                    onOpen = onOpen,
                    onLongPress = { actionFor = it },
                )
            } else {
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    beyondViewportPageCount = 1,
                ) { page ->
                    // Yengil parallaks: qo'shni sahifa barmoq ortidan sekinroq keladi.
                    //
                    // ⚠️ Siljish ICHKI qatlamda, tashqisi esa QIRQADI. Aks holda qo'shni
                    // sahifa (u pagerda ekran chetidan tashqarida turadi) parallaks
                    // hisobiga o'z uyasidan chiqib, joriy sahifaning ustiga tushardi —
                    // "Shaxsiy" bilan "Klublar" bir ekranda aralashib ko'rinardi.
                    Box(Modifier.fillMaxSize().clipToBounds()) {
                        Box(
                            Modifier.fillMaxSize().graphicsLayer {
                                val distance =
                                    (pager.currentPage - page) + pager.currentPageOffsetFraction
                                translationX = distance * size.width * PageParallax
                            },
                        ) {
                            when (ChatFolder.entries[page]) {
                                ChatFolder.PERSONAL -> ConversationsPage(
                                    items = foundChats,
                                    state = conversationsState,
                                    typing = typing,
                                    contentPadding = listPadding,
                                    empty = if (query.isNotBlank()) notFound else {
                                        "\"Do'stlar\" bo'limidan yozishni boshlang."
                                    },
                                    emptyTitle = if (query.isNotBlank()) ScNotFoundTitle else ScEmptyTitle,
                                    onOpen = onOpen,
                                    onLongPress = { actionFor = it },
                                )

                                ChatFolder.CLUBS -> ClubsPage(
                                    clubs = foundClubs,
                                    state = clubsState,
                                    contentPadding = listPadding,
                                    empty = if (query.isNotBlank()) notFound else {
                                        "Klublar tez orada qo'shiladi."
                                    },
                                    emptyTitle = if (query.isNotBlank()) ScNotFoundTitle else ScEmptyTitle,
                                    onToggleJoin = onToggleJoin,
                                    onOpen = onClubSoon,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (onNewChat != null && !searchOpen) {
            NewChatFab(
                compact = scrollingDown,
                onClick = onNewChat,
                modifier = Modifier.align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 18.dp, bottom = 18.dp),
            )
        }

        if (searchOpen) {
            // Tizim "orqaga" tugmasi qatlamni yopadi, ekrandan chiqarib yubormaydi.
            ScBackHandler { searchOpen = false }
            ScSearchOverlay(
                query = query,
                onQuery = onQuery,
                onClose = { searchOpen = false },
                placeholder = "Suhbat yoki klub qidiring",
                // Takliflar — topilgan ismlar: bir bosishda so'rov to'liq yoziladi.
                suggestions = remember(foundChats) { foundChats.map { it.other.displayName } },
            )
        }
    }

    profileStudent?.let { author ->
        StudentProfileSheet(
            studentId = author.id,
            known = author,
            onClose = { profileStudent = null },
            sections = rememberPeerProfileSections(author.id),
            // Suhbat shu ekranning o'zida ochiladi — navigatsiya kerak emas.
            onOpenChat = null,
        )
    }

    val action = actionFor
    if (action != null) {
        AlertDialog(
            onDismissRequest = { actionFor = null },
            title = { Text(action.other.displayName, style = scStyle(17f, FontWeight.ExtraBold)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ActionRow(
                        if (action.archived) ScIcons.ChevronRight else ScIcons.Archive,
                        if (action.archived) "Arxivdan chiqarish" else "Arxivlash",
                    ) {
                        if (action.archived) onUnarchive(action) else onArchive(action)
                        actionFor = null
                    }
                    ActionRow(ScIcons.Users, "Bloklash", danger = true) {
                        blockFor = action
                        actionFor = null
                    }
                    ActionRow(ScIcons.Bell, "Shikoyat qilish", danger = true) {
                        reportFor = action
                        actionFor = null
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { actionFor = null }) {
                    Text("Bekor", style = scStyle(14f, FontWeight.Bold, Sc.InkSoft))
                }
            },
        )
    }

    val blockTarget = blockFor
    if (blockTarget != null) {
        ConfirmDialog(
            title = "Bloklash",
            // Suhbatni o'chirish endpointi yo'q — blok esa bog'lanishni server tomonda uzadi.
            message = "${blockTarget.other.displayName} bloklanadi: bog'lanish o'chadi va " +
                "ikkalangiz bir-biringizga yozolmaysiz.",
            confirmLabel = "Bloklash",
            onConfirm = { onBlock(blockTarget); blockFor = null },
            onDismiss = { blockFor = null },
        )
    }

    val reportTarget = reportFor
    if (reportTarget != null) {
        ReportDialog(
            title = "Shikoyat: ${reportTarget.other.displayName}",
            onSend = { reason, note -> onReport(reportTarget, reason, note); reportFor = null },
            onDismiss = { reportFor = null },
        )
    }
}

/**
 * Suhbatlar sahifasi.
 *
 * `key` MAJBURIY: usiz [androidx.compose.foundation.lazy.LazyItemScope.animateItem]
 * elementni tanib ololmaydi va yangi xabar kelganda qator sakrab ko'chadi.
 */
@Composable
private fun ConversationsPage(
    items: List<ConversationItem>,
    state: androidx.compose.foundation.lazy.LazyListState,
    typing: Set<String>,
    contentPadding: PaddingValues,
    empty: String,
    emptyTitle: String,
    onOpen: (ConversationItem) -> Unit,
    onLongPress: (ConversationItem) -> Unit,
) {
    if (items.isEmpty()) {
        ScEmptyStateBox(
            Modifier.fillMaxSize(),
            title = emptyTitle,
            message = empty,
            icon = ScIcons.ChatRound,
        )
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = state,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            ConversationRow(
                item = item,
                index = index,
                typing = item.id in typing,
                onClick = { onOpen(item) },
                onLongPress = { onLongPress(item) },
                // Yangi xabar kelganda suhbat tepaga SILJIB chiqadi.
                modifier = Modifier.animateItem(
                    placementSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ),
            )
        }
    }
}

/**
 * "Klublar" papkasi — klublar xuddi suhbatlar kabi qator bo'lib turadi.
 *
 * Alohida klublar ekrani YO'Q: qo'shilish/chiqish shu qatorning o'zida, chunki klub —
 * jamoaviy suhbat va uning joyi xabarlar ichida.
 */
@Composable
private fun ClubsPage(
    clubs: List<Club>,
    state: androidx.compose.foundation.lazy.LazyListState,
    contentPadding: PaddingValues,
    empty: String,
    emptyTitle: String,
    onToggleJoin: (Club) -> Unit,
    onOpen: () -> Unit,
) {
    if (clubs.isEmpty()) {
        ScEmptyStateBox(
            Modifier.fillMaxSize(),
            title = emptyTitle,
            message = empty,
            icon = ScIcons.Users,
        )
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = state,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // A'zo bo'lganlar tepada — Telegramda ham o'zing turgan suhbat birinchi.
        items(clubs.sortedByDescending { it.joined }, key = { it.id }) { club ->
            ClubRow(club, onClick = onOpen, onToggleJoin = { onToggleJoin(club) })
        }
    }
}

/**
 * Yangi suhbat tugmasi.
 *
 * Pastga surilganda kichrayadi (kontentga xalaqit bermasin), yuqoriga surilganda esa
 * darhol qaytadi. Bosilganda — haptik javob va bir lahzalik "cho'kish".
 */
@Composable
private fun NewChatFab(compact: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scroll = animateFloatAsState(
        if (compact) CompactFab else 1f,
        spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessLow),
        label = "fab-scroll",
    )
    val press = animateFloatAsState(
        if (pressed) 0.92f else 1f,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "fab-press",
    )
    val brush = Sc.tileBrush

    Box(
        modifier.size(56.dp)
            .graphicsLayer {
                val scale = scroll.value * press.value
                scaleX = scale
                scaleY = scale
                alpha = if (compact) CompactFabAlpha else 1f
            }
            .scBrandShadow(14.dp, CircleShape)
            .background(brush, CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = Color.White),
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(ScIcons.Message, "Yangi suhbat", tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

/** Panelning birinchi o'lchashgacha ishlatiladigan taxminiy yig'ilish masofasi. */
private val DefaultCollapseRange = 152.dp

/** Shundan ortiq yig'ilgan bo'lsa barmoq uzilganda panel to'liq yig'iladi. */
private const val SnapThreshold = 0.4f

/** Shundan kichik siljish yo'nalish deb qaralmaydi (barmoqning titrashi). */
private const val DirectionSlop = 2f

/** Sahifalar orasidagi parallaks ulushi. */
private const val PageParallax = 0.15f

/** Pastga surilgandagi FAB o'lchami va shaffofligi. */
private const val CompactFab = 0.78f
private const val CompactFabAlpha = 0.9f
