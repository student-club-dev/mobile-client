package dev.feature.chat.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import dev.core.uikit.components.AppIcons
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScText
import dev.core.uikit.components.scStyle
import dev.core.uikit.components.scShimmerSweep
import dev.core.uikit.media.GALLERY_PAGE_SIZE
import dev.core.uikit.media.GalleryAccess
import dev.core.uikit.media.GalleryItem
import dev.core.uikit.media.PickedMedia
import dev.core.uikit.media.DEFAULT_MAX_IMAGES
import dev.core.uikit.media.rememberDeviceGallery
import dev.core.uikit.theme.Sc
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** To'r ustunlari — Telegramdagidek uchta, katagi kvadrat. */
private const val GRID_COLUMNS = 3

/** Katak uchun so'raladigan kichik nusxa (piksel) — 3 ustunli to'rda shu yetarli. */
private const val THUMB_PX = 320

/** Kataklar deyarli kvadrat — Telegramdagidek, orasi ingichka. */
private val CELL_SHAPE = RoundedCornerShape(3.dp)

/** Izohning eng katta uzunligi — serverdagi bilan bir xil (`SendPayload.MAX_CAPTION`). */
private const val MAX_CAPTION = 1024

/**
 * Biriktirish varag'i — **ilova ichidagi galereya** (Telegramdagi kabi).
 *
 * Yuqorisida qurilmadagi rasm va videolarning to'ri, pastida esa qolgan yo'llar:
 * to'liq galereya (tizim tanlagichi), fayl va kamera. Ya'ni eng ko'p ishlatiladigan ish —
 * oxirgi rasmni yuborish — bitta bosishda bajariladi, qolganlari ham ko'rinib turadi.
 *
 * Ruxsat bu yerda **majburiy emas**: berilmasa to'r o'rnida tushuntirish va tugma turadi,
 * pastdagi «Galereya» esa baribir ishlaydi — tizim tanlagichi hech qanday ruxsat so'ramaydi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AttachGallerySheet(
    onDismiss: () -> Unit,
    /** Tanlanganlar o'qib bo'lindi (+ albom izohi) — yuborish chaqiruvchida. */
    onSend: (PickedMedia, String?) -> Unit,
    onOpenSystemPicker: () -> Unit,
    onPickFile: () -> Unit,
    onCaptureVideo: () -> Unit,
    /** Dumaloq video xabar yozib olish (`VIDEO_NOTE`). */
    onRecordVideoNote: () -> Unit,
) {
    val gallery = rememberDeviceGallery()
    val scope = rememberCoroutineScope()

    val items = remember { mutableStateListOf<GalleryItem>() }
    val selected = remember { mutableStateListOf<GalleryItem>() }

    /** Albomga bitta izoh — Telegramdagidek pastdagi maydonda yoziladi. */
    var caption by remember { mutableStateOf("") }
    var endReached by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    /** Tanlanganlarni o'qish (bir necha MB) — shu paytda varaq bloklanadi. */
    var sending by remember { mutableStateOf(false) }

    val gridState = rememberLazyGridState()

    // Varaq birinchi marta ochilganda TIZIM oynasi darhol so'raladi — Telegramdagi kabi.
    //
    // Ilgari o'rniga ilovaning O'Z «Galereyaga ruxsat kerak → Ruxsat berish» varag'i
    // chiqardi va tizim oynasi faqat undan keyin ko'rinardi: foydalanuvchi bir xil
    // savolga ikki marta javob berardi. Rad etilgandan keyin esa o'sha varaq o'z
    // vazifasini bajaradi — u yerda sozlamalarga havola bor (tizim oynasi boshqa chiqmaydi).
    LaunchedEffect(Unit) {
        if (gallery.access == GalleryAccess.UNKNOWN) gallery.requestAccess()
    }

    // Ruxsat o'zgarganda ro'yxat noldan yig'iladi: «faqat tanlanganlar» dan «hammasi» ga
    // o'tilganda eski qisqa ro'yxat qolib ketardi.
    LaunchedEffect(gallery.access) {
        items.clear()
        selected.clear()
        endReached = false
        if (gallery.access == GalleryAccess.GRANTED || gallery.access == GalleryAccess.LIMITED) {
            loading = true
            items += gallery.page(offset = 0, limit = GALLERY_PAGE_SIZE)
            endReached = items.size < GALLERY_PAGE_SIZE
            loading = false
        }
    }

    // Oxiriga yaqinlashganda keyingi sahifa — butun galereyani bir marta o'qish arzon
    // telefonda bir necha soniya qotib turishga olib kelardi.
    LaunchedEffect(gridState, items.size, endReached) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (!endReached && !loading && lastVisible >= items.size - GRID_COLUMNS * 2) {
                    loading = true
                    val next = gallery.page(offset = items.size, limit = GALLERY_PAGE_SIZE)
                    items += next
                    endReached = next.isEmpty()
                    loading = false
                }
            }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Sc.Card,
        // Varaq DARROV to'liq ochiladi: yarim holatda to'rning atigi ikki qatori ko'rinib,
        // rasm tanlashdan oldin har safar varaqni yuqoriga tortishga to'g'ri kelardi.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        val showGrid = gallery.access == GalleryAccess.GRANTED || gallery.access == GalleryAccess.LIMITED

        Column(
            Modifier.fillMaxWidth().navigationBarsPadding()
                // To'r ko'rinsa varaq butun bo'sh balandlikni egallaydi — LazyVerticalGrid
                // ga **cheklangan balandlik** kerak, aks holda u o'lchanmaydi. Ruxsat
                // so'ralayotgan holatda esa varaq matn bo'yicha kichik qoladi.
                .then(if (showGrid) Modifier.fillMaxHeight() else Modifier),
        ) {
            // Sarlavha — Telegramdagidek faqat tanlov bo'lganda paydo bo'ladi va nechta
            // ekanini aytadi. Bo'sh holatda joy egallamaydi: to'r yuqoriroq turadi.
            if (selected.isNotEmpty()) {
                ScText(
                    if (selected.size >= DEFAULT_MAX_IMAGES) {
                        // Chegaraga yetilganda keyingi bosish jimgina e'tiborsiz qolardi.
                        chatStrings().maxAtOnce(selected.size)
                    } else {
                        chatStrings().selectedItems(selected.size, selected.kindLabel())
                    },
                    16f,
                    FontWeight.ExtraBold,
                    Sc.Ink,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 10.dp),
                )
            }

            Box(Modifier.weight(1f, fill = showGrid)) {
                when (gallery.access) {
                    GalleryAccess.GRANTED, GalleryAccess.LIMITED -> GalleryGrid(
                        items = items,
                        selected = selected,
                        gridState = gridState,
                        thumbnail = { item, cache ->
                            // Katak ko'ringanda yuklanadi va keshda qoladi: aks holda
                            // ro'yxat har surilganda qaytadan dekodlanardi.
                            scope.launch { cache[item.id] = gallery.thumbnail(item, THUMB_PX) }
                        },
                        onToggle = { item -> selected.toggle(item) },
                        limited = gallery.access == GalleryAccess.LIMITED,
                        onExtendAccess = gallery::requestAccess,
                        onCamera = onCaptureVideo,
                    )

                    GalleryAccess.UNKNOWN, GalleryAccess.DENIED -> AccessPrompt(
                        denied = gallery.access == GalleryAccess.DENIED,
                        onRequest = gallery::requestAccess,
                    )
                }
            }

            AttachActions(
                selectedCount = selected.size,
                sending = sending,
                galleryOpen = showGrid,
                caption = caption,
                onCaption = { caption = it },
                onOpenSystemPicker = onOpenSystemPicker,
                onPickFile = onPickFile,
                onCaptureVideo = onCaptureVideo,
                onRecordVideoNote = onRecordVideoNote,
                onSend = {
                    // Ikki marta bosilsa ikki marta yuborilardi — tugma o'chiriladi.
                    if (sending) return@AttachActions
                    sending = true
                    scope.launch {
                        val media = gallery.load(selected.toList())
                        sending = false
                        onSend(media, caption.trim().takeIf { it.isNotEmpty() })
                        onDismiss()
                    }
                },
            )
        }
    }
}

/** Sarlavhadagi so'z: hammasi rasm bo'lsa «rasm», hammasi video bo'lsa «video», aralashda «fayl». */
private fun List<GalleryItem>.kindLabel(): String {
    val s = chatStringsNow()
    return when {
        all { !it.isVideo } -> s.kindPhotos
        all { it.isVideo } -> s.kindVideos
        else -> s.kindItems
    }
}

/** Tanlash — tartib saqlanadi (albomdagi ketma-ketlik shunga qarab chiziladi). */
private fun MutableList<GalleryItem>.toggle(item: GalleryItem) {
    val existing = indexOfFirst { it.id == item.id }
    when {
        existing >= 0 -> removeAt(existing)
        // Server albomda 10 tadan ko'pini qabul qilmaydi — chegaradan keyin bosish
        // shunchaki e'tiborsiz qoladi (tanlanganlar yo'qolmasligi uchun).
        size < DEFAULT_MAX_IMAGES -> add(item)
    }
}

@Composable
private fun GalleryGrid(
    items: List<GalleryItem>,
    selected: List<GalleryItem>,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    thumbnail: (GalleryItem, MutableMap<String, ImageBitmap?>) -> Unit,
    onToggle: (GalleryItem) -> Unit,
    limited: Boolean,
    onExtendAccess: () -> Unit,
    /** To'rning birinchi katagi — kamera. */
    onCamera: () -> Unit,
) {
    val cache = remember { mutableStateMapOf<String, ImageBitmap?>() }

    Column {
        // iOS «Limited Photos» va Android 14 «Select photos»: ro'yxat qisqa bo'lishi
        // KUTILGAN, lekin foydalanuvchi buni bilmasa "rasmlarim yo'qolibdi" deb o'ylaydi.
        if (limited) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onExtendAccess)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScText(chatStrings().onlySelectedShown, 12.5f, FontWeight.Medium, Sc.Muted)
                ScText(chatStrings().showAll, 12.5f, FontWeight.ExtraBold, Sc.Brand)
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(GRID_COLUMNS),
            state = gridState,
            contentPadding = PaddingValues(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Birinchi katak — kamera (Telegramdagidek). Galereyaning ichida turgani
            // muhim: "hozir suratga olaman" degan odam varaqni yopib, boshqa yo'l
            // qidirishi shart emas.
            item(key = "camera") {
                Box(
                    Modifier.aspectRatio(1f).clip(CELL_SHAPE).background(Sc.InkSurface.copy(alpha = 0.85f))
                        .clickable(onClick = onCamera),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Camera, chatStrings().camera, tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }

            items(items, key = { it.id }) { item ->
                val order by remember(selected, item.id) {
                    derivedStateOf { selected.indexOfFirst { it.id == item.id } }
                }
                GalleryCell(
                    item = item,
                    bitmap = cache[item.id],
                    order = order,
                    onRequestThumbnail = { thumbnail(item, cache) },
                    onClick = { onToggle(item) },
                )
            }
        }
    }
}

@Composable
private fun GalleryCell(
    item: GalleryItem,
    bitmap: ImageBitmap?,
    /** Tanlanganlar ro'yxatidagi o'rni; tanlanmagan bo'lsa `-1`. */
    order: Int,
    onRequestThumbnail: () -> Unit,
    onClick: () -> Unit,
) {
    // Katak birinchi marta chizilganda so'raladi; kesh bo'lsa qayta so'ralmaydi.
    LaunchedEffect(item.id) { if (bitmap == null) onRequestThumbnail() }

    Box(
        Modifier.aspectRatio(1f).clip(CELL_SHAPE).background(Sc.Chip).clickable(onClick = onClick),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().scShimmerSweep(CELL_SHAPE))
        }

        if (item.isVideo) {
            Box(
                Modifier.align(Alignment.BottomStart).padding(4.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                ScText(ChatFormat.duration(item.durationMs), 10.5f, FontWeight.Bold, Color.White)
            }
        }

        // Tanlash halqasi — Telegramdagidek HAR katakda ko'rinadi: bo'sh oq halqa, tanlansa
        // ko'k to'ldiriladi va ichida tartib raqami turadi (albomdagi ketma-ketlik shu).
        //
        // Halqa och rasmda ham ko'rinishi uchun oq chegara + yengil qora fon: faqat chegara
        // bilan u oq devorli suratlarda yo'qolib qolardi.
        Box(
            Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp)
                .clip(CircleShape)
                .background(if (order >= 0) Sc.Brand else Color.Black.copy(alpha = 0.22f))
                .border(2.dp, if (order >= 0) Color.Transparent else Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (order >= 0) ScText("${order + 1}", 12f, FontWeight.ExtraBold, Color.White)
        }
    }
}

/** Ruxsat berilmagan holat — to'r o'rnida. */
@Composable
private fun AccessPrompt(denied: Boolean, onRequest: () -> Unit) {
    val s = chatStrings()
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScText(s.galleryPermissionTitle, 15f, FontWeight.ExtraBold, Sc.Ink)
        ScText(
            if (denied) {
                s.galleryPermissionDenied
            } else {
                s.galleryPermissionAsk
            },
            12.5f,
            FontWeight.Medium,
            Sc.Muted,
        )
        Box(
            Modifier.clip(RoundedCornerShape(14.dp)).background(Sc.Brand)
                .clickable(onClick = onRequest)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            ScText(if (denied) s.openSettings else s.grantPermission, 13f, FontWeight.ExtraBold, Color.White)
        }
    }
}

/**
 * Pastdagi **suzuvchi panel** — Telegramdagi kabi yumaloq, to'r ustida turadi.
 *
 * Tanlov bo'lmasa: yo'llar (`Galereya · Fayl · Kamera`), birinchisi faol ko'rinishda —
 * hozir ekranda aynan galereya ochiq. Tanlov bo'lsa: chapda nechta ekani, o'ngda ko'k
 * yuborish tugmasi.
 */
@Composable
private fun AttachActions(
    selectedCount: Int,
    sending: Boolean,
    /** To'r allaqachon ochiqmi — o'shanda «Galereya» faol yorliq, tugma emas. */
    galleryOpen: Boolean,
    caption: String,
    onCaption: (String) -> Unit,
    onOpenSystemPicker: () -> Unit,
    onPickFile: () -> Unit,
    onCaptureVideo: () -> Unit,
    /** Dumaloq video xabar yozib olish (`VIDEO_NOTE`). */
    onRecordVideoNote: () -> Unit,
    onSend: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Sc.Chip)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (selectedCount > 0) {
            // Izoh maydoni — Telegramdagi kabi yuborishdan oldin, o'sha panelda. Albomga
            // bitta izoh biriktiriladi (birinchi rasmning tanasiga yoziladi).
            Box(Modifier.weight(1f).padding(start = 14.dp)) {
                if (caption.isEmpty()) {
                    ScText(chatStrings().addCaption, 14f, FontWeight.Medium, Sc.NavIdle, maxLines = 1)
                }
                BasicTextField(
                    value = caption,
                    onValueChange = { if (it.length <= MAX_CAPTION) onCaption(it) },
                    textStyle = scStyle(14f, FontWeight.Medium, Sc.Ink),
                    cursorBrush = SolidColor(Sc.Brand),
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                Modifier.size(46.dp).clip(CircleShape)
                    .background(if (sending) Sc.Muted else Sc.Brand)
                    .clickable(enabled = !sending, onClick = onSend),
                contentAlignment = Alignment.Center,
            ) {
                Icon(ScIcons.Return, chatStrings().send, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        } else {
            // ⚠️ To'r ochiq bo'lsa bu tugma HECH NARSA qilmaydi — u shunchaki "hozir
            // galereyadasiz" degan yorliq. Ilgari u tizim tanlagichini ochardi va
            // foydalanuvchi bitta ishni ikkita ekranda (ikkinchisida «Done» bilan)
            // bajarardi. Ruxsat bo'lmaganda esa u yagona ishlaydigan yo'l bo'lib qoladi.
            AttachAction(
                icon = AppIcons.ImageIcon,
                label = chatStrings().gallery,
                active = galleryOpen,
                modifier = Modifier.weight(1f),
                onClick = { if (!galleryOpen) onOpenSystemPicker() },
            )
            AttachAction(ScIcons.Paperclip, chatStrings().file, false, Modifier.weight(1f), onPickFile)
            AttachAction(AppIcons.Camera, chatStrings().camera, false, Modifier.weight(1f), onCaptureVideo)
            // Dumaloq video xabar — kamerada yozib olinadi, so'ng markazidan kvadrat
            // kesilib yuboriladi. Kameradan alohida band: natija boshqa turdagi xabar
            // (`VIDEO_NOTE`) va uni izoh bilan yuborib bo'lmaydi.
            AttachAction(ScIcons.Video, chatStrings().videoNote, false, Modifier.weight(1f), onRecordVideoNote)
        }
    }
}

@Composable
private fun AttachAction(
    icon: ImageVector,
    label: String,
    /** Faol yo'l — hozir ekranda ochiq turgani (galereya). */
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier.clip(RoundedCornerShape(22.dp))
            .background(if (active) Sc.Brand.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon, null, tint = if (active) Sc.Brand else Sc.Muted, modifier = Modifier.size(20.dp))
        ScText(label, 11.5f, FontWeight.Bold, if (active) Sc.Brand else Sc.Muted)
    }
}
