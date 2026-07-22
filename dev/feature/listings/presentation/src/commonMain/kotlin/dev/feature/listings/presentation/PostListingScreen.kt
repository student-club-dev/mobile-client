package dev.feature.listings.presentation

import dev.core.uikit.components.scTopInset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.core.uikit.components.AppFontFamily
import dev.core.uikit.components.AppIcons
import dev.core.uikit.components.GlassTextField
import dev.core.uikit.components.PrimaryButton
import dev.core.uikit.theme.AppPalette
import dev.core.uikit.theme.appPalette
import dev.feature.listings.domain.model.BusinessType
import dev.feature.listings.domain.model.ListingField
import dev.feature.listings.domain.model.ListingKind
import dev.feature.listings.presentation.components.FormSection
import dev.feature.listings.presentation.components.IconSquareButton
import dev.feature.listings.presentation.form.KindListingForm
import dev.core.uikit.map.MapCenterRequest
import dev.core.uikit.components.ScCircleButton
import dev.core.uikit.components.ScGlyph
import dev.core.uikit.components.ScHeader
import dev.core.uikit.components.ScHeaderSubtitle
import dev.core.uikit.components.ScHeaderTitle
import dev.core.uikit.components.ScIconTile
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScText
import dev.core.uikit.components.scCard
import dev.core.uikit.theme.Sc
import dev.core.uikit.map.MapPicker
import dev.core.uikit.map.MapPoint
import dev.core.uikit.map.rememberUserLocation
import org.koin.compose.viewmodel.koinViewModel

/**
 * E'lon qo'yish ekrani.
 *
 * Oqim: **e'lon turi** → (chegirmada) **biznes turi** → **o'sha turning formasi** →
 * (kerak bo'lganda) **xarita**.
 *
 * Birinchi qadam aynan tur tanlash, chunki keyingi hamma narsa shunga bog'liq: ijarada
 * "nechi kishi kerak", ishda "smena", xizmatda "qaysi soha" so'raladi va bu maydonlarning
 * bir-biriga aloqasi yo'q.
 */
@Composable
fun PostListingScreen(
    onClose: () -> Unit,
    onPublished: () -> Unit,
    editListingId: String? = null,
    // `true` — Chegirma tab'idan, `false` — E'lon tab'idan (rejim qulflanadi).
    initialDiscount: Boolean? = null,
    /**
     * Qaysi turlarni qo'yish mumkin. Talabaga [ListingKind.DISCOUNT] berilmaydi: u biznes
     * turini (Game Club, Kafe...) so'raydi va bu talabaning shaxsiy e'loniga to'g'ri kelmaydi.
     */
    availableKinds: List<ListingKind> = ListingKind.entries,
    vm: PostListingViewModel = koinViewModel(),
) {
    val palette = appPalette
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(editListingId) { if (editListingId != null) vm.loadForEdit(editListingId) }
    LaunchedEffect(initialDiscount) {
        if (editListingId == null && initialDiscount != null) vm.setInitialDiscountMode(initialDiscount)
    }
    LaunchedEffect(state.published) { if (state.published) onPublished() }

    val kind = state.kind

    when {
        // Xarita hamma narsadan ustun — joy tanlanmaguncha forma ko'rinmaydi.
        state.pickingOnMap -> BranchMapScreen(state, palette, vm)

        // 1-qadam: nima e'lon qilinmoqda. Biznes shell'idan kelinganda `initialDiscount`
        // turni allaqachon DISCOUNT qilib qo'yadi va bu qadam o'tkazib yuboriladi.
        state.step == PostListingStep.KIND || kind == null ->
            ListingKindPicker(availableKinds, palette, onClose = onClose, onPick = vm::selectKind)

        // 2-qadam: faqat chegirmada — qaysi biznes turi.
        state.step == PostListingStep.TYPE ->
            BusinessTypePicker(palette, onBack = vm::back, onPick = vm::selectBusinessType)

        // 3-qadam: tanlangan turning o'z formasi.
        else -> KindListingForm(kind, state, palette, vm)
    }
}

// ---------------------------------------------------------------------------
// 1-qadam: e'lon turi
// ---------------------------------------------------------------------------

/**
 * Nima e'lon qilinmoqda. Turlar kam (4 ta) va har birining tushuntirishi bor — shuning
 * uchun ikki ustunli katakcha emas, to'liq kenglikdagi satrlar: izoh o'qilarli qoladi.
 */
@Composable
private fun ListingKindPicker(
    kinds: List<ListingKind>,
    palette: AppPalette,
    onClose: () -> Unit,
    onPick: (ListingKind) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Sc.Bg)) {
        ScHeader(bottomPadding = 24.dp) {
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Dizaynda orqaga hamisha aylana `‹` — X emas.
                ScCircleButton(ScIcons.ChevronLeft, onClose, size = 44.dp, contentDescription = "Orqaga")
                ScHeaderTitle("Yangi e'lon")
            }
            Spacer(Modifier.height(12.dp))
            ScHeaderSubtitle("Nima e'lon qilmoqchisiz? Keyingi ekran shunga moslashadi.")
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = Sc.ScreenPadding).padding(top = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            kinds.forEach { kind -> ListingKindCard(kind, onPick) }
            Spacer(Modifier.height(110.dp))
        }
    }
}

@Composable
private fun ListingKindCard(kind: ListingKind, onPick: (ListingKind) -> Unit) {
    Row(
        Modifier.fillMaxWidth().scCard(radius = 24.dp, onClick = { onPick(kind) }).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        ScIconTile(kind.tileTint(), size = 60.dp, radius = 20.dp) { KindTileGlyph(kind) }
        Column(Modifier.weight(1f)) {
            ScText(kind.label, 16.5f, FontWeight.ExtraBold, Sc.Ink, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            ScText(kind.subtitle, 13f, FontWeight.Medium, Sc.Muted, lineHeight = 19f, maxLines = 2)
        }
    }
}

/** Tanlov kartasining plitka foni — dizayn palitrasidan. */
@Composable
private fun ListingKind.tileTint(): Color = when (this) {
    ListingKind.TASK -> Sc.TintPink
    ListingKind.RENTAL -> Sc.TintGreen
    ListingKind.SERVICE -> Sc.TintBlue
    ListingKind.JOB -> Sc.TintAmber
    ListingKind.DISCOUNT -> Sc.TintViolet
}

@Composable
private fun KindTileGlyph(kind: ListingKind) {
    when (kind) {
        ListingKind.TASK -> ScGlyph(ScIcons.Book, 30.dp)
        ListingKind.RENTAL -> ScGlyph(ScIcons.HouseFilledGreen, 30.dp)
        ListingKind.SERVICE -> Icon(ScIcons.Wrench, null, tint = Sc.Brand, modifier = Modifier.size(30.dp))
        ListingKind.JOB -> Icon(ScIcons.Briefcase, null, tint = Sc.Amber, modifier = Modifier.size(30.dp))
        ListingKind.DISCOUNT -> Icon(ScIcons.DiscountTag, null, tint = Sc.Violet, modifier = Modifier.size(30.dp))
    }
}

// ---------------------------------------------------------------------------
// 2-qadam: biznes turi (faqat chegirma e'lonida)
// ---------------------------------------------------------------------------

@Composable
private fun BusinessTypePicker(
    palette: AppPalette,
    onBack: () -> Unit,
    onPick: (BusinessType) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).scTopInset(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            IconSquareButton(onBack, AppIcons.ArrowLeft, palette)
            Text(
                "Chegirma e'loni",
                style = TextStyle(fontFamily = AppFontFamily, fontSize = 20.sp, fontWeight = FontWeight.Black, color = palette.ink),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Biznesingiz turini tanlang — keyingi ekran shunga moslashadi.",
            style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.5f.sp, color = palette.inkMuted),
        )
        Spacer(Modifier.height(16.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BusinessType.entries.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { type -> BusinessTypeCard(type, palette, Modifier.weight(1f), onPick) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(110.dp))
    }
}

@Composable
private fun BusinessTypeCard(
    type: BusinessType,
    palette: AppPalette,
    modifier: Modifier,
    onPick: (BusinessType) -> Unit,
) {
    val accent = Color(type.accent)
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(palette.glass)
            .border(1.dp, palette.border, RoundedCornerShape(16.dp))
            .clickable { onPick(type) }.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Text(type.emoji, style = TextStyle(fontSize = 21.sp)) }

        Text(
            type.label,
            style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------------------
// Manzillar — xaritadan
// ---------------------------------------------------------------------------

/**
 * Manzillar bo'limi. Manzil qo'lda yozilmaydi: "+" bosiladi, xaritadan joy tanlanadi,
 * manzil teskari geokodlash bilan o'zi to'ladi.
 *
 * Yozuvlari parametr — chegirmada bu "Filiallar", ijarada "Uy joyi", ishda "Ish joyi".
 */
@Composable
fun BranchesSection(
    state: PostListingUiState,
    palette: AppPalette,
    vm: PostListingViewModel,
    title: String = "Manzil",
    subtitle: String = "Xaritadan aniq joyni belgilang",
) {
    FormSection(
        title = title,
        subtitle = subtitle,
        error = state.errorFor(ListingField.LOCATION),
    ) {
        state.branches.forEachIndexed { index, branch ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
                    .background(palette.fieldBg).padding(11.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(palette.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${index + 1}",
                            style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Black, color = palette.primary),
                        )
                    }
                    Text(
                        branch.address,
                        style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.ink),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        AppIcons.Close,
                        "Manzilni o'chirish",
                        tint = palette.inkFaint,
                        modifier = Modifier.size(15.dp).clickable { vm.removeBranch(index) },
                    )
                }

                GlassTextField(
                    branch.name.orEmpty(),
                    { vm.onBranchName(index, it) },
                    "Nomi (ixtiyoriy): Chilonzor filiali",
                    height = 44,
                )
            }
        }

        if (state.resolvingAddress) {
            Text(
                "Manzil aniqlanmoqda...",
                style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.5f.sp, fontWeight = FontWeight.Bold, color = palette.primary),
            )
        }

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
                .background(palette.primary.copy(alpha = 0.08f))
                .clickable(onClick = vm::openMap)
                .padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(AppIcons.Plus, null, tint = palette.primary, modifier = Modifier.size(17.dp))
            Spacer(Modifier.size(7.dp))
            Text(
                if (state.branches.isEmpty()) "Xaritadan belgilash" else "Yana bitta manzil",
                style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.5f.sp, fontWeight = FontWeight.ExtraBold, color = palette.primary),
            )
        }
    }
}

/**
 * Xaritadan joy tanlash — Yandex uslubi: belgi ekran markazida turadi, xarita suriladi,
 * belgi ostidagi joy tanlanadi. Qidiruv ham bor.
 */
@Composable
private fun BranchMapScreen(state: PostListingUiState, palette: AppPalette, vm: PostListingViewModel) {
    val userLocation = rememberUserLocation()

    var centerRequest by remember { mutableStateOf<MapCenterRequest?>(null) }
    var requestId by remember { mutableStateOf(0) }
    var pickedPoint by remember { mutableStateOf<MapPoint?>(null) }

    // Joylashuv birinchi marta aniqlanganda xaritani avtomatik o'sha yerga olib boramiz.
    LaunchedEffect(userLocation) {
        if (userLocation != null && centerRequest == null) {
            requestId++
            centerRequest = MapCenterRequest(userLocation, requestId)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).scTopInset().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            IconSquareButton(vm::closeMap, AppIcons.ArrowLeft, palette)
            Column(Modifier.weight(1f)) {
                Text(
                    "Joyni belgilash",
                    style = TextStyle(fontFamily = AppFontFamily, fontSize = 18.sp, fontWeight = FontWeight.Black, color = palette.ink),
                )
                Text(
                    if (state.resolvingAddress) "Manzil aniqlanmoqda..." else "Xaritani suring — belgi joyni ko'rsatadi",
                    style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.5f.sp, color = palette.inkFaint),
                )
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            GlassTextField(
                state.searchQuery,
                vm::onSearchQuery,
                "Qidirish: Mega Planet, Amir Temur ko'chasi...",
                leading = AppIcons.Search,
                height = 46,
            )
        }
        Spacer(Modifier.height(10.dp))

        Box(Modifier.fillMaxWidth().weight(1f)) {
            MapPicker(
                initial = userLocation,
                dark = palette.dark,
                onCenterChanged = { point -> pickedPoint = point },
                modifier = Modifier.fillMaxSize(),
                centerRequest = centerRequest,
            )

            if (state.searching || state.searchResults.isNotEmpty()) {
                Column(
                    Modifier.align(Alignment.TopCenter)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White),
                ) {
                    if (state.searching) {
                        Text(
                            "Qidirilmoqda...",
                            style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B6880)),
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    state.searchResults.forEach { place ->
                        Column(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    requestId++
                                    centerRequest = MapCenterRequest(MapPoint(place.lat, place.lng), requestId)
                                    vm.clearSearch()
                                }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                place.title,
                                style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF14102D)),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (place.subtitle.isNotBlank()) {
                                Text(
                                    place.subtitle,
                                    style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.sp, color = Color(0xFF8A87A0)),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            if (userLocation != null) {
                Row(
                    Modifier.align(Alignment.BottomStart)
                        .padding(start = 12.dp, bottom = 92.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .clickable {
                            requestId++
                            centerRequest = MapCenterRequest(userLocation, requestId)
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("📍", style = TextStyle(fontSize = 13.sp))
                    Text(
                        "Mening joylashuvim",
                        style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF14102D)),
                    )
                }
            }
        }

        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            PrimaryButton(
                if (state.resolvingAddress) "Manzil aniqlanmoqda..." else "Shu yerni tanlash",
                onClick = { pickedPoint?.let { vm.addBranchFromMap(it.lat, it.lng) } },
                enabled = pickedPoint != null && !state.resolvingAddress,
            )
        }
    }
}

/** Bir martalik xabar (rasm xatosi, "Qoralama saqlandi"). */
@Composable
fun MessageBar(message: String, palette: AppPalette, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(palette.primary.copy(alpha = 0.10f))
            .clickable(onClick = onDismiss)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            message,
            style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.primary),
            modifier = Modifier.weight(1f),
        )
        Icon(AppIcons.Close, "Yopish", tint = palette.primary, modifier = Modifier.size(14.dp))
    }
}
