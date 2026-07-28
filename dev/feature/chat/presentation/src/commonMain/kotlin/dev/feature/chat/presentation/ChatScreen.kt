package dev.feature.chat.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.core.uikit.components.AppIcons
import dev.core.uikit.components.ScAvatar
import dev.core.uikit.components.ScCircleButton
import dev.core.uikit.components.ScHeader
import dev.core.uikit.components.ScHeaderTitle
import dev.core.uikit.components.ScIconTile
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScText
import dev.core.uikit.components.StatusBarAppearance
import dev.core.uikit.components.scCard
import dev.core.uikit.components.scStyle
import dev.core.uikit.media.rememberMultiImagePicker
import dev.core.uikit.theme.Sc
import dev.feature.chat.domain.model.ConversationItem
import dev.feature.chat.domain.model.Message
import dev.feature.chat.domain.model.MessageStatus
import dev.feature.chat.domain.model.MessageType
import dev.feature.chat.domain.model.OutgoingImage
import dev.feature.chat.domain.model.Sticker
import dev.feature.connections.domain.model.ReportReason
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

/**
 * Suhbatlar. Ikki rejimda ishlaydi:
 *
 * - **Tab** (`onBack == null`) — pastki navigatsiyaning "Xabarlar" tab'i.
 * - **Ochilgan ekran** (`onBack != null`) — stack'ka qo'yilganda, sarlavhada orqaga tugmasi.
 *
 * [openStudentId] berilsa (Do'stlar ekranidan "Xabar" bosilganda) suhbat darhol ochiladi:
 * `POST /v1/conversations` idempotent, shuning uchun mavjudini qidirish shart emas.
 * [openConversationId] — push bosilganda keladi (`data.conversationId`, `chat.md` §10).
 */
@Composable
fun ChatScreen(
    onBack: (() -> Unit)? = null,
    openStudentId: String? = null,
    openConversationId: String? = null,
    /**
     * Suhbat ochildi/yopildi. Suhbatga kirish **navigatsiya emas** — u shu ekranning ichki
     * holati (`state.selected`), route o'zgarmaydi. Shuning uchun karkas (`StudentShell`)
     * o'zi bila olmaydi va pastki panelni yashirish uchun shu xabar kerak.
     */
    onThreadOpenChange: (Boolean) -> Unit = {},
    vm: ChatViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    val threadOpen = state.selected != null
    LaunchedEffect(threadOpen) { onThreadOpenChange(threadOpen) }

    LaunchedEffect(openStudentId) {
        if (openStudentId != null) vm.openWithStudent(openStudentId)
    }
    LaunchedEffect(openConversationId) {
        if (openConversationId != null) vm.openConversation(openConversationId)
    }

    Box(Modifier.fillMaxSize()) {
        if (state.selected == null) {
            ConversationList(
                conversations = state.conversations,
                archivedConversations = state.archivedConversations,
                onBack = onBack,
                onOpen = vm::open,
                onArchive = { vm.setArchived(it.id, true) },
                onUnarchive = { vm.setArchived(it.id, false) },
                onBlock = { vm.block(it.other.id) },
                onReport = { c, reason, note -> vm.reportStudent(c.other.id, reason, note) },
            )
        } else {
            ChatThread(
                conversation = state.selected!!,
                state = state,
                onBack = vm::close,
                onDraft = vm::onDraft,
                onSend = vm::send,
                onSendImages = vm::sendImages,
                onSendSticker = vm::sendSticker,
                onRetry = vm::retry,
                onLoadOlder = vm::loadOlder,
                onMarkRead = vm::markRead,
                onDisconnect = { vm.disconnect(it) },
                onBlock = { vm.block(it) },
                onReportStudent = { id, reason, note -> vm.reportStudent(id, reason, note) },
                onReportMessage = { id, reason, note -> vm.reportMessage(id, reason, note) },
                onSoon = vm::showMessage,
            )
        }

        // Bir martalik xabar — 2.5 s dan keyin o'zi yo'qoladi.
        val message = state.message
        if (message != null) {
            LaunchedEffect(message) {
                delay(2_500)
                vm.messageShown()
            }
            Box(
                Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = Sc.ScreenPadding, vertical = 90.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Sc.Ink.copy(alpha = 0.92f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) { ScText(message, 13.5f, FontWeight.SemiBold, Color.White) }
        }
    }
}

/**
 * Ro'yxatdagi qisqa ko'rinish. Rasm xabarining tanasi — uzun havola, stikerniki — emoji,
 * shuning uchun ular matn sifatida ko'rsatilmaydi.
 */
private fun Message?.preview(): String = when {
    this == null -> "Xabar yozing…"
    type == MessageType.IMAGE -> "📷 Rasm"
    type == MessageType.STICKER -> "$body Stiker"
    body.isBlank() -> "Xabar yozing…"
    else -> body
}

/** Suhbat avatarlari navbat bilan uch tint ranggida. */
private val avatarVisuals: List<Pair<Color, Color>>
    @Composable @ReadOnlyComposable get() = listOf(
        Sc.TintViolet to Sc.Violet,
        Sc.TintBlue to Sc.Brand,
        Sc.TintGreenDeep to Sc.Success,
    )

// ---------------------------------------------------------------------------
// Suhbatlar ro'yxati
// ---------------------------------------------------------------------------

@Composable
private fun ConversationList(
    conversations: List<ConversationItem>,
    archivedConversations: List<ConversationItem>,
    onBack: (() -> Unit)?,
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
    val list = if (showArchived) archivedConversations else conversations

    Column(Modifier.fillMaxSize().background(Sc.Bg)) {
        ScHeader {
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (showArchived || onBack != null) {
                    ScCircleButton(
                        ScIcons.ChevronLeft,
                        { if (showArchived) showArchived = false else onBack?.invoke() },
                        contentDescription = "Orqaga",
                    )
                }
                ScHeaderTitle(if (showArchived) "Arxiv" else "Xabarlar", size = 26f, modifier = Modifier.weight(1f))
                if (!showArchived && archivedConversations.isNotEmpty()) {
                    ScCircleButton(ScIcons.Archive, { showArchived = true }, contentDescription = "Arxiv")
                }
            }
        }

        if (list.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                ScText(
                    if (showArchived) "Arxiv bo'sh" else "Suhbatlar yo'q.\n\"Do'stlar\" bo'limidan yozishni boshlang.",
                    14f, FontWeight.Medium, Sc.Muted, lineHeight = 21f,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                // Tab rejimida pastda navigatsiya paneli turadi — oxirgi suhbat berkilmasin.
                contentPadding = PaddingValues(
                    start = Sc.ScreenPadding, end = Sc.ScreenPadding,
                    top = 20.dp, bottom = if (onBack == null) 110.dp else 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(list, key = { _, c -> c.id }) { index, c ->
                    ConversationRow(c, index, onClick = { onOpen(c) }, onLongPress = { actionFor = c })
                }
            }
        }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    c: ConversationItem,
    index: Int,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val (tint, accent) = avatarVisuals[index.mod(avatarVisuals.size)]
    Row(
        Modifier.fillMaxWidth()
            .scCard(radius = 22.dp, elevation = 6.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box {
            ScAvatar(
                name = c.other.displayName,
                size = 50.dp,
                avatarUrl = c.other.avatarUrl,
                background = tint,
                initialColor = accent,
            )
            // Onlayn holati SHU ro'yxatda haqiqiy (Redis'dan) — qidiruvdagidan farqli.
            if (c.other.online) {
                Box(
                    Modifier.align(Alignment.BottomEnd)
                        .padding(1.dp)
                        .size(13.dp)
                        .background(Sc.Card, RoundedCornerShape(percent = 50))
                        .padding(2.5.dp)
                        .background(Sc.Success, RoundedCornerShape(percent = 50)),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            ScText(c.other.displayName, 15.5f, FontWeight.ExtraBold, Sc.Ink, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            ScText(
                c.lastMessage.preview(),
                13.5f, FontWeight.Medium, Sc.Muted, maxLines = 1,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            ScText(ChatFormat.listStamp(c.conversation.lastMessageAt), 12f, FontWeight.SemiBold, Sc.MutedLight, maxLines = 1)
            if (c.unreadCount > 0) {
                Box(
                    Modifier.size(19.dp).background(Sc.Brand, RoundedCornerShape(percent = 50)),
                    contentAlignment = Alignment.Center,
                ) { ScText("${c.unreadCount}", 10.5f, FontWeight.ExtraBold, Color.White) }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Suhbat (Telegram uslubi)
// ---------------------------------------------------------------------------

@Composable
private fun ChatThread(
    conversation: ConversationItem,
    state: ChatUiState,
    onBack: () -> Unit,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onSendImages: (List<OutgoingImage>) -> Unit,
    onSendSticker: (Sticker) -> Unit,
    onRetry: (List<String>) -> Unit,
    onLoadOlder: () -> Unit,
    onMarkRead: () -> Unit,
    onDisconnect: (String) -> Unit,
    onBlock: (String) -> Unit,
    onReportStudent: (String, ReportReason, String?) -> Unit,
    onReportMessage: (String, ReportReason, String?) -> Unit,
    /** Hali tayyor bo'lmagan amal bosilganda ko'rsatiladigan bir martalik xabar. */
    onSoon: (String) -> Unit,
) {
    var messageMenu by remember { mutableStateOf<ChatMessageUi?>(null) }
    var reportMessageFor by remember { mutableStateOf<String?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var reportStudent by remember { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    var stickersOpen by remember { mutableStateOf(false) }
    var profileOpen by remember { mutableStateOf(false) }
    // Ochilgan rasm: qaysi xabar va uning nechanchi rasmi.
    var viewer by remember { mutableStateOf<Pair<List<ChatImageUi>, Int>?>(null) }

    val picker = rememberMultiImagePicker { picked ->
        if (picked.isNotEmpty()) {
            onSendImages(picked.map { OutgoingImage(it.bytes, it.fileName) })
        }
    }

    val listState = rememberLazyListState()
    val messages = state.messages
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)

    // Yangi xabar kelganda / klaviatura ochilganda pastga suriladi.
    LaunchedEffect(messages.size, imeBottom) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    // Ekran ochiq turganda kelgan xabarlar darhol o'qilgan hisoblanadi.
    LaunchedEffect(messages.size) { onMarkRead() }

    // Tepaga yetganda eski xabarlar yuklanadi (kursorli sahifalash, `?before=`).
    val atTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex <= 1 && messages.isNotEmpty() }
    }
    LaunchedEffect(atTop) { if (atTop) onLoadOlder() }

    Column(Modifier.fillMaxSize().background(Sc.ChatBg).imePadding()) {
        ChatThreadHeader(
            conversation = conversation,
            typing = state.peerTyping,
            realtime = state.realtime,
            onBack = onBack,
            onMenu = { showMenu = true },
            onOpenProfile = { profileOpen = true },
        )

        Box(Modifier.fillMaxWidth().weight(1f)) {
            // Fon — brend rangining 6% shaffofligidagi nuqtali pattern (22dp qadam).
            DottedBackground()
            if (messages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ScText("Xabar yozib, suhbatni boshlang", 13.5f, FontWeight.Medium, Sc.Muted)
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(messages, key = { it.id }) { m ->
                    if (m.dayLabel != null) DaySeparator(m.dayLabel)
                    MessageBubble(
                        message = m,
                        onLongPress = { messageMenu = m },
                        onOpenImage = { index -> viewer = m.images to index },
                    )
                }
            }
        }

        Composer(
            draft = state.draft,
            onDraft = onDraft,
            onSend = onSend,
            onPickImages = {
                stickersOpen = false
                picker.pick()
            },
            stickersOpen = stickersOpen,
            onToggleStickers = { stickersOpen = !stickersOpen },
            onPickSticker = onSendSticker,
        )
    }

    if (profileOpen) {
        PeerProfileSheet(
            conversation = conversation,
            typing = state.peerTyping,
            realtime = state.realtime,
            photos = state.photos,
            links = state.links,
            universityName = state.peerUniversity,
            onClose = { profileOpen = false },
            onDisconnect = {
                profileOpen = false
                confirmDisconnect = true
            },
            onBlock = {
                profileOpen = false
                confirmBlock = true
            },
            onReport = {
                profileOpen = false
                reportStudent = true
            },
            onSoon = onSoon,
        )
    }

    val openViewer = viewer
    if (openViewer != null) {
        ImageViewerDialog(
            images = openViewer.first,
            startIndex = openViewer.second,
            onDismiss = { viewer = null },
        )
    }

    // --- Dialoglar ---------------------------------------------------------------------

    val menuMessage = messageMenu
    if (menuMessage != null) {
        AlertDialog(
            onDismissRequest = { messageMenu = null },
            title = { Text("Xabar", style = scStyle(17f, FontWeight.ExtraBold)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (menuMessage.status == MessageStatus.FAILED) {
                        ActionRow(ScIcons.Return, "Qayta yuborish") {
                            onRetry(menuMessage.messageIds)
                            messageMenu = null
                        }
                    }
                    // Xabarni o'chirish/tahrirlash endpointi yo'q (chat.md §14) — faqat shikoyat.
                    if (!menuMessage.outgoing) {
                        ActionRow(ScIcons.Bell, "Shikoyat qilish", danger = true) {
                            reportMessageFor = menuMessage.id
                            messageMenu = null
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { messageMenu = null }) {
                    Text("Yopish", style = scStyle(14f, FontWeight.Bold, Sc.InkSoft))
                }
            },
        )
    }

    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            title = { Text(conversation.other.displayName, style = scStyle(17f, FontWeight.ExtraBold)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ActionRow(ScIcons.Close, "Bog'lanishni uzish") {
                        showMenu = false
                        confirmDisconnect = true
                    }
                    ActionRow(ScIcons.Users, "Bloklash", danger = true) {
                        showMenu = false
                        confirmBlock = true
                    }
                    ActionRow(ScIcons.Bell, "Shikoyat qilish", danger = true) {
                        showMenu = false
                        reportStudent = true
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMenu = false }) {
                    Text("Bekor", style = scStyle(14f, FontWeight.Bold, Sc.InkSoft))
                }
            },
        )
    }

    if (confirmDisconnect) {
        ConfirmDialog(
            title = "Bog'lanishni uzish",
            // Suhbat qoladi — tarix o'qiladi, lekin yangi xabar yozib bo'lmaydi (403).
            message = "Bog'lanish uzilgach bu suhbatga yozolmaysiz, lekin eski xabarlar qoladi.",
            confirmLabel = "Uzish",
            onConfirm = { onDisconnect(conversation.other.id); confirmDisconnect = false },
            onDismiss = { confirmDisconnect = false },
        )
    }

    if (confirmBlock) {
        ConfirmDialog(
            title = "Bloklash",
            message = "${conversation.other.displayName} bloklanadi: bog'lanish o'chadi va " +
                "ikkalangiz bir-biringizga yozolmaysiz.",
            confirmLabel = "Bloklash",
            onConfirm = { onBlock(conversation.other.id); confirmBlock = false },
            onDismiss = { confirmBlock = false },
        )
    }

    if (reportStudent) {
        ReportDialog(
            title = "Shikoyat: ${conversation.other.displayName}",
            onSend = { reason, note ->
                onReportStudent(conversation.other.id, reason, note)
                reportStudent = false
            },
            onDismiss = { reportStudent = false },
        )
    }

    val reportedMessage = reportMessageFor
    if (reportedMessage != null) {
        ReportDialog(
            title = "Xabar ustidan shikoyat",
            onSend = { reason, note ->
                onReportMessage(reportedMessage, reason, note)
                reportMessageFor = null
            },
            onDismiss = { reportMessageFor = null },
        )
    }
}

/** Suhbat sarlavhasi — gradient, pastki burchaklari to'g'ri (dizaynda yumaloq emas). */
@Composable
private fun ChatThreadHeader(
    conversation: ConversationItem,
    typing: Boolean,
    realtime: Boolean,
    onBack: () -> Unit,
    onMenu: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    StatusBarAppearance(darkIcons = false)
    Column(
        Modifier.fillMaxWidth()
            .background(Sc.headerBrush)
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HeaderGlassButton(ScIcons.ChevronLeft, "Orqaga", onBack)
            // Avatar va ism — Telegram'dagidek profilni ochadi. Orqaga va menyu tugmalari
            // shu sohadan TASHQARIDA qoladi, aks holda ular ham profilni ochib yuborardi.
            Row(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onOpenProfile)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            Box {
                ScAvatar(
                    name = conversation.other.displayName,
                    size = 44.dp,
                    avatarUrl = conversation.other.avatarUrl,
                    background = Color.White.copy(alpha = 0.9f),
                    initialColor = Sc.Violet,
                )
                if (conversation.other.online) {
                    Box(
                        Modifier.align(Alignment.BottomEnd)
                            .size(12.dp)
                            .background(Color(0xFF17A8DC), RoundedCornerShape(percent = 50))
                            .padding(2.5.dp)
                            .background(Color(0xFF2BD66A), RoundedCornerShape(percent = 50)),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                ScText(
                    conversation.other.displayName, 17f, FontWeight.ExtraBold, Color.White,
                    letterSpacing = -0.2f, maxLines = 1,
                )
                val status = when {
                    typing -> "yozmoqda…"
                    conversation.other.online -> "onlayn"
                    // Real-time kanal yopiq bo'lsa onlayn holati eskirgan bo'lishi mumkin.
                    !realtime -> "ulanmoqda…"
                    else -> ChatFormat.lastSeen(conversation.other.lastSeenAt)
                }
                ScText(status, 13f, FontWeight.Medium, Color.White.copy(alpha = 0.9f), maxLines = 1)
            }
            }
            HeaderGlassButton(ScIcons.DotsVertical, "Menyu", onMenu)
        }
    }
}

/** Gradient ustidagi shaffof-oq aylana tugma (40dp). */
@Composable
private fun HeaderGlassButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(40.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(Color.White.copy(alpha = 0.2f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(20.dp)) }
}

/** Radial nuqtalar patterni — 22dp qadam, radius 1.4dp, brend rangi 6%. */
@Composable
private fun DottedBackground() {
    val dot = Sc.BrandDark.copy(alpha = 0.06f)
    Canvas(Modifier.fillMaxSize()) {
        val step = 22.dp.toPx()
        val radius = 1.4f * density
        var y = step / 2f
        while (y < size.height) {
            var x = step / 2f
            while (x < size.width) {
                drawCircle(dot, radius, Offset(x, y))
                x += step
            }
            y += step
        }
    }
}

@Composable
private fun DaySeparator(label: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.background(Sc.Ink.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                .padding(horizontal = 13.dp, vertical = 4.dp),
        ) { ScText(label, 12f, FontWeight.Bold, Sc.ChipInk) }
    }
}

/**
 * Xabar qatori — turiga qarab pufak, rasm to'ri yoki stiker.
 *
 * `onOpenImage` albomdagi rasm bosilganda chaqiriladi (indeks bilan).
 */
@Composable
private fun MessageBubble(
    message: ChatMessageUi,
    onLongPress: () -> Unit,
    onOpenImage: (Int) -> Unit,
) {
    when (message.type) {
        MessageType.IMAGE -> ImageAlbumBubble(message, onOpen = onOpenImage, onLongPress = onLongPress)
        MessageType.STICKER -> StickerBubble(message, onLongPress = onLongPress)
        else -> TextBubble(message, onLongPress = onLongPress)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TextBubble(message: ChatMessageUi, onLongPress: () -> Unit) {
    val align = if (message.outgoing) Alignment.CenterEnd else Alignment.CenterStart
    Box(Modifier.fillMaxWidth(), contentAlignment = align) {
        // Dumcha o'z tomonida: chiquvchi 20/20/6/20, kiruvchi 20/20/20/6.
        val shape = if (message.outgoing) {
            RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 6.dp, bottomStart = 20.dp)
        } else {
            RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp)
        }
        Column(
            Modifier.widthIn(max = 280.dp)
                .clip(shape)
                .then(
                    if (message.outgoing) Modifier.background(Sc.bubbleBrush)
                    else Modifier.background(Sc.Card),
                )
                .combinedClickable(onClick = {}, onLongClick = onLongPress)
                .padding(start = 13.dp, end = 13.dp, top = 10.dp, bottom = 7.dp),
        ) {
            ScText(
                message.text, 15f, FontWeight.Medium,
                if (message.outgoing) Color.White else Sc.Ink,
                lineHeight = 21f,
            )
            Spacer(Modifier.height(2.dp))
            MessageMeta(message, Modifier.align(Alignment.End), onDark = message.outgoing)
        }
    }
}

/**
 * Pastdagi kiritish paneli — biriktirish + pill maydon + gradient tugma, tagida ochiladigan
 * stiker paneli.
 *
 * Qog'oz qisqich galereyani ochadi (bir martada 10 tagacha rasm), tabassum stikerlarni.
 * Ovoz yozish hali yo'q — mikrofon ikonasi bo'sh maydonda ko'rinadi, lekin bosilganda
 * hech narsa qilmaydi (backendda ovoz yuklash endpointi yo'q).
 */
@Composable
private fun Composer(
    draft: String,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onPickImages: () -> Unit,
    stickersOpen: Boolean,
    onToggleStickers: () -> Unit,
    onPickSticker: (Sticker) -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Sc.Border))
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Sc.Chip)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    ScIcons.Paperclip,
                    "Rasm biriktirish",
                    tint = Sc.Muted,
                    modifier = Modifier.size(21.dp).clickable(onClick = onPickImages),
                )
                Box(Modifier.weight(1f)) {
                    if (draft.isEmpty()) {
                        ScText("Xabar yozing…", 15f, FontWeight.Medium, Sc.NavIdle, maxLines = 1)
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = onDraft,
                        textStyle = scStyle(15f, FontWeight.Medium, Sc.Ink),
                        cursorBrush = SolidColor(Sc.Brand),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Icon(
                    ScIcons.Smile,
                    "Stikerlar",
                    tint = if (stickersOpen) Sc.Brand else Sc.Muted,
                    modifier = Modifier.size(21.dp).clickable(onClick = onToggleStickers),
                )
            }
            Box(
                Modifier.size(48.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Sc.tileBrush)
                    // Bo'sh maydonda yuborish ma'nosiz — ovoz yozish esa hali yo'q.
                    .clickable(enabled = draft.isNotBlank(), onClick = onSend),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (draft.isBlank()) ScIcons.Mic else ScIcons.Return,
                    "Yuborish", tint = Color.White, modifier = Modifier.size(22.dp),
                )
            }
        }
        if (stickersOpen) StickerPanel(onPick = onPickSticker)
    }
}

// ---------------------------------------------------------------------------
// Dialoglar
// ---------------------------------------------------------------------------

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (danger) Sc.Danger else Sc.Brand
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        ScText(label, 14f, FontWeight.Bold, if (danger) tint else Sc.Ink)
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = scStyle(17f, FontWeight.ExtraBold)) },
        text = { Text(message, style = scStyle(14f, FontWeight.Medium, Sc.InkSoft, lineHeight = 20f)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, style = scStyle(14f, FontWeight.ExtraBold, Sc.Danger))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Bekor", style = scStyle(14f, FontWeight.Bold, Sc.InkSoft))
            }
        },
    )
}

/** Shikoyat dialogi — sabab (majburiy) + ixtiyoriy izoh (≤1000 belgi). */
@Composable
private fun ReportDialog(
    title: String,
    onSend: (ReportReason, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by remember { mutableStateOf(ReportReason.SPAM) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = scStyle(17f, FontWeight.ExtraBold)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    ReportReason.entries.forEach { r ->
                        Box(
                            Modifier.clip(RoundedCornerShape(999.dp))
                                .background(if (reason == r) Sc.Brand else Sc.Chip)
                                .clickable { reason = r }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        ) {
                            ScText(
                                r.label, 12.5f, FontWeight.Bold,
                                if (reason == r) Color.White else Sc.ChipInk, maxLines = 1,
                            )
                        }
                    }
                }
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Sc.FieldBg)
                        .border(1.dp, Sc.Border, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                ) {
                    if (note.isEmpty()) ScText("Izoh (ixtiyoriy)", 13.5f, FontWeight.Medium, Sc.NavIdle)
                    BasicTextField(
                        value = note,
                        onValueChange = { if (it.length <= 1000) note = it },
                        textStyle = scStyle(13.5f, FontWeight.Medium, Sc.Ink),
                        cursorBrush = SolidColor(Sc.Brand),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSend(reason, note.takeIf { it.isNotBlank() }) }) {
                Text("Yuborish", style = scStyle(14f, FontWeight.ExtraBold, Sc.Danger))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Bekor", style = scStyle(14f, FontWeight.Bold, Sc.InkSoft))
            }
        },
    )
}

private val ReportReason.label: String
    get() = when (this) {
        ReportReason.SPAM -> "Spam"
        ReportReason.SCAM -> "Firibgarlik"
        ReportReason.HARASSMENT -> "Haqorat"
        ReportReason.INAPPROPRIATE -> "Nomaqbul"
        ReportReason.OTHER -> "Boshqa"
    }
