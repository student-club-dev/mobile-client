package dev.feature.chat.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import coil3.compose.AsyncImagePainter
import dev.core.uikit.components.AppIcons
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScText
import dev.core.uikit.media.toImageBitmapOrNull
import dev.core.uikit.theme.Sc
import dev.core.uikit.components.scShimmerSweep
import dev.feature.chat.domain.model.MessageStatus

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
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ImageAlbumBubble(
    message: ChatMessageUi,
    onOpen: (Int) -> Unit,
    onLongPress: () -> Unit,
) {
    val images = message.images
    if (images.isEmpty()) return

    val align = if (message.outgoing) Alignment.CenterEnd else Alignment.CenterStart
    Box(Modifier.fillMaxWidth(), contentAlignment = align) {
        Column(
            Modifier.width(BUBBLE_MAX_WIDTH)
                .clip(RoundedCornerShape(18.dp))
                .background(if (message.outgoing) Sc.Brand.copy(alpha = 0.12f) else Sc.Card)
                .combinedClickable(onClick = {}, onLongClick = onLongPress)
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
                // chunki `ChatImageUi` ichida `ByteArray` bor va tenglik ishonchsiz.
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
private fun ImageCell(image: ChatImageUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
        // Yuklanayotganda: local nusxa ko'rinib turadi, ustidan shimmer to'lqini o'tadi.
        if (image.loading) {
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.22f))
                    .scShimmerSweep(RoundedCornerShape(14.dp)),
            )
        }
    }
}

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
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun StickerBubble(message: ChatMessageUi, onLongPress: () -> Unit) {
    val align = if (message.outgoing) Alignment.CenterEnd else Alignment.CenterStart
    Box(Modifier.fillMaxWidth(), contentAlignment = align) {
        Column(
            Modifier.combinedClickable(onClick = {}, onLongClick = onLongPress),
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

/** Bosilgan rasmni to'liq ekranda ochadi. Albomda chapga/o'ngga o'tish tugmalari bilan. */
@Composable
internal fun ImageViewerDialog(images: List<ChatImageUi>, startIndex: Int, onDismiss: () -> Unit) {
    if (images.isEmpty()) return
    var index by remember(startIndex) { mutableStateOf(startIndex.coerceIn(images.indices)) }
    val current = images[index.coerceIn(images.indices)]

    Dialog(
        onDismissRequest = onDismiss,
        // Tizimning odatiy kengligi olib tashlanadi — rasm butun ekranni egallasin.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.96f))) {
            // To'liq ekranda thumb (320px) emas, asl nusxa — aks holda rasm xira ko'rinardi.
            val url = current.fullUrl ?: current.url
            val bytes = current.localBytes
            when {
                url != null -> AsyncImage(
                    model = url,
                    contentDescription = "Rasm",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                )
                bytes != null -> {
                    val bitmap = remember(bytes) { bytes.toImageBitmapOrNull() }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Rasm",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                        )
                    }
                }
            }

            ViewerButton(Modifier.align(Alignment.TopEnd).padding(14.dp), "Yopish", onDismiss)

            if (images.size > 1) {
                if (index > 0) {
                    ViewerButton(
                        Modifier.align(Alignment.CenterStart).padding(10.dp),
                        "Oldingi",
                        onClick = { index-- },
                        icon = false,
                    )
                }
                if (index < images.lastIndex) {
                    ViewerButton(
                        Modifier.align(Alignment.CenterEnd).padding(10.dp),
                        "Keyingi",
                        onClick = { index++ },
                        icon = false,
                    )
                }
                Box(
                    Modifier.align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 20.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.16f))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) { ScText("${index + 1} / ${images.size}", 12.5f, FontWeight.Bold, Color.White) }
            }
        }
    }
}

@Composable
private fun ViewerButton(
    modifier: Modifier,
    label: String,
    onClick: () -> Unit,
    icon: Boolean = true,
) {
    Box(
        modifier.size(40.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(Color.White.copy(alpha = 0.18f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (icon) {
            Icon(ScIcons.Close, label, tint = Color.White, modifier = Modifier.size(20.dp))
        } else {
            ScText(if (label == "Oldingi") "‹" else "›", 24f, FontWeight.Bold, Color.White)
        }
    }
}

private const val STICKER_SIZE = 58f

/** Rasmli stiker — emoji varianti bilan taxminan bir xil ko'rinishda bo'lsin. */
private val STICKER_IMAGE_SIZE = 120.dp

private const val DEFAULT_ASPECT = 4f / 3f
