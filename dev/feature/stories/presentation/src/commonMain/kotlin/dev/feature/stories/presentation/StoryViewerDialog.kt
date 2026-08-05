package dev.feature.stories.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.core.uikit.components.ScNetworkImage
import dev.core.uikit.media.ScVideoPlayer
import dev.core.uikit.components.ScAvatar
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScText
import dev.core.uikit.components.StatusBarAppearance
import dev.core.uikit.theme.Sc
import dev.feature.stories.domain.model.Story
import dev.feature.stories.domain.model.StoryKind

/**
 * Hikoya ko'ruvchisi — to'liq ekran, tepada progress chiziqlari.
 *
 * Boshqaruv Instagram/Telegram bilan bir xil: **o'ng yarmiga tegish** — keyingi,
 * **chap yarmiga** — oldingi, **ushlab turish** — pauza. Rasm [Story.DEFAULT_IMEGE_MS]
 * emas, [Story.displayMs] bo'yicha turadi (video davomiyligi serverdan keladi).
 *
 * Video haqiqiy pleyerda o'ynaydi ([ScVideoPlayer]) va tugagach o'zi keyingisiga o'tadi;
 * rasm esa [Story.displayMs] (odatda 5 s) davomida turadi.
 *
 * ⚠️ Media havolalari **token bilan** so'raladi (§11.2): rasm uchun buni ilovaning rasm
 * klienti o'zi qo'yadi, video pleyeriga esa sarlavhalar qo'lda uzatiladi.
 */
@Composable
internal fun StoryViewerDialog(
    state: StoryViewerState,
    /** `Authorization: Bearer …` — video pleyeri uchun. */
    mediaHeaders: Map<String, String>,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
    onDelete: (String) -> Unit,
    /**
     * Muallif ustiga bosildi — uning profili ochiladi (Telegram/Instagramdagidek).
     *
     * `null` — bosish o'chirilgan: o'z hikoyangizda ochadigan profil yo'q.
     */
    onOpenAuthor: ((String) -> Unit)? = null,
) {
    val group = state.group ?: return
    val story = state.story ?: return
    /** Barmoq ekranda ushlab turilibdi ([StoryTapZone]). */
    var held by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Hikoya almashsa ro'yxat yopiladi — u aynan shu postniki.
    var viewersOpen by remember(story.id) { mutableStateOf(false) }
    val isVideo = story.kind == StoryKind.VIDEO

    // Ustiga oyna chiqqanda hikoya o'zi keyingisiga o'tib ketmasin: o'chirishni tasdiqlash
    // ham, ko'rganlar ro'yxati ham ushlab turish bilan bir xil — vaqt to'xtaydi.
    val paused = held || confirmDelete || viewersOpen

    /**
     * Tepadagi chiziqning to'lish ulushi (`0f..1f`) — **rasm uchun**.
     *
     * `Animatable` (oddiy `animateFloatAsState` emas): pauzada chiziq **turgan joyida
     * qoladi** va qo'yib yuborilganda o'sha yerdan davom etadi. Avvalgi variant pauzada
     * nishonni `0f` ga tushirib, chiziqni boshiga qaytarardi.
     */
    val imageProgress = remember(story.id) { Animatable(0f) }

    /** Videoda chiziq **pleyerdan** kelgan pozitsiya bo'yicha to'ladi (taymer bo'yicha emas). */
    var videoPositionMs by remember(story.id) { mutableStateOf(0L) }
    var videoDurationMs by remember(story.id) { mutableStateOf(0L) }

    // Rasm — [Story.DEFAULT_IMAGE_MS] (5 s) davomida turadi va chiziq shu vaqt ichida
    // to'ladi; to'lgach o'zi keyingisiga o'tadi.
    //
    // ⚠️ Videoda bu ishlamaydi: uni pleyerning o'zi tugatadi (`onEnded`). Ikkalasi birga
    // ishlasa hikoya davomiylikdan oldin sakrab ketardi.
    LaunchedEffect(story.id, paused, isVideo) {
        if (isVideo || paused) return@LaunchedEffect
        // Qolgan vaqt — pauzadan keyin ham to'liq 5 soniya kutib qolmaslik uchun.
        val remaining = ((1f - imageProgress.value) * story.displayMs).toInt().coerceAtLeast(0)
        imageProgress.animateTo(1f, tween(durationMillis = remaining, easing = LinearEasing))
        onNext()
    }

    val progress = when {
        isVideo && videoDurationMs > 0 -> (videoPositionMs.toFloat() / videoDurationMs).coerceIn(0f, 1f)
        // Video hali metadatani o'qimadi — chiziq bo'sh turadi (noto'g'ri to'lgandan ko'ra).
        isVideo -> 0f
        else -> imageProgress.value
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        StatusBarAppearance(darkIcons = false)
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (story.mediaPurged && story.localUri == null) {
                // Arxivda bir yillik saqlash muddati o'tgan — fayl serverda yo'q
                // (`url` → 404). Pleyer/rasm yuklovchisiga bermaymiz: ekran jimgina qora
                // qolib, foydalanuvchi sababini bilmasdi.
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    ScText(
                        "Bu hikoyaning fayli saqlanmagan — arxivda faqat yozuvi qoldi.",
                        14f,
                        FontWeight.Medium,
                        Color.White,
                    )
                }
            } else if (isVideo) {
                ScVideoPlayer(
                    // Telefondagi nusxa bo'lsa — o'sha: tarmoq ham, kutish ham yo'q.
                    url = story.displayUrl,
                    // ⚠️ Story medialari **token bilan** so'raladi (§11.2) — faqat muallif va
                    // unga bog'langan odam o'qiy oladi, ya'ni tokensiz pleyer `404` olardi.
                    headers = mediaHeaders,
                    // Hikoya o'zi o'ynaydi va boshqaruv paneli ko'rsatilmaydi: bu Instagram
                    // uslubidagi ekran, u yerda pauza/tugma emas, ekranni ushlab turish bilan
                    // boshqariladi — shuning uchun ijro `paused` ga bog'langan.
                    autoPlay = !paused,
                    showControls = false,
                    // ⚠️ 9:16 majburlanmaydi (§1) — boshqa nisbat ham keladi va "fit" qilinadi.
                    contentScaleFit = true,
                    onEnded = onNext,
                    // Tepadagi chiziq aynan shu bo'yicha to'ladi: video buferlansa u ham
                    // to'xtab turadi, ya'ni chiziq kadrdan oldinga ketmaydi.
                    onProgress = { position, duration ->
                        videoPositionMs = position
                        if (duration > 0) videoDurationMs = duration
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                ScNetworkImage(
                    url = story.displayUrl,
                    contentDescription = story.caption,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Tegish zonalari — eng pastda, ustidagi tugmalar ularni to'sadi.
            //
            // Telegram/Instagram boshqaruvi: chap yarmi — oldingi, o'ng yarmi — keyingi,
            // **ushlab turish** esa pauza (rasmda chiziq to'xtaydi, videoda ijro ham).
            Row(Modifier.fillMaxSize()) {
                StoryTapZone(
                    Modifier.weight(1f),
                    onHold = { held = it },
                    onTap = onPrevious,
                )
                StoryTapZone(
                    Modifier.weight(1f),
                    onHold = { held = it },
                    onTap = onNext,
                )
            }

            // Tepadagi gradient — progress va ism har qanday rasmda o'qilsin.
            Box(
                Modifier.fillMaxWidth().height(140.dp).background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)),
                ),
            )

            Column(Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                StoryProgress(
                    count = group.stories.size,
                    current = state.index,
                    progress = progress,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Avatar va ism — bitta bosiladigan bo'lak: muallifning profili
                    // ochiladi. Ular tepadagi qatorda, ya'ni hikoyani surish zonalaridan
                    // ([StoryTapZone]) tashqarida — bosish keyingi hikoyaga o'tkazmaydi.
                    Row(
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(percent = 50))
                            .then(
                                if (onOpenAuthor == null) {
                                    Modifier
                                } else {
                                    Modifier.clickable { onOpenAuthor(group.author.id) }
                                },
                            )
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ScAvatar(
                            name = group.author.displayName,
                            size = 32.dp,
                            avatarUrl = group.author.avatarUrl,
                        )
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            ScText(group.author.displayName, 13.5f, FontWeight.Bold, Color.White, maxLines = 1)
                            // O'z hikoyamda ko'rishlar soni bor; boshqalarnikida u ATAYLAB `null`.
                            //
                            // Bosilsa — kim ko'rgani ([StoryViewersSheet]). Ro'yxat arxivdagi
                            // post uchun ham ochiladi: son muzlagan bo'lsa ham qatorlar joyida.
                            story.viewsCount?.let {
                                ScText(
                                    "$it marta ko'rilgan",
                                    11f,
                                    FontWeight.Medium,
                                    Color.White.copy(alpha = 0.75f),
                                    Modifier.clickable { viewersOpen = true },
                                )
                            }
                        }
                    }
                    story.viewsCount?.let {
                        // Ko'rishlar soni faqat muallifda bor — ya'ni bu **mening** hikoyam.
                        // Loyihada alohida "trash" ikonkasi yo'q — chatda ham o'chirish
                        // `Close` bilan ko'rsatiladi (`ChatScreen`dagi ActionRow).
                        ScText(
                            "O'chirish",
                            12f,
                            FontWeight.Bold,
                            Color.White,
                            Modifier.clickable { confirmDelete = true },
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(14.dp))
                    }
                    Icon(
                        ScIcons.Close,
                        "Yopish",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp).clickable(onClick = onClose),
                    )
                }
            }

            story.caption?.let { caption ->
                Box(
                    Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 22.dp),
                ) {
                    ScText(caption, 14f, FontWeight.Medium, Color.White, maxLines = 4)
                }
            }

            // Kim ko'rgani — o'z postimda, ko'rishlar soni bosilganda. Tegish zonalarining
            // ustida turadi, ya'ni ro'yxat ochiqda ekranga tegish hikoyani surmaydi.
            if (viewersOpen) {
                StoryViewersSheet(storyId = story.id, onClose = { viewersOpen = false })
            }

            if (confirmDelete) {
                DeleteConfirm(
                    onCancel = { confirmDelete = false },
                    onConfirm = { confirmDelete = false; onDelete(story.id) },
                )
            }
        }
    }
}

/**
 * Tepadagi bo'lakli oq chiziq — har hikoya uchun bittadan.
 *
 * Ko'rilganlari to'liq, keyingilari bo'sh, **faol bo'lagi** esa [progress] bo'yicha to'ladi.
 * Vaqtni bu komponent o'zi sanamaydi: rasmda uni animatsiya, videoda esa **pleyerning
 * pozitsiyasi** boshqaradi — ikkalasi ham chaqiruvchida hisoblanadi va bir xil `0f..1f` ga
 * keltiriladi.
 */
@Composable
private fun StoryProgress(count: Int, current: Int, progress: Float) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(count) { index ->
            val fraction = when {
                index < current -> 1f
                index > current -> 0f
                else -> progress.coerceIn(0f, 1f)
            }
            Box(
                Modifier.weight(1f).height(2.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.35f)),
            ) {
                Box(
                    Modifier.fillMaxWidth(fraction).fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White),
                )
            }
        }
    }
}

/**
 * Ekranning yarmi — bosilsa o'tish, **ushlab turilsa pauza**.
 *
 * `clickable` yaramaydi: u faqat bosib-qo'yib yuborishni biladi, "barmoq turgan vaqt"
 * haqida hech nima demaydi. `detectTapGestures` esa bosishning boshi va oxirini alohida
 * beradi — pauza aynan shu oraliqda.
 */
@Composable
private fun StoryTapZone(modifier: Modifier, onHold: (Boolean) -> Unit, onTap: () -> Unit) {
    Box(
        modifier.fillMaxHeight().pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    onHold(true)
                    // Barmoq ko'tarilgunicha (yoki imo-ishora bekor bo'lgunicha) kutamiz.
                    tryAwaitRelease()
                    onHold(false)
                },
                onTap = { onTap() },
            )
        },
    )
}

@Composable
private fun DeleteConfirm(onCancel: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onCancel) {
        Column(
            Modifier.clip(RoundedCornerShape(20.dp)).background(Sc.Card).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScText("Hikoyani o'chirasizmi?", 16f, FontWeight.ExtraBold, Sc.Ink)
            // Story TAHRIRLANMAYDI — o'chirib, qaytadan qo'yiladi (§7).
            ScText(
                "Hikoya darhol yo'qoladi. Tahrirlash imkoni yo'q — o'chirib, qaytadan qo'yish kerak.",
                13f,
                FontWeight.Medium,
                Sc.Muted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ScText(
                    "Bekor qilish", 14f, FontWeight.Bold, Sc.Muted,
                    Modifier.weight(1f).clickable(onClick = onCancel).padding(vertical = 8.dp),
                )
                ScText(
                    "O'chirish", 14f, FontWeight.ExtraBold, Sc.Danger,
                    Modifier.weight(1f).clickable(onClick = onConfirm).padding(vertical = 8.dp),
                )
            }
        }
    }
}
