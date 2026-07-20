package dev.feature.listings.presentation.browse

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.core.designsystem.components.AppFontFamily
import dev.core.designsystem.components.AppIcons
import dev.core.designsystem.components.GlassTextField
import dev.core.designsystem.theme.AppPalette
import dev.core.designsystem.theme.appPalette
import dev.feature.listings.domain.model.ListingKind
import dev.feature.listings.presentation.components.IconSquareButton
import dev.core.designsystem.map.MapLinkButton
import dev.core.designsystem.map.OfferMarker
import dev.core.designsystem.map.OffersMapOverlay
import dev.core.designsystem.map.rememberUserLocation
import dev.feature.listings.domain.model.Listing
import org.koin.compose.viewmodel.koinViewModel

/**
 * Talabaga ko'rinadigan e'lonlar: Ijara, Xizmatlar va Ish e'lonlari.
 *
 * Uchalasi bitta ekranda, tepadagi tab bilan almashadi. Nega alohida uchta ekran emas:
 * ular bir xil ishlaydi (qidiruv → filtr → ro'yxat) va faqat kartochka bilan filtr
 * maydonlari farq qiladi. Uchta ekran bo'lsa qidiruv, bo'sh holat va masofa hisobi
 * uch marta takrorlanardi.
 *
 * @param initialKind qaysi tab ochiq bo'lsin (pastki "Ishlar" tab'idan kelinganda — JOB).
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

    LaunchedEffect(initialKind) { vm.selectKind(initialKind) }

    // Eng yaqinlari yuqorida. Joylashuv noma'lum bo'lsa tartib o'zgarmaydi.
    val sorted = remember(state.listings, userLocation) {
        state.listings
            .map { it to it.nearestBranch(userLocation?.lat, userLocation?.lng) }
            .sortedBy { (_, nearest) -> nearest?.distanceMeters ?: Double.MAX_VALUE }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Header(state, palette, onBack, vm)

            KindTabs(
                selected = state.kind,
                onSelect = vm::selectKind,
                palette = palette,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(12.dp))

            SearchRow(
                query = state.query,
                activeFilterCount = state.activeFilterCount,
                palette = palette,
                onQuery = vm::onQuery,
                onOpenFilter = {
                    vm.openFilter()
                    showFilter = true
                },
            )
            Spacer(Modifier.height(10.dp))

            // Filtrlangan natijani xaritada ham ko'rish — ro'yxat bilan bir xil to'plam.
            MapLinkButton(
                palette = palette,
                onClick = { showMap = true },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(12.dp))

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
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
                    item { BrowseEmptyState(state, palette) }
                }
            }
        }

        // Xarita ro'yxat bilan BIR XIL `state.listings` dan quriladi — filtr, qidiruv va tab
        // ikkalasiga birdek ta'sir qiladi, foydalanuvchi ikki xil natija ko'rmaydi.
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
                    Box(Modifier.weight(1f)) {
                        GlassTextField(state.query, vm::onQuery, "Qidirish...", leading = AppIcons.Search, height = 46)
                    }
                    FilterButton(state.activeFilterCount, palette) {
                        vm.openFilter()
                        showFilter = true
                    }
                },
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

/**
 * E'lonni xarita markeriga aylantiradi. Manzili yo'q e'lon (`branches` bo'sh) xaritada
 * ko'rsatilmaydi — `null` qaytadi, chunki uni qayerga qo'yishni bilmaymiz.
 *
 * Bir nechta filiali bor e'lon eng yaqin filialida turadi: bitta e'lon uchun bir nechta
 * marker chiqarsak xarita takroriy narx pufaklari bilan to'lib ketardi.
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

@Composable
private fun Header(
    state: ListingsBrowseUiState,
    palette: AppPalette,
    onBack: (() -> Unit)?,
    vm: ListingsBrowseViewModel,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 54.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            if (onBack != null) IconSquareButton(onBack, AppIcons.ArrowLeft, palette)
            Column {
                Text(
                    "E'lonlar",
                    style = TextStyle(fontFamily = AppFontFamily, fontSize = 24.sp, fontWeight = FontWeight.Black, color = palette.ink),
                )
                Text(
                    countLabel(state),
                    style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.5f.sp, color = palette.inkMuted),
                )
            }
        }
    }
}

/** "12 ta e'lon" yoki filtrlanganda "124 tadan 12 tasi". */
private fun countLabel(state: ListingsBrowseUiState): String = when {
    state.totalCount == 0 -> "Hozircha e'lon yo'q"
    state.listings.size == state.totalCount -> "${state.totalCount} ta faol e'lon"
    else -> "${state.totalCount} tadan ${state.listings.size} tasi"
}

/**
 * Bo'lim tanlash. Chegirma bu yerda yo'q — uning o'z ekrani bor va mantig'i boshqacha
 * (chegirma foizi, promokod, filial).
 */
private val browseKinds = listOf(ListingKind.RENTAL, ListingKind.SERVICE, ListingKind.JOB)

@Composable
private fun KindTabs(
    selected: ListingKind,
    onSelect: (ListingKind) -> Unit,
    palette: AppPalette,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.fieldBg)
            .border(1.dp, palette.border, RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        browseKinds.forEach { kind ->
            KindTab(kind, selected == kind, palette) { onSelect(kind) }
        }
    }
}

@Composable
private fun RowScope.KindTab(
    kind: ListingKind,
    selected: Boolean,
    palette: AppPalette,
    onClick: () -> Unit,
) {
    // Tanlangan tab o'z turining rangini oladi — talaba qaysi bo'limda ekanini rangdan biladi.
    val accent = Color(kind.accent)
    val background by animateColorAsState(if (selected) accent else Color.Transparent)
    val content by animateColorAsState(if (selected) palette.onPrimary else palette.inkMuted)

    Row(
        Modifier.weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${kind.emoji}  ${tabLabel(kind)}",
            style = TextStyle(
                fontFamily = AppFontFamily,
                fontSize = 12.5f.sp,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = content,
            ),
        )
    }
}

/** Tab'da qisqa yozuv — to'liq nomi ("Ijara — turarjoy") uch ustunga sig'maydi. */
private fun tabLabel(kind: ListingKind): String = when (kind) {
    ListingKind.RENTAL -> "Ijara"
    ListingKind.SERVICE -> "Xizmat"
    ListingKind.JOB -> "Ish"
    ListingKind.DISCOUNT -> "Chegirma"
    ListingKind.TASK -> "Yordam"
}

@Composable
private fun SearchRow(
    query: String,
    activeFilterCount: Int,
    palette: AppPalette,
    onQuery: (String) -> Unit,
    onOpenFilter: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            GlassTextField(query, onQuery, "Qidirish: Chilonzor, kuryer, IELTS...", leading = AppIcons.Search, height = 46)
        }
        FilterButton(activeFilterCount, palette, onOpenFilter)
    }
}

@Composable
private fun FilterButton(activeCount: Int, palette: AppPalette, onClick: () -> Unit) {
    val active = activeCount > 0
    Box(
        Modifier.size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) palette.primary else palette.fieldBg)
            .border(1.dp, palette.border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (active) {
            Text(
                "$activeCount",
                style = TextStyle(fontFamily = AppFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Black, color = palette.onPrimary),
            )
        } else {
            Icon(AppIcons.Filter, "Filter", tint = palette.inkMuted, modifier = Modifier.size(19.dp))
        }
    }
}

/**
 * Bo'sh holat. Ikki holat ajratiladi: bo'limda umuman e'lon yo'qmi yoki filtr hammasini
 * kesib tashladimi — foydalanuvchi nima qilishi kerakligi bu ikkisida butunlay boshqacha.
 */
@Composable
private fun BrowseEmptyState(state: ListingsBrowseUiState, palette: AppPalette) {
    val message = when {
        state.isFilteredEmpty -> "Bu shartlarga mos e'lon topilmadi. Filtrni yumshating yoki qidiruvni o'zgartiring."
        else -> emptySectionMessage(state.kind)
    }

    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(72.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(state.kind.accent).copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { Text(state.kind.emoji, style = TextStyle(fontSize = 30.sp)) }

        Text(
            if (state.isFilteredEmpty) "Natija yo'q" else "Hozircha bo'sh",
            style = TextStyle(fontFamily = AppFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Black, color = palette.ink),
        )
        Text(
            message,
            style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.5f.sp, color = palette.inkFaint),
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

private fun emptySectionMessage(kind: ListingKind): String = when (kind) {
    ListingKind.RENTAL -> "Hali ijara e'loni joylanmagan. Birinchi bo'lib siz joylashingiz mumkin."
    ListingKind.SERVICE -> "Hali xizmat e'loni yo'q. O'z xizmatingizni joylab ko'ring."
    ListingKind.JOB -> "Hali ish e'loni yo'q. Tez orada paydo bo'ladi."
    ListingKind.DISCOUNT -> "Hali chegirma e'loni yo'q."
    ListingKind.TASK -> "Hali topshiriq yo'q. Yordam kerak bo'lsa birinchi bo'lib so'rang."
}
