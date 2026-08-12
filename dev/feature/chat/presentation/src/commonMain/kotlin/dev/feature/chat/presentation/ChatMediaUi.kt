package dev.feature.chat.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
import dev.core.uikit.media.rememberScVideoState
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
                chatStrings().sendFailedRetry, 11f, FontWeight.SemiBold,
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
    /** Ketayotgan videoni to'xtatish — halqa ichidagi `×`. Berilmasa belgi chizilmaydi. */
    onCancelUpload: ((String) -> Unit)? = null,
    /**
     * Pufakning **bo'sh joyi** bosildi (kataklar orasi, meta qatori).
     *
     * Uzun bosish bu yerda emas — u qatorning o'zida (`ChatScreen`), chunki barmoqni surib
     * belgilash bitta uzluksiz imo-ishora bo'lishi kerak. Pufak `combinedClickable` bilan
     * uzun bosishni **o'ziga olib qo'yardi** va surish boshlanmasdi.
     */
    onTap: () -> Unit,
    /**
     * To'rda video katagi bor — uning birinchi kadrini tayyorlash uchun.
     *
     * ⚠️ Video xabar aynan SHU yerda chiziladi, [VideoBubble] da emas: `VIDEO` turi
     * `MEDIA_GRID` ga kiradi va rasm bilan bitta mozaikadan o'tadi.
     */
    onNeedPoster: (mediaId: String?, url: String?) -> Unit = { _, _ -> },
) {
    val images = message.images
    if (images.isEmpty()) return

    // Har bir video katagi uchun bir marta. `fullUrl` — videoning o'zi; `url` esa
    // allaqachon poster bo'lishi mumkin va uni manba qilib berish aylanma bo'lardi.
    LaunchedEffect(images) {
        // Faqat serverda poster BO'LMAGAN videolar uchun: serverning tayyor rasmi bo'lsa
        // kadr ajratish ortiqcha ish bo'lardi.
        images.filter { it.video && it.url == null }.forEach { onNeedPoster(it.mediaId, it.fullUrl) }
    }

    val align = if (message.outgoing) Alignment.CenterEnd else Alignment.CenterStart
    Box(Modifier.fillMaxWidth(), contentAlignment = align) {
        // ⚠️ Media pufagida **fon ham, ichki chekinish ham yo'q**: Telegramdagi kabi rasmning
        // o'zi pufak bo'ladi. Ilgari atrofida rangli ramka bor edi va shu sabab rasm ham,
        // video ham maketdan kichikroq ko'rinardi.
        Column(
            Modifier.width(BUBBLE_MAX_WIDTH)
                .clip(RoundedCornerShape(BUBBLE_RADIUS))
                .clickable(onClick = onTap),
            verticalArrangement = Arrangement.spacedBy(GRID_GAP),
        ) {
            if (images.size == 1) {
                ImageCell(
                    image = images[0],
                    onClick = { onOpen(0) },
                    onCancelUpload = onCancelUpload,
                    // Yakka media — o'z nisbatida, lekin CHEKLANGAN oraliqda: juda uzun
                    // (9:16) video ekranni to'ldirib yuborardi, juda keng esa yupqa
                    // chiziqqa aylanardi. Rasm va video bir xil qoidaga bo'ysunadi —
                    // shuning uchun ular yonma-yon bir xil o'lchamda ko'rinadi.
                    modifier = Modifier.fillMaxWidth().aspectRatio(clampAspect(images[0].aspectRatio)),
                    // Yakka mediada burchaklar pufakning o'zi bilan bir xil.
                    shape = RoundedCornerShape(BUBBLE_RADIUS),
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
                                onCancelUpload = onCancelUpload,
                                modifier = Modifier.weight(1f).aspectRatio(1f),
                            )
                            ImageCell(
                                image = images[second],
                                onClick = { onOpen(second) },
                                onCancelUpload = onCancelUpload,
                                modifier = Modifier.weight(1f).aspectRatio(1f),
                            )
                        }
                        index += 2
                    } else {
                        // Toq qolgan oxirgisi — keng va past katak.
                        ImageCell(
                            image = images[first],
                            onClick = { onOpen(first) },
                            onCancelUpload = onCancelUpload,
                            modifier = Modifier.fillMaxWidth().aspectRatio(2f),
                        )
                        index += 1
                    }
                }
            }
        }

        // Vaqt — **medianing ustida**, o'ng pastki burchakda qora yorliqchada (Telegramdagi
        // kabi). Ilgari u to'r ostidagi alohida qatorda turardi va pufak media balandligidan
        // ortiqcha joy egallardi.
        MetaChip(
            message = message,
            modifier = Modifier.align(if (message.outgoing) Alignment.BottomEnd else Alignment.BottomStart)
                .padding(8.dp),
        )
    }
}

/**
 * Media ustidagi vaqt yorlig'i — yarim shaffof qora fon, oq matn.
 *
 * Fon **shart**: rasm och bo'lsa oq matn ko'rinmay qolardi, qora bo'lsa esa xira matn
 * yo'qolardi. Telegram ham aynan shu sababdan yorliqcha chizadi.
 */
@Composable
private fun MetaChip(message: ChatMessageUi, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(percent = 50))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        MessageMeta(message, onDark = true)
    }
}

/**
 * Yakka medianing nisbati — **cheklangan** oraliqda.
 *
 * Server o'lcham bermasa (video metama'lumoti ba'zan kelmaydi) 4:3 olinadi. Cheklov esa
 * rasm bilan videoni bir xil qoidaga bo'ysundiradi: 9:16 lik video ekranni to'ldirib
 * yubormaydi, panorama rasm esa yupqa chiziqqa aylanmaydi.
 */
private fun clampAspect(ratio: Float?): Float =
    (ratio ?: DEFAULT_ASPECT).coerceIn(MIN_ASPECT, MAX_ASPECT)

/** Pufak burchaklari va nisbat chegaralari. */
private val BUBBLE_RADIUS = 16.dp
private const val MIN_ASPECT = 0.62f
private const val MAX_ASPECT = 1.9f

/**
 * Bitta katak. Yuklanayotgan rasmda serverda havola yo'q — tanlangan faylning **local
 * nusxasi** ko'rsatiladi va ustidan shimmer to'lqini yuradi.
 */
@Composable
private fun ImageCell(
    image: ChatMediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onCancelUpload: ((String) -> Unit)? = null,
    /** To'rdagi katakda kichik radius, yakka mediada — pufakning o'zi bilan bir xil. */
    shape: Shape = RoundedCornerShape(CELL_RADIUS),
) {
    Box(
        modifier.clip(shape)
            .background(Sc.Chip)
            .clickable(enabled = !image.loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val bytes = image.localBytes
        when {
            image.url != null -> AsyncImage(
                // ⚠️ Bu yerda **to'liq nusxa** (1920px WebP) so'raladi, `?variant=thumb` emas.
                // Thumb — 320px, pufak esa 280dp keng (3x qurilmada ~840px): kichik nusxa
                // 2.6 baravar cho'zilib xira ko'rinardi, o'sha rasm ochilganda esa tiniq
                // edi — foydalanuvchi buni darhol sezadi.
                //
                // Trafik xavfi yo'q: server rasmni 1920px WebP ga siqib qo'yadi
                // (`02-API-CHANGES.md` §media), ya'ni "to'liq" ham bir necha yuz KB.
                // Coil dekodlashda katakning o'lchamiga qadar kichraytiradi — xotira
                // katakning o'zicha qoladi. Havola bo'lmasa thumb'ga qaytamiz.
                //
                // ⚠️ VIDEO bunga kirmaydi: unda `fullUrl` — videoning O'ZI (`.mp4`), Coil
                // esa undan kadr chiqara olmaydi va katak bo'sh kulrang bo'lib qolardi.
                // Videoda `url` allaqachon poster (telefonda ajratilgan birinchi kadr).
                model = if (image.video) image.url else image.fullUrl ?: image.url,
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
                shape = shape,
                ringSize = UPLOAD_RING,
                // Bekor qilish faqat videoda: u siqish bilan birga bir necha daqiqa
                // ketishi mumkin, rasm esa sekundlarda tugaydi va belgiga bosib ulgurib
                // bo'lmaydi.
                onCancel = if (image.video && onCancelUpload != null) {
                    { onCancelUpload(image.messageId) }
                } else {
                    null
                },
            )
            image.loading -> Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.22f))
                    .scShimmerSweep(shape),
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
        // Yuborilmagan xabarda «Tayyorlanmoqda…» ko'rsatilmaydi: pufakning o'zida
        // allaqachon «yuborilmadi · qayta urinish» turadi va ikkalasi birga qarama-qarshi
        // o'qilardi. Serverdagi transkod holati bu yerda ahamiyatsiz — xabar baribir ketmagan.
        if (item.processing && !item.failed) {
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
    if (item.durationMs > 0 && !(item.processing && !item.failed)) {
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

/** To'rdagi katakning burchagi (yakka media pufak radiusini oladi). */
private val CELL_RADIUS = 12.dp

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
    /** Video ostida ko'rsatiladigan izoh (xabar matni). */
    caption: String? = null,
    /**
     * Ochilgan video — uni telefonga bir marta saqlash uchun. Rasmlarda chaqirilmaydi.
     */
    onVideoOpened: (mediaId: String?, url: String?) -> Unit = { _, _ -> },
    onDismiss: () -> Unit,
) {
    if (items.isEmpty()) return
    val pager = rememberPagerState(
        initialPage = startIndex.coerceIn(items.indices),
        pageCount = { items.size },
    )
    val scope = rememberCoroutineScope()

    // Surib o'tilgan HAR bir video saqlanadi, faqat birinchisi emas: albomda beshta video
    // bo'lsa foydalanuvchi hammasini ko'radi va hammasi telefonda qolishi kerak.
    val current = items.getOrNull(pager.currentPage)
    LaunchedEffect(current?.mediaId) {
        if (current?.video == true) onVideoOpened(current.mediaId, current.fullUrl ?: current.url)
    }

    // Videoning o'z boshqaruvi (Telegramdagidek). Holat SAHIFA bo'yicha emas, ko'rgich
    // bo'yicha bitta: bir vaqtda faqat bitta video o'ynaydi, ya'ni ikkitasi kerak emas.
    val videoState = rememberScVideoState()
    // Panel videoga bosilganda yashirinadi/ko'rinadi. Rasmda ma'nosi yo'q — u yerda
    // bosish hech narsa qilmaydi.
    var controlsVisible by remember { mutableStateOf(true) }
    // Sahifa almashsa panel qaytadan ko'rinsin: yangi videoni boshqarish kerak bo'ladi.
    LaunchedEffect(pager.currentPage) { controlsVisible = true }

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
                        // Tizimning tayyor paneli o'chirilgan — uning o'rniga
                        // [VideoPlayerControls] chiziladi (Telegram ko'rinishi).
                        showControls = false,
                        contentScaleFit = true,
                        // Holat faqat OCHIQ sahifaga ulanadi: aks holda tasmadagi barcha
                        // videolar bitta ko'chirgichni bir vaqtda yangilab yuborardi.
                        state = if (pager.currentPage == page) videoState else null,
                        modifier = Modifier.fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { controlsVisible = !controlsVisible }
                            },
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

            // Videoning boshqaruv paneli — faqat ochiq sahifa video bo'lsa.
            if (current?.video == true) {
                VideoPlayerControls(
                    state = videoState,
                    visible = controlsVisible,
                    caption = caption,
                )
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
    onVideoOpened: (mediaId: String?, url: String?) -> Unit = { _, _ -> },
    onDismiss: () -> Unit,
) = MediaViewerDialog(
    items = images,
    startIndex = startIndex,
    mediaHeaders = mediaHeaders,
    onVideoOpened = onVideoOpened,
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
                    if (playing) chatStrings().stop else chatStrings().play,
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
internal fun VideoBubble(
    message: ChatMessageUi,
    onOpen: () -> Unit,
    /** Pufak ekranga chiqdi — birinchi kadrni tayyorlash uchun. */
    onNeedPoster: (mediaId: String?, url: String?) -> Unit = { _, _ -> },
) {
    val video = message.attachment ?: return
    // Kadr faqat KERAK bo'lganda ajratiladi: pufak ekranga chiqmasa ish ham boshlanmaydi.
    LaunchedEffect(video.mediaId) { onNeedPoster(video.mediaId, video.url) }
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
 * Dumaloq video xabar (`VIDEO_NOTE`) — Telegram'dagi «kruglyashka».
 *
 * Oddiy videodan uch narsa bilan farq qiladi va uchalasi ham **formatning ta'rifi**:
 *
 * 1. **Doira** — server faylning kvadratligini talab qiladi (`422 MEDIA_NOT_SQUARE`),
 *    ya'ni nisbat doim 1:1 va uni `aspectRatio` dan olish shart emas.
 * 2. **Pufak yo'q** — video to'g'ridan-to'g'ri fonda turadi, chetlari yumaloq emas, aylana.
 * 3. **Izoh yo'q** — server matnni qabul qilmaydi, shuning uchun matn maydoni ham yo'q.
 *
 * Davomiylik yozuvi doiraning **ostida** emas, ichida pastda: doiradan tashqarida u
 * osilib qolgandek ko'rinardi.
 */
@Composable
internal fun VideoNoteBubble(
    message: ChatMessageUi,
    onOpen: () -> Unit,
    /** Doira ekranga chiqdi — birinchi kadrini tayyorlash uchun. */
    onNeedPoster: (mediaId: String?, url: String?) -> Unit = { _, _ -> },
) {
    val video = message.attachment ?: return
    LaunchedEffect(video.mediaId) { onNeedPoster(video.mediaId, video.url) }
    val align = if (message.outgoing) Alignment.CenterEnd else Alignment.CenterStart
    Box(Modifier.fillMaxWidth(), contentAlignment = align) {
        Box(
            Modifier.size(VIDEO_NOTE_SIZE)
                .clip(CircleShape)
                .background(Sc.Chip)
                .clickable(onClick = onOpen),
        ) {
            AsyncImage(
                model = video.thumbUrl ?: video.url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                if (video.processing) {
                    ScText("Tayyorlanmoqda…", 12.5f, FontWeight.Bold, Color.White)
                } else {
                    Box(
                        Modifier.size(44.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.42f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            ScIcons.ChevronRight,
                            "O'ynatish",
                            tint = Color.White,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }
            }
            Column(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (video.durationMs > 0) {
                    ScText(
                        ChatFormat.duration(video.durationMs),
                        11f,
                        FontWeight.Bold,
                        Color.White,
                    )
                }
                MessageMeta(message, onDark = true)
            }
        }
    }
}

/** Dumaloq video xabarning diametri — Telegram'dagi bilan bir xil his beradi. */
private val VIDEO_NOTE_SIZE = 208.dp

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
        // Dumaloq xabar yuklanayotganda ham **doira** bo'lib turadi: shakl yuklash tugagach
        // o'zgarmasligi kerak, aks holda pufakcha to'rtburchakdan aylanaga sakrardi.
        if (message.type == MessageType.VIDEO_NOTE) {
            Box(
                Modifier.size(VIDEO_NOTE_SIZE)
                    .clip(CircleShape)
                    .background(Sc.Chip)
                    .clickable(onClick = onTap),
                contentAlignment = Alignment.Center,
            ) {
                ScUploadRing(upload.progress)
                MessageMeta(
                    message,
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                    onDark = true,
                )
            }
            return@Box
        }

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
                    MessageType.VOICE -> chatStrings().voiceMessage
                    else -> upload.fileName ?: chatStrings().file
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
