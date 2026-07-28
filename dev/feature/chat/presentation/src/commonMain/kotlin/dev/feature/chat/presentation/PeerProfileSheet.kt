package dev.feature.chat.presentation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import dev.core.uikit.components.AppIcons
import dev.core.uikit.components.ScAvatar
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScText
import dev.core.uikit.components.StatusBarAppearance
import dev.core.uikit.theme.Sc
import dev.feature.chat.domain.model.ConversationItem
import dev.feature.connections.domain.model.Gender

/** Profil ekranidagi bo'limlar — Telegram maketidagi tartibda. */
private enum class ProfileTab(val label: String) {
    POSTS("Postlar"),
    MEDIA("Media"),
    FILES("Fayllar"),
    LINKS("Havolalar"),
}

/**
 * Suhbatdosh profili — chat sarlavhasi bosilganda ochiladi (Telegram maketi).
 *
 * Avatar ikki holatda: odatda **markazda kichik doira**, bosilganda esa butun kenglikni
 * egallaydigan **katta rasm** (ustida gradient va ism). Telegram ham aynan shunday ishlaydi.
 *
 * ⚠️ Ma'lumotning bir qismini backend **bermaydi** — telefon raqami va tarjimayi hol
 * `StudentSummaryDto` da yo'q, «Postlar» va «Fayllar» uchun esa tushunchaning o'zi yo'q.
 * Ular «tez orada» holatida turadi; talablar `STORY_AND_PROFILE_BACKEND.md` da.
 */
@Composable
internal fun PeerProfileSheet(
    conversation: ConversationItem,
    typing: Boolean,
    realtime: Boolean,
    /** Suhbatdagi rasmlar — «Media» bo'limi, yangidan eskiga. */
    photos: List<ChatImageUi>,
    /** Xabarlardan ajratib olingan havolalar — «Havolalar» bo'limi. */
    links: List<ChatLinkUi>,
    universityName: String?,
    onClose: () -> Unit,
    onDisconnect: () -> Unit,
    onBlock: () -> Unit,
    onReport: () -> Unit,
    onSoon: (String) -> Unit,
) {
    var viewer by remember { mutableStateOf<Int?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(ProfileTab.MEDIA) }
    val student = conversation.other

    val status = when {
        typing -> "yozmoqda…"
        student.online -> "onlayn"
        !realtime -> "ulanmoqda…"
        else -> ChatFormat.lastSeen(student.lastSeenAt)
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        StatusBarAppearance(darkIcons = false)
        Column(Modifier.fillMaxSize().background(Sc.Bg).verticalScroll(rememberScrollState())) {

            if (expanded && !student.avatarUrl.isNullOrBlank()) {
                ExpandedHeader(
                    name = student.displayName,
                    status = status,
                    avatarUrl = student.avatarUrl,
                    // Backend bitta rasm beradi; ro'yxat kelganda chiziqchalar o'zi ko'payadi.
                    photoCount = 1,
                    onClose = onClose,
                    onCollapse = { expanded = false },
                    onOpenPhoto = { viewer = AVATAR_VIEWER },
                )
            } else {
                CollapsedHeader(
                    student = student,
                    status = status,
                    onClose = onClose,
                    onExpand = {
                        if (!student.avatarUrl.isNullOrBlank()) expanded = true
                    },
                )
            }

            Column(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Spacer(Modifier.height(2.dp))

                // --- To'rtta amal tugmasi ---------------------------------------------
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    ActionTile(ScIcons.ChatRound, "Xabar", Modifier.weight(1f), onClick = onClose)
                    ActionTile(ScIcons.Bell, "Sukut qilish", Modifier.weight(1f)) {
                        onSoon("Sukut qilish tez orada")
                    }
                    ActionTile(ScIcons.PhoneCall, "Chaqiruv", Modifier.weight(1f)) {
                        onSoon("Qo'ng'iroq tez orada")
                    }
                    ActionTile(AppIcons.Camera, "Video", Modifier.weight(1f)) {
                        onSoon("Video qo'ng'iroq tez orada")
                    }
                }

                // --- Ma'lumotlar -------------------------------------------------------
                InfoCard {
                    student.username?.takeIf { it.isNotBlank() }?.let {
                        InfoRow("@$it", "Foydalanuvchi nomi")
                    }
                    // Backend suhbatdoshning raqamini ham, tarjimayi holini ham bermaydi.
                    InfoRow(null, "Mobil raqam")
                    InfoRow(null, "Tarjimayi hol")
                    universityName?.let { InfoRow(it, "Universitet") }
                    student.courseYear?.let { InfoRow(it.courseLabel(), "Kurs") }
                    student.gender?.let {
                        InfoRow(if (it == Gender.MALE) "Erkak" else "Ayol", "Jinsi")
                    }
                }

                // --- Bo'limlar ---------------------------------------------------------
                TabBar(selected = tab, onSelect = { tab = it })

                when (tab) {
                    ProfileTab.MEDIA -> if (photos.isEmpty()) {
                        EmptySection("Bu suhbatda hali rasm yo'q")
                    } else {
                        PhotoGrid(photos, onOpen = { viewer = it })
                    }
                    ProfileTab.LINKS -> if (links.isEmpty()) {
                        EmptySection("Bu suhbatda havola yuborilmagan")
                    } else {
                        LinkList(links)
                    }
                    // Ikkalasi ham backendga bog'liq — qarang: `STORY_AND_PROFILE_BACKEND.md`.
                    ProfileTab.POSTS -> EmptySection("Postlar tez orada")
                    ProfileTab.FILES -> EmptySection("Fayl yuborish tez orada")
                }

                // --- Amallar -----------------------------------------------------------
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Sc.Card)) {
                    ProfileAction(ScIcons.Close, "Bog'lanishni uzish", onClick = onDisconnect)
                    ProfileAction(ScIcons.Users, "Bloklash", danger = true, onClick = onBlock)
                    ProfileAction(ScIcons.Bell, "Shikoyat qilish", danger = true, onClick = onReport)
                }
                Spacer(Modifier.height(8.dp).navigationBarsPadding())
            }
        }
    }

    val openIndex = viewer
    if (openIndex == AVATAR_VIEWER) {
        ImageViewerDialog(
            images = listOf(ChatImageUi("avatar", student.avatarUrl, null, null)),
            startIndex = 0,
            onDismiss = { viewer = null },
        )
    } else if (openIndex != null) {
        ImageViewerDialog(photos, openIndex, onDismiss = { viewer = null })
    }
}

// ---------------------------------------------------------------------------
// Sarlavha
// ---------------------------------------------------------------------------

/** Odatiy holat — markazda kichik doira, ostida ism va holat. */
@Composable
private fun CollapsedHeader(
    student: dev.feature.connections.domain.model.StudentSummary,
    status: String,
    onClose: () -> Unit,
    onExpand: () -> Unit,
) {
    val size by animateDpAsState(96.dp, label = "avatar")
    Column(
        Modifier.fillMaxWidth()
            .background(Sc.headerBrush)
            .statusBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 20.dp),
    ) {
        HeaderBar(onClose = onClose, onMenu = null, tint = Color.White)
        Spacer(Modifier.height(6.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ScAvatar(
                name = student.displayName,
                size = size,
                avatarUrl = student.avatarUrl,
                background = Color.White.copy(alpha = 0.9f),
                initialColor = Sc.Violet,
                modifier = Modifier.clickable(onClick = onExpand),
            )
            Spacer(Modifier.height(12.dp))
            ScText(
                student.displayName, 21f, FontWeight.ExtraBold, Color.White,
                letterSpacing = -0.3f, maxLines = 2,
            )
            Spacer(Modifier.height(3.dp))
            ScText(status, 13.5f, FontWeight.Medium, Color.White.copy(alpha = 0.85f))
        }
    }
}

/**
 * Yoyilgan holat — rasm butun kenglikda, ustida pastdan yuqoriga qorayadigan gradient
 * va chap pastda ism. Yuqorida rasm sonini ko'rsatuvchi chiziqchalar.
 */
@Composable
private fun ExpandedHeader(
    name: String,
    status: String,
    avatarUrl: String?,
    photoCount: Int,
    onClose: () -> Unit,
    onCollapse: () -> Unit,
    onOpenPhoto: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().aspectRatio(1f).clickable(onClick = onOpenPhoto)) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = "Profil rasmi",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // Yuqorida ham, pastda ham gradient: tugmalar va ism har qanday rasm ustida o'qiladi.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.45f),
                    0.28f to Color.Transparent,
                    0.62f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.75f),
                ),
            ),
        )

        Column(Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp)) {
            if (photoCount > 1) {
                PhotoDashes(count = photoCount, current = 0)
                Spacer(Modifier.height(8.dp))
            }
            HeaderBar(onClose = onClose, onMenu = onCollapse, tint = Color.White)
        }

        Column(Modifier.align(Alignment.BottomStart).padding(start = 18.dp, end = 18.dp, bottom = 18.dp)) {
            ScText(name, 27f, FontWeight.ExtraBold, Color.White, letterSpacing = -0.5f, maxLines = 2)
            Spacer(Modifier.height(2.dp))
            ScText(status, 14f, FontWeight.Medium, Color.White.copy(alpha = 0.85f))
        }
    }
}

/** Telegram'dagidek: rasm soniga qarab yuqoridagi chiziqchalar. */
@Composable
private fun PhotoDashes(count: Int, current: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
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

@Composable
private fun HeaderBar(onClose: () -> Unit, onMenu: (() -> Unit)?, tint: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        GlassButton(ScIcons.ChevronLeft, "Orqaga", tint, onClose)
        Spacer(Modifier.weight(1f))
        if (onMenu != null) GlassButton(ScIcons.Close, "Yig'ish", tint, onMenu)
    }
}

@Composable
private fun GlassButton(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(Color.White.copy(alpha = 0.2f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = tint, modifier = Modifier.size(20.dp)) }
}

// ---------------------------------------------------------------------------
// Bo'laklar
// ---------------------------------------------------------------------------

@Composable
private fun ActionTile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp))
            .background(Sc.Card)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = Sc.Brand, modifier = Modifier.size(21.dp))
        ScText(label, 11.5f, FontWeight.SemiBold, Sc.InkSoft, maxLines = 1)
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Sc.Card)) { content() }
}

/** [value] `null` bo'lsa — backend bu maydonni bermaydi, «tez orada» ko'rinadi. */
@Composable
private fun InfoRow(value: String?, label: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp)) {
        if (value != null) {
            ScText(value, 15f, FontWeight.Bold, Sc.Ink, maxLines = 2)
        } else {
            ScText("tez orada", 15f, FontWeight.Medium, Sc.MutedLight)
        }
        Spacer(Modifier.height(2.dp))
        ScText(label, 12f, FontWeight.Medium, Sc.MutedLight)
    }
}

@Composable
private fun TabBar(selected: ProfileTab, onSelect: (ProfileTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Sc.Card).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ProfileTab.entries.forEach { item ->
            val active = item == selected
            Box(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) Sc.Brand.copy(alpha = 0.13f) else Color.Transparent)
                    .clickable { onSelect(item) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                ScText(
                    item.label, 12.5f, FontWeight.Bold,
                    if (active) Sc.Brand else Sc.Muted, maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PhotoGrid(photos: List<ChatImageUi>, onOpen: (Int) -> Unit) {
    // Ichki to'r o'zi aylanmaydi — balandligi qatorlar soniga qarab hisoblanadi, aks holda
    // tashqi `verticalScroll` bilan ziddiyat chiqadi.
    val rows = (photos.size + PHOTO_COLUMNS - 1) / PHOTO_COLUMNS
    LazyVerticalGrid(
        columns = GridCells.Fixed(PHOTO_COLUMNS),
        userScrollEnabled = false,
        horizontalArrangement = Arrangement.spacedBy(PHOTO_GAP),
        verticalArrangement = Arrangement.spacedBy(PHOTO_GAP),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.fillMaxWidth().height((PHOTO_CELL + PHOTO_GAP) * rows),
    ) {
        itemsIndexed(photos, key = { _, photo -> photo.messageId }) { index, photo ->
            Box(
                Modifier.aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Sc.Chip)
                    .clickable { onOpen(index) },
            ) {
                AsyncImage(
                    model = photo.url,
                    contentDescription = "Rasm",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun LinkList(links: List<ChatLinkUi>) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Sc.Card)) {
        links.forEachIndexed { index, link ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Sc.BorderSoft))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(ScIcons.Paperclip, null, tint = Sc.Brand, modifier = Modifier.size(18.dp))
                Column(Modifier.weight(1f)) {
                    ScText(link.host, 14f, FontWeight.Bold, Sc.Ink, maxLines = 1)
                    Spacer(Modifier.height(2.dp))
                    ScText(link.url, 12f, FontWeight.Medium, Sc.MutedLight, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun EmptySection(text: String) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Sc.Card).padding(28.dp),
        contentAlignment = Alignment.Center,
    ) { ScText(text, 13f, FontWeight.Medium, Sc.MutedLight) }
}

@Composable
private fun ProfileAction(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) Sc.Danger else Sc.Ink
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(19.dp))
        ScText(label, 14.5f, FontWeight.SemiBold, color)
    }
}

/** `"1".."4"` / `"MASTER"` → o'qiladigan matn. */
private fun String.courseLabel(): String = when (uppercase()) {
    "MASTER" -> "Magistratura"
    else -> "$this-kurs"
}

/** Avatar ko'rgichi suhbat rasmlaridan farq qilsin — indeks sifatida maxsus qiymat. */
private const val AVATAR_VIEWER = -1

private const val PHOTO_COLUMNS = 3
private val PHOTO_CELL = 108.dp
private val PHOTO_GAP = 4.dp
