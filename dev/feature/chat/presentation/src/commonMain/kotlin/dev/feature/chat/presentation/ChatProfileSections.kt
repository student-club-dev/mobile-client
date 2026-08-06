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

/**
 * Uch ustunli media to'ri — **oddiy `Column` + `Row`**, `LazyVerticalGrid` EMAS.
 *
 * ⚠️ Ilgari bu yerda `userScrollEnabled = false` va qo'lda hisoblangan balandlikka ega
 * `LazyVerticalGrid` turardi. U ko'rinmaydigan, lekin og'ir nuqson keltirdi: lazy tartib
 * o'zi aylanmasa ham **nested scroll dispatcher'iga ega** va joylashuvi o'zgarganda
 * (rasmlar kelganda) ota-scrollga delta uzatadi. Bu bo'lim esa profil varag'ining
 * `verticalScroll` ustunida, ustida yig'iluvchi sarlavha bilan yashaydi — natijada
 * media kelgan zahoti sarlavha **o'z-o'zidan topbargacha yig'ilib** qolardi va uni
 * qaytarib ochib bo'lmasdi (foydalanuvchi barmog'i umuman ishtirok etmagan holda).
 *
 * Qatorlarga bo'lish esa hech narsa hisoblamaydi va balandlik tabiiy chiqadi. Aynan shu
 * sabab `PostGrid` da ham lazy to'r ishlatilmagan — izohi o'sha yerda.
 */
@Composable
internal fun ChatPhotoGrid(photos: List<ChatMediaItem>, onOpen: (Int) -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PHOTO_GAP),
    ) {
        photos.chunked(PHOTO_COLUMNS).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(PHOTO_GAP)) {
                row.forEachIndexed { columnIndex, photo ->
                    Box(
                        Modifier.weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Sc.Chip)
                            .clickable { onOpen(rowIndex * PHOTO_COLUMNS + columnIndex) },
                    ) {
                        AsyncImage(
                            model = photo.url,
                            contentDescription = "Rasm",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                // Oxirgi qator to'lmasa qolgan joy bo'sh qoladi — aks holda ikkita rasm
                // butun kenglikka cho'zilib, katak o'lchamlari qatorma-qator o'zgarardi.
                repeat(PHOTO_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
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


private const val PHOTO_COLUMNS = 3
private val PHOTO_GAP = 4.dp
