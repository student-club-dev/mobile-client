package dev.feature.chat.presentation

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import dev.core.uikit.components.ScAvatar
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScText
import dev.core.uikit.components.StatusBarAppearance
import dev.core.uikit.theme.Sc
import dev.feature.chat.domain.model.ConversationItem
import dev.feature.connections.domain.model.Gender

/**
 * Suhbatdosh profili — sarlavha bosilganda ochiladi (Telegram uslubi).
 *
 * Ma'lumot **local keshdan** olinadi (`ConversationEntity`), shuning uchun ekran oflayn ham
 * ochiladi va tarmoqni kutmaydi. Backendda «talaba profilini olish» endpointi yo'q
 * (`GET /v1/students/{id}` mavjud emas), ya'ni bu yerda ko'rsatiladigan hamma narsa —
 * suhbatlar ro'yxati javobidan kelgan qisqa profil.
 */
@Composable
internal fun PeerProfileSheet(
    conversation: ConversationItem,
    typing: Boolean,
    realtime: Boolean,
    /** Suhbatdagi rasmlar — «Umumiy media» to'ri, yangidan eskiga. */
    photos: List<ChatImageUi>,
    universityName: String?,
    onClose: () -> Unit,
    onDisconnect: () -> Unit,
    onBlock: () -> Unit,
    onReport: () -> Unit,
) {
    var viewer by remember { mutableStateOf<Int?>(null) }
    var avatarOpen by remember { mutableStateOf(false) }
    val student = conversation.other

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        StatusBarAppearance(darkIcons = false)
        Column(Modifier.fillMaxSize().background(Sc.ChatBg)) {
            // --- Gradient shapka: katta avatar, ism, holat -------------------------------
            Column(
                Modifier.fillMaxWidth()
                    .background(Sc.headerBrush)
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 22.dp),
            ) {
                Box(
                    Modifier.size(40.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Color.White.copy(alpha = 0.2f))
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Icon(ScIcons.Close, "Yopish", tint = Color.White, modifier = Modifier.size(20.dp)) }

                Spacer(Modifier.height(14.dp))
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ScAvatar(
                        name = student.displayName,
                        size = 104.dp,
                        avatarUrl = student.avatarUrl,
                        background = Color.White.copy(alpha = 0.9f),
                        initialColor = Sc.Violet,
                        // Rasm bo'lsagina kattalashtiramiz — bosh harfni ochishning ma'nosi yo'q.
                        modifier = Modifier.clickable(enabled = !student.avatarUrl.isNullOrBlank()) {
                            avatarOpen = true
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    ScText(
                        student.displayName, 21f, FontWeight.ExtraBold, Color.White,
                        letterSpacing = -0.3f, maxLines = 2,
                    )
                    val status = when {
                        typing -> "yozmoqda…"
                        student.online -> "onlayn"
                        !realtime -> "ulanmoqda…"
                        else -> ChatFormat.lastSeen(student.lastSeenAt)
                    }
                    Spacer(Modifier.height(3.dp))
                    ScText(status, 13.5f, FontWeight.Medium, Color.White.copy(alpha = 0.9f))
                }
            }

            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // --- Ma'lumotlar -------------------------------------------------------
                val rows = buildList {
                    student.username?.takeIf { it.isNotBlank() }?.let { add("Foydalanuvchi nomi" to "@$it") }
                    universityName?.let { add("Universitet" to it) }
                    student.courseYear?.let { add("Kurs" to it.courseLabel()) }
                    student.gender?.let { add("Jinsi" to if (it == Gender.MALE) "Erkak" else "Ayol") }
                }
                if (rows.isNotEmpty()) {
                    InfoCard(rows)
                }

                // --- Umumiy media ------------------------------------------------------
                if (photos.isNotEmpty()) {
                    SectionTitle("Umumiy media · ${photos.size}")
                    // Ichki to'r o'zi aylanmasin — balandligi qatorlar soniga qarab hisoblanadi,
                    // aks holda tashqi `verticalScroll` bilan ziddiyat chiqadi.
                    val rowsCount = (photos.size + PHOTO_COLUMNS - 1) / PHOTO_COLUMNS
                    val gridHeight = (PHOTO_CELL + PHOTO_GAP) * rowsCount
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(PHOTO_COLUMNS),
                        userScrollEnabled = false,
                        horizontalArrangement = Arrangement.spacedBy(PHOTO_GAP),
                        verticalArrangement = Arrangement.spacedBy(PHOTO_GAP),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.fillMaxWidth().height(gridHeight),
                    ) {
                        itemsIndexed(photos, key = { _, photo -> photo.messageId }) { index, photo ->
                            Box(
                                Modifier.aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Sc.Chip)
                                    .clickable { viewer = index },
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

                // --- Amallar -----------------------------------------------------------
                SectionTitle("Amallar")
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Sc.Card),
                ) {
                    ProfileAction(ScIcons.Close, "Bog'lanishni uzish", onClick = onDisconnect)
                    ProfileAction(ScIcons.Users, "Bloklash", danger = true, onClick = onBlock)
                    ProfileAction(ScIcons.Bell, "Shikoyat qilish", danger = true, onClick = onReport)
                }
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }

    val openIndex = viewer
    if (openIndex != null) {
        ImageViewerDialog(photos, openIndex, onDismiss = { viewer = null })
    }
    if (avatarOpen) {
        ImageViewerDialog(
            images = listOf(
                ChatImageUi(
                    messageId = "avatar",
                    url = student.avatarUrl,
                    localBytes = null,
                    aspectRatio = null,
                ),
            ),
            startIndex = 0,
            onDismiss = { avatarOpen = false },
        )
    }
}

@Composable
private fun InfoCard(rows: List<Pair<String, String>>) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Sc.Card)) {
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Sc.BorderSoft))
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                ScText(value, 15f, FontWeight.Bold, Sc.Ink, maxLines = 2)
                Spacer(Modifier.height(2.dp))
                ScText(label, 12f, FontWeight.Medium, Sc.MutedLight)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    ScText(text, 13f, FontWeight.ExtraBold, Sc.Muted, letterSpacing = 0.3f)
}

@Composable
private fun ProfileAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
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

private const val PHOTO_COLUMNS = 3
private val PHOTO_CELL = 108.dp
private val PHOTO_GAP = 4.dp
