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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScSoftButton
import dev.core.uikit.components.ScText
import dev.core.uikit.components.scStyle
import dev.core.uikit.theme.Sc
import dev.feature.chat.domain.model.GifItem
import dev.feature.chat.domain.model.GifProvider
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.compose.viewmodel.koinViewModel

/** Panel balandligi — stiker paneli bilan bir xil his, klaviatura o'rnini egallaydi. */
private val PANEL_HEIGHT = 320.dp

/** To'r ustuni: ikkita — GIF'lar keng va gorizontal, uchtada juda mayda ko'rinadi. */
private const val GRID_COLUMNS = 2

/** Oxiriga necha katak qolganda keyingi sahifa so'raladi. */
private const val PREFETCH_DISTANCE = 6

/**
 * GIF qidiruv paneli (`gif.md`).
 *
 * Uch narsa ataylab shunday qilingan:
 *
 * 1. **Atribut belgisi doim ko'rinadi** — natija bo'lmasa ham, xatoda ham. Bu shartnomaviy
 *    talab va production kalitini olish sharti (ular buni ekran yozuvidan tekshiradi).
 * 2. **Bo'sh so'rov — trending.** Alohida endpoint yo'q, panel ochilishi oddiy qidiruv.
 * 3. Ko'rsatilayotgani — `thumbUrl` (statik kadr). `url` esa **MP4** va uni o'ynatish
 *    Compose Multiplatform'da alohida pleyer talab qiladi — qarang [GifCell] izohi.
 *
 * [onPick] — tanlangan GIF. Panel `POST /gifs/{id}/share` ni o'zi chaqiradi (kontrakt),
 * xabarni **yuborish** esa chaqiruvchining ishi.
 */
@Composable
fun GifPanel(
    onPick: (GifItem) -> Unit,
    modifier: Modifier = Modifier,
    vm: GifPanelViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
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

    Column(modifier.fillMaxWidth().height(PANEL_HEIGHT).background(Sc.Card)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Sc.Border))

        GifSearchField(
            query = state.query,
            onQueryChange = vm::onQueryChange,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )

        Box(Modifier.fillMaxWidth().weight(1f)) {
            val error = state.error
            when {
                state.loading -> CenterProgress()

                error != null -> GifErrorView(
                    message = error.userMessage,
                    retriable = error.retriable,
                    onRetry = vm::retry,
                )

                state.empty -> CenterText(
                    if (state.query.isBlank()) "GIF topilmadi." else "«${state.query}» bo'yicha GIF topilmadi.",
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(GRID_COLUMNS),
                    state = grid,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.items, key = { it.id }) { gif ->
                        GifCell(gif) {
                            vm.onPicked(gif)
                            onPick(gif)
                        }
                    }
                    if (state.loadingMore) {
                        item(span = { GridItemSpan(GRID_COLUMNS) }) {
                            Box(Modifier.fillMaxWidth().height(48.dp), Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(20.dp), Sc.Brand, strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }

        ProviderAttribution(state.provider)
    }
}

/**
 * ⚠️ **«Powered by KLIPY» — majburiy.**
 *
 * Provayder shartlari brend belgisini qidiruv panelida ko'rsatishni talab qiladi va
 * production kalitini olish uchun ular buni **ilova ichidagi ekran yozuvidan** tekshiradi
 * (`PENDING_ACTIONS.md`). Shuning uchun bu qator paneldan **hech qanday holatda
 * yashirilmaydi** — yuklanayotganda ham, xatoda ham, natija bo'lmasa ham.
 *
 * Qaysi brend ko'rsatilishini javobdagi `provider` maydoni aytadi — hozir doim `KLIPY`.
 *
 * TODO(brend): rasmiy logotip Partner Panel'dan olinadi (backend jamoasi beradi). Kelgach
 * shu matn o'rniga `Image` qo'yiladi, joyi va ko'rinish qoidasi o'zgarmaydi.
 */
@Composable
private fun ProviderAttribution(provider: GifProvider) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Sc.Border))
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        ScText(provider.attribution, 11f, FontWeight.Bold, Sc.MutedLight, maxLines = 1)
    }
}

@Composable
private fun GifSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Sc.Chip)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(ScIcons.Search, null, tint = Sc.Muted, modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                ScText("GIF qidirish…", 15f, FontWeight.Medium, Sc.NavIdle, maxLines = 1)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = scStyle(15f, FontWeight.Medium, Sc.Ink),
                cursorBrush = SolidColor(Sc.Brand),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                ScIcons.Close,
                "Tozalash",
                tint = Sc.Muted,
                modifier = Modifier.size(16.dp).clickable { onQueryChange("") },
            )
        }
    }
}

/**
 * Bitta katak.
 *
 * ⚠️ Hozir **statik kadr** (`thumbUrl`) ko'rsatiladi. `url` — ovozsiz, takrorlanuvchi MP4
 * va uni Compose Multiplatform'da o'ynatish uchun umumiy video pleyer kerak (Android'da
 * ExoPlayer, iOS'da `AVPlayer` — `expect/actual` komponent). Loyihada bunday komponent
 * hali yo'q, shuning uchun MP4 keyingi bosqichga qoldirildi: qarang hisobot.
 */
@Composable
private fun GifCell(gif: GifItem, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            // Har bir GIF o'z nisbatida — panelda maket "yirtilib" ketmaydi.
            .aspectRatio(gif.aspectRatio.coerceIn(0.5f, 2f))
            .clip(RoundedCornerShape(12.dp))
            .background(Sc.Chip)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = gif.thumbUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun GifErrorView(message: String, retriable: Boolean, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScText(message, 13.5f, FontWeight.Medium, Sc.InkSoft)
        if (retriable) {
            ScSoftButton(
                text = "Qayta urinish",
                onClick = onRetry,
                modifier = Modifier.padding(top = 12.dp, start = 24.dp, end = 24.dp),
                verticalPadding = 10.dp,
                fontSize = 13.5f,
            )
        }
    }
}

@Composable
private fun CenterProgress() {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        CircularProgressIndicator(Modifier.size(24.dp), Sc.Brand, strokeWidth = 2.dp)
    }
}

@Composable
private fun CenterText(text: String) {
    Box(Modifier.fillMaxSize().padding(horizontal = 28.dp), Alignment.Center) {
        ScText(text, 13.5f, FontWeight.Medium, Sc.MutedLight)
    }
}
