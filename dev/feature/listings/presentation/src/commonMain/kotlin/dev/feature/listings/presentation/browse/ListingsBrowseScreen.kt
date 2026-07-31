package dev.feature.listings.presentation.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.Text
import dev.core.uikit.components.ScCircleButton
import dev.core.uikit.components.ScGlyph
import dev.core.uikit.components.ScHeader
import dev.core.uikit.components.ScHeaderSubtitle
import dev.core.uikit.components.ScHeaderTitle
import dev.core.uikit.components.ScIconTile
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScText
import dev.core.uikit.components.scCard
import dev.core.uikit.components.scStyle
import dev.core.uikit.components.ScSearchOverlay
import dev.core.uikit.map.OfferMarker
import dev.core.uikit.map.OffersMapOverlay
import dev.core.uikit.map.rememberUserLocation
import dev.core.uikit.theme.Sc
import dev.core.uikit.theme.appPalette
import dev.feature.listings.domain.model.Listing
import dev.feature.listings.domain.model.ListingKind
import org.koin.compose.viewmodel.koinViewModel

/**
 * Talabaga ko'rinadigan e'lonlar: Yordam, Ijara, Xizmat va Ish.
 *
 * To'rtta bo'lim bitta ekranda, tepadagi **segmentli** boshqaruv bilan almashadi
 * (dizaynda to'rtalasi bir qatorda, teng kenglikda va scroll qilinmaydi).
 *
 * @param initialKind qaysi bo'lim ochiq bo'lsin (Home'dagi "Barchasi" tugmalaridan).
 * @param onBack `null` bo'lsa orqaga tugmasi ko'rsatilmaydi (tab sifatida ochilgan).
 */
@Composable
fun ListingsBrowseScreen(
    onOpenListing: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialKind: ListingKind = ListingKind.JOB,
    onBack: (() -> Unit)? = null,
    vm: ListingsBrowseViewModel = koinViewModel(),
) {
    val palette = appPalette
    val state by vm.state.collectAsStateWithLifecycle()
    val filterState by vm.filterState.collectAsStateWithLifecycle()
    val userLocation = rememberUserLocation()

    var showFilter by remember { mutableStateOf(false) }
    var showMap by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }

    LaunchedEffect(initialKind) { vm.selectKind(initialKind) }

    // Eng yaqinlari yuqorida. Joylashuv noma'lum bo'lsa tartib o'zgarmaydi.
    val sorted = remember(state.listings, userLocation) {
        state.listings
            .map { it to it.nearestBranch(userLocation?.lat, userLocation?.lng) }
            .sortedBy { (_, nearest) -> nearest?.distanceMeters ?: Double.MAX_VALUE }
    }

    Box(modifier.fillMaxSize().background(Sc.Bg)) {
        Column(Modifier.fillMaxSize()) {
            BrowseHeader(
                subtitle = countLabel(state),
                onBack = onBack,
                onSearch = { showSearch = true },
                onFilter = { vm.openFilter(); showFilter = true },
                activeFilterCount = state.activeFilterCount,
            )

            Column(
                Modifier.fillMaxWidth().padding(horizontal = Sc.ScreenPadding).padding(top = 20.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp),
            ) {
                KindSegments(state.kind, vm::selectKind)
                MapBar { showMap = true }
            }
            Spacer(Modifier.height(15.dp))

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Sc.ScreenPadding, end = Sc.ScreenPadding,
                    bottom = if (onBack == null) 110.dp else 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                items(sorted, key = { (listing, _) -> listing.id }) { (listing, nearest) ->
                    ListingCard(
                        listing = listing,
                        distanceLabel = nearest?.distanceLabel(),
                        branchLabel = nearest?.branch?.display(),
                        palette = palette,
                        onClick = { onOpenListing(listing.id) },
                    )
                }
                if (state.listings.isEmpty()) {
                    item { BrowseEmptyState(state) }
                }
            }
        }

        // Xarita ro'yxat bilan BIR XIL `state.listings` dan quriladi — filtr, qidiruv va
        // bo'lim ikkalasiga birdek ta'sir qiladi.
        if (showMap) {
            val markers = remember(state.listings, userLocation) {
                state.listings.mapNotNull { it.toMarker(userLocation?.lat, userLocation?.lng) }
            }
            OffersMapOverlay(
                markers = markers,
                palette = palette,
                onClose = { showMap = false },
                userLocation = userLocation,
                onMarkerTap = onOpenListing,
                topBarExtras = {
                    Box(Modifier.weight(1f))
                    ScCircleButton(ScIcons.Search, { showSearch = true }, size = 46.dp)
                    ScCircleButton(ScIcons.Filter, { vm.openFilter(); showFilter = true }, size = 46.dp)
                },
            )
        }

        if (showSearch) {
            ScSearchOverlay(
                query = state.query,
                onQuery = vm::onQuery,
                onClose = { showSearch = false },
                placeholder = "Chilonzor, kuryer, IELTS…",
                suggestions = searchSuggestions,
            )
        }

        if (showFilter) {
            ListingFilterSheet(
                state = filterState,
                palette = palette,
                vm = vm,
                onApply = {
                    vm.applyFilters()
                    showFilter = false
                },
                onClose = { showFilter = false },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Topbar
// ---------------------------------------------------------------------------

@Composable
private fun BrowseHeader(
    subtitle: String,
    onBack: (() -> Unit)?,
    onSearch: () -> Unit,
    onFilter: () -> Unit,
    activeFilterCount: Int,
) {
    ScHeader {
        Row(
            Modifier.fillMaxWidth().padding(top = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (onBack != null) {
                ScCircleButton(ScIcons.ChevronLeft, onBack, contentDescription = "Orqaga")
            }
            Column(Modifier.weight(1f)) {
                ScHeaderTitle("E'lonlar", size = 26f)
                Spacer(Modifier.height(3.dp))
                ScHeaderSubtitle(subtitle)
            }
            ScCircleButton(ScIcons.Search, onSearch, size = 46.dp, contentDescription = "Qidirish")
            ScCircleButton(
                ScIcons.Filter, onFilter, size = 46.dp,
                contentDescription = "Filtr",
                // Faol filtr belgisi — brend rangida. Qizil "xato/shoshilinch" degani,
                // bu yerda esa shunchaki holat.
                badge = activeFilterCount > 0, badgeColor = Sc.Brand,
            )
        }
    }
}

/** "12 ta e'lon" yoki filtrlanganda "124 tadan 12 tasi". */
private fun countLabel(state: ListingsBrowseUiState): String = when {
    state.totalCount == 0 -> "Hozircha e'lon yo'q"
    state.listings.size == state.totalCount -> "${state.totalCount} ta faol e'lon"
    else -> "${state.totalCount} tadan ${state.listings.size} tasi"
}

// ---------------------------------------------------------------------------
// Segmentli bo'lim tanlash
// ---------------------------------------------------------------------------

/**
 * Talabaga ko'rinadigan bo'limlar. [ListingKind.DISCOUNT] yo'q — u "Siz uchun" feed'ida.
 *
 * Yangi tur qo'shilganda shu ro'yxatga ham qo'shish ESDAN CHIQMASIN — bu qo'lda tuzilgan
 * ro'yxat, `ListingKind.entries` emas.
 */
private val browseKinds = listOf(
    ListingKind.TASK,
    ListingKind.RENTAL,
    ListingKind.SERVICE,
    ListingKind.JOB,
)

/** To'rt bo'lim bitta qatorda, teng kenglikda; faol bo'lim gradient bilan belgilanadi. */
@Composable
private fun KindSegments(selected: ListingKind, onSelect: (ListingKind) -> Unit) {
    Row(
        Modifier.fillMaxWidth().scCard(radius = 15.dp, elevation = 6.dp).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        browseKinds.forEach { kind ->
            KindSegment(kind, selected == kind) { onSelect(kind) }
        }
    }
}

@Composable
private fun RowScope.KindSegment(kind: ListingKind, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.weight(1f)
            .clip(RoundedCornerShape(11.dp))
            .then(if (selected) Modifier.background(Sc.buttonBrush) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KindGlyph(kind, selected, 17.dp)
        ScText(
            tabLabel(kind), 12.5f, FontWeight.Bold,
            if (selected) Color.White else Sc.InkSoft, maxLines = 1,
        )
    }
}

/**
 * Bo'lim belgisi (qo'llanma: Yordam=`ic_book`, Ijara=`ic_home_filled`,
 * Xizmat=`ic_tools`, Ish=`ic_briefcase`).
 *
 * Faol segment gradient ustida turadi — belgi oq rangga bo'yaladi. Nofaolida esa
 * ko'p rangli ikonalar o'z ranglarida ([ScGlyph], ya'ni bo'yalmaydi), bir rangli
 * ikonalar bo'lim rangida chiziladi.
 */
@Composable
private fun KindGlyph(kind: ListingKind, onGradient: Boolean, size: Dp) {
    val icon = when (kind) {
        ListingKind.TASK -> ScIcons.Book
        ListingKind.RENTAL -> ScIcons.HouseFilledGreen
        ListingKind.SERVICE -> ScIcons.Wrench
        ListingKind.JOB, ListingKind.DISCOUNT -> ScIcons.Briefcase
    }
    val multicolor = kind == ListingKind.TASK || kind == ListingKind.RENTAL
    when {
        onGradient -> Icon(icon, null, tint = Color.White, modifier = Modifier.size(size))
        multicolor -> ScGlyph(icon, size)
        else -> Icon(icon, null, tint = kind.accentColor(), modifier = Modifier.size(size))
    }
}

/** Bir rangli bo'lim belgilarining rangi. */
@Composable
private fun ListingKind.accentColor(): Color = when (this) {
    ListingKind.SERVICE -> Sc.Brand
    else -> Sc.Amber
}

/** Segmentga sig'adigan qisqa yozuv (to'liq nomi "Ijara — turarjoy"). */
private fun tabLabel(kind: ListingKind): String = when (kind) {
    ListingKind.RENTAL -> "Ijara"
    ListingKind.SERVICE -> "Xizmat"
    ListingKind.JOB -> "Ish"
    ListingKind.DISCOUNT -> "Chegirma"
    ListingKind.TASK -> "Yordam"
}

// ---------------------------------------------------------------------------
// "Xaritada ko'rish"
// ---------------------------------------------------------------------------

@Composable
private fun MapBar(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .scCard(radius = 18.dp, elevation = 0.dp, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(ScIcons.Map, null, tint = Sc.Brand, modifier = Modifier.size(22.dp))
        ScText("Xaritada ko'rish", 15f, FontWeight.Bold, Sc.Ink, Modifier.weight(1f), maxLines = 1)
        Icon(ScIcons.ChevronRight, null, tint = Sc.NavIdle, modifier = Modifier.size(16.dp))
    }
}

// ---------------------------------------------------------------------------
// Qidiruv qatlami — klaviatura tepasida suzuvchi maydon
// ---------------------------------------------------------------------------

/** Qidiruv maydonining tagidagi tayyor takliflar (dizayndagi qator). */
private val searchSuggestions = listOf("kuryer", "IELTS", "referat")

// ---------------------------------------------------------------------------
// Bo'sh holat
// ---------------------------------------------------------------------------

/**
 * Bo'sh holat. Ikki holat ajratiladi: bo'limda umuman e'lon yo'qmi yoki filtr hammasini
 * kesib tashladimi — foydalanuvchi nima qilishi kerakligi bu ikkisida boshqacha.
 */
@Composable
private fun BrowseEmptyState(state: ListingsBrowseUiState) {
    val message = when {
        state.isFilteredEmpty -> "Bu shartlarga mos e'lon topilmadi. Filtrni yumshating yoki qidiruvni o'zgartiring."
        else -> emptySectionMessage(state.kind)
    }
    Column(
        Modifier.fillMaxWidth().padding(top = 60.dp, start = 20.dp, end = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScIconTile(state.kind.tint(), size = 96.dp, radius = 30.dp) {
            KindGlyph(state.kind, onGradient = false, size = 46.dp)
        }
        Spacer(Modifier.height(18.dp))
        ScText(if (state.isFilteredEmpty) "Natija yo'q" else "Hozircha bo'sh", 19f, FontWeight.ExtraBold, Sc.Ink)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = scStyle(14f, FontWeight.Medium, Sc.Muted, lineHeight = 21f).copy(textAlign = TextAlign.Center),
        )
    }
}

/** Bo'lim plitkasining tint foni. */
@Composable
private fun ListingKind.tint(): Color = when (this) {
    ListingKind.TASK -> Sc.TintPink
    ListingKind.RENTAL -> Sc.TintGreen
    ListingKind.SERVICE -> Sc.TintBlue
    ListingKind.JOB, ListingKind.DISCOUNT -> Sc.TintAmber
}

private fun emptySectionMessage(kind: ListingKind): String = when (kind) {
    ListingKind.RENTAL -> "Hali ijara e'loni joylanmagan. Birinchi bo'lib siz joylashingiz mumkin."
    ListingKind.SERVICE -> "Hali xizmat e'loni yo'q. O'z xizmatingizni joylab ko'ring."
    ListingKind.JOB -> "Hali ish e'loni yo'q. Tez orada paydo bo'ladi."
    ListingKind.DISCOUNT -> "Hali chegirma e'loni yo'q."
    ListingKind.TASK -> "Hali topshiriq yo'q. Yordam kerak bo'lsa birinchi bo'lib so'rang."
}

// ---------------------------------------------------------------------------
// Xarita markerlari
// ---------------------------------------------------------------------------

/**
 * E'lonni xarita markeriga aylantiradi. Manzili yo'q e'lon (`branches` bo'sh) xaritada
 * ko'rsatilmaydi — `null` qaytadi, chunki uni qayerga qo'yishni bilmaymiz.
 */
private fun Listing.toMarker(userLat: Double?, userLng: Double?): OfferMarker? {
    val branch = nearestBranch(userLat, userLng)?.branch ?: return null
    return OfferMarker(
        id = id,
        lat = branch.lat,
        lng = branch.lng,
        label = markerPrice(),
        colorHex = hexRgb(accent),
    )
}

/** Marker pufagiga sig'adigan qisqa narx: "2.5 mln", "300k", "Kelishilgan". */
private fun Listing.markerPrice(): String = when {
    isNegotiable -> "Kelishilgan"
    price >= 1_000_000 -> "${(price / 100_000) / 10.0} mln"
    price >= 1_000 -> "${price / 1_000}k"
    else -> "$price so'm"
}

/** `0xFF7C5CFF` → `"#7C5CFF"` — JS/CSS shu ko'rinishni kutadi. */
private fun hexRgb(argb: Long): String {
    val rgb = (argb and 0xFFFFFF).toString(16).padStart(6, '0')
    return "#$rgb"
}
