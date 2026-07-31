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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.core.uikit.components.ScText
import dev.core.uikit.theme.Sc
import dev.core.uikit.components.ScShimmerFooter
import dev.core.uikit.components.ScShimmerGrid
import dev.feature.chat.domain.model.GifItem
import dev.feature.chat.domain.model.Sticker
import dev.feature.chat.domain.model.StickerSearchItem
import dev.feature.chat.domain.model.stickerMessage
import dev.feature.chat.presentation.StickerImage
import kotlinx.coroutines.flow.distinctUntilChanged
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
    onPickStickerRef: (StickerSearchItem) -> Unit,
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
            ChatMediaTab.STICKERS -> RemoteStickerPanel(
                onPick = onPickSticker,
                onPickSearchResult = onPickStickerRef,
            )
            // GIF paneli o'z atribut qatorini o'zi chizadi — u hech qachon yashirilmaydi.
            ChatMediaTab.GIF -> GifPanel(onPick = onPickGif)
        }
    }
}

/** Panel balandligi — GIF paneli bilan bir xil, bo'lim almashganda "sakramasin". */
private val PANEL_HEIGHT = 320.dp

/** Natijalar to'rining ustunlari — stikerlar kvadrat, GIF'dan ko'ra ko'proq sig'adi. */
private const val SEARCH_GRID_COLUMNS = 3

/** Oxiriga necha katak qolganda keyingi sahifa so'raladi. */
private const val PREFETCH_DISTANCE = 9

/**
 * Stiker paneli — **katalog + qidiruv**.
 *
 * Katalog `GET /v1/stickers/packs` dan olinadi; server bo'sh qaytarsa yoki javob bermasa
 * **Fluent Emoji 3D** zaxirasi ko'rsatiladi (`StickerCatalog` — 1600 dan ortiq stiker,
 * 9 ta paket).
 *
 * Qidiruv maydoniga yozilganda panel KLIPY natijalariga o'tadi
 * (`GET /v1/stickers/search` — `handoff/06-STICKER-SEARCH.md`). Bu **qo'shimcha qatlam**:
 * qidiruv ishlamasa ham (endpoint deploy qilinmagan, kvota tugagan, internet yo'q) maydonni
 * tozalash panelni katalogga qaytaradi va hech narsa yo'qolmaydi.
 *
 * [onPick] — katalogdagi stiker (emoji yoki server katalogi qatori).
 * [onPickSearchResult] — KLIPY natijasi; u **boshqacha** yuboriladi (`sticker` obyekti,
 * `stickerId` emas), shuning uchun alohida chaqiruv.
 */
@Composable
fun RemoteStickerPanel(
    onPick: (Sticker) -> Unit,
    onPickSearchResult: (StickerSearchItem) -> Unit,
    modifier: Modifier = Modifier,
    vm: StickerPanelViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(modifier.fillMaxWidth().height(PANEL_HEIGHT)) {
        // Qidiruv bu deploymentda sozlanmagan bo'lsa maydon umuman chizilmaydi — bosilganda
        // har safar xato beradigan tugma foydalanuvchini aldash bo'lardi.
        if (state.searchAvailable) {
            MediaSearchField(
                query = state.query,
                onQueryChange = vm::onQueryChange,
                placeholder = "Stiker qidirish…",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (state.searchMode) {
                StickerSearchResults(state = state, vm = vm, onPick = onPickSearchResult)
            } else {
                StickerCatalogGrid(state = state, onPick = onPick)
            }
        }

        // Atribut faqat KLIPY natijalari ustida — paketlar boshqa manbadan
        // (Fluent Emoji / server katalogi), ularga KLIPY belgisini qo'yish noto'g'ri
        // atribut bo'lardi.
        if (state.searchMode) ProviderAttribution(state.provider)
    }
}

/** Paket yorliqlari + katalog to'ri (qidiruv bo'sh bo'lganda). */
@Composable
private fun StickerCatalogGrid(state: StickerPanelState, onPick: (Sticker) -> Unit) {
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
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
        }
        return
    }
    val pack = packs[packIndex.coerceIn(packs.indices)]

    Column(Modifier.fillMaxSize()) {
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

/**
 * KLIPY qidiruv natijalari.
 *
 * Xato holati ko'rsatiladi (katalogdan farqli): bu yerda jimgina bo'sh to'r qolsa
 * foydalanuvchi qidiruvni o'zi buzgan deb o'ylardi. Endpoint hali deploy qilinmagan
 * bo'lsa ham shu yo'l ishlaydi — `404` "xizmat ishlamayapti" bo'lib ko'rinadi.
 */
@Composable
private fun StickerSearchResults(
    state: StickerPanelState,
    vm: StickerPanelViewModel,
    onPick: (StickerSearchItem) -> Unit,
) {
    val grid = rememberLazyGridState()

    // Cheksiz scroll: oxiriga yaqinlashganda keyingi sahifa. `derivedStateOf` — har piksel
    // surilishda emas, faqat shart o'zgarganda qayta hisoblanadi.
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= grid.layoutInfo.totalItemsCount - PREFETCH_DISTANCE
        }
    }
    LaunchedEffect(grid) {
        snapshotFlow { shouldLoadMore }.distinctUntilChanged().collect { if (it) vm.loadMore() }
    }

    val error = state.error
    when {
        state.searching -> ScShimmerGrid(
            columns = SEARCH_GRID_COLUMNS,
            rows = 4,
            spacing = 6.dp,
            cellHeight = 72.dp,
            modifier = Modifier.padding(vertical = 2.dp),
        )

        error != null -> MediaSearchErrorView(
            message = error.stickerMessage,
            retriable = error.retriable,
            onRetry = vm::retry,
        )

        state.emptyResults -> MediaSearchCenterText("«${state.query}» bo'yicha stiker topilmadi.")

        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(SEARCH_GRID_COLUMNS),
            state = grid,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.results, key = { it.id }) { item ->
                Box(
                    Modifier.fillMaxWidth()
                        // Stikerlar deyarli kvadrat, lekin cho'zilganlari ham uchraydi.
                        .aspectRatio(item.aspectRatio.coerceIn(0.6f, 1.6f))
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            vm.onPicked(item)
                            onPick(item)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = item.thumbUrl,
                        contentDescription = null,
                        // `Fit` — stiker shaffof fonli, `Crop` uni qirqib yuborardi.
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                    )
                }
            }
            if (state.loadingMore) {
                item(span = { GridItemSpan(SEARCH_GRID_COLUMNS) }) {
                    ScShimmerFooter(Modifier.height(48.dp))
                }
            }
        }
    }
}
