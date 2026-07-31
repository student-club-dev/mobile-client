package dev.feature.chat.presentation

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import dev.core.uikit.components.AppIcons
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScText
import dev.core.uikit.components.StatusBarAppearance
import dev.core.uikit.theme.Sc
import dev.feature.chat.domain.model.ConversationItem
import dev.feature.connections.domain.model.Gender
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Profil ekranidagi bo'limlar — Telegram maketidagi tartibda. */
/**
 * ⚠️ «Postlar» **ataylab yo'q** (`handoff/08-PROFILE.md` §7): ilovada post tushunchasi yo'q,
 * backend `GET /v1/students/{id}/listings` ni ham qo'shmadi. Bo'sh tab chalg'itardi.
 */
private enum class ProfileTab(val label: String) {
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
 * Tarjimayi hol, telefon raqami va rasmlar to'plami `StudentSummaryDto` dan keladi
 * (`handoff/08-PROFILE.md` §3). Telefon ko'pincha `null` bo'ladi — sukut sozlama `NOBODY`,
 * o'shanda qator umuman chizilmaydi.
 */
@Composable
internal fun PeerProfileSheet(
    conversation: ConversationItem,
    typing: Boolean,
    realtime: Boolean,
    /** Suhbatdagi rasmlar — «Media» bo'limi, yangidan eskiga. */
    photos: List<ChatMediaItem>,
    /** Xabarlardan ajratib olingan havolalar — «Havolalar» bo'limi. */
    links: List<ChatLinkUi>,
    /** Suhbatda yuborilgan hujjatlar — «Fayllar» bo'limi, yangidan eskiga. */
    files: List<ChatMessageUi>,
    /** Fayl qatori bosildi — yuklab olish / ochish. */
    onOpenFile: (ChatMessageUi) -> Unit,
    universityName: String?,
    onClose: () -> Unit,
    onDisconnect: () -> Unit,
    onBlock: () -> Unit,
    onReport: () -> Unit,
    onSoon: (String) -> Unit,
) {
    var viewer by remember { mutableStateOf<Int?>(null) }
    var tab by remember { mutableStateOf(ProfileTab.MEDIA) }
    val student = conversation.other
    /**
     * Profil rasmlari — `photos` bo'sh bo'lsa eskicha `avatarUrl` ga tushamiz. Ikkalasi ham
     * bo'lmasa bosh harf ko'rinadi (`handoff/08-PROFILE.md` §3).
     */
    val profilePhotos = remember(student.photos, student.avatarUrl) {
        student.photos.map { it.url }.ifEmpty { listOfNotNull(student.avatarUrl?.takeIf { it.isNotBlank() }) }
    }
    var photoIndex by remember(profilePhotos) { mutableStateOf(0) }
    val hasPhoto = profilePhotos.isNotEmpty()

    val status = when {
        typing -> "yozmoqda…"
        student.online -> "onlayn"
        !realtime -> "ulanmoqda…"
        else -> ChatFormat.lastSeen(student.lastSeenAt)
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        StatusBarAppearance(darkIcons = false)
        BoxWithConstraints(Modifier.fillMaxSize().background(Sc.Bg)) {
            val density = LocalDensity.current
            val minPx = with(density) { COLLAPSED_HEADER.toPx() }
            // Yoyilgan sarlavha — kvadrat: balandligi ekran kengligiga teng.
            val maxPx = with(density) { maxWidth.toPx() }

            // Sarlavha balandligi PIKSELDA saqlanadi va barmoq bilan bir kadrda
            // o'zgaradi. `Animatable` ishlatilmadi: uning `snapTo` si suspend, ya'ni
            // har bir surish korutinaga tushib bir kadr kechikardi.
            var headerPx by remember(maxPx) { mutableFloatStateOf(minPx) }
            val scope = rememberCoroutineScope()
            var snapJob by remember { mutableStateOf<Job?>(null) }

            fun snapTo(target: Float) {
                snapJob?.cancel()
                snapJob = scope.launch {
                    animate(headerPx, target, animationSpec = tween(SNAP_DURATION_MS)) { value, _ ->
                        headerPx = value
                    }
                }
            }

            val scroll = rememberScrollState()

            /**
             * Yig'iluvchi sarlavha (collapsing toolbar): kontent aylanishidan OLDIN
             * sarlavha o'z ulushini oladi, shuning uchun u barmoqqa 1:1 ergashadi.
             */
            val collapsing = remember(minPx, maxPx, hasPhoto, scroll) {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        val delta = available.y
                        // Yuqoriga surish — avval SARLAVHA yig'iladi, kontent keyin aylanadi.
                        if (delta < 0 && headerPx > minPx) {
                            snapJob?.cancel()
                            val next = (headerPx + delta).coerceAtLeast(minPx)
                            val used = next - headerPx
                            headerPx = next
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
                        // `available` — kontent yutmagan qoldiq, ya'ni ro'yxat allaqachon
                        // tepasida. Shundagina sarlavha yoyiladi.
                        if (delta > 0 && hasPhoto && headerPx < maxPx) {
                            snapJob?.cancel()
                            val next = (headerPx + delta).coerceAtMost(maxPx)
                            val used = next - headerPx
                            headerPx = next
                            return Offset(0f, used)
                        }
                        return Offset.Zero
                    }

                    // Barmoq ko'tarilganda oraliq holatda qolmasin — eng yaqiniga tushadi.
                    override suspend fun onPreFling(available: Velocity): Velocity {
                        if (headerPx > minPx && headerPx < maxPx) {
                            val half = minPx + (maxPx - minPx) * SNAP_POINT
                            snapTo(if (headerPx >= half) maxPx else minPx)
                        }
                        return Velocity.Zero
                    }
                }
            }

            val progress = if (maxPx > minPx) {
                ((headerPx - minPx) / (maxPx - minPx)).coerceIn(0f, 1f)
            } else {
                0f
            }

            Column(
                Modifier.fillMaxSize().nestedScroll(collapsing).verticalScroll(scroll),
            ) {
                ProfileHeader(
                    name = student.displayName,
                    status = status,
                    avatarUrl = profilePhotos.getOrNull(photoIndex),
                    height = with(density) { headerPx.toDp() },
                    progress = progress,
                    photoCount = profilePhotos.size,
                    photoIndex = photoIndex,
                    onClose = onClose,
                    onAvatarClick = {
                        when {
                            !hasPhoto -> Unit
                            // Yig'ilgan holatda bosish — yoyadi; yoyilganda — to'liq ekran.
                            progress < 1f -> snapTo(maxPx)
                            else -> viewer = AVATAR_VIEWER
                        }
                    },
                    // Telegramdagidek: yoyilgan rasmning chap/o'ng yarmiga tegish keyingi
                    // rasmga o'tkazadi. Surish (`swipe`) ishlatilmadi — sarlavhaning o'zi
                    // vertikal `nestedScroll` da yashaydi va ikkala jest to'qnashardi.
                    onStep = { forward ->
                        if (profilePhotos.size > 1) {
                            photoIndex = (photoIndex + if (forward) 1 else profilePhotos.size - 1) %
                                profilePhotos.size
                        }
                    },
                )

                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Spacer(Modifier.height(2.dp))

                    // --- To'rtta amal tugmasi -----------------------------------------
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
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

                    // --- Ma'lumotlar ---------------------------------------------------
                    InfoCard {
                        student.username?.takeIf { it.isNotBlank() }?.let {
                            InfoRow("@$it", "Foydalanuvchi nomi")
                        }
                        // ⚠️ `null` bo'lsa qator UMUMAN chizilmaydi: telefon sukut bo'yicha
                        // `NOBODY` (ya'ni ko'pchilikda yo'q) va bo'sh "Telefon: —" qatori
                        // foyda bermaydi (`handoff/08-PROFILE.md` §3).
                        student.phoneNumber?.let { InfoRow(it, "Mobil raqam") }
                        // Bio'da havola/raqam serverda rad etiladi — oddiy matn sifatida
                        // chizamiz, link detection kerak emas.
                        student.bio?.let { InfoRow(it, "Tarjimayi hol") }
                        universityName?.let { InfoRow(it, "Universitet") }
                        student.courseYear?.let { InfoRow(it.courseLabel(), "Kurs") }
                        student.gender?.let {
                            InfoRow(if (it == Gender.MALE) "Erkak" else "Ayol", "Jinsi")
                        }
                    }

                    // --- Bo'limlar -----------------------------------------------------
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
                        // Ikkalasi ham backendga bog'liq — `STORY_AND_PROFILE_BACKEND.md`.
                        ProfileTab.FILES -> if (files.isEmpty()) {
                            EmptySection("Bu suhbatda fayl yuborilmagan")
                        } else {
                            FileList(files, onOpen = onOpenFile)
                        }
                    }

                    // --- Amallar -------------------------------------------------------
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Sc.Card)) {
                        ProfileAction(ScIcons.Close, "Bog'lanishni uzish", onClick = onDisconnect)
                        ProfileAction(ScIcons.Users, "Bloklash", danger = true, onClick = onBlock)
                        ProfileAction(ScIcons.Bell, "Shikoyat qilish", danger = true, onClick = onReport)
                    }
                    Spacer(Modifier.height(8.dp).navigationBarsPadding())
                }
            }
        }
    }

    val openIndex = viewer
    if (openIndex == AVATAR_VIEWER) {
        ImageViewerDialog(
            images = profilePhotos.mapIndexed { index, url -> ChatMediaItem("photo-$index", url, null, null) },
            startIndex = photoIndex.coerceIn(0, (profilePhotos.size - 1).coerceAtLeast(0)),
            onDismiss = { viewer = null },
        )
    } else if (openIndex != null) {
        ImageViewerDialog(photos, openIndex, onDismiss = { viewer = null })
    }
}

// ---------------------------------------------------------------------------
// Sarlavha — yig'iluvchi (collapsing)
// ---------------------------------------------------------------------------

/**
 * Bitta sarlavha, ikki chekka holat orasida **uzluksiz** o'zgaradi:
 *
 * - `progress = 0` — brend gradienti, markazda 96 dp li dumaloq avatar, ostida ism;
 * - `progress = 1` — rasm butun kenglikni egallaydi, ustida pastdan qorayadigan gradient
 *   va chap pastda ism.
 *
 * Avatar **o'sib boradi**: o'lchami va burchak radiusi oralatib hisoblanadi, shuning
 * uchun doira asta-sekin to'rtburchak rasmga aylanadi. Matnlar esa bir-birini almashtiradi
 * (shaffoflik bo'yicha) — ikki xil joylashuvni piksel bo'yicha oralatishdan ko'ra
 * ishonchliroq va sakramaydi.
 */
@Composable
private fun ProfileHeader(
    name: String,
    status: String,
    avatarUrl: String?,
    height: Dp,
    progress: Float,
    photoCount: Int,
    photoIndex: Int,
    onClose: () -> Unit,
    onAvatarClick: () -> Unit,
    onStep: (forward: Boolean) -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(height)) {
        // Brend gradienti — rasm yoyilgani sari so'nadi.
        Box(Modifier.fillMaxSize().alpha(1f - progress).background(Sc.headerBrush))

        // Avatar: markazdagi doiradan butun sarlavhagacha.
        val avatarSize = lerp(COLLAPSED_AVATAR, height, progress)
        val corner = lerp(COLLAPSED_AVATAR / 2, 0.dp, progress)
        Box(
            Modifier.align(Alignment.TopCenter)
                .padding(top = lerp(COLLAPSED_AVATAR_TOP, 0.dp, progress))
                .size(avatarSize)
                .clip(RoundedCornerShape(corner))
                .background(Color.White.copy(alpha = 0.9f))
                .clickable(onClick = onAvatarClick),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarUrl.isNullOrBlank()) {
                ScText(name.take(1).uppercase(), 34f, FontWeight.ExtraBold, Sc.Violet)
            } else {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "Profil rasmi",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Yoyilgan rasmda chap/o'ng tegish zonalari — Telegramdagidek rasmlar orasida
        // yurish. Faqat bir nechta rasm bo'lganda va faqat to'liq yoyilganda: yig'ilgan
        // holatda avatar kichkina va bosish uni yoyishi kerak (`onAvatarClick`).
        if (photoCount > 1 && progress > 0.99f) {
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
            if (photoCount > 1) {
                PhotoDashes(count = photoCount, current = photoIndex, alpha = progress)
                Spacer(Modifier.height(8.dp))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlassButton(ScIcons.ChevronLeft, "Orqaga", Color.White, onClose)
            }
        }

        // Yig'ilgan holatdagi ism — markazda, avatar ostida.
        if (progress < 1f) {
            Column(
                Modifier.align(Alignment.BottomCenter)
                    .alpha(1f - progress)
                    .padding(start = 16.dp, end = 16.dp, bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ScText(name, 21f, FontWeight.ExtraBold, Color.White, letterSpacing = -0.3f, maxLines = 2)
                Spacer(Modifier.height(3.dp))
                ScText(status, 13.5f, FontWeight.Medium, Color.White.copy(alpha = 0.85f))
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
                ScText(status, 14f, FontWeight.Medium, Color.White.copy(alpha = 0.85f))
            }
        }
    }
}

/** Telegram'dagidek: rasm soniga qarab yuqoridagi chiziqchalar. */
@Composable
private fun PhotoDashes(count: Int, current: Int, alpha: Float) {
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
private fun PhotoGrid(photos: List<ChatMediaItem>, onOpen: (Int) -> Unit) {
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

/**
 * «Fayllar» bo'limi — chatdagi `FILE` xabarlari.
 *
 * Ovoz va video bu yerga **kirmaydi**: ular alohida turlar va ro'yxatda butunlay boshqacha
 * ko'rinishi kerak (to'lqin, poster) — bitta qatorga tiqishtirish faqat chalkashtirardi.
 */
@Composable
private fun FileList(files: List<ChatMessageUi>, onOpen: (ChatMessageUi) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Sc.Card)) {
        files.forEach { message ->
            val file = message.attachment ?: return@forEach
            Row(
                Modifier.fillMaxWidth()
                    .clickable { onOpen(message) }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(Sc.TintBlue),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(ScIcons.Paperclip, null, tint = Sc.Brand, modifier = Modifier.size(17.dp))
                }
                Column(Modifier.weight(1f)) {
                    ScText(file.fileName ?: "Fayl", 14f, FontWeight.Bold, Sc.Ink, maxLines = 1)
                    val size = ChatFormat.fileSize(file.sizeBytes)
                    if (size.isNotEmpty()) {
                        ScText(size, 11.5f, FontWeight.Medium, Sc.Muted, maxLines = 1)
                    }
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

/** Jest tasodifiy tegishdan ishlab ketmasin — shu piksellardan keyin hisobga olinadi. */
/** Yig'ilgan sarlavha balandligi (avatar + ism + holat sig'adigan eng kichik o'lcham). */
private val COLLAPSED_HEADER = 250.dp

/** Yig'ilgan holatdagi avatar diametri va uning tepadan chekinishi. */
private val COLLAPSED_AVATAR = 96.dp
private val COLLAPSED_AVATAR_TOP = 62.dp

/** Barmoq ko'tarilganda shu ulushdan oshgan sarlavha to'liq ochiladi, aks holda yopiladi. */
private const val SNAP_POINT = 0.4f
private const val SNAP_DURATION_MS = 220

private const val PHOTO_COLUMNS = 3
private val PHOTO_CELL = 108.dp
private val PHOTO_GAP = 4.dp
