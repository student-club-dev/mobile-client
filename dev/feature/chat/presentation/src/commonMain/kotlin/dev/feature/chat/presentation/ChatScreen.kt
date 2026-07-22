package dev.feature.chat.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import dev.core.uikit.components.ScCircleButton
import dev.core.uikit.components.ScHeader
import dev.core.uikit.components.ScHeaderTitle
import dev.core.uikit.components.ScIconTile
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScText
import dev.core.uikit.components.StatusBarAppearance
import dev.core.uikit.components.scCard
import dev.core.uikit.components.scStyle
import dev.core.uikit.theme.Sc
import dev.feature.chat.domain.model.Conversation
import dev.feature.chat.domain.model.Message
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Suhbatlar. Ikki rejimda ishlaydi:
 *
 * - **Tab** (`onBack == null`) — pastki navigatsiyaning "Xabarlar" tab'i.
 * - **Ochilgan ekran** (`onBack != null`) — stack'ka qo'yilganda, sarlavhada orqaga tugmasi.
 */
@Composable
fun ChatScreen(onBack: (() -> Unit)? = null, vm: ChatViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    if (state.selected == null) {
        ConversationList(
            conversations = state.conversations,
            archivedConversations = state.archivedConversations,
            onBack = onBack,
            onOpen = vm::open,
            onDelete = { vm.deleteConversation(it.id) },
            onArchive = { vm.setArchived(it.id, true) },
            onUnarchive = { vm.setArchived(it.id, false) },
        )
    } else {
        ChatThread(
            state.selected!!, state.messages, state.draft,
            onBack = vm::close,
            onDraft = vm::onDraft,
            onSend = vm::send,
            onDeleteMessage = vm::deleteMessage,
            onClearMessages = vm::clearMessages,
        )
    }
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
    conversations: List<Conversation>,
    archivedConversations: List<Conversation>,
    onBack: (() -> Unit)?,
    onOpen: (Conversation) -> Unit,
    onDelete: (Conversation) -> Unit,
    onArchive: (Conversation) -> Unit,
    onUnarchive: (Conversation) -> Unit,
) {
    var showArchived by remember { mutableStateOf(false) }
    var conversationForAction by remember { mutableStateOf<Conversation?>(null) }
    var conversationToDelete by remember { mutableStateOf<Conversation?>(null) }
    val list = if (showArchived) archivedConversations else conversations

    Column(Modifier.fillMaxSize().background(Sc.Bg)) {
        ScHeader {
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (showArchived || onBack != null) {
                    // Spetsifikatsiya: gradient topbar'da orqaga — oq aylana + `‹` brandDark.
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
                ScText(if (showArchived) "Arxiv bo'sh" else "Suhbatlar yo'q", 14f, FontWeight.Medium, Sc.Muted)
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
                items(list, key = { it.id }) { c ->
                    val index = list.indexOf(c).coerceAtLeast(0)
                    ConversationRow(c, index, onClick = { onOpen(c) }, onLongPress = { conversationForAction = c })
                }
            }
        }
    }

    // Uzoq bosishda amallar (arxivlash / o'chirish)
    val action = conversationForAction
    if (action != null) {
        AlertDialog(
            onDismissRequest = { conversationForAction = null },
            title = { Text(action.peerName, style = scStyle(17f, FontWeight.ExtraBold)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ActionRow(
                        if (action.archived) ScIcons.ChevronRight else ScIcons.Archive,
                        if (action.archived) "Arxivdan chiqarish" else "Arxivlash",
                    ) {
                        if (action.archived) onUnarchive(action) else onArchive(action)
                        conversationForAction = null
                    }
                    ActionRow(ScIcons.Close, "O'chirish", danger = true) {
                        conversationToDelete = action
                        conversationForAction = null
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { conversationForAction = null }) {
                    Text("Bekor", style = scStyle(14f, FontWeight.Bold, Sc.InkSoft))
                }
            },
        )
    }

    val target = conversationToDelete
    if (target != null) {
        ConfirmDialog(
            title = "Suhbatni o'chirish",
            message = "\"${target.peerName}\" bilan suhbat va barcha xabarlar o'chiriladi. Davom etasizmi?",
            confirmLabel = "O'chirish",
            onConfirm = { onDelete(target); conversationToDelete = null },
            onDismiss = { conversationToDelete = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(c: Conversation, index: Int, onClick: () -> Unit, onLongPress: () -> Unit) {
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
            ScIconTile(tint, size = 50.dp, radius = 25.dp) {
                ScText(c.peerInitial, 19f, FontWeight.ExtraBold, accent)
            }
            if (c.online) {
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
            ScText(c.peerName, 15.5f, FontWeight.ExtraBold, Sc.Ink, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            ScText(c.lastMessage, 13.5f, FontWeight.Medium, Sc.Muted, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            ScText(c.lastTime, 12f, FontWeight.SemiBold, Sc.MutedLight, maxLines = 1)
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
    conversation: Conversation,
    messages: List<Message>,
    draft: String,
    onBack: () -> Unit,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onDeleteMessage: (String) -> Unit,
    onClearMessages: () -> Unit,
) {
    var messageToDelete by remember { mutableStateOf<Message?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(messages.size, imeBottom) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(Modifier.fillMaxSize().background(Sc.ChatBg).imePadding()) {
        ChatThreadHeader(conversation, onBack, onMenu = { showClearConfirm = true })

        Box(Modifier.fillMaxWidth().weight(1f)) {
            // Fon — brend rangining 6% shaffofligidagi nuqtali pattern (22dp qadam).
            DottedBackground()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (messages.isNotEmpty()) {
                    item("date") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Box(
                                Modifier.background(Sc.Ink.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 13.dp, vertical = 4.dp),
                            ) { ScText("Bugun", 12f, FontWeight.Bold, Sc.ChipInk) }
                        }
                    }
                }
                items(messages, key = { it.id }) { m ->
                    MessageBubble(m, onLongPress = { messageToDelete = m })
                }
            }
        }

        Composer(draft, onDraft, onSend)
    }

    val target = messageToDelete
    if (target != null) {
        ConfirmDialog(
            title = "Xabarni o'chirish",
            message = "Bu xabarni o'chirmoqchimisiz?",
            confirmLabel = "O'chirish",
            onConfirm = { onDeleteMessage(target.id); messageToDelete = null },
            onDismiss = { messageToDelete = null },
        )
    }

    if (showClearConfirm) {
        ConfirmDialog(
            title = "Suhbatni tozalash",
            message = "Bu suhbatdagi barcha xabarlar o'chiriladi. Davom etasizmi?",
            confirmLabel = "Tozalash",
            onConfirm = { onClearMessages(); showClearConfirm = false },
            onDismiss = { showClearConfirm = false },
        )
    }
}

/** Suhbat sarlavhasi — gradient, pastki burchaklari to'g'ri (dizaynda yumaloq emas). */
@Composable
private fun ChatThreadHeader(conversation: Conversation, onBack: () -> Unit, onMenu: () -> Unit) {
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
            Box {
                Box(
                    Modifier.size(44.dp)
                        .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(percent = 50)),
                    contentAlignment = Alignment.Center,
                ) { ScText(conversation.peerInitial, 18f, FontWeight.ExtraBold, Sc.Violet) }
                if (conversation.online) {
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
                ScText(conversation.peerName, 17f, FontWeight.ExtraBold, Color.White, letterSpacing = -0.2f, maxLines = 1)
                ScText(
                    if (conversation.online) "online" else "oflayn",
                    13f, FontWeight.Medium, Color.White.copy(alpha = 0.9f), maxLines = 1,
                )
            }
            HeaderGlassButton(ScIcons.PhoneCall, "Qo'ng'iroq") {}
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(message: Message, onLongPress: () -> Unit) {
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
            Row(
                Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ScText(
                    message.time, 11f, FontWeight.SemiBold,
                    if (message.outgoing) Color.White.copy(alpha = 0.85f) else Sc.MutedLight,
                )
                if (message.outgoing) {
                    Icon(ScIcons.DoubleCheck, null, tint = Color.White, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}

/** Pastdagi kiritish paneli — biriktirish + pill maydon + gradient tugma. */
@Composable
private fun Composer(draft: String, onDraft: (String) -> Unit, onSend: () -> Unit) {
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
                Icon(ScIcons.Paperclip, "Biriktirish", tint = Sc.Muted, modifier = Modifier.size(21.dp))
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
                Icon(ScIcons.Smile, null, tint = Sc.Muted, modifier = Modifier.size(21.dp))
            }
            Box(
                Modifier.size(48.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Sc.tileBrush)
                    .clickable(onClick = onSend),
                contentAlignment = Alignment.Center,
            ) {
                // Matn kiritilmaganda — mikrofon, kiritilganda jo'natish belgisi.
                Icon(
                    if (draft.isBlank()) ScIcons.Mic else ScIcons.Return,
                    "Yuborish", tint = Color.White, modifier = Modifier.size(22.dp),
                )
            }
        }
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
    val tint = if (danger) Color(0xFFDC2626) else Sc.Brand
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
                Text(confirmLabel, style = scStyle(14f, FontWeight.ExtraBold, Color(0xFFDC2626)))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Bekor", style = scStyle(14f, FontWeight.Bold, Sc.InkSoft))
            }
        },
    )
}
