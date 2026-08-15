package dev.feature.auth.presentation.main

import dev.core.uikit.components.ScBackButton
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.BottomSheetDefaults
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
import dev.core.domain.model.Region
import dev.core.domain.model.DiscountOffer
import dev.core.domain.model.DiscountTag
import dev.core.uikit.components.AppFontFamily
import dev.core.uikit.components.ScCircleButton
import dev.core.uikit.components.ScEmptyState
import dev.core.uikit.components.ScFavoriteButton
import dev.core.uikit.components.ScHeader
import dev.core.uikit.components.ScHideBottomBar
import dev.core.uikit.components.ScHeaderSubtitle
import dev.core.uikit.components.ScHeaderTitle
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScNotFoundTitle
import dev.core.uikit.theme.Sc
import dev.core.uikit.map.ScLocationLabel
import dev.core.uikit.map.rememberShowOnMap
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
import dev.core.uikit.locale.uiStrings

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
    /**
     * Bosh ekrandagi karta bosilganda — o'sha e'lonning tafsiloti darhol ochiladi
     * (ekran ortida bo'lim feed'i turadi, ya'ni yopilgach foydalanuvchi ro'yxatda qoladi).
     */
    initialOfferId: String? = null,
) {
    val palette = appPalette
    // Faqat ekran ochilganda: keyin foydalanuvchi Filter'da bo'limni o'zgartirsa
    // (kalit o'zgarmagani uchun) qayta tiklanmaydi.
    LaunchedEffect(initialGroupKey) { vm.openGroup(initialGroupKey) }
    LaunchedEffect(initialOfferId) { initialOfferId?.let(vm::openOffer) }
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
                placeholder = discountsStrings().searchHint,
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
                    ScBackButton(onBack, contentDescription = uiStrings().back)
                }
                Column(Modifier.weight(1f)) {
                    ScHeaderTitle(discountsStrings().title, size = 21f)
                    Spacer(Modifier.height(3.dp))
                    ScHeaderSubtitle(discountsStrings().pickDirection)
                }
                ScCircleButton(ScIcons.Search, onClick = onOpenSearch, contentDescription = discountsStrings().search)
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
                // uiStrings().all — turlarsiz to'liq feed. Butun qatorni egallaydi.
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

                // Katalog bo'sh bo'lsa plitka chizilmaydi — tepadagi uiStrings().all kartasi
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
        // `minLines = 2` — nom bir qatorga sig'sa ham ikki qator joy oladi. Bunsiz
        // "Ovqatlanish" (1 qator) va "O'yin va dam olish" (2 qator) kataklari har xil
        // balandlikda chiqib, to'r zinapoyaga o'xshab qolardi (bug hisoboti #39).
        Text(
            item.name,
            style = TextStyle(
                fontFamily = AppFontFamily, fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold, color = palette.ink,
            ),
            minLines = 2,
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
        // Sonni server beradi (turlarning `listingsCount` yig'indisi).
        //
        // ⚠️ `0` bo'lganda ham qator CHIZILADI — faqat qizil "e'lon yo'q" matni bilan.
        // Ilgari u butunlay yashirilardi va yonma-yon turgan ikkita katak har xil
        // balandlikda chiqib, to'r "buzuq" ko'rinardi (bug hisoboti #39). Endi qator
        // har doim bor, ya'ni kataklar bir chiziqda tugaydi — va bo'sh bo'lim ham
        // ochilishidan oldin bo'shligini aytadi.
        Text(
            if (item.offerCount > 0) {
                discountsStrings().offersCount(item.offerCount)
            } else {
                discountsStrings().noListings
            },
            style = TextStyle(
                fontFamily = AppFontFamily, fontSize = 11.5f.sp,
                fontWeight = FontWeight.Bold,
                color = if (item.offerCount > 0) accent else Sc.Danger,
            ),
            maxLines = 1,
        )
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
        // Bo'lim katagi bilan bir xil balandlik uchun — [CatalogSectionCard] izohiga q.
        Text(
            type.name,
            style = TextStyle(
                fontFamily = AppFontFamily, fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold, color = palette.ink,
            ),
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // Sonni server beradi (`CatalogTypeDto.offerCount`) — keshdagi e'lonlar emas,
        // shuning uchun bu yerda hisoblanmaydi. `0` bo'lsa qizil "e'lon yo'q" yoziladi:
        // qator har doim bor, ya'ni to'rdagi kataklar bir chiziqda tugaydi (#39).
        Text(
            if (type.offerCount > 0) {
                discountsStrings().offersCount(type.offerCount)
            } else {
                discountsStrings().noListings
            },
            style = TextStyle(
                fontFamily = AppFontFamily, fontSize = 11.5f.sp,
                fontWeight = FontWeight.Bold,
                color = if (type.offerCount > 0) accent else Sc.Danger,
            ),
            maxLines = 1,
        )
    }
}

/** Katalog tepasidagi uiStrings().all kartasi — turlarsiz to'liq feed. */
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
                discountsStrings().allOffers,
                style = TextStyle(
                    fontFamily = AppFontFamily, fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold, color = palette.onPrimary,
                ),
            )
            Text(
                if (totalOffers > 0) discountsStrings().allDirectionsCount(totalOffers)
                else discountsStrings().allDirections,
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
                    ScBackButton(onBack, contentDescription = uiStrings().back)
                }
                Column(Modifier.weight(1f)) {
                    // Chipdan tur tanlangan bo'lsa — sarlavha o'sha tur ("🥟 Somsa");
                    // aks holda ochiq bo'lim nomi ("🍽 Ovqatlanish", "🛠 Xizmatlar").
                    val type = state.type
                    val section = state.section
                    val title = type?.let { "${it.emoji} ${it.name}".trim() }
                        ?: section?.let { "${it.emoji} ${it.name}".trim() }
                        ?: discountsStrings().allOffers
                    // 26f da "🍽 Ovqatlanish" ikkita tugma yonida sig'may kesilardi.
                    ScHeaderTitle(title, size = 21f)
                    Spacer(Modifier.height(3.dp))
                    // Sarlavha yonida ikkita tugma turgani uchun izoh QISQA: uzun matn
                    // ikki qatorga tushib, topbarni cho'zib yuborardi.
                    ScHeaderSubtitle(
                        if (type == null && section == null) {
                            discountsStrings().offersWithDiscounts(state.totalCount)
                        } else {
                            discountsStrings().offersCount(state.totalCount)
                        },
                    )
                }
                // Qidiruv — klaviatura ustidagi suzuvchi maydonni ochadi. Qidiruv matni
                // bor bo'lsa tugmada nuqta yonadi (natijalar filtrlanganini bildiradi).
                ScCircleButton(
                    ScIcons.Search,
                    onClick = onOpenSearch,
                    contentDescription = discountsStrings().search,
                    badge = state.query.isNotBlank(),
                    badgeColor = Sc.Brand,
                )
                // Filtr: faol filtr bo'lsa tugmada nuqta yonadi (soni Filter ekranida).
                ScCircleButton(
                    ScIcons.Filter,
                    onClick = onOpenFilter,
                    contentDescription = discountsStrings().filter,
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
                    OfferCard(offer, saved, palette, vm::toggleSaved, openDetail)
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
                                message = discountsStrings().noOffersForFilter,
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
        item { TypeChip(discountsStrings().all, selected == null, palette) { onSelect(null) } }
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
private fun FilterButton(
    activeCount: Int,
    palette: AppPalette,
    onClick: () -> Unit,
    height: Dp = 46.dp,
) {
    val active = activeCount > 0
    Row(
        Modifier.height(height).clip(RoundedCornerShape(13.dp))
            .background(if (active) palette.primary else palette.glass)
            .border(1.dp, if (active) palette.primary else palette.border, RoundedCornerShape(13.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(discountsStrings().filter, style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (active) Color.White else palette.ink))
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
            label = if (group.size > 1) first.merchant else "${first.effectivePrice.formatAmountShort()} ${uiStrings().currency}",
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
                // Xaritada boshqaruvlar pastroq balandlikda — orqaga tugmasi bilan bir xil.
                GlassTextField(state.query, vm::onQuery, discountsStrings().searchHint, leading = AppIcons.Search, height = 38)
            }
            FilterButton(state.activeFilterCount, palette, onOpenFilter, height = 38.dp)
        },
        belowTopBar = {
            // ⚠️ Chiplar **biznes turlari** (Milliy taomlar, Fast food…), bo'limlar
            // (Osh/Plov, Burgerlar…) EMAS. Xaritada odam "qayerga borishim mumkin" deb
            // qaraydi — bu do'kon TURI. Bo'lim esa menyu ichidagi taom nomi va u
            // xaritadagi nuqtani tanlashda hech narsa bermaydi (bug hisoboti #30).
            MapTypeChips(
                types = filterState.categories,
                counts = filterState.typeCounts,
                selectedId = state.type?.id,
                palette = palette,
                onSelect = vm::selectType,
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
 * Kartalar feed'dagi bilan AYNAN bir xil ([OfferCard]) —
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
                        if (merchants.size == 1) first.merchant else discountsStrings().shopsCount(merchants.size),
                        style = TextStyle(fontFamily = AppFontFamily, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(discountsStrings().offersCount(offers.size), first.location.takeIf { merchants.size == 1 })
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
                    OfferCard(offer, saved, palette, onToggleSaved) { onOpen(offer.id) }
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
private fun MapTypeChips(
    types: List<DiscountCategory>,
    counts: Map<String, Int>,
    selectedId: String?,
    palette: AppPalette,
    onSelect: (String?) -> Unit,
) {
    if (types.isEmpty()) return
    LazyRow(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            MapChip(discountsStrings().all, selectedId == null, palette) { onSelect(null) }
        }
        items(types, key = { it.id }) { type ->
            val label = "${type.emoji} ${type.name}".trim().withCount(counts[type.id])
            MapChip(label, type.id == selectedId, palette) {
                // Ikkinchi marta bosilsa — tanlov bekor bo'ladi.
                onSelect(if (type.id == selectedId) null else type.id)
            }
        }
    }
}

/** Xarita ustidagi chip — fon xarita bo'lgani uchun to'liq qorong'i/oq (shaffof emas). */
@Composable
private fun MapChip(label: String, selected: Boolean, palette: AppPalette, onClick: () -> Unit) {
    // Xarita ustidagi boshqaruvlar ATAYLAB ro'yxatdagilardan kichikroq: bu yerda asosiy
    // kontent — xaritaning O'ZI, chiplar esa uni to'sib turmasligi kerak (#25).
    Box(
        Modifier.clip(RoundedCornerShape(10.dp))
            .background(if (selected) palette.primary else palette.glassStrong)
            .border(1.dp, if (selected) palette.primary else palette.border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            style = TextStyle(
                fontFamily = AppFontFamily, fontSize = 11.5f.sp, fontWeight = FontWeight.Bold,
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
    // Filtr — to'liq ekranli qatlam va o'z orqaga tugmasi bor: karkasning pastki paneli
    // va «+» tugmasi ustidan chizilib, «Qo'llash» ni bosib bo'lmas holga keltirardi
    // (bug hisoboti #27). Panel yashiringach tugma ostidagi ulkan bo'shliq ham keraksiz.
    ScHideBottomBar()
    Column(Modifier.fillMaxSize().background(palette.bgBrush)) {
        // Sarlavha
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).scTopInset().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(palette.glass).border(1.dp, palette.border, RoundedCornerShape(12.dp)).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.ArrowLeft, uiStrings().close, tint = palette.ink, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.size(12.dp))
            Text(discountsStrings().filter, style = TextStyle(fontFamily = AppFontFamily, fontSize = 20.sp, fontWeight = FontWeight.Black, color = palette.ink), modifier = Modifier.weight(1f))
            Text(discountsStrings().clear, style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.primary), modifier = Modifier.clickable { vm.resetDraft() })
        }

        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            // Chegirma holati — sonlar server sxemasidan (`filter-schema`), bo'lsa.
            FilterSection(discountsStrings().discountState, palette) {
                FilterPill(discountsStrings().all.withCount(fs.kindCounts["ALL"]), d.discountFilter == DiscountFilter.ALL, palette) { vm.onDraftDiscountFilter(DiscountFilter.ALL) }
                FilterPill(discountsStrings().discounted.withCount(fs.kindCounts["DISCOUNT"]), d.discountFilter == DiscountFilter.DISCOUNT, palette) { vm.onDraftDiscountFilter(DiscountFilter.DISCOUNT) }
                FilterPill(discountsStrings().notDiscounted.withCount(fs.kindCounts["REGULAR"]), d.discountFilter == DiscountFilter.REGULAR, palette) { vm.onDraftDiscountFilter(DiscountFilter.REGULAR) }
            }

            // Joylashuv — boshqa filtrlardan farqli: tanlangan zahoti so'rovga ketadi
            // (`filter.geo.regionIds`) va feed qayta tortiladi.
            RegionSelect(vm, palette)

            // Katalog bo'limi va biznes turi — ochiluvchi SELECT'lar.
            //
            // Ilgari ikkalasi ham chiplar "devori" edi: 8 ta bo'lim + 20 gacha tur filtr
            // ekranining butun balandligini egallab, Saralash va «Qo'llash» pastga surilib
            // ketardi (bug hisoboti #29). Endi tanlangani bitta qatorda ko'rinadi
            // (bittadan ortiq bo'lsa — «+N»), ro'yxat esa varaqda ochiladi.
            if (fs.sections.isNotEmpty()) {
                val selectedSection = fs.sections.firstOrNull { s ->
                    d.groupKey == s.groupKey &&
                        (if (s.partial) d.typeKeys == s.typeKeys else d.typeKeys.isEmpty())
                }
                FilterSelectField(
                    label = discountsStrings().catalogSection,
                    selected = listOfNotNull(selectedSection?.let { "${it.emoji} ${it.name}".trim() }),
                    palette = palette,
                    options = fs.sections.map { s ->
                        FilterOption(
                            id = s.key,
                            label = "${s.emoji} ${s.name}".trim(),
                            selected = s == selectedSection,
                        )
                    },
                    // Bo'lim — bitta: u butun ekranning konteksti (sarlavha, feed doirasi).
                    multiple = false,
                    onToggle = { key -> vm.onDraftSection(fs.sections.firstOrNull { it.key == key }) },
                    onClear = { vm.onDraftSection(null) },
                )
            }

            FilterSelectField(
                label = discountsStrings().businessType,
                selected = fs.categories.filter { it.id in d.categoryIds }
                    .map { "${it.emoji} ${it.name}".trim() },
                palette = palette,
                options = fs.categories.map { cat ->
                    FilterOption(
                        id = cat.id,
                        label = "${cat.emoji} ${cat.name}".trim().withCount(fs.typeCounts[cat.id]),
                        selected = cat.id in d.categoryIds,
                    )
                },
                multiple = true,
                onToggle = vm::toggleDraftCategory,
                onClear = { vm.toggleDraftCategory(null) },
            )

            // Jins (faqat tanlangan turda jins bo'lsa — masalan kiyim)
            if (fs.genderApplicable) {
                FilterSection(discountsStrings().gender, palette) {
                    FilterPill(discountsStrings().all, d.gender == null, palette) { vm.onDraftGender(null) }
                    FilterPill(discountsStrings().male, d.gender == "MALE", palette) { vm.onDraftGender("MALE") }
                    FilterPill(discountsStrings().female, d.gender == "FEMALE", palette) { vm.onDraftGender("FEMALE") }
                }
            }

            // ⚠️ «Bo'lim» (menyu ichidagi taom/xizmat nomi: "Osh", "Burger") filtri
            // ATAYLAB olib tashlangan. Biznes turi bo'yicha filtr yetarli, ikkinchi
            // daraja esa filtrni faqat uzaytirib, tanlovni chalkashtirardi (bug hisoboti
            // #31). Model va so'rov qatlamida u saqlanib turibdi (`subcategories`) —
            // xaritadagi chiplar va qidiruv takliflari o'sha yo'ldan foydalanadi.

            // Saralash
            FilterSection(discountsStrings().sort, palette) {
                FilterPill(discountsStrings().sortRelevant, d.sort == OfferSort.RELEVANCE, palette) { vm.onDraftSort(OfferSort.RELEVANCE) }
                FilterPill(discountsStrings().sortDiscount, d.sort == OfferSort.DISCOUNT_DESC, palette) { vm.onDraftSort(OfferSort.DISCOUNT_DESC) }
                FilterPill(discountsStrings().sortCheap, d.sort == OfferSort.PRICE_ASC, palette) { vm.onDraftSort(OfferSort.PRICE_ASC) }
                FilterPill(discountsStrings().sortExpensive, d.sort == OfferSort.PRICE_DESC, palette) { vm.onDraftSort(OfferSort.PRICE_DESC) }
            }

            // Sxemadagi ma'lumot — serverda nechta e'lon bor va narxlar oralig'i qanday.
            val schemaInfo = listOfNotNull(
                fs.schemaTotal?.let { discountsStrings().serverCount(it) },
                fs.priceRange?.let { "${it.first.formatAmount()} – ${it.last.formatAmount()} ${uiStrings().currency}" },
            ).joinToString(" · ")
            if (schemaInfo.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(schemaInfo, style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.5f.sp, color = palette.inkFaint))
            }

            Spacer(Modifier.height(16.dp))
        }

        // Pastki panel — Qo'llash (jonli natija soni bilan). Karkas paneli yashiringan
        // ([ScHideBottomBar]), shuning uchun faqat tizim navigatsiyasi uchun chekinish.
        Box(
            Modifier.fillMaxWidth().navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp)).background(palette.primary).clickable(onClick = onApply),
                contentAlignment = Alignment.Center,
            ) {
                Text(discountsStrings().apply(fs.previewCount), style = TextStyle(fontFamily = AppFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.White))
            }
        }
    }
}

/** [FilterSelectField] dagi bitta variant. */
private data class FilterOption(val id: String, val label: String, val selected: Boolean)

/**
 * Filtr guruhi — bitta qatorli **select**, ro'yxati varaqda.
 *
 * Tanlanganlar qatorda ko'rinadi, lekin **eng ko'pi bitta yorliq**: qolganlari `+N`
 * bo'lib yig'iladi. Uzun nomli uchta tur tanlanganda qator ikki-uch qatorga cho'zilib,
 * filtr yana o'sha "devor" ko'rinishiga qaytardi (bug hisoboti #29).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSelectField(
    label: String,
    selected: List<String>,
    options: List<FilterOption>,
    palette: AppPalette,
    multiple: Boolean,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
) {
    if (options.isEmpty()) return
    var open by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Text(
            label,
            style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.ink),
        )
        Spacer(Modifier.size(9.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(palette.glass)
                .border(1.dp, palette.border, RoundedCornerShape(14.dp))
                .clickable { open = true }
                .padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (selected.isEmpty()) {
                Text(
                    uiStrings().all,
                    style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.5f.sp, fontWeight = FontWeight.Bold, color = palette.inkFaint),
                    modifier = Modifier.weight(1f),
                )
            } else {
                SelectedTag(selected.first(), palette, Modifier.weight(1f, fill = false))
                if (selected.size > 1) {
                    Text(
                        "+${selected.size - 1}",
                        style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.5f.sp, fontWeight = FontWeight.ExtraBold, color = palette.primary),
                    )
                }
                Spacer(Modifier.weight(1f))
            }
            Icon(AppIcons.ChevronDown, null, tint = palette.inkFaint, modifier = Modifier.size(17.dp))
        }
    }

    if (open) {
        ModalBottomSheet(
            onDismissRequest = { open = false },
            containerColor = Sc.Card,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    label,
                    style = TextStyle(fontFamily = AppFontFamily, fontSize = 17.sp, fontWeight = FontWeight.Black, color = palette.ink),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                PickerOption(uiStrings().all, options.none { it.selected }, palette) {
                    onClear()
                    // Bitta tanlovda varaq darhol yopiladi, ko'p tanlovda ochiq qoladi:
                    // odam odatda ketma-ket bir nechtasini belgilaydi.
                    if (!multiple) open = false
                }
                options.forEach { option ->
                    PickerOption(option.label, option.selected, palette) {
                        onToggle(option.id)
                        if (!multiple) open = false
                    }
                }
            }
        }
    }
}

/** Select qatoridagi tanlangan qiymat yorlig'i. */
@Composable
private fun SelectedTag(label: String, palette: AppPalette, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(9.dp)).background(palette.primary.copy(alpha = 0.12f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(
            label,
            style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.5f.sp, fontWeight = FontWeight.Bold, color = palette.primary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Viloyat tanlash — bosilganda pastdan **varaq** ochiladi ([RegionPickerSheet]).
 */
@Composable
private fun RegionSelect(vm: DiscountsViewModel, palette: AppPalette) {
    val picker by vm.regionPicker.collectAsStateWithLifecycle()
    val selected by vm.selectedRegion.collectAsStateWithLifecycle()
    var sheetOpen by remember { mutableStateOf(false) }

    LaunchedEffect(sheetOpen) { if (sheetOpen) vm.loadRegions() }

    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Text(
            discountsStrings().location,
            style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.ink),
        )
        Spacer(Modifier.size(9.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(palette.glass)
                .border(1.dp, palette.border, RoundedCornerShape(14.dp))
                .clickable { sheetOpen = true }
                .padding(horizontal = 13.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(ScIcons.MapPin, null, tint = palette.primary, modifier = Modifier.size(17.dp))
            Text(
                selected?.name ?: discountsStrings().allRegions,
                style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.5f.sp, fontWeight = FontWeight.Bold, color = palette.ink),
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Icon(AppIcons.ChevronDown, null, tint = palette.inkFaint, modifier = Modifier.size(17.dp))
        }
    }

    // ⚠️ Ro'yxat ekranning ICHIDA ochilmaydi, VARAQ bo'lib chiqadi.
    //
    // Ilgari 14 ta viloyat filtr ustunining o'rtasiga qo'shilardi: ostidagi butun filtr
    // (biznes turi, saralash, Qo'llash tugmasi) ekrandan pastga surilib ketardi va
    // foydalanuvchi qayerda turganini yo'qotardi (bug hisoboti #26). Varaq esa ochiladi,
    // tanlanadi va yopiladi — filtr joyidan qimirlamaydi.
    if (sheetOpen) {
        RegionPickerSheet(
            picker = picker,
            selectedId = selected?.id,
            palette = palette,
            onSelect = { region ->
                vm.selectRegion(region)
                sheetOpen = false
            },
            onDismiss = { sheetOpen = false },
        )
    }
}

/** Viloyat tanlash varag'i — [RegionSelect] uchun. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegionPickerSheet(
    picker: RegionPickerState,
    selectedId: String?,
    palette: AppPalette,
    onSelect: (Region?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Sc.Card,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            Modifier.fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                discountsStrings().location,
                style = TextStyle(fontFamily = AppFontFamily, fontSize = 17.sp, fontWeight = FontWeight.Black, color = palette.ink),
                modifier = Modifier.padding(bottom = 8.dp),
            )
            when {
                picker.loading -> ScShimmerList(rows = 6, leading = false, spacing = 10.dp)
                picker.error != null -> Text(
                    picker.error.orEmpty(),
                    style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.5f.sp, color = Color(0xFFDC2626)),
                )
                else -> {
                    PickerOption(discountsStrings().allRegions, selectedId == null, palette) {
                        onSelect(null)
                    }
                    picker.regions.forEach { region ->
                        PickerOption(region.name, region.id == selectedId, palette) { onSelect(region) }
                    }
                }
            }
        }
    }
}

/**
 * Tanlash varag'idagi bitta qator — viloyat ham, katalog bo'limi ham, biznes turi ham.
 *
 * Belgilangani ko'k ramka va «✓» bilan ajraladi. Ko'p tanlovli ro'yxatda ham shu qator
 * ishlatiladi: bir nechta qator bir vaqtda belgilangan bo'lishi mumkin.
 */
@Composable
private fun PickerOption(label: String, active: Boolean, palette: AppPalette, onClick: () -> Unit) {
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

// ---------------------------------------------------------------------------
// Karta rasmi
// ---------------------------------------------------------------------------

/** Feed kartasi bannerining balandligi — Home kartalaridagidek ~kenglikka nisbatan past. */
private val BannerHeight = 150.dp

/**
 * Karta banneri: e'lon RASMI (`DiscountOffer.imageUrl`) va uning USTIDAGI ma'lumot —
 * chegirma nishoni, tur yorlig'i, narx va manzil. E'lon NOMI banner OSTIDA ([OfferCard]).
 *
 * Rasm kelguncha kulrang shimmer, havola yo'q/buzuq bo'lsa — turning emoji si; banner
 * hech qachon bo'sh qolmaydi. Matn o'qilishi uchun pastdan qora gradient tushiriladi.
 */
@Composable
private fun OfferBanner(
    offer: DiscountOffer,
    accent: Color,
    saved: Boolean,
    onShowOnMap: (() -> Unit)?,
    onToggleSaved: () -> Unit,
) {
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

        // Saqlash — rasmning o'ng-tepasida (pastda endi matn turadi). Yurak, arxiv
        // qutisi emas: ikonaning ma'nosi ko'rinishidan tushunilishi kerak (#36).
        ScFavoriteButton(
            saved = saved,
            onToggle = { onToggleSaved() },
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            idleTint = Color.White,
            contentDescription = discountsStrings().save,
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
            if (offer.effectivePrice > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        "${offer.effectivePrice.formatAmount()} ${uiStrings().currency}",
                        style = TextStyle(fontFamily = AppFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White),
                        modifier = Modifier.alignByBaseline(),
                    )
                    // Ustidan chizilgan eski narx FAQAT chegirmada: chegirmasiz e'londa u
                    // joriy narxning nusxasi bo'lib, "arzonlashgandek" yolg'on his berardi.
                    if (offer.isDiscount && offer.originalPrice > offer.finalPrice) {
                        Text(
                            offer.originalPrice.formatAmount(),
                            style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.5f.sp, color = Color.White.copy(alpha = 0.7f), textDecoration = TextDecoration.LineThrough),
                            modifier = Modifier.alignByBaseline(),
                        )
                    }
                    Text(
                        "/ ${offer.priceUnit}",
                        style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f)),
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }
            offer.location?.takeIf { it.isNotBlank() }?.let { location ->
                // Manzil bosilsa — o'sha nuqta xaritada. Koordinatasiz e'londa oddiy yozuv.
                ScLocationLabel(
                    text = location,
                    size = 11f,
                    color = Color.White.copy(alpha = if (offer.hasLocation) 0.92f else 0.78f),
                    onShowOnMap = onShowOnMap,
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
// E'lon kartasi — chegirmali ham, chegirmasiz ham AYNAN bir xil ko'rinishda
// ---------------------------------------------------------------------------

/**
 * Feed'dagi yagona e'lon kartasi.
 *
 * Ilgari ikki xil karta bor edi: chegirmalisi katta rasmli banner, chegirmasizi esa
 * ixcham gorizontal qator. Bitta ro'yxatda ular navbatma-navbat kelib, feed "buzuq"
 * ko'rinardi — ba'zi e'lonlar ko'zga tashlanib, boshqalari deyarli ko'rinmasdi (bug
 * hisoboti #40). Endi hamma e'lon bir xil vaznda: farq faqat chegirma nishonida va
 * ustidan chizilgan eski narxda.
 *
 * Rasm ostida **e'lon nomi** turadi. Ilgari u yerda faqat biznes nomi bo'lgani uchun
 * "Jordan shoes Pro" kabi nomlar kartada umuman ko'rinmasdi (#41) — foydalanuvchi nima
 * sotilayotganini faqat tafsilotni ochib bilardi.
 */
@Composable
private fun OfferCard(
    offer: DiscountOffer,
    saved: Boolean,
    palette: AppPalette,
    onToggleSaved: (DiscountOffer, Boolean) -> Unit,
    onOpen: () -> Unit,
) {
    val accent = Color(offer.bannerAccent)

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.glass)
            .border(1.dp, palette.border, RoundedCornerShape(18.dp)).clickable(onClick = onOpen),
    ) {
        // Narx, tur yorlig'i va manzil rasm USTIDA; rasm ostida nom va biznes qoladi.
        OfferBanner(
            offer = offer,
            accent = accent,
            saved = saved,
            onShowOnMap = rememberShowOnMap(offer.merchant, offer.lat, offer.lng),
        ) { onToggleSaved(offer, saved) }
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                offer.title,
                style = TextStyle(fontFamily = AppFontFamily, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                offer.merchant,
                style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Filtr chipiga server bergan sonni qo'shadi: "Pitsa" → "Pitsa · 54". Son yo'q bo'lsa — o'zi. */
private fun String.withCount(count: Int?): String = if (count == null) this else "$this · $count"
