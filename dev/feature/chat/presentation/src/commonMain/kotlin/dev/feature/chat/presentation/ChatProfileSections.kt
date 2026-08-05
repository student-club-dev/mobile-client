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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScText
import dev.core.uikit.theme.Sc

// Suhbatdosh profilidagi CHAT bo'limlari — «Media», «Fayllar», «Havolalar».
//
// Profil varag'ining o'zi endi umumiy (`connections:presentation` dagi
// `StudentProfileSheet`): u story lentasidan ham, chatdan ham bir xil ochiladi. Faqat
// bo'limlarning MAZMUNI moduldan modulga farq qiladi va u shu yerda qoladi — suhbat
// tarixidan yig'ilgan rasm/fayl/havolalarni chatdan boshqa hech kim bilmaydi.

@Composable
internal fun ChatPhotoGrid(photos: List<ChatMediaItem>, onOpen: (Int) -> Unit) {
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
internal fun ChatLinkList(links: List<ChatLinkUi>) {
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
internal fun ChatFileList(files: List<ChatFileUi>, onOpen: (ChatFileUi) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Sc.Card)) {
        files.forEach { file ->
            Row(
                Modifier.fillMaxWidth()
                    .clickable { onOpen(file) }
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
                    ScText(file.fileName, 14f, FontWeight.Bold, Sc.Ink, maxLines = 1)
                    val size = ChatFormat.fileSize(file.sizeBytes)
                    if (size.isNotEmpty()) {
                        ScText(size, 11.5f, FontWeight.Medium, Sc.Muted, maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * Profil varag'idagi bo'limning bir qatorlik izohi — hozircha faqat "Yuklanmoqda…" uchun.
 *
 * Bo'sh holat bu yerda chizilmaydi: ma'lumoti yo'q bo'lim ro'yxatga umuman qo'shilmaydi
 * ([rememberPeerProfileSections]), ya'ni foydalanuvchi bo'sh tabga tusha olmaydi.
 */
@Composable
internal fun ChatSectionNote(text: String) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Sc.Card),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.padding(28.dp)) { ScText(text, 13f, FontWeight.Medium, Sc.MutedLight) }
    }
}


private const val PHOTO_COLUMNS = 3
private val PHOTO_CELL = 108.dp
private val PHOTO_GAP = 4.dp
