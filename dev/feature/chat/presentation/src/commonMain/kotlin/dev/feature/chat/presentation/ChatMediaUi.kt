package dev.feature.chat.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import dev.core.uikit.components.AppIcons
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScText
import dev.core.uikit.components.ScUploadOverlay
import dev.core.uikit.components.ScUploadRing
import dev.core.uikit.components.StatusBarAppearance
import dev.core.uikit.components.scUploadPercent
import dev.core.uikit.media.ScVideoPlayer
import dev.core.uikit.media.toImageBitmapOrNull
import kotlinx.coroutines.launch
import dev.core.uikit.theme.Sc
import dev.core.uikit.components.scShimmerSweep
import dev.feature.chat.domain.model.MessageStatus
import dev.feature.chat.domain.model.MessageType

/** Pufakning eng katta kengligi — matnli xabar bilan bir xil. */
private val BUBBLE_MAX_WIDTH = 280.dp

/** To'rdagi kataklar orasidagi ajratgich. */
private val GRID_GAP = 3.dp

// ---------------------------------------------------------------------------
// Umumiy: vaqt + yetkazildi/o'qildi belgichalari
// ---------------------------------------------------------------------------

/**
 * Xabarning pastki qatori: soat va chiquvchi xabarda belgichalar.
 *
 * [onDark] — gradient pufak ustidami (oq matn) yoki och fondami (xira matn). Rasm va
 * stiker pufaklarida fon och, shuning uchun ular `false` beradi.
 */
@Composable
internal fun MessageMeta(message: ChatMessageUi, modifier: Modifier = Modifier, onDark: Boolean) {
    val failed = message.status == MessageStatus.FAILED
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val timeColor = when {
            onDark -> Color.White.copy(alpha = 0.85f)
            else -> Sc.MutedLight
        }
        if (failed) {
            ScText(
                "yuborilmadi · qayta urinish", 11f, FontWeight.SemiBold,
                if (onDark) Color.White else Sc.Danger,
            )
        } else {
            ScText(message.time, 11f, FontWeight.SemiBold, timeColor)
        }
        if (message.outgoing && !failed) {
            // Bitta belgicha — server qabul qildi; ikkita — suhbatdoshning qurilmasiga
            // yetdi; yorqin ikkita — o'qildi.
            val doubleCheck = message.status != MessageStatus.SENDING && message.delivered
            val tint = if (onDark) Color.White else Sc.Brand
            Icon(
                if (doubleCheck) ScIcons.DoubleCheck else AppIcons.Check,
                null,
                tint = tint.copy(alpha = if (message.read) 1f else 0.55f),
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Rasm(lar)
// ---------------------------------------------------------------------------

/**
 * Rasm pufagi — yakka rasm yoki albom to'ri.
 *
 * Joylashuv Telegram'nikiga yaqin: bitta rasm o'z nisbatida, ikkitadan ko'p bo'lsa ikki
 * ustunli kvadrat kataklar, toq qolgani esa oxirgi qatorni **to'liq** egallaydi.
 */
@Composable
internal fun ImageAlbumBubble(
    message: ChatMessageUi,
    onOpen: (Int) -> Unit,
    /**
     * Pufakning **bo'sh joyi** bosildi (kataklar orasi, meta qatori).
     *
     * Uzun bosish bu yerda emas — u qatorning o'zida (`ChatScreen`), chunki barmoqni surib
     * belgilash bitta uzluksiz imo-ishora bo'lishi kerak. Pufak `combinedClickable` bilan
     * uzun bosishni **o'ziga olib qo'yardi** va surish boshlanmasdi.
     */
    onTap: () -> Unit,
) {
    val images = message.images
    if (images.isEmpty()) return

    val align = if (message.outgoing) Alignment.CenterEnd else Alignment.CenterStart
    Box(Modifier.fillMaxWidth(), contentAlignment = align) {
        Column(
            Modifier.width(BUBBLE_MAX_WIDTH)
                .clip(RoundedCornerShape(18.dp))
                .background(if (message.outgoing) Sc.Brand.copy(alpha = 0.12f) else Sc.Card)
                .clickable(onClick = onTap)
                .padding(3.dp),
            verticalArrangement = Arrangement.spacedBy(GRID_GAP),
        ) {
            if (images.size == 1) {
                ImageCell(
                    image = images[0],
                    onClick = { onOpen(0) },
                    modifier = Modifier.fillMaxWidth()
                        // Nisbat noma'lum bo'lsa (server o'lcham qaytarmaydi) — 4:3.
                        .aspectRatio(images[0].aspectRatio ?: DEFAULT_ASPECT),
                )
            } else {
                // Ikkitadan qator. Indeks QO'LDA hisoblanadi: `indexOf` ishlatib bo'lmaydi,
                // chunki `ChatMediaItem` ichida `ByteArray` bor va tenglik ishonchsiz.
                var index = 0
                while (index < images.size) {
                    val first = index
                    val second = index + 1
                    if (second < images.size) {
                        Row(horizontalArrangement = Arrangement.spacedBy(GRID_GAP)) {
                            ImageCell(
                                image = images[first],
                                onClick = { onOpen(first) },
                                modifier = Modifier.weight(1f).aspectRatio(1f),
                            )
                            ImageCell(
                                image = images[second],
                                onClick = { onOpen(second) },
                                modifier = Modifier.weight(1f).aspectRatio(1f),
                            )
                        }
                        index += 2
                    } else {
                        // Toq qolgan oxirgisi — keng va past katak.
                        ImageCell(
                            image = images[first],
                            onClick = { onOpen(first) },
                            modifier = Modifier.fillMaxWidth().aspectRatio(2f),
                        )
                        index += 1
                    }
                }
            }

            MessageMeta(
                message = message,
                modifier = Modifier.align(Alignment.End).padding(horizontal = 6.dp, vertical = 2.dp),
                onDark = false,
            )
        }
    }
}

/**
 * Bitta katak. Yuklanayotgan rasmda serverda havola yo'q — tanlangan faylning **local
 * nusxasi** ko'rsatiladi va ustidan shimmer to'lqini yuradi.
 */
@Composable
private fun ImageCell(image: ChatMediaItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(14.dp))
            .background(Sc.Chip)
            .clickable(enabled = !image.loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val bytes = image.localBytes
        when {
            image.url != null -> AsyncImage(
                model = image.url,
                contentDescription = "Rasm",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // `remember(bytes)` — dekodlash har qayta chizilishda takrorlanmasin.
            bytes != null -> {
                val bitmap = remember(bytes) { bytes.toImageBitmapOrNull() }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Rasm",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        // Yuklanayotganda: local nusxa ko'rinib turadi, ustidan foiz halqasi.
        //
        // Halqa faqat fayl HAQIQATAN ketayotganda ([ChatMediaItem.uploading]) chiziladi.
        // Yuborish yiqilgan yoki navbatda turgan rasmda (albom ketma-ket yuklanadi) foiz
        // yo'q — o'sha yerda eski shimmer qoladi, aks holda `0%` da qotib turgan halqa
        // "osilib qoldi" degan taassurot berardi.
        when {
            image.uploading -> ScUploadOverlay(
                progress = image.uploadProgress,
                shape = RoundedCornerShape(14.dp),
                ringSize = UPLOAD_RING,
            )
            image.loading -> Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.22f))
                    .scShimmerSweep(RoundedCornerShape(14.dp)),
            )
            // Video katagi — poster ustida o'ynatish belgisi va davomiyligi (Telegramdagidek).
            // Katak rasmnikidan farq qilmaydi: albom aralash bo'lganda to'r yaxlit ko'rinsin.
            image.video -> VideoCellOverlay(image)
        }
    }
}

/** Video katagining ustki qatlami: o'ynatish belgisi, davomiyligi yoki «tayyorlanmoqda». */
@Composable
private fun BoxScope.VideoCellOverlay(item: ChatMediaItem) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        if (item.processing) {
            // Server videoni hali transkod qilmoqda — poster bor, o'zi hali yo'q.
            Box(
                Modifier.clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) { ScText("Tayyorlanmoqda…", 11.5f, FontWeight.Bold, Color.White) }
        } else {
            Box(
                Modifier.size(PLAY_BADGE)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.Black.copy(alpha = 0.42f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    ScIcons.ChevronRight,
                    "O'ynatish",
                    tint = Color.White,
                    modifier = Modifier.size(PLAY_ICON),
                )
            }
        }
    }
    if (item.durationMs > 0 && !item.processing) {
        Box(
            Modifier.align(Alignment.TopStart)
                .padding(6.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            ScText(ChatFormat.duration(item.durationMs), 10.5f, FontWeight.Bold, Color.White)
        }
    }
}

private val PLAY_BADGE = 42.dp
private val PLAY_ICON = 20.dp

/** Albom katagidagi halqa — kichik kataklarga ham sig'sin. */
private val UPLOAD_RING = 44.dp

// ---------------------------------------------------------------------------
// Stiker
// ---------------------------------------------------------------------------

/**
 * Stiker tasviri — yuklanmasa **emojining o'ziga** qaytadi.
 *
 * Zaxira katalogning tasvirlari CDN'da yotadi (`FluentEmoji`), ilovaga kiritilmagan. Tarmoq
 * yo'q yoki CDN berkitilgan bo'lsa ular kelmaydi va bunda stiker **yo'qolmasligi** kerak:
 * xabar tanasi baribir emojining o'zi, ya'ni tizim emojisi to'g'ri va to'liq zaxira.
 *
 * [modifier] faqat rasmga tegishli — emoji varianti matn sifatida o'z o'lchamida chiziladi.
 */
@Composable
internal fun StickerImage(
    emoji: String,
    url: String?,
    fallbackSize: Float,
    modifier: Modifier = Modifier,
) {
    // `url` bo'yicha kalitlangan: ro'yxatdagi katak boshqa stikerga qayta ishlatilganda
    // oldingisining xatosi yangisiga o'tib qolmasin.
    var failed by remember(url) { mutableStateOf(false) }
    if (url == null || failed) {
        ScText(emoji, fallbackSize, FontWeight.Normal, Sc.Ink)
        return
    }
    AsyncImage(
        model = url,
        contentDescription = emoji,
        contentScale = ContentScale.Fit,
        onState = { state -> if (state is AsyncImagePainter.State.Error) failed = true },
        modifier = modifier,
    )
}

/**
 * Stiker — **pufaksiz**. Vaqt va belgichalar pastida, foni yo'q (Telegram/WhatsApp'dagi kabi).
 *
 * Tasvir server katalogidan yoki Fluent CDN'idan keladi; ikkalasi ham bo'lmasa emojining
 * o'zi katta qilib chiziladi ([StickerImage]).
 */
@Composable
internal fun StickerBubble(message: ChatMessageUi, onTap: () -> Unit) {
    val align = if (message.outgoing) Alignment.CenterEnd else Alignment.CenterStart
    Box(Modifier.fillMaxWidth(), contentAlignment = align) {
        Column(
            Modifier.clickable(onClick = onTap),
            horizontalAlignment = if (message.outgoing) Alignment.End else Alignment.Start,
        ) {
            StickerImage(
                emoji = message.sticker.orEmpty(),
                url = message.stickerUrl,
                fallbackSize = STICKER_SIZE,
                modifier = Modifier.size(STICKER_IMAGE_SIZE),
            )
            MessageMeta(message, Modifier.padding(top = 2.dp), onDark = false)
        }
    }
}

// Eski `StickerPanel` (ilovaga kiritilgan emoji katalogi) olib tashlandi: uning o'rnini
// `gif/ChatMediaPanel` egalladi — u serverdagi paketlarni (`GET /v1/stickers/packs`)
// ko'rsatadi va katalog bo'sh/xato bo'lsa o'sha zaxira emoji katalogiga qaytadi.

// ---------------------------------------------------------------------------
// To'liq ekranli ko'rgich
// ---------------------------------------------------------------------------

/**
 * Media ko'rgichi — **rasm ham, video ham** shu yerda ochiladi (Telegramdagidek).
 *
 * Tuzilishi: tepada muallif va sana, o'rtada surib o'tiladigan media, pastda esa boshqa
 * medialarning tasmasi. Video haqiqiy pleyerda ([ScVideoPlayer]) tizimning **o'z boshqaruv
 * paneli** bilan o'ynaydi — ko'chirgich, vaqt va pauza shundan keladi; ularni qo'lda
 * chizsak ikkala platformada ham "deyarli o'xshash, lekin boshqacha" bo'lib qolardi.
 *
 * ⚠️ Bir vaqtda **faqat ochiq turgan** sahifadagi video o'ynaydi: ExoPlayer/AVPlayer og'ir
 * resurs (dekoder, bufer, tarmoq) va tasmadagi har sahifa uchun bittadan pleyer ochilsa
 * qurilma qotib qolardi.
 */
@Composable
internal fun MediaViewerDialog(
    items: List<ChatMediaItem>,
    startIndex: Int,
    /** `Authorization: Bearer …` — video pleyeri uchun (havola himoyalangan). */
    mediaHeaders: Map<String, String> = emptyMap(),
    /** Tepadagi sarlavha — kim yuborgani. */
    title: String? = null,
    /** Sarlavha ostidagi qator — qachon yuborilgani. */
    subtitle: String? = null,
    onDismiss: () -> Unit,
) {
    if (items.isEmpty()) return
    val pager = rememberPagerState(
        initialPage = startIndex.coerceIn(items.indices),
        pageCount = { items.size },
    )
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        // Tizimning odatiy kengligi olib tashlanadi — media butun ekranni egallasin.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        StatusBarAppearance(darkIcons = false)
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                val item = items[page]
                val url = item.fullUrl ?: item.url
                when {
                    item.video && url != null -> ScVideoPlayer(
                        url = url,
                        headers = mediaHeaders,
                        // Faqat ochiq sahifa o'ynaydi — qolganlari pauzada turadi.
                        autoPlay = pager.currentPage == page,
                        showControls = true,
                        contentScaleFit = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                    url != null -> AsyncImage(
                        model = url,
                        contentDescription = "Rasm",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(vertical = 12.dp),
                    )
                    item.localBytes != null -> {
                        val bytes = item.localBytes
                        val bitmap = remember(bytes) { bytes.toImageBitmapOrNull() }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "Rasm",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            }

            // Tepadagi qatlam: gradient + orqaga, muallif/sana va sanoq.
            Column(
                Modifier.fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent),
                        ),
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            ScIcons.ChevronLeft,
                            "Yopish",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        if (title != null) {
                            ScText(title, 15f, FontWeight.Bold, Color.White, maxLines = 1)
                        }
                        if (subtitle != null) {
                            ScText(
                                subtitle,
                                12f,
                                FontWeight.Medium,
                                Color.White.copy(alpha = 0.75f),
                                maxLines = 1,
                            )
                        }
                    }
                    if (items.size > 1) {
                        Box(
                            Modifier.clip(RoundedCornerShape(20.dp))
                                .background(Color.Black.copy(alpha = 0.45f))
                                .padding(horizontal = 11.dp, vertical = 4.dp),
                        ) {
                            ScText(
                                "${pager.currentPage + 1} / ${items.size}",
                                12.5f,
                                FontWeight.Bold,
                                Color.White,
                            )
                        }
                    }
                }
            }

            // Pastdagi tasma — albomdagi qolgan medialar; bosilgani darhol ochiladi.
            if (items.size > 1) {
                LazyRow(
                    Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        // Video boshqaruv paneli pastda turadi — tasma uning ustiga chiqadi.
                        .padding(bottom = STRIP_BOTTOM_PADDING),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(items) { index, item ->
                        StripThumb(
                            item = item,
                            selected = index == pager.currentPage,
                            onClick = { scope.launch { pager.animateScrollToPage(index) } },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Eski nom — faqat rasm ko'rgichi sifatida chaqirilgan joylar uchun.
 *
 * Ichida aynan [MediaViewerDialog] ishlaydi: rasm bilan video bitta ko'rgichda ochilishi
 * kerak, aks holda albom aralash bo'lganda surib o'tish video ustida uzilib qolardi.
 */
@Composable
internal fun ImageViewerDialog(
    images: List<ChatMediaItem>,
    startIndex: Int,
    mediaHeaders: Map<String, String> = emptyMap(),
    onDismiss: () -> Unit,
) = MediaViewerDialog(
    items = images,
    startIndex = startIndex,
    mediaHeaders = mediaHeaders,
    onDismiss = onDismiss,
)

/** Pastdagi tasmadagi bitta kichik nusxa. Tanlangani oq ramka bilan ajratiladi. */
@Composable
private fun StripThumb(item: ChatMediaItem, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(STRIP_THUMB)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .then(
                if (selected) {
                    Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (item.url != null) {
            AsyncImage(
                model = item.url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (item.video) {
            Icon(
                ScIcons.ChevronRight,
                null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Tasmadagi kvadratcha va uning pleyer boshqaruvidan yuqorida turishi. */
private val STRIP_THUMB = 54.dp
private val STRIP_BOTTOM_PADDING = 74.dp

private const val STICKER_SIZE = 58f

/** Rasmli stiker — emoji varianti bilan taxminan bir xil ko'rinishda bo'lsin. */
private val STICKER_IMAGE_SIZE = 120.dp

private const val DEFAULT_ASPECT = 4f / 3f

// ---------------------------------------------------------------------------
// Rasm bo'lmagan biriktirmalar — fayl, ovoz, video
// ---------------------------------------------------------------------------

/**
 * Fayl pufagi — ikonka, nom va hajm.
 *
 * Havola **token bilan** ochiladi (`GET /v1/media/{id}/raw` suhbat a'zoligini tekshiradi),
 * shuning uchun bosish tashqi brauzerga emas, ilova ichidagi yuklab olishga bog'lanadi
 * ([onOpen] chaqiruvchining ishi).
 */
@Composable
internal fun FileBubble(message: ChatMessageUi, onOpen: () -> Unit) {
    val file = message.attachment ?: return
    val align = if (message.outgoing) Alignment.CenterEnd else Alignment.CenterStart
    Box(Modifier.fillMaxWidth(), contentAlignment = align) {
        Row(
            Modifier.width(BUBBLE_MAX_WIDTH)
                .clip(RoundedCornerShape(18.dp))
                .background(if (message.outgoing) Sc.Brand.copy(alpha = 0.12f) else Sc.Card)
                .clickable(onClick = onOpen)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Sc.TintBlue),
                contentAlignment = Alignment.Center,
            ) {
                Icon(ScIcons.Paperclip, null, tint = Sc.Brand, modifier = Modifier.size(19.dp))
            }
            Column(Modifier.weight(1f)) {
                ScText(file.fileName ?: "Fayl", 14f, FontWeight.Bold, Sc.Ink, maxLines = 1)
                val size = ChatFormat.fileSize(file.sizeBytes)
                if (size.isNotEmpty()) {
                    ScText(size, 11.5f, FontWeight.Medium, Sc.Muted, maxLines = 1)
                }
            }
            MessageMeta(message, onDark = false)
        }
    }
}

/**
 * Ovozli xabar pufagi — ijro tugmasi, to'lqin va davomiylik.
 *
 * To'lqin **serverdan** keladi (aynan 48 nuqta, `0..100`) — uni klientda hisoblash uchun
 * butun faylni yuklab, dekodlash kerak bo'lardi (`handoff/02-API-CHANGES.md` §4c).
 *
 * [progress] — `0f..1f`, ijro pozitsiyasi. Eshitilgan qism yorqin, qolgani xira bo'ladi.
 */
@Composable
internal fun VoiceBubble(
    message: ChatMessageUi,
    playing: Boolean,
    progress: Float,
    onTogglePlay: () -> Unit,
    onTap: () -> Unit,
) {
    val voice = message.attachment ?: return
    val align = if (message.outgoing) Alignment.CenterEnd else Alignment.CenterStart
    Box(Modifier.fillMaxWidth(), contentAlignment = align) {
        Row(
            Modifier.width(BUBBLE_MAX_WIDTH)
                .clip(RoundedCornerShape(18.dp))
                .background(if (message.outgoing) Sc.Brand.copy(alpha = 0.12f) else Sc.Card)
                .clickable(onClick = onTap)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(percent = 50)).background(Sc.Brand)
                    .clickable(onClick = onTogglePlay),
                contentAlignment = Alignment.Center,
            ) {
                // Alohida "pauza" ikonkasi yo'q — ijro paytida mikrofon o'rniga to'xtatish
                // ma'nosidagi `Close` ko'rsatiladi.
                Icon(
                    if (playing) ScIcons.Close else ScIcons.Mic,
                    if (playing) "To'xtatish" else "Eshitish",
                    tint = Color.White,
                    modifier = Modifier.size(17.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Waveform(voice.waveform, progress)
                ScText(ChatFormat.duration(voice.durationMs), 11f, FontWeight.SemiBold, Sc.Muted, maxLines = 1)
            }
            MessageMeta(message, onDark = false)
        }
    }
}

/**
 * To'lqin — 48 ta ustun.
 *
 * Ro'yxat bo'sh bo'lsa (eski xabar yoki server bermagan) bir tekis past ustunlar chiziladi:
 * bo'sh joy qoldirsak pufak "buzilgan" ko'rinardi.
 */
@Composable
private fun Waveform(points: List<Int>, progress: Float) {
    val bars = points.ifEmpty { List(WAVEFORM_POINTS) { WAVEFORM_FALLBACK } }
    val playedUpTo = (bars.size * progress).toInt()
    Row(
        Modifier.fillMaxWidth().height(26.dp),
        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        bars.forEachIndexed { index, value ->
            // Eng past ustun ham ko'rinib tursin — 0 balandlik "tishsiz" bo'shliq qoldirardi.
            val fraction = (value.coerceIn(0, 100) / 100f).coerceAtLeast(0.12f)
            Box(
                Modifier.weight(1f)
                    .fillMaxHeight(fraction)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (index <= playedUpTo) Sc.Brand else Sc.Border),
            )
        }
    }
}

/**
 * Video pufagi — poster kadr va ijro belgisi.
 *
 * ⚠️ Server videoni **transkod qiladi**: `PROCESSING` holatida poster bor, o'zi hali yo'q.
 * O'shanda ijro belgisi o'rniga "tayyorlanmoqda" ko'rsatiladi — bosilsa faqat xato chiqardi.
 */
@Composable
internal fun VideoBubble(message: ChatMessageUi, onOpen: () -> Unit) {
    val video = message.attachment ?: return
    val align = if (message.outgoing) Alignment.CenterEnd else Alignment.CenterStart
    Box(Modifier.fillMaxWidth(), contentAlignment = align) {
        Box(
            Modifier.width(BUBBLE_MAX_WIDTH)
                .clip(RoundedCornerShape(18.dp))
                .background(if (message.outgoing) Sc.Brand.copy(alpha = 0.12f) else Sc.Card)
                // Transkodlanayotgan video ham bosiladi: chaqiruvchi tanlash rejimida uni
                // belgilaydi, oddiy holatda esa "tayyorlanmoqda" deb javob beradi.
                .clickable(onClick = onOpen)
                .padding(3.dp),
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .aspectRatio(video.aspectRatio ?: DEFAULT_ASPECT)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Sc.Chip),
            ) {
                AsyncImage(
                    model = video.thumbUrl ?: video.url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (video.processing) {
                        ScText("Tayyorlanmoqda…", 12.5f, FontWeight.Bold, Color.White)
                    } else {
                        Box(
                            Modifier.size(46.dp)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                ScIcons.ChevronRight,
                                "O'ynatish",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
                if (video.durationMs > 0) {
                    Box(
                        Modifier.align(Alignment.BottomStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        ScText(ChatFormat.duration(video.durationMs), 10.5f, FontWeight.Bold, Color.White)
                    }
                }
                MessageMeta(message, Modifier.align(Alignment.BottomEnd).padding(8.dp), onDark = true)
            }
        }
    }
}

/**
 * **Ketayotgan** biriktirma — fayl, video yoki ovoz hali yuklanmoqda.
 *
 * Nega alohida pufak: biriktirma serverning javobi bilan keladi, ya'ni yuklash davomida
 * xabarda `attachment` YO'Q. Ilgari bunday qator hech qaysi shoxobchaga tushmay, matn
 * pufagi bo'lib chizilardi — ekranda **bo'sh pufak** turardi va foydalanuvchi yuborish
 * ketayotganini umuman bilmasdi.
 *
 * Videoda halqa poster o'rnida (poster ham hali yo'q), fayl va ovozda esa ikonka o'rnida
 * turadi — shakl tayyor pufak bilan bir xil qoladi, ya'ni yuklash tugaganda maket
 * sakramaydi.
 */
@Composable
internal fun UploadingAttachmentBubble(message: ChatMessageUi, onTap: () -> Unit) {
    val upload = message.upload ?: return
    val align = if (message.outgoing) Alignment.CenterEnd else Alignment.CenterStart
    val background = if (message.outgoing) Sc.Brand.copy(alpha = 0.12f) else Sc.Card

    Box(Modifier.fillMaxWidth(), contentAlignment = align) {
        if (message.type == MessageType.VIDEO) {
            Box(
                Modifier.width(BUBBLE_MAX_WIDTH)
                    .clip(RoundedCornerShape(18.dp))
                    .background(background)
                    .clickable(onClick = onTap)
                    .padding(3.dp),
            ) {
                Box(
                    Modifier.fillMaxWidth()
                        .aspectRatio(DEFAULT_ASPECT)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Sc.Chip),
                    contentAlignment = Alignment.Center,
                ) {
                    ScUploadRing(upload.progress)
                    if (upload.sizeBytes > 0) {
                        Box(
                            Modifier.align(Alignment.BottomStart)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            ScText(
                                ChatFormat.fileSize(upload.sizeBytes),
                                10.5f,
                                FontWeight.Bold,
                                Color.White,
                            )
                        }
                    }
                    MessageMeta(message, Modifier.align(Alignment.BottomEnd).padding(8.dp), onDark = true)
                }
            }
            return@Box
        }

        Row(
            Modifier.width(BUBBLE_MAX_WIDTH)
                .clip(RoundedCornerShape(18.dp))
                .background(background)
                .clickable(onClick = onTap)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            ScUploadRing(upload.progress, size = SMALL_RING, stroke = 2.5.dp)
            Column(Modifier.weight(1f)) {
                val title = when (message.type) {
                    MessageType.VOICE -> "Ovozli xabar"
                    else -> upload.fileName ?: "Fayl"
                }
                ScText(title, 14f, FontWeight.Bold, Sc.Ink, maxLines = 1)
                ScText(uploadCaption(upload), 11.5f, FontWeight.Medium, Sc.Muted, maxLines = 1)
            }
            MessageMeta(message, onDark = false)
        }
    }
}

/** «42% · 3,2 MB» — hajm noma'lum bo'lsa faqat holat matni. */
private fun uploadCaption(upload: ChatUploadUi): String {
    val percent = upload.progress?.let { scUploadPercent(it) } ?: "Yuklanmoqda…"
    val size = ChatFormat.fileSize(upload.sizeBytes)
    return if (size.isEmpty()) percent else "$percent · $size"
}

/** Fayl/ovoz pufagidagi halqa — ikonka o'rnini egallaydi, ya'ni o'sha o'lchamda. */
private val SMALL_RING = 40.dp

/** Server har doim aynan shuncha nuqta beradi. */
private const val WAVEFORM_POINTS = 48

/** To'lqin kelmaganda ishlatiladigan bir tekis balandlik. */
private const val WAVEFORM_FALLBACK = 30
