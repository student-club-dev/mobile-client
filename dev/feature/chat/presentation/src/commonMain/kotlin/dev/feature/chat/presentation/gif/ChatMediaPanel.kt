package dev.feature.chat.presentation.gif

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.core.uikit.components.ScText
import dev.core.uikit.theme.Sc
import dev.core.uikit.components.ScShimmerGrid
import dev.feature.chat.domain.model.GifItem
import dev.feature.chat.domain.model.Sticker
import dev.feature.chat.presentation.StickerImage
import org.koin.compose.viewmodel.koinViewModel

/** Kompozitor ustidagi panelning ikki bo'limi. */
enum class ChatMediaTab(val title: String) {
    STICKERS("Stikerlar"),
    GIF("GIF"),
}

/**
 * Kompozitor ustidagi **stiker + GIF** paneli.
 *
 * Ikkalasi bitta panelda: foydalanuvchi uchun bu bitta "ifoda" oynasi, va GIF bo'limi
 * ochilganda atribut belgisi ham shu yerda ko'rinadi.
 *
 * [onPickSticker] — stiker tanlandi. Serverdan kelgan stiker (`Sticker.isRemote`)
 * `stickerId` bilan, zaxira emoji esa matn sifatida yuboriladi — bu farqni **yuborish
 * nuqtasi** hal qiladi (`ChatRepository.sendSticker`), panel emas.
 *
 * [onPickGif] — GIF tanlandi; yuborishda `gif` obyekti **o'zgartirilmasdan** qaytariladi
 * (`GifItem.toRef()`).
 */
@Composable
fun ChatMediaPanel(
    onPickSticker: (Sticker) -> Unit,
    onPickGif: (GifItem) -> Unit,
    modifier: Modifier = Modifier,
    initialTab: ChatMediaTab = ChatMediaTab.STICKERS,
) {
    var tab by remember { mutableStateOf(initialTab) }

    Column(modifier.fillMaxWidth().background(Sc.Card)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Sc.Border))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChatMediaTab.entries.forEach { entry ->
                val active = entry == tab
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .background(if (active) Sc.Brand.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable { tab = entry }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    ScText(
                        entry.title,
                        13f,
                        if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                        if (active) Sc.Brand else Sc.Muted,
                    )
                }
            }
        }

        when (tab) {
            ChatMediaTab.STICKERS -> RemoteStickerPanel(onPick = onPickSticker)
            // GIF paneli o'z atribut qatorini o'zi chizadi — u hech qachon yashirilmaydi.
            ChatMediaTab.GIF -> GifPanel(onPick = onPickGif)
        }
    }
}

/** Panel balandligi — GIF paneli bilan bir xil, bo'lim almashganda "sakramasin". */
private val PANEL_HEIGHT = 320.dp

/**
 * Serverdan kelgan katalog bo'yicha stiker paneli.
 *
 * Katalog `GET /v1/stickers/packs` dan olinadi; server bo'sh qaytarsa yoki javob bermasa
 * **Fluent Emoji 3D** zaxira katalogi ko'rsatiladi (`StickerCatalog` — 1600 dan ortiq
 * stiker, 9 ta paket) — backendda stiker **tasvirlari** hali yo'q
 * (`PENDING_ACTIONS.md` §6).
 */
@Composable
fun RemoteStickerPanel(
    onPick: (Sticker) -> Unit,
    modifier: Modifier = Modifier,
    vm: StickerPanelViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var packIndex by remember { mutableStateOf(0) }
    val packs = state.packs
    if (packs.isEmpty()) {
        // Katalog kelguncha panel bo'sh ochilib qolmasin — kataklarning skeleti.
        if (state.loading) {
            ScShimmerGrid(
                columns = 5,
                rows = 4,
                spacing = 6.dp,
                cellHeight = 52.dp,
                modifier = modifier.fillMaxWidth().height(PANEL_HEIGHT).padding(vertical = 8.dp),
            )
        }
        return
    }
    val pack = packs[packIndex.coerceIn(packs.indices)]

    Column(modifier.fillMaxWidth().height(PANEL_HEIGHT)) {
        // Paket yorliqlari — **surilanadigan**: zaxira katalogda 9 ta paket bor va ular
        // qat'iy `Row` da ekranga sig'masdi (9 × 38dp + oraliq ≈ 400dp).
        LazyRow(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(packs, key = { _, item -> item.id }) { index, item ->
                val active = index == packIndex
                Box(
                    Modifier.size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) Sc.Brand.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable { packIndex = index },
                    contentAlignment = Alignment.Center,
                ) {
                    StickerImage(
                        emoji = item.cover,
                        url = item.coverUrl,
                        fallbackSize = 20f,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(56.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            items(pack.stickers, key = { it.id }) { sticker ->
                Box(
                    Modifier.aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPick(sticker) },
                    contentAlignment = Alignment.Center,
                ) {
                    StickerImage(
                        emoji = sticker.emoji,
                        url = sticker.url,
                        fallbackSize = 30f,
                        modifier = Modifier.fillMaxSize().padding(6.dp),
                    )
                }
            }
        }
    }
}
