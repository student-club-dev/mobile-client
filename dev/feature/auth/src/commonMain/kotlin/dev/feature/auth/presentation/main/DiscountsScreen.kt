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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.core.domain.model.DiscountCategory
import dev.core.domain.model.DiscountOffer
import dev.core.domain.model.DiscountTag
import dev.core.uikit.components.AppFontFamily
import dev.core.uikit.components.ScCircleButton
import dev.core.uikit.components.ScEmptyState
import dev.core.uikit.components.ScHeader
import dev.core.uikit.components.ScHeaderSubtitle
import dev.core.uikit.components.ScHeaderTitle
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScNotFoundTitle
import dev.core.uikit.theme.Sc
import dev.core.uikit.components.AppIcons
import dev.core.uikit.components.GlassTextField
import dev.core.uikit.components.ScShimmerCard
import dev.core.uikit.components.ScShimmerList
import dev.core.uikit.components.ScNetworkImage
import dev.core.uikit.components.ScSearchOverlay
import dev.core.uikit.components.ScPullRefresh
import dev.core.uikit.theme.AppPalette
import dev.core.uikit.theme.appPalette
import dev.feature.listings.presentation.NearbyDiscountsSection
import dev.core.uikit.map.OfferMarker
import dev.core.uikit.map.MapLinkButton
import dev.core.uikit.map.OffersMapOverlay
import dev.core.uikit.map.markersCenter
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import dev.core.common.format.formatAmountShort
import dev.core.common.format.formatAmount

/**
 * "Siz uchun" feed.
 *
 * [initialGroupKey] berilsa ekran o'sha katalog bo'limi bilan ochiladi (Home'dagi
 * "🍕 Ovqatlar → Barchasi"): sarlavha bo'lim nomiga aylanadi va filtr shu bo'limga
 * qo'yiladi — foydalanuvchi uni Filter'dan o'zgartira oladi.
 */
@Composable
fun DiscountsScreen(
    vm: DiscountsViewModel = koinViewModel(),
    onBack: (() -> Unit)? = null,
    initialGroupKey: String? = null,
) {
    val palette = appPalette
    // Faqat ekran ochilganda: keyin foydalanuvchi Filter'da bo'limni o'zgartirsa
    // (kalit o'zgarmagani uchun) qayta tiklanmaydi.
    LaunchedEffect(initialGroupKey) { vm.openGroup(initialGroupKey) }
    val state by vm.state.collectAsStateWithLifecycle()
    val filterState by vm.filterState.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val detail by vm.detail.collectAsStateWithLifecycle()
    val catalogOpen by vm.catalogOpen.collectAsStateWithLifecycle()
    val catalog by vm.catalogState.collectAsStateWithLifecycle()
    val pullRefreshing by vm.pullRefreshing.collectAsStateWithLifecycle()
    var showFilter by remember { mutableStateOf(false) }
    var showMap by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        // Ekranga kirilganda avval KATALOG: biznes turlari bo'limlarga birlashtirilgan holda.
        // Bo'lim bosilgach uning barcha turlari bitta feed'da ochiladi.
        if (catalogOpen) {
            CatalogContent(
                catalog, palette,
                onBack = onBack,
                onOpenSection = vm::openSection,
                onOpenType = vm::openType,
                onOpenAll = vm::openAllOffers,
                onOpenSearch = { showSearch = true },
                refreshing = pullRefreshing,
                onRefresh = vm::refresh,
            )
        } else {
            FeedContent(
                state, palette, vm,
                // Orqaga — katalogga qaytadi. Tashqi `onBack` (ekran stack'da ochilgan
                // holat) faqat katalogda ishlaydi: ikki qadam bitta tugmaga sig'maydi.
                onBack = vm::backToCatalog,
                onOpenFilter = { vm.openFilter(); showFilter = true },
                onOpenMap = { showMap = true },
                onOpenSearch = { showSearch = true },
                refreshing = pullRefreshing,
                onRefresh = vm::refresh,
            )
        }
        // Qidiruv — klaviatura ustidagi suzuvchi maydon (E'lonlar ekranidagi bilan bir xil).
        // Server takliflari (`/v1/discounts/suggest`) maydon ostidagi tasmaga chiqadi.
        if (showSearch) {
            ScSearchOverlay(
                query = state.query,
                onQuery = vm::onQuery,
                onClose = { showSearch = false },
                placeholder = "Do'kon yoki e'lon qidiring",
                suggestions = suggestions.map { it.label },
                onSuggestionPick = { label ->
                    suggestions.firstOrNull { it.label == label }?.let(vm::onSuggestionPicked)
                },
            )
        }
        if (showMap) {
            MapOverlay(
                state, filterState, palette, vm,
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
// Katalog — biznes turlari (ekranga kirilgandagi birinchi ko'rinish)
// ---------------------------------------------------------------------------

/**
 * Katalog — biznes turlari BO'LIMLARGA birlashtirilgan holda, ikki ustunli to'r.
 *
 * Ilgari 27 ta tur alohida katak edi ("Somsa", "Fast food", "Milliy taomlar" — uchtasi
 * yonma-yon); endi ular serverdagi guruh bo'yicha bitta "Ovqatlanish" katagiga yig'iladi,
 * turlar esa bo'lim ichida chip bo'lib chiqadi.
 *
 * Ro'yxat ILOVADA yozilmagan: bo'limlar ham, turlar ham (nomi/emojisi/rangi) serverdan
 * (`POST /v1/catalog/groups` + `/v1/catalog/types`). Yangi tur qo'shilsa ekranga tegish
 * shart emas — u o'z bo'limi ichida paydo bo'ladi.
 */
@Composable
private fun CatalogContent(
    catalog: CatalogUiState,
    palette: AppPalette,
    onBack: (() -> Unit)?,
    onOpenSection: (CatalogSection) -> Unit,
    onOpenType: (DiscountCategory) -> Unit,
    onOpenAll: () -> Unit,
    onOpenSearch: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Sc.Bg)) {
        ScHeader {
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (onBack != null) {
                    ScCircleButton(ScIcons.ChevronLeft, onBack, contentDescription = "Orqaga")
                }
                Column(Modifier.weight(1f)) {
                    ScHeaderTitle("Takliflar", size = 21f)
                    Spacer(Modifier.height(3.dp))
                    ScHeaderSubtitle("Yo'nalishni tanlang")
                }
                ScCircleButton(ScIcons.Search, onClick = onOpenSearch, contentDescription = "Qidiruv")
            }
        }

        // Tepadan tortish — katalog va e'lonlar serverdan qayta o'qiladi.
        ScPullRefresh(refreshing = refreshing, onRefresh = onRefresh) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 110.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                // "Barchasi" — turlarsiz to'liq feed. Butun qatorni egallaydi.
                item(span = { GridItemSpan(maxLineSpan) }) {
                    AllOffersCard(catalog.totalOffers, palette, onOpenAll)
                }

                if (catalog.loading) {
                    items(6) { ScShimmerCard() }
                }

                items(catalog.sections, key = { it.key }) { section ->
                    CatalogSectionCard(section, palette) { onOpenSection(section) }
                }

                // Bo'limga bog'lanmagan turlar — o'z katagi bilan (aks holda ular yo'qolardi).
                items(catalog.looseTypes, key = { it.id }) { type ->
                    CatalogTypeCard(type, palette) { onOpenType(type) }
                }

                // Katalog bo'sh bo'lsa plitka chizilmaydi — tepadagi "Barchasi" kartasi
                // baribir turadi, ya'ni ekran bo'm-bo'sh qolmaydi.
            }
        }
    }
}

/**
 * Katalogdagi bitta BO'LIM: emoji plitka + nom + ichidagi turlar + e'lonlar soni.
 *
 * Turlar qatori ("Milliy taomlar · Fast food · Somsa") ataylab ko'rsatiladi: foydalanuvchi
 * qidirayotgan turi qaysi bo'limga tushganini kartaning o'zidan ko'radi.
 */
@Composable
private fun CatalogSectionCard(
    item: CatalogSection,
    palette: AppPalette,
    onClick: () -> Unit,
) {
    val accent = Color(item.accent)
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.glass)
            .border(1.dp, palette.border, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                item.emoji.ifBlank { "🏷" },
                style = TextStyle(fontFamily = AppFontFamily, fontSize = 21.sp),
            )
        }
        Text(
            item.name,
            style = TextStyle(
                fontFamily = AppFontFamily, fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold, color = palette.ink,
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // Izoh BITTA qatorda, sig'magani "…" bilan kesiladi: turlar ro'yxati uzun bo'lgan
        // bo'limlar (Ovqatlanish) katagini ikki barobar cho'zib, to'rni notekis qilardi.
        Text(
            item.typesPreview,
            style = TextStyle(
                fontFamily = AppFontFamily, fontSize = 11.sp, color = palette.inkFaint,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Sonni server beradi (turlarning `listingsCount` yig'indisi). `0` bo'lsa chizilmaydi.
        if (item.offerCount > 0) {
            Text(
                "${item.offerCount} ta e'lon",
                style = TextStyle(
                    fontFamily = AppFontFamily, fontSize = 11.5f.sp,
                    fontWeight = FontWeight.Bold, color = accent,
                ),
                maxLines = 1,
            )
        }
    }
}

/** Katalogdagi bitta biznes turi: emoji plitka + nom + e'lonlar soni. */
@Composable
private fun CatalogTypeCard(
    type: DiscountCategory,
    palette: AppPalette,
    onClick: () -> Unit,
) {
    val accent = Color(type.accent)
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.glass)
            .border(1.dp, palette.border, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                type.emoji.ifBlank { "🏷" },
                style = TextStyle(fontFamily = AppFontFamily, fontSize = 21.sp),
            )
        }
        Text(
            type.name,
            style = TextStyle(
                fontFamily = AppFontFamily, fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold, color = palette.ink,
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // Sonni server beradi (`CatalogTypeDto.offerCount`) — keshdagi e'lonlar emas,
        // shuning uchun bu yerda hisoblanmaydi. `0` bo'lsa qator umuman chizilmaydi.
        if (type.offerCount > 0) {
            Text(
                "${type.offerCount} ta e'lon",
                style = TextStyle(
                    fontFamily = AppFontFamily, fontSize = 11.5f.sp,
                    fontWeight = FontWeight.Bold, color = accent,
                ),
                maxLines = 1,
            )
        }
    }
}

/** Katalog tepasidagi "Barchasi" kartasi — turlarsiz to'liq feed. */
@Composable
private fun AllOffersCard(totalOffers: Int, palette: AppPalette, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.primaryBrush)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "Barcha takliflar",
                style = TextStyle(
                    fontFamily = AppFontFamily, fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold, color = palette.onPrimary,
                ),
            )
            Text(
                if (totalOffers > 0) "$totalOffers ta e'lon — barcha yo'nalishlar"
                else "Barcha yo'nalishlar bo'yicha",
                style = TextStyle(
                    fontFamily = AppFontFamily, fontSize = 11.5f.sp,
                    color = palette.onPrimary.copy(alpha = 0.85f),
                ),
                maxLines = 1,
            )
        }
        Icon(
            ScIcons.ChevronRight, null,
            tint = palette.onPrimary,
            modifier = Modifier.size(18.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Feed — sarlavha + qidiruv + Filter tugma + e'lonlar
// ---------------------------------------------------------------------------
@Composable
private fun FeedContent(
    state: DiscountsUiState,
    palette: AppPalette,
    vm: DiscountsViewModel,
    onBack: (() -> Unit)?,
    onOpenFilter: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenSearch: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Sc.Bg)) {
        ScHeader {
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (onBack != null) {
                    ScCircleButton(ScIcons.ChevronLeft, onBack, contentDescription = "Orqaga")
                }
                Column(Modifier.weight(1f)) {
                    // Chipdan tur tanlangan bo'lsa — sarlavha o'sha tur ("🥟 Somsa");
                    // aks holda ochiq bo'lim nomi ("🍽 Ovqatlanish", "🛠 Xizmatlar").
                    val type = state.type
                    val section = state.section
                    val title = type?.let { "${it.emoji} ${it.name}".trim() }
                        ?: section?.let { "${it.emoji} ${it.name}".trim() }
                        ?: "Barcha takliflar"
                    // 26f da "🍽 Ovqatlanish" ikkita tugma yonida sig'may kesilardi.
                    ScHeaderTitle(title, size = 21f)
                    Spacer(Modifier.height(3.dp))
                    // Sarlavha yonida ikkita tugma turgani uchun izoh QISQA: uzun matn
                    // ikki qatorga tushib, topbarni cho'zib yuborardi.
                    ScHeaderSubtitle(
                        if (type == null && section == null) {
                            "${state.totalCount} ta e'lon — chegirma va takliflar"
                        } else {
                            "${state.totalCount} ta e'lon"
                        },
                    )
                }
                // Qidiruv — klaviatura ustidagi suzuvchi maydonni ochadi. Qidiruv matni
                // bor bo'lsa tugmada nuqta yonadi (natijalar filtrlanganini bildiradi).
                ScCircleButton(
                    ScIcons.Search,
                    onClick = onOpenSearch,
                    contentDescription = "Qidiruv",
                    badge = state.query.isNotBlank(),
                    badgeColor = Sc.Brand,
                )
                // Filtr: faol filtr bo'lsa tugmada nuqta yonadi (soni Filter ekranida).
                ScCircleButton(
                    ScIcons.Filter,
                    onClick = onOpenFilter,
                    contentDescription = "Filter",
                    badge = state.activeFilterCount > 0,
                    badgeColor = Sc.Brand,
                )
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp)) {
            MapLinkButton(palette, onOpenMap)
        }

        // Bo'lim ichidagi biznes turlari — katalogda birlashtirilgani shu yerda ochiladi
        // ("Ovqatlanish" → Milliy taomlar / Fast food / Somsa).
        TypeChips(
            types = state.sectionTypes,
            selected = state.type?.id,
            palette = palette,
            onSelect = vm::selectType,
        )

        Spacer(Modifier.height(12.dp))

        // Tepadan tortish — feed serverdan qayta o'qiladi (bo'lim ochiq bo'lsa to'liq).
        ScPullRefresh(refreshing = refreshing, onRefresh = onRefresh) {
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
                        // Yuklanayotganda — kartalarning skeleti; tugagach "topilmadi".
                        if (state.loading) {
                            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                                repeat(3) { ScShimmerCard() }
                            }
                        } else {
                            ScEmptyState(
                                Modifier.padding(top = 24.dp),
                                title = ScNotFoundTitle,
                                message = "Bu filtr bo'yicha e'lon topilmadi. Shartlarni yumshating.",
                                icon = ScIcons.Search,
                                tint = palette.glass,
                                iconColor = palette.inkFaint,
                                titleColor = palette.ink,
                                messageColor = palette.inkFaint,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Feed tepasidagi biznes turi chiplari ("Hammasi · 🍛 Milliy taomlar · 🍔 Fast food · 🥟 Somsa").
 *
 * Katalogda turlar bo'limga birlashtirilgani uchun toraytirish AYNAN shu yerda bo'ladi —
 * Filter ekranini ochish shart emas. Tanlov darhol qo'llanadi; ikkinchi marta bosilsa bekor
 * bo'ladi. Bo'lim ochilmagan yoki ichida bitta tur bo'lsa qator umuman chizilmaydi.
 */
@Composable
private fun TypeChips(
    types: List<DiscountCategory>,
    selected: String?,
    palette: AppPalette,
    onSelect: (String?) -> Unit,
) {
    if (types.size < 2) return
    LazyRow(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { TypeChip("Hammasi", selected == null, palette) { onSelect(null) } }
        items(types, key = { it.id }) { type ->
            val label = "${type.emoji} ${type.name}".trim().withCount(type.offerCount.takeIf { it > 0 })
            TypeChip(label, type.id == selected, palette) {
                onSelect(if (type.id == selected) null else type.id)
            }
        }
    }
}

@Composable
private fun TypeChip(label: String, selected: Boolean, palette: AppPalette, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(11.dp))
            .background(if (selected) palette.primary else palette.glass)
            .border(1.dp, if (selected) palette.primary else palette.border, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            style = TextStyle(
                fontFamily = AppFontFamily, fontSize = 12.5f.sp, fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else palette.ink,
            ),
            maxLines = 1,
        )
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
// Xarita overlay — barcha (filtrlangan) e'lonlar narx markerlari bilan
// ---------------------------------------------------------------------------
@Composable
private fun MapOverlay(
    state: DiscountsUiState,
    filterState: FilterDraftState,
    palette: AppPalette,
    vm: DiscountsViewModel,
    onClose: () -> Unit,
    onOpenFilter: () -> Unit,
) {
    // Bo'lim ("Lavash", "Ko'k somsa"...) variantlari serverdan (`/v1/catalog/filter-schema`).
    // Filter ekrani ochilmagan bo'lsa sxema hali yo'q — shu yerda bir marta tortiladi.
    LaunchedEffect(Unit) { vm.ensureSchema() }

    val located = state.offers.filter { it.hasLocation }
    // Bir do'konning bir nechta e'loni bir xil nuqtada turadi — narx pufaklari ustma-ust
    // tushmasligi uchun ular bitta markerga yig'iladi (yorliqda biznes nomi + soni).
    val groups = remember(located) { located.groupBy { it.businessKey() }.values.toList() }
    val markers = groups.map { group ->
        val first = group.first()
        OfferMarker(
            // Guruh markeri bosilganda uning BIRINCHI e'loni id'si qaytadi — pastdagi
            // ro'yxat shu orqali guruhni topadi.
            id = first.id,
            lat = first.lat, lng = first.lng,
            label = if (group.size > 1) first.merchant else "${first.effectivePrice.formatAmountShort()} so'm",
            colorHex = hexRgb(first.bannerAccent),
            highlight = group.any { it.isDiscount },
            count = group.size,
        )
    }

    // Bosilgan pin ichidagi e'lon id'lari. Xarita yaqin turgan bir nechta biznesni bitta
    // pinga qo'shishi mumkin, shuning uchun bittadan ko'p bo'lishi normal.
    var selectedIds by remember { mutableStateOf<List<String>>(emptyList()) }
    val selectedOffers = remember(selectedIds, groups) {
        // Har bir id o'z biznes guruhini olib keladi — varaqda o'sha do'konning hamma
        // (filtrga mos) e'lonlari ko'rinsin.
        selectedIds.flatMap { id -> groups.firstOrNull { g -> g.any { it.id == id } }.orEmpty() }
            .distinctBy { it.id }
    }

    OffersMapOverlay(
        markers = markers,
        palette = palette,
        onClose = onClose,
        center = markersCenter(markers),
        onMarkerTap = { ids -> selectedIds = ids.split(",").filter { it.isNotBlank() } },
        topBarExtras = {
            Box(Modifier.weight(1f)) {
                GlassTextField(state.query, vm::onQuery, "Do'kon yoki e'lon qidiring", leading = AppIcons.Search, height = 46)
            }
            FilterButton(state.activeFilterCount, palette, onOpenFilter)
        },
        belowTopBar = {
            // Kategoriya chiplari — bosilganda markerlar DARHOL filtrlanadi (ro'yxat ham).
            MapCategoryChips(
                categories = filterState.availableSubcategories,
                counts = filterState.subcategoryCounts,
                selected = state.subcategories.firstOrNull(),
                palette = palette,
                onSelect = vm::selectSubcategory,
            )
        },
    )

    // Marker bosilganda — modal bottom sheet: ichida "Siz uchun" feed'idagi KATTA kartalar
    // (bir biznesning bir nechta e'loni bo'lsa hammasi ro'yxat bo'lib chiqadi).
    if (selectedOffers.isNotEmpty()) {
        MapOffersSheet(
            offers = selectedOffers,
            savedIds = state.savedIds,
            palette = palette,
            onToggleSaved = vm::toggleSaved,
            onOpen = { id ->
                selectedIds = emptyList()
                vm.openOffer(id)
            },
            onDismiss = { selectedIds = emptyList() },
        )
    }
}

/**
 * Xaritadagi nuqta bosilganda ochiladigan modal varaq.
 *
 * Kartalar feed'dagi bilan AYNAN bir xil ([DiscountOfferCard] / [RegularOfferCard]) —
 * foydalanuvchi xaritada ham, ro'yxatda ham bitta ko'rinishni ko'radi. Bir biznesning
 * bir nechta e'loni bo'lsa, tepada uning nomi va soni turadi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapOffersSheet(
    offers: List<DiscountOffer>,
    savedIds: Set<String>,
    palette: AppPalette,
    onToggleSaved: (DiscountOffer, Boolean) -> Unit,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = offers.first()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Sc.Bg,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = offers.size > 1),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Sarlavha: bitta do'konning e'lonlari bo'lsa — uning nomi; pin bir nechta
            // do'konni birlashtirgan bo'lsa — nechta do'kon ekani.
            val merchants = offers.map { it.merchant }.distinct()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                OfferThumb(first, Color(first.bannerAccent), size = 42.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        if (merchants.size == 1) first.merchant else "${merchants.size} ta do'kon",
                        style = TextStyle(fontFamily = AppFontFamily, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull("${offers.size} ta e'lon", first.location.takeIf { merchants.size == 1 })
                            .joinToString(" · "),
                        style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.sp, color = palette.inkFaint),
                        maxLines = 1,
                    )
                }
            }

            // Ro'yxat uzun bo'lsa varaq ichida varaqlanadi.
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                items(offers, key = { it.id }) { offer ->
                    val saved = savedIds.contains(offer.id)
                    if (offer.isDiscount) {
                        DiscountOfferCard(offer, saved, palette, onToggleSaved) { onOpen(offer.id) }
                    } else {
                        RegularOfferCard(offer, saved, palette, onToggleSaved) { onOpen(offer.id) }
                    }
                }
            }
        }
    }
}

/**
 * Marker guruhining kaliti — bitta biznesning bitta nuqtasi.
 *
 * Do'kon nomi bilan birga KOORDINATA ham kalitga kiradi: bir tarmoqning turli filiallari
 * xaritada alohida nuqta bo'lib qolishi kerak. Koordinata ~11 m aniqlikda yaxlitlanadi —
 * bir bino ichidagi kichik farqlar bitta nuqtaga yig'ilsin.
 */
private fun DiscountOffer.businessKey(): String {
    fun round(v: Double): Long = (v * 10_000).toLong()
    return "$merchant@${round(lat)},${round(lng)}"
}

/**
 * Xarita ustidagi kategoriya chiplari ("Hammasi · Lavash 3 · Ko'k somsa 1 …").
 *
 * Ro'yxat serverdan keladi (`/v1/catalog/filter-schema` → `categories`), sonlar ham
 * o'shanikidir — shuning uchun "0 natija" beradigan chip ko'rinmaydi.
 *
 * Tanlov DARHOL qo'llanadi: markerlar ham, ostidagi ro'yxat ham bir vaqtda filtrlanadi.
 */
@Composable
private fun MapCategoryChips(
    categories: List<String>,
    counts: Map<String, Int>,
    selected: String?,
    palette: AppPalette,
    onSelect: (String?) -> Unit,
) {
    if (categories.isEmpty()) return
    LazyRow(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            MapChip("Hammasi", selected == null, palette) { onSelect(null) }
        }
        items(categories, key = { it }) { category ->
            MapChip(category.withCount(counts[category]), category == selected, palette) {
                // Ikkinchi marta bosilsa — tanlov bekor bo'ladi.
                onSelect(if (category == selected) null else category)
            }
        }
    }
}

/** Xarita ustidagi chip — fon xarita bo'lgani uchun to'liq qorong'i/oq (shaffof emas). */
@Composable
private fun MapChip(label: String, selected: Boolean, palette: AppPalette, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(11.dp))
            .background(if (selected) palette.primary else palette.glassStrong)
            .border(1.dp, if (selected) palette.primary else palette.border, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            style = TextStyle(
                fontFamily = AppFontFamily, fontSize = 12.5f.sp, fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else palette.ink,
            ),
            maxLines = 1,
        )
    }
}

// ARGB Long -> "#RRGGBB"
private fun hexRgb(argb: Long): String = "#" + (argb and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()


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

            // Joylashuv — boshqa filtrlardan farqli: tanlangan zahoti so'rovga ketadi
            // (`filter.geo.regionIds`) va feed qayta tortiladi.
            RegionSelect(vm, palette)

            // Katalog bo'limi — katalog ekranidagi kataklar bilan bir xil ro'yxat
            // ("Ovqatlanish", "Sport", "Savdo", "Xizmatlar"). Home'dan "Barchasi" bilan
            // kelinganda shu yerda tanlangan bo'lib turadi.
            if (fs.sections.isNotEmpty()) {
                FilterSection("Katalog bo'limi", palette) {
                    CategoryPill("Barchasi", null, d.groupKey == null, palette) { vm.onDraftSection(null) }
                    fs.sections.forEach { s ->
                        // Bo'lingan guruhda (Savdo/Xizmatlar) turlar to'plami ham mos kelishi shart.
                        val selected = d.groupKey == s.groupKey &&
                            (if (s.partial) d.typeKeys == s.typeKeys else d.typeKeys.isEmpty())
                        CategoryPill(
                            "${s.emoji} ${s.name}".trim(), s.accent, selected, palette,
                        ) { vm.onDraftSection(s) }
                    }
                }
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
                fs.priceRange?.let { "${it.first.formatAmount()} – ${it.last.formatAmount()} so'm" },
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

/**
 * "Joylashuv" — bosilganda viloyatlar ro'yxatini ochib beradigan select.
 * Ro'yxat shu yerda, filtrning ichida ochiladi (alohida oyna emas) — filtr o'zi to'liq
 * ekranli qoplama bo'lgani uchun ustiga yana bir oyna qo'yish shart emas.
 */
@Composable
private fun RegionSelect(vm: DiscountsViewModel, palette: AppPalette) {
    val picker by vm.regionPicker.collectAsStateWithLifecycle()
    val selected by vm.selectedRegion.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(expanded) { if (expanded) vm.loadRegions() }

    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Text(
            "Joylashuv",
            style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.ink),
        )
        Spacer(Modifier.size(9.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(palette.glass)
                .border(1.dp, if (expanded) palette.primary else palette.border, RoundedCornerShape(14.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 13.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(ScIcons.MapPin, null, tint = palette.primary, modifier = Modifier.size(17.dp))
            Text(
                selected?.name ?: "Butun O‘zbekiston",
                style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.5f.sp, fontWeight = FontWeight.Bold, color = palette.ink),
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Icon(AppIcons.ChevronDown, null, tint = palette.inkFaint, modifier = Modifier.size(17.dp))
        }

        if (expanded) {
            Spacer(Modifier.size(8.dp))
            when {
                // Viloyat qatorlari o'rniga o'shalarning skeleti.
                picker.loading -> ScShimmerList(rows = 5, leading = false, spacing = 10.dp)

                picker.error != null -> Text(
                    picker.error.orEmpty(),
                    style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.5f.sp, color = Color(0xFFDC2626)),
                )

                else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    RegionOption("Butun O‘zbekiston", selected == null, palette) {
                        vm.selectRegion(null)
                        expanded = false
                    }
                    picker.regions.forEach { region ->
                        RegionOption(region.name, region.id == selected?.id, palette) {
                            vm.selectRegion(region)
                            expanded = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RegionOption(label: String, active: Boolean, palette: AppPalette, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (active) palette.primary.copy(alpha = 0.10f) else palette.glass)
            .border(1.dp, if (active) palette.primary else palette.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            style = TextStyle(
                fontFamily = AppFontFamily, fontSize = 13.sp,
                fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium,
                color = if (active) palette.primary else palette.ink,
            ),
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        if (active) Icon(AppIcons.Check, null, tint = palette.primary, modifier = Modifier.size(16.dp))
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
// Karta rasmi
// ---------------------------------------------------------------------------

/** Feed kartasi bannerining balandligi — Home kartalaridagidek ~kenglikka nisbatan past. */
private val BannerHeight = 150.dp

/**
 * Chegirmali karta banneri: e'lon RASMI (`DiscountOffer.imageUrl`) va uning USTIDAGI
 * ma'lumot — chegirma nishoni, tur yorlig'i, narx va manzil. E'lon nomi/tavsifi kartada
 * YO'Q (ular tafsilot oynasida).
 *
 * Rasm kelguncha kulrang shimmer, havola yo'q/buzuq bo'lsa — turning emoji si; banner
 * hech qachon bo'sh qolmaydi. Matn o'qilishi uchun pastdan qora gradient tushiriladi.
 */
@Composable
private fun OfferBanner(offer: DiscountOffer, accent: Color, saved: Boolean, onToggleSaved: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(BannerHeight).background(accent.copy(alpha = 0.16f))) {
        ScNetworkImage(url = offer.imageUrl, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(offer.emoji, style = TextStyle(fontSize = 46.sp))
            }
        }
        // Matn ostidagi qorayish — och rasmda ham oq matn o'qiladi.
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.40f to Color.Black.copy(alpha = 0.12f),
                    1f to Color.Black.copy(alpha = 0.82f),
                ),
            ),
        )

        if (offer.discountPercent > 0) {
            Box(
                Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(11.dp))
                    .background(accent).padding(horizontal = 11.dp, vertical = 6.dp),
            ) {
                Text("−${offer.discountPercent}%", style = TextStyle(fontFamily = AppFontFamily, fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.White))
            }
        }

        // Saqlash — rasmning o'ng-tepasida (pastda endi matn turadi).
        Icon(
            AppIcons.Bookmark, "Saqlash",
            tint = if (saved) accent else Color.White,
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(22.dp)
                .clickable(onClick = onToggleSaved),
        )

        Column(
            Modifier.align(Alignment.BottomStart).padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Yorliq foni doim oq (rasm ustida ham o'qilsin), shuning uchun matn ham to'q.
            if (offer.subcategory.isNotBlank()) {
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.94f))
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                ) {
                    Text(offer.subcategory, style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = InkOnLight))
                }
            }
            if (offer.originalPrice > 0) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("${offer.finalPrice.formatAmount()} so'm", style = TextStyle(fontFamily = AppFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White))
                    Text("${offer.originalPrice.formatAmount()}", style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.5f.sp, color = Color.White.copy(alpha = 0.7f), textDecoration = TextDecoration.LineThrough))
                    Text("/ ${offer.priceUnit}", style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f)))
                }
            }
            offer.location?.takeIf { it.isNotBlank() }?.let { location ->
                Text(
                    "📍 $location",
                    style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.sp, color = Color.White.copy(alpha = 0.78f)),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Oddiy (chegirmasiz) kartadagi kichik plitka — rasm bo'lsa rasm, bo'lmasa emoji. */
@Composable
private fun OfferThumb(offer: DiscountOffer, accent: Color, size: Dp = 52.dp) {
    val shape = RoundedCornerShape(13.dp)
    Box(Modifier.size(size).clip(shape).background(accent.copy(alpha = 0.12f))) {
        ScNetworkImage(url = offer.imageUrl, modifier = Modifier.fillMaxSize(), shape = shape) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(offer.emoji, style = TextStyle(fontSize = 24.sp))
            }
        }
    }
}

/** Oq yorliq ustidagi matn — mavzudan qat'i nazar to'q (fon doim oq). */
private val InkOnLight = Color(0xFF0F2A43)

// ---------------------------------------------------------------------------
// Chegirmali e'lon kartasi — rasmli banner + eski/yangi narx
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

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.glass).border(1.dp, palette.border, RoundedCornerShape(18.dp)).clickable(onClick = onOpen)) {
        // E'lon ma'lumoti rasm USTIDA; rasm ostida faqat biznes nomi qoladi.
        OfferBanner(offer, accent, saved) { onToggleSaved(offer, saved) }
        Text(
            offer.merchant,
            style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
        )
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
        OfferThumb(offer, accent)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            // Faqat biznes nomi — e'lon nomi kartada ko'rsatilmaydi.
            Text(offer.merchant, style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.5f.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (offer.subcategory.isNotBlank()) {
                    Box(Modifier.clip(RoundedCornerShape(7.dp)).background(accent.copy(alpha = 0.12f)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                        Text(offer.subcategory, style = TextStyle(fontFamily = AppFontFamily, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = accent))
                    }
                }
                offer.location?.let { Text(it, style = TextStyle(fontFamily = AppFontFamily, fontSize = 10.5f.sp, color = palette.inkFaint), maxLines = 1) }
            }
            if (offer.originalPrice > 0) {
                Text("${offer.originalPrice.formatAmount()} so'm / ${offer.priceUnit}", style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Black, color = palette.ink))
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

/** Filtr chipiga server bergan sonni qo'shadi: "Pitsa" → "Pitsa · 54". Son yo'q bo'lsa — o'zi. */
private fun String.withCount(count: Int?): String = if (count == null) this else "$this · $count"
