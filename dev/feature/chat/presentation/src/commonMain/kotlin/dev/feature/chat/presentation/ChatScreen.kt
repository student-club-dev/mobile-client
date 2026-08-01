package dev.feature.chat.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.core.uikit.components.AppIcons
import dev.core.uikit.components.ScAvatar
import dev.core.uikit.components.ScCircleButton
import dev.core.uikit.components.ScHeader
import dev.core.uikit.components.ScHeaderTitle
import dev.core.uikit.components.ScIconTile
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScText
import dev.core.uikit.components.ScUploadRing
import dev.core.uikit.components.StatusBarAppearance
import dev.core.uikit.components.scCard
import dev.core.uikit.components.scStyle
import dev.core.uikit.media.PickedVideo
import dev.core.uikit.media.deleteMediaFile
import dev.core.uikit.media.ownsFile
import dev.core.uikit.media.rememberMultiImagePicker
import dev.core.uikit.media.ScVideoPlayer
import dev.core.uikit.media.rememberAudioPlayer
import dev.core.uikit.media.rememberAudioRecorder
import dev.core.uikit.media.VideoPreparer
import dev.core.uikit.media.rememberVideoCapture
import dev.core.uikit.media.rememberVideoPreparer
import dev.core.uikit.media.videoNeedsPreparing
import dev.core.uikit.media.rememberVideoPicker
import dev.core.uikit.media.rememberFilePicker
import dev.core.uikit.theme.Sc
import dev.feature.chat.domain.model.ConversationItem
import dev.feature.chat.domain.model.GifItem
import dev.feature.chat.domain.model.Message
import dev.feature.chat.domain.model.MessageStatus
import dev.feature.chat.domain.model.MessageType
import dev.feature.chat.domain.model.OutgoingImage
import dev.feature.chat.domain.model.OutgoingVideo
import dev.feature.chat.domain.model.Sticker
import dev.feature.chat.domain.model.StickerSearchItem
import dev.feature.chat.presentation.gif.ChatMediaPanel
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
 * [openConversationId] — push bosilganda keladi (`data.conversationId`, `03-WEBSOCKET.md` §10).
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
                onSendStickerRef = vm::sendStickerRef,
                onSendGif = vm::sendGif,
                onRetry = vm::retry,
                onDeleteMessages = vm::deleteMessages,
                onLoadOlder = vm::loadOlder,
                onMarkRead = vm::markRead,
                onDisconnect = { vm.disconnect(it) },
                onBlock = { vm.block(it) },
                onReportStudent = { id, reason, note -> vm.reportStudent(id, reason, note) },
                onReportMessage = { id, reason, note -> vm.reportMessage(id, reason, note) },
                // Pleyer va yuklab olish keyingi qadamda ulanadi (platforma komponentlari
                // tayyor bo'lgach) — hozircha foydalanuvchi bo'sh bosishdan xabardor bo'lsin.
                onSendFile = vm::sendFile,
                onSendVideo = vm::sendVideo,
                onCancelUpload = vm::cancelUpload,
                onSendVoice = vm::sendVoice,
                // Sarlavha har kompozitsiyada qayta o'qiladi — token yangilangach ham to'g'ri.
                mediaHeaders = vm.mediaHeaders(),
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
 * Ro'yxatdagi qisqa ko'rinish.
 *
 * Media xabarda tana **bo'sh** (server `body` ni faqat matn va izoh uchun to'ldiradi),
 * shuning uchun ko'rinish turdan quriladi. Turi keshda saqlanadi (`lastMessageType`).
 */
private fun Message?.preview(): String = when {
    this == null -> "Xabar yozing…"
    deleted -> "Xabar o'chirildi"
    type == MessageType.IMAGE -> "📷 Rasm"
    type == MessageType.GIF -> "GIF"
    type == MessageType.VIDEO -> "🎬 Video"
    type == MessageType.VOICE -> "🎤 Ovozli xabar"
    type == MessageType.FILE -> "📎 Fayl"
    type == MessageType.STICKER -> "${sticker?.emoji.orEmpty()} Stiker".trim()
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
    onSendStickerRef: (StickerSearchItem) -> Unit,
    onSendGif: (GifItem) -> Unit,
    onRetry: (List<String>) -> Unit,
    /** Belgilangan xabarlarni o'chirish; ikkinchi argument — suhbatdoshda ham o'chsinmi. */
    onDeleteMessages: (List<String>, Boolean) -> Unit,
    onLoadOlder: () -> Unit,
    onMarkRead: () -> Unit,
    onDisconnect: (String) -> Unit,
    onBlock: (String) -> Unit,
    onReportStudent: (String, ReportReason, String?) -> Unit,
    onReportMessage: (String, ReportReason, String?) -> Unit,
    onSendFile: (ByteArray, String) -> Unit,
    onSendVideo: (OutgoingVideo) -> Unit,
    /** Ketayotgan videoni to'xtatish — siqish ham, yuklash ham uziladi. */
    onCancelUpload: (String) -> Unit,
    onSendVoice: (ByteArray, String) -> Unit,
    /** Media so'rovlari uchun `Authorization` sarlavhasi — pleyerlar tokensiz `404` oladi. */
    mediaHeaders: Map<String, String>,
    /** Hali tayyor bo'lmagan amal bosilganda ko'rsatiladigan bir martalik xabar. */
    onSoon: (String) -> Unit,
) {
    /**
     * Belgilangan qatorlar (`ChatMessageUi.id` bo'yicha) — bo'sh bo'lmasa **tanlash rejimi**.
     *
     * Albom bitta qator: uning `id` si belgilanadi, o'chirishga esa [ChatMessageUi.messageIds]
     * dagi hammasi ketadi — ekranda ular bitta to'r, ya'ni yarmini o'chirish g'alati bo'lardi.
     */
    var selectedIds by remember(conversation.id) { mutableStateOf(emptySet<String>()) }
    var reportMessageFor by remember { mutableStateOf<String?>(null) }
    /** Bitta belgilangan xabar ustidagi qo'shimcha amallar (⋮). */
    var singleMenu by remember { mutableStateOf<ChatMessageUi?>(null) }
    /** Matnini belgilash uchun ochilgan xabar — bir gapni tanlab nusxa olish. */
    var selectTextFor by remember { mutableStateOf<ChatMessageUi?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var reportStudent by remember { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    var stickersOpen by remember { mutableStateOf(false) }
    var profileOpen by remember { mutableStateOf(false) }
    // Ochilgan rasm: qaysi xabar va uning nechanchi rasmi.
    var viewer by remember { mutableStateOf<Pair<List<ChatMediaItem>, Int>?>(null) }

    /**
     * Eshitilayotgan ovozli xabar — bir vaqtda **faqat bittasi**.
     *
     * Holat ekran darajasida: ikkita pufak bir vaqtda o'ynasa foydalanuvchi hech qaysisini
     * eshita olmasdi, va har pufak o'z pleyerini ushlab tursa mobil qurilmada audio kanal
     * tugab qolardi.
     */
    var playingVoiceId by remember(conversation.id) { mutableStateOf<String?>(null) }
    var voiceProgress by remember(conversation.id) { mutableStateOf(0f) }

    /** To'liq ekranda ochilgan video. */
    var videoViewer by remember { mutableStateOf<ChatAttachmentUi?>(null) }

    /** Biriktirma menyusi — qog'oz qisqich bosilganda: Rasm / Video / Fayl. */
    var attachMenu by remember { mutableStateOf(false) }

    val filePicker = rememberFilePicker { picked ->
        if (picked != null) onSendFile(picked.bytes, picked.fileName)
    }
    /**
     * Tanlangan, lekin hali **yuborilmagan** video — ko'rish va izoh ekranida turadi.
     *
     * Darrov yuborilmaydi: noto'g'ri faylni tanlash yoki izohni unutish tuzatib bo'lmaydigan
     * xato edi, xabarni faqat o'chirish qolardi.
     */
    var previewVideo by remember { mutableStateOf<PickedVideo?>(null) }

    // Kamera galereya bilan bir xil natijani beradi ([PickedVideo]) va o'sha ko'rish
    // ekranidan o'tadi — foydalanuvchi uchun ikkisi orasida farq yo'q.
    val videoCapture = rememberVideoCapture { picked ->
        if (picked != null) previewVideo = picked
    }

    // Siqish yuborilgandan KEYIN ishlaydi — Telegramdagi kabi. Shuning uchun u tanlagichda
    // emas, yuboriladigan videoga biriktiriladi va repozitoriy uni halqa ichida chaqiradi.
    val videoPreparer = rememberVideoPreparer()

    val videoPicker = rememberVideoPicker { picked ->
        if (picked != null) {
            previewVideo = picked
        } else {
            // Sabab deyarli doim bitta: server chegarasi — 3 daqiqa. Undan uzunini siqib
            // ham sig'dirib bo'lmaydi, shuning uchun buni ochiq aytamiz (ilgari tanlov
            // jimgina yo'qolardi va "bosdim, hech nima bo'lmadi" bo'lib qolardi).
            onSoon("Videoni yuborib bo'lmadi — u 3 daqiqadan uzun yoki formati qo'llab-quvvatlanmaydi.")
        }
    }

    var recording by remember { mutableStateOf(false) }
    val recorder = rememberAudioRecorder { audio ->
        recording = false
        // `null` — bekor qilindi yoki mikrofonga ruxsat berilmadi. Ikkalasida ham
        // foydalanuvchi nima bo'lganini biladi (tizim oynasi), qo'shimcha xabar ortiqcha.
        if (audio != null) onSendVoice(audio.bytes, audio.fileName)
    }

    val audioPlayer = rememberAudioPlayer(
        headers = mediaHeaders,
        onProgress = { position, duration ->
            voiceProgress = if (duration > 0) position.toFloat() / duration else 0f
        },
        onEnded = {
            playingVoiceId = null
            voiceProgress = 0f
        },
    )

    val picker = rememberMultiImagePicker { picked ->
        if (picked.isNotEmpty()) {
            onSendImages(picked.map { OutgoingImage(it.bytes, it.fileName) })
        }
    }

    val listState = rememberLazyListState()
    val messages = state.messages

    // --- Belgilash rejimi ----------------------------------------------------------------

    // Ro'yxatdagi HAQIQIY qatorlar bo'yicha: xabar keshdan yo'qolsa (o'zimizda o'chirdik)
    // tanlov o'zi bo'shaydi va panel yopiladi — id'lar ro'yxatiga tayanish uni ekranda
    // osilib qolgan holda qoldirardi.
    val selectedRows = remember(messages, selectedIds) { messages.filter { it.id in selectedIds } }
    val selectionMode = selectedRows.isNotEmpty()
    // Albomdagi har bir rasm alohida xabar — foydalanuvchiga ham shu son ko'rsatiladi.
    val selectedCount = selectedRows.sumOf { it.messageIds.size }
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current

    val toggleSelection: (ChatMessageUi) -> Unit = { m ->
        selectedIds = if (m.id in selectedIds) selectedIds - m.id else selectedIds + m.id
    }

    /**
     * Surib belgilash holati: qaysi qatordan boshlandi va boshlanishidagi tanlov.
     *
     * Baza saqlanadi, chunki surish avvalgi tanlovni **bekor qilmasligi** kerak: bosh
     * barmoq bilan bir nechta xabar belgilab, keyin uzun bosib qolganini surib qo'shish —
     * Telegram'dagi aynan shu xatti-harakat.
     *
     * Boshlangan joy **indeks emas, id** bilan saqlanadi: surish paytida tepaga yetib
     * eski tarix yuklansa, hamma indekslar siljib, belgilash butunlay boshqa xabarlarga
     * o'tib ketardi.
     */
    var dragAnchorId by remember { mutableStateOf<String?>(null) }
    var dragBase by remember { mutableStateOf(emptySet<String>()) }

    /** Barmoqning ro'yxat ichidagi balandligi; `null` — surish ketmayapti. */
    var dragY by remember { mutableStateOf<Float?>(null) }

    // Imo-ishora ro'yxatni **kalit sifatida olmaydi**: `messages` har bir yangi xabarda va
    // hatto o'qildi belgichasi kelganda ham yangi obyekt bo'ladi, ya'ni `pointerInput` qayta
    // ishga tushib, ketayotgan surishni yarmida uzib qo'yardi.
    val currentMessages by rememberUpdatedState(messages)

    /** Barmoq ostidagi qatorgacha bo'lgan oraliqni belgilaydi. */
    val extendSelection: (Float) -> Unit = { y ->
        val rows = currentMessages
        val anchor = rows.indexOfFirst { it.id == dragAnchorId }
        val target = listState.indexAt(y)
        if (anchor >= 0 && target != null && target <= rows.lastIndex) {
            val from = minOf(anchor, target)
            val to = maxOf(anchor, target)
            selectedIds = dragBase + rows.subList(from, to + 1).map { it.id }
        }
    }

    // ⚠️ `LazyListItemInfo.offset` contentPadding'ni HISOBGA OLMAYDI (u faqat chizishda,
    // `place()` da qo'shiladi), barmoqning koordinatasi esa oladi. Shuning uchun barmoqning
    // `y` i qatorlar fazosiga o'tkaziladi — busiz belgilash bir qator chamasi pastga
    // surilib ketardi. Shu fazoda ro'yxatning ko'rinadigan qismi aynan
    // `viewportStartOffset..viewportEndOffset` oralig'i bo'ladi, ya'ni chetlarni aniqlash
    // ham to'g'ri ishlaydi.
    val listPadPx: Float
    val edgePx: Float
    with(LocalDensity.current) {
        listPadPx = LIST_VERTICAL_PADDING.toPx()
        edgePx = AUTO_SCROLL_EDGE.toPx()
    }

    // Barmoq ekranning tepa yoki past chetiga yetsa — ro'yxat o'zi suriladi va belgilash
    // davom etadi (Telegram'dagidek). Surish tezligi chetga qanchalik chuqur kirganiga
    // qarab o'sadi: chekkada sekin, eng chetida tez.
    //
    // ⚠️ Bu **avto-surish**, foydalanuvchining aylantirishi emas: imo-ishora hodisalarni
    // o'zi yeb qo'yadi ([detectLongPressDragSelect]), ya'ni surish paytida ro'yxat faqat
    // shu yerdan harakatlanadi. Har kadrda barmoq ostiga yangi qator kelgani uchun tanlov
    // ham qayta hisoblanadi — barmoq qimirlamasa ham.
    LaunchedEffect(dragY != null) {
        while (dragY != null) {
            val y = dragY ?: break
            val info = listState.layoutInfo
            val top = info.viewportStartOffset + edgePx
            val bottom = info.viewportEndOffset - edgePx
            val step = when {
                y < top -> -AUTO_SCROLL_MAX_STEP * ((top - y) / edgePx).coerceIn(MIN_SCROLL_FRACTION, 1f)
                y > bottom -> AUTO_SCROLL_MAX_STEP * ((y - bottom) / edgePx).coerceIn(MIN_SCROLL_FRACTION, 1f)
                else -> 0f
            }
            if (step != 0f) {
                listState.scrollBy(step)
                extendSelection(y)
            }
            // Kadr boshiga bog'lanamiz: `delay` bilan surish qurilma tezligiga qarab
            // turlicha silliq bo'lardi.
            withFrameNanos { }
        }
    }

    // ⚠️ Klaviatura balandligi animatsiya davomida HAR KADRDA o'zgaradi. Uni to'g'ridan-
    // to'g'ri o'qish butun ekranni har kadrda qayta chizardi va `LaunchedEffect` ni qayta
    // ishga tushirib, sekundiga o'nlab surish animatsiyasini boshlab yuborardi — chat
    // aynan shundan qotib qolardi. Shuning uchun faqat "ochiqmi" bayrog'i kuzatiladi.
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val imeOpen by remember(imeInsets, density) {
        derivedStateOf { imeInsets.getBottom(density) > 0 }
    }

    // Foydalanuvchi pastdami — eski xabarlarni o'qiyotgan bo'lsa uni pastga tortmaymiz.
    val atBottom by remember(listState) {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            last == null || last.index >= listState.layoutInfo.totalItemsCount - 2
        }
    }

    // Suhbat birinchi ochilganda — DARHOL pastga. `animateScrollToItem` bu yerda butun
    // tarixni aylantirib chiqardi va ochilish sezilarli darajada sekinlashardi.
    var positioned by remember(conversation.id) { mutableStateOf(false) }
    LaunchedEffect(conversation.id, messages.size) {
        if (messages.isEmpty()) return@LaunchedEffect
        when {
            !positioned -> {
                listState.scrollToItem(messages.lastIndex)
                positioned = true
            }
            atBottom -> listState.animateScrollToItem(messages.lastIndex)
        }
    }
    LaunchedEffect(imeOpen) {
        if (imeOpen && messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    // Ekran ochiq turganda kelgan xabarlar darhol o'qilgan hisoblanadi. Kalit — oxirgi
    // xabar id'si: ro'yxat uzunligi eski tarix yuklanganda ham o'zgaradi, u esa
    // "o'qildi" kursoriga aloqasi yo'q.
    LaunchedEffect(messages.lastOrNull()?.id) { if (messages.isNotEmpty()) onMarkRead() }

    // Tepaga yetganda eski xabarlar yuklanadi (kursorli sahifalash, `?before=`).
    val atTop by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex <= 1 && listState.layoutInfo.totalItemsCount > 0
        }
    }
    LaunchedEffect(atTop) { if (atTop) onLoadOlder() }

    Column(Modifier.fillMaxSize().background(Sc.ChatBg).imePadding()) {
        // Tanlash rejimida sarlavha butunlay almashadi (Telegram'dagidek): ism va holat
        // o'rniga tanlanganlar soni va amallar chiqadi.
        if (selectionMode) {
            SelectionHeader(
                count = selectedCount,
                onClose = { selectedIds = emptySet() },
                onCopy = {
                    val text = selectedRows.filter { !it.deleted }
                        .map { it.text }
                        .filter { it.isNotBlank() }
                        .joinToString("\n")
                    if (text.isBlank()) {
                        onSoon("Bu xabarlarda nusxa olinadigan matn yo'q")
                    } else {
                        clipboard.setText(AnnotatedString(text))
                        selectedIds = emptySet()
                        onSoon("Nusxa olindi")
                    }
                },
                onDelete = { confirmDelete = true },
                // Qo'shimcha amallar bitta xabarga tegishli (shikoyat, qayta yuborish,
                // matnni belgilash) — bir nechtasi tanlanganda ular ma'nosiz.
                onMore = { selectedRows.singleOrNull()?.let { singleMenu = it } },
                showMore = selectedRows.size == 1,
            )
        } else {
            ChatThreadHeader(
                conversation = conversation,
                typing = state.peerTyping,
                realtime = state.realtime,
                onBack = onBack,
                onMenu = { showMenu = true },
                onOpenProfile = { profileOpen = true },
            )
        }

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
                // Uzun bosib surib belgilash — imo-ishora ro'yxatning O'ZIDA: qatorga
                // osilganda qator ekrandan chiqishi bilan (LazyColumn uni kompozitsiyadan
                // olib tashlaydi) surish uzilardi va bir ekrandan uzunroq belgilab
                // bo'lmasdi.
                modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                    detectLongPressDragSelect(
                        onStart = { offset ->
                            val y = offset.y - listPadPx
                            val row = listState.indexAt(y)?.let { currentMessages.getOrNull(it) }
                            if (row != null) {
                                // Telegram'dagidek: belgilash rejimi qisqa titrash bilan
                                // ochiladi, aks holda uzun bosish "ishladimi?" degan savol
                                // qolardi.
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                dragAnchorId = row.id
                                dragBase = selectedIds
                                selectedIds = selectedIds + row.id
                                dragY = y
                            }
                        },
                        onDrag = { position ->
                            val y = position.y - listPadPx
                            dragY = y
                            extendSelection(y)
                        },
                        onEnd = {
                            dragAnchorId = null
                            dragY = null
                        },
                    )
                },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = LIST_VERTICAL_PADDING),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(messages, key = { it.id }) { m ->
                    val selected = m.id in selectedIds
                    Column(
                        Modifier.fillMaxWidth()
                            // Belgilangan qator butun kengligi bo'ylab yoritiladi — pufak
                            // o'z rangini saqlaydi, ya'ni chiquvchi/kiruvchi farqi yo'qolmaydi.
                            .background(if (selected) Sc.Brand.copy(alpha = 0.12f) else Color.Transparent),
                    ) {
                        if (m.dayLabel != null) DaySeparator(m.dayLabel)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (selectionMode) {
                                SelectionCheck(selected)
                                Spacer(Modifier.width(8.dp))
                            }
                            Box(Modifier.weight(1f)) {
                                MessageBubble(
                                    message = m,
                                    // Tanlash rejimida har qanday bosish — belgilash;
                                    // aks holda pufakning bo'sh joyi hech nima qilmaydi.
                                    onTap = { if (selectionMode) toggleSelection(m) },
                                    onOpenImage = { imageIndex ->
                                        if (selectionMode) toggleSelection(m) else viewer = m.images to imageIndex
                                    },
                                    onCancelUpload = onCancelUpload,
                                    onOpenAttachment = { message ->
                                        val media = message.attachment
                                        when {
                                            selectionMode -> toggleSelection(message)
                                            media == null -> Unit
                                            // Transkodlanmagan videoning fayli hali yo'q —
                                            // pleyer uni ocholmaydi.
                                            media.processing -> onSoon("Video hali tayyorlanmoqda")
                                            message.type == MessageType.VIDEO -> videoViewer = media
                                            // Faylni ilova ichida ochadigan komponent yo'q — uni tizim
                                            // brauzeriga uzatib bo'lmaydi ham (havola token talab qiladi).
                                            else -> onSoon("Faylni yuklab olish tez orada")
                                        }
                                    },
                                    onToggleVoice = { message ->
                                        // Boshqa xabar bosilsa avvalgisi to'xtaydi — pozitsiya ham nolga.
                                        when {
                                            selectionMode -> toggleSelection(message)
                                            playingVoiceId == message.id -> {
                                                playingVoiceId = null
                                                audioPlayer.stop()
                                            }
                                            else -> {
                                                playingVoiceId = message.id
                                                voiceProgress = 0f
                                                message.attachment?.url?.let { audioPlayer.play(it) }
                                            }
                                        }
                                    },
                                    playingVoiceId = playingVoiceId,
                                    voiceProgress = voiceProgress,
                                )
                            }
                        }
                    }
                }
            }

        }

        Composer(
            draft = state.draft,
            onDraft = onDraft,
            onSend = onSend,
            onPickImages = {
                stickersOpen = false
                attachMenu = true
            },
            recording = recording,
            onToggleRecording = {
                if (recording) {
                    recorder.stop()
                } else {
                    stickersOpen = false
                    recording = true
                    recorder.start()
                }
            },
            stickersOpen = stickersOpen,
            onToggleStickers = { stickersOpen = !stickersOpen },
            onPickSticker = onSendSticker,
            // Panel tanlangandan keyin yopiladi: GIF/stiker yuborilgach ro'yxat pastga
            // suriladi va ochiq panel yangi xabarni to'sib qo'yardi.
            onPickStickerRef = { stickersOpen = false; onSendStickerRef(it) },
            onPickGif = { stickersOpen = false; onSendGif(it) },
        )
    }

    if (attachMenu) {
        AttachMenu(
            onDismiss = { attachMenu = false },
            onPickImages = { attachMenu = false; picker.pick() },
            onPickVideo = { attachMenu = false; videoPicker.pick() },
            onCaptureVideo = { attachMenu = false; videoCapture.pick() },
            onPickFile = { attachMenu = false; filePicker.pick() },
        )
    }

    previewVideo?.let { picked ->
        VideoPreviewSheet(
            video = picked,
            // Bekor qilinsa keshdagi fayl DARROV o'chadi — u o'nlab MB va uni boshqa hech
            // kim tozalamaydi. ⚠️ Faqat **bizniki** bo'lsa: galereyadan tanlangan video
            // ko'chirilmagan bo'lishi mumkin va u yerdagi yo'l foydalanuvchining o'z fayli.
            onCancel = {
                if (picked.ownsFile) deleteMediaFile(picked.path)
                previewVideo = null
            },
            onSend = { caption ->
                previewVideo = null
                onSendVideo(picked.toOutgoing(caption, videoPreparer))
            },
        )
    }

    videoViewer?.let { video ->
        Dialog(
            onDismissRequest = { videoViewer = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                ScVideoPlayer(
                    url = video.url,
                    headers = mediaHeaders,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(14.dp)
                        .clickable { videoViewer = null },
                ) {
                    Icon(ScIcons.Close, "Yopish", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
    }

    if (profileOpen) {
        PeerProfileSheet(
            conversation = conversation,
            typing = state.peerTyping,
            realtime = state.realtime,
            photos = state.photos,
            links = state.links,
            files = state.files,
            onOpenFile = { profileOpen = false; onSoon("Faylni yuklab olish tez orada") },
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
            // Videoning havolasi himoyalangan (`/v1/media/{id}/raw`) — pleyerga sarlavha
            // berilmasa `404` olardi va ekran qop-qora bo'lib qolardi.
            mediaHeaders = mediaHeaders,
            onDismiss = { viewer = null },
        )
    }

    // --- Dialoglar ---------------------------------------------------------------------

    val menuMessage = singleMenu
    if (menuMessage != null) {
        AlertDialog(
            onDismissRequest = { singleMenu = null },
            title = { Text("Xabar", style = scStyle(17f, FontWeight.ExtraBold)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (menuMessage.status == MessageStatus.FAILED) {
                        ActionRow(ScIcons.Return, "Qayta yuborish") {
                            onRetry(menuMessage.messageIds)
                            singleMenu = null
                            selectedIds = emptySet()
                        }
                    }
                    // Gapni belgilab nusxa olish — pufakning o'zida matn tanlab bo'lmaydi:
                    // u yerda uzun bosish belgilash rejimini ochadi.
                    if (!menuMessage.deleted && menuMessage.text.isNotBlank()) {
                        ActionRow(ScIcons.FileText, "Matnni belgilash") {
                            selectTextFor = menuMessage
                            singleMenu = null
                        }
                    }
                    if (!menuMessage.outgoing && !menuMessage.deleted) {
                        ActionRow(ScIcons.Bell, "Shikoyat qilish", danger = true) {
                            reportMessageFor = menuMessage.id
                            singleMenu = null
                            selectedIds = emptySet()
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { singleMenu = null }) {
                    Text("Yopish", style = scStyle(14f, FontWeight.Bold, Sc.InkSoft))
                }
            },
        )
    }

    if (confirmDelete) {
        DeleteMessagesDialog(
            count = selectedCount,
            peerName = conversation.other.displayName,
            // Suhbatdoshning (yoki allaqachon o'chirilgan) xabarini serverdan o'chirib
            // bo'lmaydi — o'shanda katak umuman ko'rsatilmaydi (`DELETE /v1/messages/{id}`
            // faqat o'z xabaringga ruxsat beradi).
            canDeleteForPeer = selectedRows.isNotEmpty() && selectedRows.all { it.canDelete },
            onConfirm = { forEveryone ->
                onDeleteMessages(selectedRows.flatMap { it.messageIds }, forEveryone)
                confirmDelete = false
                selectedIds = emptySet()
            },
            onDismiss = { confirmDelete = false },
        )
    }

    val textMessage = selectTextFor
    if (textMessage != null) {
        SelectTextDialog(
            text = textMessage.text,
            onCopyAll = {
                clipboard.setText(AnnotatedString(textMessage.text))
                selectTextFor = null
                selectedIds = emptySet()
                onSoon("Nusxa olindi")
            },
            onDismiss = { selectTextFor = null },
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

/**
 * Belgilash rejimidagi sarlavha — Telegram'dagidek suhbat sarlavhasining **o'rniga** chiqadi.
 *
 * Chapda yopish, o'rtada tanlanganlar soni, o'ngda amallar. «Yo'naltirish» bu yerda yo'q:
 * backendda forward endpointi yo'q va ishlamaydigan tugma qo'yishdan ko'ra qo'ymagan yaxshi
 * (`CHAT_SELECTION_AND_HISTORY_BACKEND.md` §0).
 */
@Composable
private fun SelectionHeader(
    count: Int,
    onClose: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onMore: () -> Unit,
    /** Qo'shimcha amallar faqat BITTA xabar tanlanganda ma'noli. */
    showMore: Boolean,
) {
    StatusBarAppearance(darkIcons = false)
    Row(
        Modifier.fillMaxWidth()
            .background(Sc.headerBrush)
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HeaderGlassButton(ScIcons.Close, "Belgilashni bekor qilish", onClose)
        ScText(
            "$count ta tanlandi", 17f, FontWeight.ExtraBold, Color.White,
            letterSpacing = -0.2f, maxLines = 1, modifier = Modifier.weight(1f),
        )
        HeaderGlassButton(ScIcons.Copy, "Nusxa olish", onCopy)
        if (showMore) HeaderGlassButton(ScIcons.DotsVertical, "Yana", onMore)
        HeaderGlassButton(ScIcons.Trash, "O'chirish", onDelete)
    }
}

/** Qator boshidagi belgilash katagi — tanlanganida brend rangi bilan to'ladi. */
@Composable
private fun SelectionCheck(selected: Boolean) {
    Box(
        Modifier.size(23.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(if (selected) Sc.Brand else Color.White.copy(alpha = 0.7f))
            .border(1.5.dp, if (selected) Sc.Brand else Sc.Border, RoundedCornerShape(percent = 50)),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(AppIcons.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
        }
    }
}

/**
 * Ro'yxatning ichidagi [y] balandligida turgan qator indeksi.
 *
 * Qidiruv "aynan qator ichida" emas, "boshi barmoqdan yuqorida turgan **eng pastki** qator":
 * xabarlar orasida 10dp bo'shliq bor va aniq tegishlilik bilan qidirilganda barmoq shu
 * bo'shliqqa tushishi bilan mos qator topilmay, belgilash sakrab ketardi. Bu usul chetlarni
 * ham o'zi hal qiladi — barmoq ro'yxatdan tashqariga chiqsa eng yaqin ko'rinadigan qator.
 */
private fun LazyListState.indexAt(y: Float): Int? {
    val visible = layoutInfo.visibleItemsInfo
    if (visible.isEmpty()) return null
    return (visible.lastOrNull { y >= it.offset } ?: visible.first()).index
}

/**
 * Uzun bosib **surib belgilash** — Telegram'dagi imo-ishora.
 *
 * Nega tayyor [detectDragGesturesAfterLongPress] emas, balki qo'lda:
 *
 * 1. **Hodisa `Initial` bosqichida olinadi.** Ro'yxat aylantirgichi shu modifikatorning
 *    ICHIDA turadi va `Main` bosqichida hodisani BIRINCHI bo'lib ko'radi — o'shanda uzun
 *    bosishdan keyingi surish belgilash o'rniga oddiy aylantirishga aylanib ketardi.
 *    `Initial` esa ota'dan bolaga boradi: biz olib qo'ysak, aylantirgichga hech nima
 *    yetmaydi va ro'yxat faqat avto-surish orqali harakatlanadi.
 * 2. Shu sababli imo-ishora **ro'yxat darajasida** yashaydi. Qatorga osilganida qator
 *    ekrandan chiqishi bilan kompozitsiyadan olib tashlanib, surish uzilardi.
 *
 * Uzun bosishning o'zi `Main` bosqichida kutiladi ([awaitLongPressOrCancellation]): shu
 * yarim soniya ichida barmoq qimirlasa aylantirgich hodisani yeydi va kutish bekor bo'ladi,
 * ya'ni **oddiy aylantirish avvalgidek** ishlaydi.
 */
private suspend fun PointerInputScope.detectLongPressDragSelect(
    onStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onEnd: () -> Unit,
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
    onStart(longPress.position)
    try {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            // Barmoq ko'tarildi — imo-ishora tugadi.
            if (!change.pressed) break
            change.consume()
            onDrag(change.position)
        }
    } finally {
        // Bekor qilinganda ham (ekran yopildi, boshqa imo-ishora g'olib chiqdi) holat
        // tozalanadi — aks holda avto-surish sikli abadiy aylanib qolardi.
        onEnd()
    }
}

/**
 * Xabarlar ro'yxatining tepa/past ichki chekkasi.
 *
 * Alohida doimiy: barmoq koordinatasini qatorlar fazosiga o'tkazishda aynan shu qiymat
 * ayriladi ([LazyListState.indexAt] izohiga qarang). Ikkisi ajralib qolsa belgilash
 * jimgina qo'shni qatorga siljib ketardi.
 */
private val LIST_VERTICAL_PADDING = 16.dp

/** Avto-surish boshlanadigan chekka — barmoq shu masofaga kirsa ro'yxat sura boshlaydi. */
private val AUTO_SCROLL_EDGE = 84.dp

/** Bir kadrdagi eng katta surish (px) — ~60 kadrda sekundiga 1500px, qo'l bilan bir xilda. */
private const val AUTO_SCROLL_MAX_STEP = 25f

/** Chekkaga endigina kirganda ham sezilarli surish bo'lsin. */
private const val MIN_SCROLL_FRACTION = 0.2f

/**
 * O'chirishni tasdiqlash — Telegram'dagi aynan shu oyna.
 *
 * Sukut bo'yicha xabar **faqat o'zingizda** o'chadi; katak belgilansa suhbatdoshda ham.
 * Katak faqat hammasi o'zingniki bo'lganda ko'rinadi: `DELETE /v1/messages/{id}` o'zganikini
 * o'chirmaydi va belgilangan katak yolg'on va'da bo'lardi.
 */
@Composable
private fun DeleteMessagesDialog(
    count: Int,
    peerName: String,
    canDeleteForPeer: Boolean,
    onConfirm: (forEveryone: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var forEveryone by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$count ta xabarni o'chirish", style = scStyle(17f, FontWeight.ExtraBold)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "Haqiqatan ham bu xabarlarni o'chirmoqchimisiz?",
                    style = scStyle(14f, FontWeight.Medium, Sc.InkSoft, lineHeight = 20f),
                )
                if (canDeleteForPeer) {
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { forEveryone = !forEveryone }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier.size(21.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (forEveryone) Sc.Brand else Color.Transparent)
                                .border(
                                    1.5.dp,
                                    if (forEveryone) Sc.Brand else Sc.Border,
                                    RoundedCornerShape(6.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (forEveryone) {
                                Icon(AppIcons.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
                            }
                        }
                        ScText("$peerName uchun ham o'chirilsin", 14f, FontWeight.SemiBold, Sc.Ink)
                    }
                } else {
                    // Sabab aytilmasa foydalanuvchi katakni "yo'qolib qolgan" deb o'ylardi.
                    Text(
                        "Suhbatdoshning xabarlarini faqat o'zingizda o'chira olasiz.",
                        style = scStyle(12.5f, FontWeight.Medium, Sc.Muted, lineHeight = 18f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(forEveryone) }) {
                Text("O'chirish", style = scStyle(14f, FontWeight.ExtraBold, Sc.Danger))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Bekor qilish", style = scStyle(14f, FontWeight.Bold, Sc.InkSoft))
            }
        },
    )
}

/**
 * Gapni belgilash oynasi — xabar matni **tanlanadigan** holda ko'rsatiladi.
 *
 * Nega alohida oyna: pufakning o'zida uzun bosish belgilash rejimini ochadi, ya'ni u yerda
 * matn tanlash imo-ishorasiga joy yo'q. Bu yerda esa odatdagi tizim tanlagichi ishlaydi —
 * kerakli gapni belgilab, tizimning «Copy» tugmasini bosish mumkin.
 */
@Composable
private fun SelectTextDialog(text: String, onCopyAll: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Matnni belgilash", style = scStyle(17f, FontWeight.ExtraBold)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Kerakli gapni bosib turib belgilang, so'ng chiqqan «Copy» tugmasini bosing.",
                    style = scStyle(12.5f, FontWeight.Medium, Sc.Muted, lineHeight = 18f),
                )
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Sc.FieldBg)
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                ) {
                    SelectionContainer {
                        Text(text, style = scStyle(15f, FontWeight.Medium, Sc.Ink, lineHeight = 21f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCopyAll) {
                Text("Hammasini nusxalash", style = scStyle(14f, FontWeight.ExtraBold, Sc.Brand))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Yopish", style = scStyle(14f, FontWeight.Bold, Sc.InkSoft))
            }
        },
    )
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
    // `drawWithCache` — nuqtalar joylashuvi FAQAT o'lcham o'zgarganda hisoblanadi.
    // Oddiy `Canvas` da bu ichma-ich sikl (ekranga ~500 ta doira) har chizishda,
    // ya'ni aylantirishning har kadrida qayta bajarilardi.
    Box(
        Modifier.fillMaxSize().drawWithCache {
            val step = 22.dp.toPx()
            val radius = 1.4f * density
            // `buildList` ichida `size` RO'YXAT o'lchamiga aylanadi — maydonni oldindan olamiz.
            val area = size
            val centers = buildList {
                var y = step / 2f
                while (y < area.height) {
                    var x = step / 2f
                    while (x < area.width) {
                        add(Offset(x, y))
                        x += step
                    }
                    y += step
                }
            }
            onDrawBehind { centers.forEach { drawCircle(dot, radius, it) } }
        },
    )
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
    /**
     * Pufakning bo'sh joyi bosildi. Uzun bosish bu yerda **yo'q**: u qatorning o'zida,
     * surib belgilash bilan bitta imo-ishora bo'lishi uchun.
     */
    onTap: () -> Unit,
    onOpenImage: (Int) -> Unit,
    /** Ketayotgan videoni to'xtatish — halqa ichidagi `×`. */
    onCancelUpload: (String) -> Unit,
    /** Fayl yoki video bosildi — chaqiruvchi uni ochadi (yuklab olish / pleyer). */
    onOpenAttachment: (ChatMessageUi) -> Unit,
    onToggleVoice: (ChatMessageUi) -> Unit,
    /** Hozir eshitilayotgan ovozli xabar id'si — bir vaqtda faqat bittasi ijro etiladi. */
    playingVoiceId: String?,
    /** Eshitilayotganining pozitsiyasi, `0f..1f`. */
    voiceProgress: Float,
) {
    when {
        // O'chirilgan xabar — turi qanday bo'lishidan qat'i nazar oddiy tombstone pufagi.
        message.deleted -> TextBubble(message, onTap = onTap)
        // Rasm, GIF **va video** — hammasi bitta mozaikada chiziladi ([ChatMediaItem]),
        // shuning uchun shoxobcha turga emas, to'rda element borligiga qarab tanlanadi.
        // Aralash albom (rasm + video) ham shu yerdan o'tadi.
        message.images.isNotEmpty() ->
            ImageAlbumBubble(
                message,
                onOpen = onOpenImage,
                onCancelUpload = onCancelUpload,
                onTap = onTap,
            )
        // Fayl hali ketmoqda — biriktirma serverning javobi bilan keladi, ya'ni quyidagi
        // shoxobchalarning hech biri hozircha ishlamaydi.
        message.upload != null -> UploadingAttachmentBubble(message, onTap = onTap)
        message.type == MessageType.FILE && message.attachment != null ->
            FileBubble(message, onOpen = { onOpenAttachment(message) })
        message.type == MessageType.VOICE && message.attachment != null ->
            VoiceBubble(
                message = message,
                playing = playingVoiceId == message.id,
                progress = if (playingVoiceId == message.id) voiceProgress else 0f,
                onTogglePlay = { onToggleVoice(message) },
                onTap = onTap,
            )
        message.type == MessageType.VIDEO && message.attachment != null ->
            VideoBubble(message, onOpen = { onOpenAttachment(message) })
        message.sticker != null -> StickerBubble(message, onTap = onTap)
        else -> TextBubble(message, onTap = onTap)
    }
}

@Composable
private fun TextBubble(message: ChatMessageUi, onTap: () -> Unit) {
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
                .clickable(onClick = onTap)
                .padding(start = 13.dp, end = 13.dp, top = 10.dp, bottom = 7.dp),
        ) {
            // Tombstone xira va kursiv emas, shunchaki so'nikroq — u xabar emas, iz.
            val textColor = when {
                message.deleted && message.outgoing -> Color.White.copy(alpha = 0.75f)
                message.deleted -> Sc.Muted
                message.outgoing -> Color.White
                else -> Sc.Ink
            }
            ScText(message.text, 15f, FontWeight.Medium, textColor, lineHeight = 21f)
            Spacer(Modifier.height(2.dp))
            MessageMeta(message, Modifier.align(Alignment.End), onDark = message.outgoing)
        }
    }
}

/**
 * Pastdagi kiritish paneli — biriktirish + pill maydon + gradient tugma, tagida ochiladigan
 * stiker paneli.
 *
 * Qog'oz qisqich biriktirma menyusini ochadi (Rasm / Video / Fayl), tabassum stikerlarni.
 * Matn maydoni bo'sh bo'lsa o'ng tugma **ovoz yozadi**: bosilganda yozish boshlanadi,
 * qayta bosilganda to'xtaydi va xabar ketadi.
 */
@Composable
private fun Composer(
    draft: String,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onPickImages: () -> Unit,
    /** Ovoz yozilyaptimi — mikrofon tugmasi holatini shu belgilaydi. */
    recording: Boolean,
    onToggleRecording: () -> Unit,
    stickersOpen: Boolean,
    onToggleStickers: () -> Unit,
    onPickSticker: (Sticker) -> Unit,
    onPickStickerRef: (StickerSearchItem) -> Unit,
    onPickGif: (GifItem) -> Unit,
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
                    // Matn bo'lsa — yuborish, bo'lmasa — ovoz yozishni boshlash/to'xtatish.
                    .clickable { if (draft.isNotBlank()) onSend() else onToggleRecording() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    when {
                        draft.isNotBlank() -> ScIcons.Return
                        // Yozib turganda — to'xtatish belgisi (alohida "stop" ikonkasi yo'q).
                        recording -> ScIcons.Close
                        else -> ScIcons.Mic
                    },
                    if (draft.isNotBlank()) "Yuborish" else if (recording) "To'xtatish" else "Ovoz yozish",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        // Stikerlar va GIF bitta panelda, yorliqlar bilan: kompozitorda ikkinchi ikonaga
        // joy yo'q va foydalanuvchi ikkalasini ham bir xil maqsadda ochadi.
        if (stickersOpen) {
            ChatMediaPanel(
                onPickSticker = onPickSticker,
                onPickStickerRef = onPickStickerRef,
                onPickGif = onPickGif,
            )
        }
    }
}

/**
 * Biriktirma menyusi — qog'oz qisqich bosilganda.
 *
 * Uchta yo'l uch xil `kind` bilan yuklanadi (rasm 12 MB, video 64 MB · ≤3 daq, fayl 48 MB),
 * shuning uchun ular bitta tanlagichga birlashtirilmagan: tizim tanlagichlari ham har xil
 * (galereya va hujjatlar provayderi).
 */
@Composable
private fun AttachMenu(
    onDismiss: () -> Unit,
    onPickImages: () -> Unit,
    onPickVideo: () -> Unit,
    onCaptureVideo: () -> Unit,
    onPickFile: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(20.dp)).background(Sc.Card).padding(vertical = 8.dp),
        ) {
            // Har band o'z ikonkasi bilan: ilgari «Rasm» qidiruv lupasini, «Video» bilan
            // «Kamera» esa bitta kamera ikonkasini ulashardi va menyuni faqat matndan
            // o'qib tushunish mumkin edi.
            ActionRow(AppIcons.ImageIcon, "Rasm", onClick = onPickImages)
            ActionRow(AppIcons.Video, "Video", onClick = onPickVideo)
            // Kamera galereyadan ALOHIDA band: "Video" galereyani ochadi va u yerdan
            // kamerani topib bo'lmaydi — hozir suratga olishni xohlagan foydalanuvchi
            // ilovadan chiqib, kamerani ochib, qaytib kelishi kerak bo'lardi.
            ActionRow(AppIcons.Camera, "Kamera", onClick = onCaptureVideo)
            ActionRow(ScIcons.Paperclip, "Fayl", onClick = onPickFile)
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
