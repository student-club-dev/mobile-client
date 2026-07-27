package dev.feature.auth.presentation.main

import dev.core.uikit.components.scTopInset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.core.domain.model.DiscountOffer
import dev.core.domain.model.DiscountTag
import dev.core.domain.model.OfferSuggestion
import dev.core.domain.model.SuggestionKind
import dev.core.uikit.components.AppFontFamily
import dev.core.uikit.components.ScCircleButton
import dev.core.uikit.components.ScHeader
import dev.core.uikit.components.ScHeaderSubtitle
import dev.core.uikit.components.ScHeaderTitle
import dev.core.uikit.components.ScIcons
import dev.core.uikit.theme.Sc
import dev.core.uikit.components.AppIcons
import dev.core.uikit.components.GlassTextField
import dev.core.uikit.theme.AppPalette
import dev.core.uikit.theme.appPalette
import dev.feature.listings.presentation.NearbyDiscountsSection
import dev.core.uikit.map.OfferMarker
import dev.core.uikit.map.MapLinkButton
import dev.core.uikit.map.OffersMapOverlay
import dev.core.uikit.map.markersCenter
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DiscountsScreen(vm: DiscountsViewModel = koinViewModel(), onBack: (() -> Unit)? = null) {
    val palette = appPalette
    val state by vm.state.collectAsStateWithLifecycle()
    val filterState by vm.filterState.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val detail by vm.detail.collectAsStateWithLifecycle()
    var showFilter by remember { mutableStateOf(false) }
    var showMap by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        FeedContent(
            state, suggestions, palette, vm,
            onBack = onBack,
            onOpenFilter = { vm.openFilter(); showFilter = true },
            onOpenMap = { showMap = true },
        )
        if (showMap) {
            MapOverlay(
                state, palette, vm,
                onClose = { showMap = false },
                onOpenFilter = { vm.openFilter(); showFilter = true },
            )
        }
        if (showFilter) {
            FilterScreen(
                filterState, palette, vm,
                onApply = { vm.applyFilters(); showFilter = false },
                onClose = { showFilter = false },
            )
        }
        // Tafsilot hammasining ustida — xarita/filtr ochiq bo'lsa ham ko'rinadi.
        detail?.let { d ->
            OfferDetailSheet(
                state = d,
                saved = d.detail?.id?.let { state.savedIds.contains(it) } ?: false,
                palette = palette,
                onToggleSaved = vm::toggleSaved,
                onClose = vm::closeOffer,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Feed — sarlavha + qidiruv + Filter tugma + e'lonlar
// ---------------------------------------------------------------------------
@Composable
private fun FeedContent(
    state: DiscountsUiState,
    suggestions: List<OfferSuggestion>,
    palette: AppPalette,
    vm: DiscountsViewModel,
    onBack: (() -> Unit)?,
    onOpenFilter: () -> Unit,
    onOpenMap: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Sc.Bg)) {
        ScHeader {
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                if (onBack != null) {
                    ScCircleButton(ScIcons.ChevronLeft, onBack, contentDescription = "Orqaga")
                }
                Column(Modifier.weight(1f)) {
                    ScHeaderTitle("Siz uchun", size = 26f)
                    Spacer(Modifier.height(3.dp))
                    ScHeaderSubtitle("${state.totalCount} ta e'lon — chegirmali va oddiy takliflar.")
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(Modifier.weight(1f)) {
                    GlassTextField(state.query, vm::onQuery, "Do'kon yoki e'lon qidiring", leading = AppIcons.Search, height = 46)
                }
                FilterButton(state.activeFilterCount, palette, onOpenFilter)
            }
            // Server takliflari — yozayotganda qatorning tagida chiqadi (`/v1/discounts/suggest`).
            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                SuggestionList(suggestions, palette, vm::onSuggestionPicked)
            }
            Spacer(Modifier.height(9.dp))
            MapLinkButton(palette, onOpenMap)
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            item { NearbyDiscountsSection() }

            items(state.offers, key = { it.id }) { offer ->
                val saved = state.savedIds.contains(offer.id)
                val openDetail = { vm.openOffer(offer.id) }
                if (offer.isDiscount) DiscountOfferCard(offer, saved, palette, vm::toggleSaved, openDetail)
                else RegularOfferCard(offer, saved, palette, vm::toggleSaved, openDetail)
            }

            if (state.offers.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                        Text("Bu filtr bo'yicha e'lon topilmadi.", style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, color = palette.inkFaint))
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterButton(activeCount: Int, palette: AppPalette, onClick: () -> Unit) {
    val active = activeCount > 0
    Row(
        Modifier.height(46.dp).clip(RoundedCornerShape(13.dp))
            .background(if (active) palette.primary else palette.glass)
            .border(1.dp, if (active) palette.primary else palette.border, RoundedCornerShape(13.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Filter", style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (active) Color.White else palette.ink))
        if (active) {
            Box(Modifier.size(19.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.28f)), contentAlignment = Alignment.Center) {
                Text("$activeCount", style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Qidiruv takliflari (`POST /v1/discounts/suggest`)
// ---------------------------------------------------------------------------
@Composable
private fun SuggestionList(
    suggestions: List<OfferSuggestion>,
    palette: AppPalette,
    onPick: (OfferSuggestion) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(palette.glassStrong)
            .border(1.dp, palette.border, RoundedCornerShape(13.dp)),
    ) {
        suggestions.forEach { s ->
            Row(
                Modifier.fillMaxWidth().clickable { onPick(s) }.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(s.kind.icon(), style = TextStyle(fontSize = 13.sp))
                Text(
                    s.label,
                    style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.ink),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${s.count}",
                    style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.5f.sp, color = palette.inkFaint),
                )
            }
        }
    }
}

/** Taklif turi — bir qarashda ajratish uchun kichik belgi. */
private fun SuggestionKind.icon(): String = when (this) {
    SuggestionKind.TYPE -> "🏷"
    SuggestionKind.CATEGORY -> "📂"
    SuggestionKind.BUSINESS -> "🏪"
    SuggestionKind.LISTING -> "🔎"
}

// ---------------------------------------------------------------------------
// Xarita overlay — barcha (filtrlangan) e'lonlar narx markerlari bilan
// ---------------------------------------------------------------------------
@Composable
private fun MapOverlay(
    state: DiscountsUiState,
    palette: AppPalette,
    vm: DiscountsViewModel,
    onClose: () -> Unit,
    onOpenFilter: () -> Unit,
) {
    val located = state.offers.filter { it.hasLocation }
    val markers = located.map { o ->
        OfferMarker(
            id = o.id, lat = o.lat, lng = o.lng,
            label = "${o.effectivePrice.priceShort()} so'm",
            colorHex = hexRgb(o.bannerAccent),
            highlight = o.isDiscount,
        )
    }

    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = located.firstOrNull { it.id == selectedId }

    OffersMapOverlay(
        markers = markers,
        palette = palette,
        onClose = onClose,
        center = markersCenter(markers),
        onMarkerTap = { selectedId = it },
        topBarExtras = {
            Box(Modifier.weight(1f)) {
                GlassTextField(state.query, vm::onQuery, "Do'kon yoki e'lon qidiring", leading = AppIcons.Search, height = 46)
            }
            FilterButton(state.activeFilterCount, palette, onOpenFilter)
        },
        detail = {
            // Marker bosilganda — tafsilot kartasi (pastda)
            if (selected != null) {
                MarkerDetailCard(
                    offer = selected,
                    saved = state.savedIds.contains(selected.id),
                    palette = palette,
                    onToggleSaved = vm::toggleSaved,
                    onClose = { selectedId = null },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                )
            }
        },
    )
}

// Xaritada marker bosilganda chiqadigan kengaytirilgan e'lon kartasi.
@Composable
private fun MarkerDetailCard(
    offer: DiscountOffer,
    saved: Boolean,
    palette: AppPalette,
    onToggleSaved: (DiscountOffer, Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = Color(offer.bannerAccent)
    val clipboard = LocalClipboardManager.current
    var copied by remember(offer.id) { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1500); copied = false } }

    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.glassStrong).border(1.dp, palette.border, RoundedCornerShape(18.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(13.dp)).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Text(offer.emoji, style = TextStyle(fontSize = 24.sp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${offer.merchant} — ${offer.title}", style = TextStyle(fontFamily = AppFontFamily, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (offer.subcategory.isNotBlank()) {
                    Text(offer.subcategory, style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accent))
                }
            }
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(palette.glass).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Text("✕", style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Black, color = palette.inkMuted)) }
        }

        // Narx
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("${offer.effectivePrice.sum()} so'm", style = TextStyle(fontFamily = AppFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Black, color = if (offer.isDiscount) accent else palette.ink))
            if (offer.isDiscount && offer.originalPrice > offer.finalPrice) {
                Text("${offer.originalPrice.sum()}", style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.sp, color = palette.inkFaint, textDecoration = TextDecoration.LineThrough))
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(accent).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text("−${offer.discountPercent}%", style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White))
                }
            }
            Text("/ ${offer.priceUnit}", style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.sp, color = palette.inkFaint))
        }

        OfferTagRow(offer, saved, palette, clipboard, copied, onCopy = { copied = true }, onToggleSaved = onToggleSaved)

        val meta = listOfNotNull(offer.location?.let { "📍 $it" }, offer.expiry).joinToString(" · ")
        if (meta.isNotBlank()) Text(meta, style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.sp, color = palette.inkFaint))
    }
}

// ARGB Long -> "#RRGGBB"
private fun hexRgb(argb: Long): String = "#" + (argb and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()

// 21000 -> "21k", 890000 -> "890k", 6500000 -> "6.5M"
private fun Long.priceShort(): String = when {
    this >= 1_000_000 -> {
        val whole = this / 1_000_000
        val frac = (this % 1_000_000) / 100_000
        if (frac == 0L) "${whole}M" else "$whole.${frac}M"
    }
    this >= 1_000 -> "${this / 1_000}k"
    else -> "$this"
}

// ---------------------------------------------------------------------------
// Filter ekrani — barcha bo'limlarni to'liq filterlash
// ---------------------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterScreen(
    fs: FilterDraftState,
    palette: AppPalette,
    vm: DiscountsViewModel,
    onApply: () -> Unit,
    onClose: () -> Unit,
) {
    val d = fs.draft
    Column(Modifier.fillMaxSize().background(palette.bgBrush)) {
        // Sarlavha
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).scTopInset().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(palette.glass).border(1.dp, palette.border, RoundedCornerShape(12.dp)).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.ArrowLeft, "Yopish", tint = palette.ink, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.size(12.dp))
            Text("Filter", style = TextStyle(fontFamily = AppFontFamily, fontSize = 20.sp, fontWeight = FontWeight.Black, color = palette.ink), modifier = Modifier.weight(1f))
            Text("Tozalash", style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.primary), modifier = Modifier.clickable { vm.resetDraft() })
        }

        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            // Chegirma holati — sonlar server sxemasidan (`filter-schema`), bo'lsa.
            FilterSection("Chegirma holati", palette) {
                FilterPill("Hammasi".withCount(fs.kindCounts["ALL"]), d.discountFilter == DiscountFilter.ALL, palette) { vm.onDraftDiscountFilter(DiscountFilter.ALL) }
                FilterPill("Chegirmali".withCount(fs.kindCounts["DISCOUNT"]), d.discountFilter == DiscountFilter.DISCOUNT, palette) { vm.onDraftDiscountFilter(DiscountFilter.DISCOUNT) }
                FilterPill("Chegirmasiz".withCount(fs.kindCounts["REGULAR"]), d.discountFilter == DiscountFilter.REGULAR, palette) { vm.onDraftDiscountFilter(DiscountFilter.REGULAR) }
            }

            // Biznes turi
            FilterSection("Biznes turi", palette) {
                CategoryPill("Barchasi", null, d.categoryId == null, palette) { vm.onDraftCategory(null) }
                fs.categories.forEach { cat ->
                    val label = "${cat.emoji} ${cat.name}".withCount(fs.typeCounts[cat.id])
                    CategoryPill(label, cat.accent, d.categoryId == cat.id, palette) { vm.onDraftCategory(cat.id) }
                }
            }

            // Jins (faqat tanlangan turda jins bo'lsa — masalan kiyim)
            if (fs.genderApplicable) {
                FilterSection("Jins", palette) {
                    FilterPill("Hammasi", d.gender == null, palette) { vm.onDraftGender(null) }
                    FilterPill("Erkak", d.gender == "MALE", palette) { vm.onDraftGender("MALE") }
                    FilterPill("Ayol", d.gender == "FEMALE", palette) { vm.onDraftGender("FEMALE") }
                }
            }

            // Bo'lim — server sxemasi kelgan bo'lsa undan (sonlari bilan), aks holda keshdan.
            if (fs.availableSubcategories.isNotEmpty()) {
                FilterSection("Bo'lim", palette) {
                    fs.availableSubcategories.forEach { sub ->
                        FilterPill(sub.withCount(fs.subcategoryCounts[sub]), sub in d.subcategories, palette) {
                            vm.toggleDraftSubcategory(sub)
                        }
                    }
                }
            }

            // Saralash
            FilterSection("Saralash", palette) {
                FilterPill("Mos", d.sort == OfferSort.RELEVANCE, palette) { vm.onDraftSort(OfferSort.RELEVANCE) }
                FilterPill("Chegirma %", d.sort == OfferSort.DISCOUNT_DESC, palette) { vm.onDraftSort(OfferSort.DISCOUNT_DESC) }
                FilterPill("Arzon", d.sort == OfferSort.PRICE_ASC, palette) { vm.onDraftSort(OfferSort.PRICE_ASC) }
                FilterPill("Qimmat", d.sort == OfferSort.PRICE_DESC, palette) { vm.onDraftSort(OfferSort.PRICE_DESC) }
            }

            // Sxemadagi ma'lumot — serverda nechta e'lon bor va narxlar oralig'i qanday.
            val schemaInfo = listOfNotNull(
                fs.schemaTotal?.let { "Serverda $it ta e'lon" },
                fs.priceRange?.let { "${it.first.sum()} – ${it.last.sum()} so'm" },
            ).joinToString(" · ")
            if (schemaInfo.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(schemaInfo, style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.5f.sp, color = palette.inkFaint))
            }

            Spacer(Modifier.height(16.dp))
        }

        // Pastki panel — Qo'llash (jonli natija soni bilan).
        // Pastki tab paneli (StudentShell BottomBar ~88.dp) ustidan ko'rinishi uchun bottom padding.
        Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp)) {
            Box(
                Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp)).background(palette.primary).clickable(onClick = onApply),
                contentAlignment = Alignment.Center,
            ) {
                Text("Qo'llash · ${fs.previewCount} ta e'lon", style = TextStyle(fontFamily = AppFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.White))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(title: String, palette: AppPalette, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Text(title, style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.5f.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink))
        Spacer(Modifier.height(9.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

// ---------------------------------------------------------------------------
// Chip'lar
// ---------------------------------------------------------------------------
@Composable
private fun FilterPill(label: String, selected: Boolean, palette: AppPalette, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(11.dp))
            .background(if (selected) palette.primary else palette.glass)
            .border(1.dp, if (selected) palette.primary else palette.border, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.5f.sp, fontWeight = FontWeight.Bold, color = if (selected) Color.White else palette.ink))
    }
}

@Composable
private fun CategoryPill(label: String, accent: Long?, selected: Boolean, palette: AppPalette, onClick: () -> Unit) {
    val tint = accent?.let { Color(it) } ?: palette.primary
    Box(
        Modifier.clip(RoundedCornerShape(11.dp))
            .background(if (selected) tint.copy(alpha = 0.16f) else palette.glass)
            .border(1.dp, if (selected) tint else palette.border, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(label, style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (selected) tint else palette.inkMuted), maxLines = 1)
    }
}

// ---------------------------------------------------------------------------
// Chegirmali e'lon kartasi — rangli banner + eski/yangi narx
// ---------------------------------------------------------------------------
@Composable
private fun DiscountOfferCard(
    offer: DiscountOffer,
    saved: Boolean,
    palette: AppPalette,
    onToggleSaved: (DiscountOffer, Boolean) -> Unit,
    onOpen: () -> Unit,
) {
    val accent = Color(offer.bannerAccent)
    val clipboard = LocalClipboardManager.current
    var copied by remember(offer.id) { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1500); copied = false } }

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.glass).border(1.dp, palette.border, RoundedCornerShape(18.dp)).clickable(onClick = onOpen)) {
        Box(Modifier.fillMaxWidth().height(64.dp).background(accent.copy(alpha = 0.16f)).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
            Text(offer.emoji, style = TextStyle(fontSize = 30.sp))
            if (offer.subcategory.isNotBlank()) {
                Box(Modifier.align(Alignment.BottomStart).padding(bottom = 8.dp, start = 42.dp)) {
                    Text(offer.subcategory, style = TextStyle(fontFamily = AppFontFamily, fontSize = 10.5f.sp, fontWeight = FontWeight.Bold, color = accent))
                }
            }
            Box(
                Modifier.align(Alignment.CenterEnd).clip(RoundedCornerShape(10.dp)).background(accent).padding(horizontal = 11.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("−${offer.discountPercent}%", style = TextStyle(fontFamily = AppFontFamily, fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.White))
            }
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${offer.merchant} — ${offer.title}", style = TextStyle(fontFamily = AppFontFamily, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)

            if (offer.originalPrice > 0) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("${offer.finalPrice.sum()} so'm", style = TextStyle(fontFamily = AppFontFamily, fontSize = 15.sp, fontWeight = FontWeight.Black, color = accent))
                    Text("${offer.originalPrice.sum()}", style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.5f.sp, color = palette.inkFaint, textDecoration = TextDecoration.LineThrough))
                    Text("/ ${offer.priceUnit}", style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.sp, color = palette.inkFaint))
                }
            }

            OfferTagRow(offer, saved, palette, clipboard, copied, onCopy = { copied = true }, onToggleSaved = onToggleSaved)

            val meta = listOfNotNull(offer.location?.let { "📍 $it" }, offer.expiry).joinToString(" · ")
            if (meta.isNotBlank()) Text(meta, style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.sp, color = palette.inkFaint))
        }
    }
}

// ---------------------------------------------------------------------------
// Chegirmasiz oddiy e'lon kartasi — ixcham gorizontal, bannersiz
// ---------------------------------------------------------------------------
@Composable
private fun RegularOfferCard(
    offer: DiscountOffer,
    saved: Boolean,
    palette: AppPalette,
    onToggleSaved: (DiscountOffer, Boolean) -> Unit,
    onOpen: () -> Unit,
) {
    val accent = Color(offer.bannerAccent)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(palette.glass).border(1.dp, palette.border, RoundedCornerShape(16.dp)).clickable(onClick = onOpen).padding(11.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(13.dp)).background(accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Text(offer.emoji, style = TextStyle(fontSize = 24.sp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("${offer.merchant} — ${offer.title}", style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.5f.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (offer.subcategory.isNotBlank()) {
                    Box(Modifier.clip(RoundedCornerShape(7.dp)).background(accent.copy(alpha = 0.12f)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                        Text(offer.subcategory, style = TextStyle(fontFamily = AppFontFamily, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = accent))
                    }
                }
                offer.location?.let { Text(it, style = TextStyle(fontFamily = AppFontFamily, fontSize = 10.5f.sp, color = palette.inkFaint), maxLines = 1) }
            }
            if (offer.originalPrice > 0) {
                Text("${offer.originalPrice.sum()} so'm / ${offer.priceUnit}", style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Black, color = palette.ink))
            }
        }
        Icon(
            AppIcons.Bookmark, "Saqlash",
            tint = if (saved) palette.primary else palette.inkFaint,
            modifier = Modifier.size(20.dp).clickable { onToggleSaved(offer, saved) },
        )
    }
}

// ---------------------------------------------------------------------------
// Tag (Talaba ID / Promokod) + promo nusxalash + bookmark
// ---------------------------------------------------------------------------
@Composable
private fun OfferTagRow(
    offer: DiscountOffer,
    saved: Boolean,
    palette: AppPalette,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    copied: Boolean,
    onCopy: () -> Unit,
    onToggleSaved: (DiscountOffer, Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val tagText = if (offer.tag == DiscountTag.STUDENT_ID) "Talaba ID" else "Promokod"
        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(palette.primary.copy(alpha = 0.10f)).padding(horizontal = 9.dp, vertical = 4.dp)) {
            Text(tagText, style = TextStyle(fontFamily = AppFontFamily, fontSize = 10.5f.sp, fontWeight = FontWeight.Bold, color = palette.primary))
        }
        val promo = offer.promoCode
        if (promo != null) {
            Spacer(Modifier.size(6.dp))
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).background(palette.primary.copy(alpha = 0.08f))
                    .clickable { clipboard.setText(AnnotatedString(promo)); onCopy() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(if (copied) "Nusxalandi ✓" else promo, style = TextStyle(fontFamily = AppFontFamily, fontSize = 10.5f.sp, fontWeight = FontWeight.ExtraBold, color = palette.primary))
                if (!copied) Icon(AppIcons.FileText, "Nusxalash", tint = palette.primary, modifier = Modifier.size(11.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        Icon(
            AppIcons.Bookmark, "Saqlash",
            tint = if (saved) palette.primary else palette.inkFaint,
            modifier = Modifier.size(20.dp).clickable { onToggleSaved(offer, saved) },
        )
    }
}

// "55000" → "55 000"
private fun Long.sum(): String = toString().reversed().chunked(3).joinToString(" ").reversed()

/** Filtr chipiga server bergan sonni qo'shadi: "Pitsa" → "Pitsa · 54". Son yo'q bo'lsa — o'zi. */
private fun String.withCount(count: Int?): String = if (count == null) this else "$this · $count"
