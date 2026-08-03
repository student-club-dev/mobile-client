package dev.feature.university.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.core.uikit.components.ScCircleButton
import dev.core.uikit.components.ScGlyph
import dev.core.uikit.components.ScGradientButton
import dev.core.uikit.components.ScHeader
import dev.core.uikit.components.ScHeaderTitle
import dev.core.uikit.components.ScIconTile
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScAvatar
import dev.core.uikit.components.ScMonogramTile
import dev.core.uikit.components.ScSectionHeader
import dev.core.uikit.components.ScSheetHandle
import dev.core.uikit.components.ScSoftButton
import dev.core.uikit.components.ScText
import dev.core.uikit.components.scCard
import dev.core.uikit.components.scStyle
import dev.core.uikit.components.ScShimmerBox
import dev.core.uikit.components.ScShimmerLine
import dev.core.uikit.components.ScShimmerList
import dev.core.uikit.map.MapPoint
import dev.core.uikit.map.OfferMarker
import dev.core.uikit.map.OffersMap
import dev.core.uikit.map.rememberUserLocation
import dev.core.uikit.theme.Sc
import dev.core.domain.model.DiscountOffer
import dev.feature.listings.domain.model.Listing
import dev.feature.listings.domain.model.formatSum
import dev.feature.students.domain.model.FriendStatus
import dev.feature.students.domain.model.Student
import dev.feature.university.domain.model.University
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.runtime.ReadOnlyComposable

/** Ro'yxatdagi plitkalar navbat bilan shu tint/accent juftlarini oladi. */
private val tilePalette: List<Pair<Color, Color>>
    @Composable @ReadOnlyComposable get() = listOf(
    Sc.TintBlue to Sc.Brand,
    Sc.TintOrange to Sc.Orange,
    Sc.TintViolet to Sc.Violet,
    Sc.TintAmber to Sc.Amber,
    Sc.TintGreenDeep to Sc.Success,
)

@Composable
fun MyUniversityScreen(
    /** Topshiriq e'loni bosilganda uning tafsilot ekrani ochiladi. */
    onOpenListing: (String) -> Unit = {},
    /** "Barchasi" — talaba e'lonlari ekrani, Yordam tab'i bilan. */
    onOpenTasks: () -> Unit = {},
    vm: MyUniversityViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val picker by vm.picker.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }
    var showStudents by remember { mutableStateOf(false) }
    var showPrintMap by remember { mutableStateOf(false) }
    var showFoodMap by remember { mutableStateOf(false) }
    var selectedOffer by remember { mutableStateOf<DiscountOffer?>(null) }

    Box(Modifier.fillMaxSize().background(Sc.Bg)) {
        Column(Modifier.fillMaxSize()) {
            ScHeader {
                ScHeaderTitle("Mening universitetim", modifier = Modifier.padding(top = 20.dp))
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 18.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                item {
                    UniversitiesSelector(Modifier.padding(horizontal = Sc.ScreenPadding)) {
                        vm.loadUniversities(); showPicker = true
                    }
                }

                val uni = state.university
                if (uni == null) {
                    item {
                        EmptyUniversity(
                            loading = state.loading,
                            modifier = Modifier.padding(horizontal = Sc.ScreenPadding),
                        )
                    }
                } else {
                    item { UniversityCard(uni, state.mates.size, Modifier.padding(horizontal = Sc.ScreenPadding)) }
                    item {
                        ScSectionHeader(
                            "Do'stlashish uchun talabalar",
                            Modifier.padding(horizontal = Sc.ScreenPadding),
                            action = if (state.mates.isNotEmpty()) "Barchasi" else null,
                            onAction = { showStudents = true },
                        )
                    }
                    if (state.mates.isEmpty()) {
                        item {
                            ScText(
                                "Hozircha talaba yo'q.", 13f, FontWeight.Medium, Sc.Muted,
                                Modifier.padding(horizontal = Sc.ScreenPadding),
                            )
                        }
                    } else {
                        item {
                            LazyRow(
                                Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = Sc.ScreenPadding),
                                horizontalArrangement = Arrangement.spacedBy(13.dp),
                            ) {
                                itemsIndexed(state.mates, key = { _, s -> s.id }) { index, s ->
                                    MateCard(s, index) { vm.toggleFriend(s) }
                                }
                            }
                        }
                    }
                    // Universitetimga tegishli topshiriq e'lonlari — talabalardan keyin,
                    // atrofdagi joylardan oldin (u ham "universitetim" haqidagi ma'lumot).
                    tasksSection(state.tasks, onSeeAll = onOpenTasks, onOpen = onOpenListing)
                }

                nearbySection("Ovqatlar", state.foods, onMap = { showFoodMap = true }) { selectedOffer = it }
                nearbySection("Printerxonalar", state.printShops, onMap = { showPrintMap = true }) { selectedOffer = it }
            }
        }

        if (showPicker) {
            UniversitySheet(
                picker = picker,
                onQuery = vm::onUniversityQuery,
                onSelect = { vm.selectUniversity(it); showPicker = false },
                onDismiss = { showPicker = false },
            )
        }
        if (showStudents) {
            StudentsOverlay(
                students = state.mates,
                onFriend = { vm.toggleFriend(it) },
                onClose = { showStudents = false },
            )
        }
        if (showPrintMap) {
            OffersMapSection(
                title = "Printerxonalar xaritada",
                shops = state.printShops,
                onMarkerTap = { id -> selectedOffer = state.printShops.firstOrNull { it.id == id } },
                onClose = { showPrintMap = false },
            )
        }
        if (showFoodMap) {
            OffersMapSection(
                title = "Ovqatlar xaritada",
                shops = state.foods,
                onMarkerTap = { id -> selectedOffer = state.foods.firstOrNull { it.id == id } },
                onClose = { showFoodMap = false },
            )
        }
        selectedOffer?.let { offer ->
            OfferDetailSheet(offer, onClose = { selectedOffer = null })
        }
    }
}

// ---------------------------------------------------------------------------
// Universitetlar selektori + universitet karti
// ---------------------------------------------------------------------------

/** Bosilganda universitet tanlash sheet'ini ochadi (tepa-past chevron shuni bildiradi). */
@Composable
private fun UniversitiesSelector(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier.fillMaxWidth()
            .clip(shape)
            .background(Sc.TintBlue)
            .border(1.dp, Sc.BorderSoft, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Icon(ScIcons.CapBadge, null, tint = Sc.Brand, modifier = Modifier.size(22.dp))
        ScText("Universitetlar", 16f, FontWeight.ExtraBold, Sc.Brand, Modifier.weight(1f), maxLines = 1)
        Icon(ScIcons.ChevronUpDown, null, tint = Sc.Brand, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun UniversityCard(uni: University, matesCount: Int, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().scCard(radius = 26.dp, elevation = 10.dp).padding(18.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
            ScMonogramTile(uni.monogram, Sc.TintGreenDeep, Sc.Success, size = 78.dp, radius = 22.dp, fontSize = 20f)
            Column(Modifier.weight(1f)) {
                ScText(uni.name, 17f, FontWeight.ExtraBold, Sc.Ink, lineHeight = 21f, letterSpacing = -0.2f)
                if (uni.city.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            ScIcons.MapPin, null, tint = Sc.Muted,
                            modifier = Modifier.padding(top = 1.dp).size(15.dp),
                        )
                        ScText(uni.city, 12.5f, FontWeight.Medium, Sc.Muted, lineHeight = 18f)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            StatChip(ScIcons.Users, "$matesCount talaba", Modifier.weight(1f))
            StatChip(ScIcons.CapBadge, uni.monogram, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(15.dp)
    Row(
        modifier.clip(shape).border(1.dp, Sc.Border, shape).padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = Sc.Brand, modifier = Modifier.size(19.dp))
        ScText(label, 14f, FontWeight.Bold, Sc.Ink, maxLines = 1)
    }
}

@Composable
private fun EmptyUniversity(loading: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().scCard(radius = 26.dp).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Yuklanayotganda kartaning O'ZI skelet bo'ladi: plitka + ikki qator matn.
        if (loading) {
            ScShimmerBox(Modifier.size(66.dp), RoundedCornerShape(20.dp))
            ScShimmerLine(0.55f, 15.dp)
            ScShimmerLine(0.75f, 11.dp)
            return@Column
        }
        ScIconTile(Sc.TintBlue, size = 66.dp, radius = 20.dp) {
            Icon(ScIcons.CapBadge, null, tint = Sc.Brand, modifier = Modifier.size(30.dp))
        }
        ScText("Universitet tanlanmagan", 17f, FontWeight.ExtraBold, Sc.Ink)
        ScText(
            "Yuqoridagi \"Universitetlar\" tugmasidan universitetingizni tanlang.",
            13f, FontWeight.Medium, Sc.Muted, lineHeight = 19f,
        )
    }
}

// ---------------------------------------------------------------------------
// Talaba kartalari
// ---------------------------------------------------------------------------

@Composable
private fun MateCard(student: Student, index: Int, onFriend: () -> Unit) {
    val (tint, accent) = tilePalette[index.mod(tilePalette.size)]
    Column(
        Modifier.width(150.dp).scCard(radius = 26.dp).padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScAvatar(
            name = student.firstName,
            size = 62.dp,
            avatarUrl = student.avatarUrl,
            background = tint,
            initialColor = accent,
            shape = RoundedCornerShape(24.dp),
        )
        Spacer(Modifier.height(12.dp))
        ScText(student.firstName, 16f, FontWeight.ExtraBold, Sc.Ink, maxLines = 1)
        Spacer(Modifier.height(3.dp))
        ScText("${student.faculty} · ${courseText(student.course)}", 12.5f, FontWeight.SemiBold, Sc.Muted, maxLines = 1)
        Spacer(Modifier.height(14.dp))
        FriendButton(student, onFriend)
    }
}

@Composable
private fun FriendButton(student: Student, onFriend: () -> Unit) {
    val pending = student.friendStatus != FriendStatus.NONE
    val label = when (student.friendStatus) {
        FriendStatus.FRIENDS -> "Do'st"
        FriendStatus.PENDING -> "Kutilmoqda"
        FriendStatus.NONE -> "+ Do'st"
    }
    if (pending) {
        ScSoftButton(
            label, onFriend,
            radius = 16.dp, verticalPadding = 10.dp, fontSize = 13.5f,
            background = Sc.TintBlue, color = Sc.Brand,
        )
    } else {
        ScGradientButton(
            label, onFriend,
            radius = 16.dp, verticalPadding = 10.dp, fontSize = 13.5f, weight = FontWeight.Bold,
        )
    }
}

// ---------------------------------------------------------------------------
// Fanlardan yordam — universitetimga tegishli topshiriqlar
// ---------------------------------------------------------------------------

/**
 * "Fanlardan yordam" — AYNAN shu universitetga bog'langan topshiriq e'lonlari
 * (`Listing.universityId`). Bir OTMdagi talaba bir xil fandan, bir xil o'qituvchining
 * topshirig'idan yordam so'raydi — shuning uchun bu ro'yxat universitet ekranida turadi.
 *
 * E'lon yo'q bo'lsa sarlavha baribir ko'rinadi va nima uchun bo'shligini aytadi: bo'lim
 * butunlay yashirilsa, foydalanuvchi bunday imkoniyat borligini umuman bilmay qolardi.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.tasksSection(
    tasks: List<Listing>,
    onSeeAll: () -> Unit,
    onOpen: (String) -> Unit,
) {
    item {
        ScSectionHeader(
            "📚 Fanlardan yordam",
            Modifier.padding(horizontal = Sc.ScreenPadding),
            subtitle = "Universitetimdagi topshiriq e'lonlari",
            action = if (tasks.isNotEmpty()) "Barchasi" else null,
            onAction = onSeeAll,
        )
    }
    if (tasks.isEmpty()) {
        item {
            ScText(
                "Hozircha topshiriq e'loni yo'q.", 13f, FontWeight.Medium, Sc.Muted,
                Modifier.padding(horizontal = Sc.ScreenPadding),
            )
        }
        return
    }
    items(tasks.take(5), key = { it.id }) { listing ->
        TaskCard(listing, Modifier.padding(horizontal = Sc.ScreenPadding)) { onOpen(listing.id) }
    }
}

@Composable
private fun TaskCard(listing: Listing, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val task = listing.taskDetails
    Row(
        modifier.fillMaxWidth().scCard(radius = 20.dp, onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScIconTile(Sc.TintPink, size = 46.dp, radius = 15.dp) {
            Text(listing.emoji, style = TextStyle(fontSize = 21.sp))
        }
        Column(Modifier.weight(1f)) {
            ScText(listing.title, 15f, FontWeight.ExtraBold, Sc.Ink, maxLines = 1)
            val summary = task?.summary().orEmpty()
            if (summary.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                ScText(summary, 12f, FontWeight.Medium, Sc.Muted, maxLines = 1)
            }
            Spacer(Modifier.height(6.dp))
            ScText(
                if (listing.isNegotiable) "Kelishilgan" else "${listing.price.formatSum()} so'm",
                13.5f, FontWeight.ExtraBold, Sc.Success, maxLines = 1,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Universitet atrofidagi joylar
// ---------------------------------------------------------------------------

private fun androidx.compose.foundation.lazy.LazyListScope.nearbySection(
    title: String,
    offers: List<DiscountOffer>,
    onMap: () -> Unit,
    onOffer: (DiscountOffer) -> Unit,
) {
    item {
        ScSectionHeader(
            title,
            Modifier.padding(horizontal = Sc.ScreenPadding),
            action = if (offers.isNotEmpty()) "Xarita" else null,
            actionIcon = ScIcons.Map,
            onAction = onMap,
        )
    }
    if (offers.isNotEmpty()) {
        item {
            LazyRow(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                contentPadding = PaddingValues(horizontal = Sc.ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                items(offers, key = { it.id }) { NearbyOfferCard(it) { onOffer(it) } }
            }
        }
    }
}

@Composable
private fun NearbyOfferCard(shop: DiscountOffer, onClick: () -> Unit) {
    val pizza = ScIcons.Pizza
    Column(
        Modifier.width(240.dp).scCard(radius = 24.dp, onClick = onClick).padding(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScIconTile(Sc.TintOrange, size = 52.dp, radius = 16.dp) { ScGlyph(pizza, 28.dp) }
            Column {
                ScText(shop.merchant, 16f, FontWeight.ExtraBold, Sc.Ink, maxLines = 1)
                if (shop.subcategory.isNotBlank()) {
                    ScText(shop.subcategory, 13f, FontWeight.Bold, Sc.Orange, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        ScText(shop.title, 13f, FontWeight.Medium, Sc.InkSoft, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        ScText("${shop.effectivePrice.sum()} so'm / ${shop.priceUnit}", 16f, FontWeight.ExtraBold, Sc.Orange, maxLines = 1)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(ScIcons.MapPin, null, tint = Sc.Pink, modifier = Modifier.size(14.dp))
            ScText(shop.location ?: "Yunusobod", 12.5f, FontWeight.SemiBold, Sc.Muted, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------------------
// Universitet tanlash sheet'i
// ---------------------------------------------------------------------------

@Composable
private fun UniversitySheet(
    picker: UniversityPickerState,
    onQuery: (String) -> Unit,
    onSelect: (University) -> Unit,
    onDismiss: () -> Unit,
) {
    // Tanlangan universitet — pastdagi tugma bosilguncha faqat belgilanadi.
    var selected by remember { mutableStateOf<University?>(null) }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0B1622).copy(alpha = 0.32f)).clickable(onClick = onDismiss))
        Column(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 46.dp)
                .clip(RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp))
                .background(Sc.Card),
        ) {
            ScSheetHandle()
            Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 14.dp)) {
                ScText("Universitetni tanlang", 22f, FontWeight.ExtraBold, Sc.Ink, letterSpacing = -0.4f)
                Spacer(Modifier.height(15.dp))
                SheetSearchField(picker.query, onQuery)
            }
            Box(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                when {
                    // Universitet qatorlarining skeleti (chapda monogramma plitkasi bor).
                    picker.loading -> ScShimmerList(
                        rows = 6,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
                        rowHeight = 46.dp,
                    )
                    picker.error != null -> CenterNote {
                        ScText("Ro'yxatni yuklab bo'lmadi.\nInternetni tekshiring.", 13f, FontWeight.Medium, Sc.Muted)
                    }
                    picker.results.isEmpty() -> CenterNote {
                        ScText("Universitet topilmadi.", 13f, FontWeight.Medium, Sc.Muted)
                    }
                    else -> LazyColumn(
                        Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 2.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(picker.results, key = { _, u -> u.id }) { index, uni ->
                            UniversityPickRow(uni, index, uni.id == selected?.id) { selected = uni }
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Sc.Bg))
            Box(
                Modifier.fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 18.dp),
            ) {
                val target = selected
                if (target != null) {
                    ScGradientButton("Universitetni tanlang", onClick = { onSelect(target) })
                } else {
                    ScSoftButton("Universitetni tanlang", {}, radius = 18.dp, fontSize = 15.5f, color = Sc.NavIdle)
                }
            }
        }
    }
}

@Composable
private fun CenterNote(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun SheetSearchField(query: String, onQuery: (String) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(Sc.FieldBg)
            .border(1.dp, Sc.Border, shape)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(ScIcons.Search, null, tint = Sc.NavIdle, modifier = Modifier.size(19.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                ScText("Universitet qidiring", 14.5f, FontWeight.Medium, Sc.NavIdle, maxLines = 1)
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = scStyle(14.5f, FontWeight.Medium, Sc.Ink),
                cursorBrush = SolidColor(Sc.Brand),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun UniversityPickRow(uni: University, index: Int, selected: Boolean, onClick: () -> Unit) {
    val (tint, accent) = tilePalette[index.mod(tilePalette.size)]
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(Sc.Card)
            .border(if (selected) 2.dp else 1.dp, if (selected) Sc.Brand else Sc.Border, shape)
            .clickable(onClick = onClick)
            .padding(if (selected) 12.dp else 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        ScMonogramTile(uni.monogram, tint, accent, size = 54.dp, radius = 16.dp, fontSize = 14f)
        Column(Modifier.weight(1f)) {
            ScText(uni.name, 14.5f, FontWeight.ExtraBold, Sc.Ink, lineHeight = 19f, maxLines = 2)
            if (uni.city.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                ScText(uni.city, 12f, FontWeight.Medium, Sc.Muted, maxLines = 1)
            }
        }
        // Radio — tanlanganda brend halqa + ichki nuqta.
        Box(
            Modifier.size(24.dp)
                .border(2.dp, if (selected) Sc.Brand else Sc.Handle, RoundedCornerShape(percent = 50)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(Modifier.size(12.dp).background(Sc.Brand, RoundedCornerShape(percent = 50)))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Talabalar (batafsil) — qidiruv + kurs filtri
// ---------------------------------------------------------------------------

@Composable
private fun StudentsOverlay(students: List<Student>, onFriend: (Student) -> Unit, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var course by remember { mutableStateOf<Int?>(null) }
    val filtered = remember(students, query, course) {
        students.filter {
            (query.isBlank() || it.fullName.contains(query, ignoreCase = true) || it.faculty.contains(query, ignoreCase = true)) &&
                (course == null || it.course == course)
        }
    }

    Column(Modifier.fillMaxSize().background(Sc.Bg)) {
        ScHeader {
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                ScCircleButton(ScIcons.ChevronLeft, onClose, contentDescription = "Orqaga")
                ScHeaderTitle("Talabalar", modifier = Modifier.weight(1f))
            }
        }
        Column(Modifier.padding(horizontal = Sc.ScreenPadding).padding(top = 16.dp)) {
            SheetSearchField(query) { query = it }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CoursePill("Hammasi", course == null) { course = null }
                listOf(1, 2, 3, 4, 5).forEach { c ->
                    CoursePill(courseText(c), course == c) { course = c }
                }
            }
            Spacer(Modifier.height(12.dp))
            ScText("${filtered.size} ta talaba", 12.5f, FontWeight.Medium, Sc.Muted)
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Sc.ScreenPadding, end = Sc.ScreenPadding, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(filtered, key = { _, s -> s.id }) { index, s -> DetailedStudentCard(s, index, onFriend) }
            if (filtered.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                        ScText("Talaba topilmadi.", 13f, FontWeight.Medium, Sc.Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun CoursePill(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.clip(shape)
            .then(if (selected) Modifier.background(Sc.buttonBrush) else Modifier.background(Sc.Chip))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 9.dp),
    ) {
        ScText(label, 13.5f, FontWeight.Bold, if (selected) Color.White else Sc.ChipInk, maxLines = 1)
    }
}

@Composable
private fun DetailedStudentCard(student: Student, index: Int, onFriend: (Student) -> Unit) {
    val (tint, accent) = tilePalette[index.mod(tilePalette.size)]
    Column(Modifier.fillMaxWidth().scCard(radius = 22.dp).padding(15.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            ScAvatar(
                name = student.fullName,
                size = 52.dp,
                avatarUrl = student.avatarUrl,
                background = tint,
                initialColor = accent,
            )
            Column(Modifier.weight(1f)) {
                ScText(student.fullName, 15.5f, FontWeight.ExtraBold, Sc.Ink, maxLines = 1)
                Spacer(Modifier.height(3.dp))
                ScText("${student.faculty} · ${courseText(student.course)}", 12.5f, FontWeight.Medium, Sc.Muted, maxLines = 1)
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ScText("⭐ ${student.rating}", 11.5f, FontWeight.Bold, Sc.MutedLight)
                    ScText("👥 ${student.friendsCount} do'st", 11.5f, FontWeight.Medium, Sc.MutedLight)
                    ScText(student.universityMonogram, 11.5f, FontWeight.Medium, Sc.MutedLight)
                }
            }
            Box(Modifier.width(96.dp)) { FriendButton(student) { onFriend(student) } }
        }
        if (student.interests.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                student.interests.take(5).forEach { interest ->
                    Box(
                        Modifier.clip(RoundedCornerShape(10.dp)).background(Sc.TintBlue)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) { ScText(interest, 11f, FontWeight.Medium, Sc.Brand, maxLines = 1) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// E'lon tafsiloti va xarita
// ---------------------------------------------------------------------------

@Composable
private fun OfferDetailSheet(shop: DiscountOffer, onClose: () -> Unit) {
    val pizza = ScIcons.Pizza
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0B1622).copy(alpha = 0.42f)).clickable(onClick = onClose))
        Column(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(Sc.Card)
                .navigationBarsPadding()
                .padding(start = 22.dp, end = 22.dp, bottom = 20.dp),
        ) {
            ScSheetHandle()
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ScIconTile(Sc.TintOrange, size = 52.dp, radius = 16.dp) { ScGlyph(pizza, 28.dp) }
                Column(Modifier.weight(1f)) {
                    ScText(shop.merchant, 17f, FontWeight.ExtraBold, Sc.Ink, maxLines = 1)
                    if (shop.subcategory.isNotBlank()) {
                        ScText(shop.subcategory, 13f, FontWeight.Bold, Sc.Orange, maxLines = 1)
                    }
                }
                ScCircleButton(ScIcons.Close, onClose, size = 34.dp, iconSize = 16.dp, background = Sc.Chip, tint = Sc.InkSoft)
            }
            Spacer(Modifier.height(12.dp))
            ScText(shop.title, 13.5f, FontWeight.Medium, Sc.InkSoft, lineHeight = 20f)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScText("${shop.effectivePrice.sum()} so'm", 18f, FontWeight.ExtraBold, Sc.Orange)
                if (shop.isDiscount && shop.originalPrice > shop.finalPrice) {
                    Text(
                        shop.originalPrice.sum(),
                        style = scStyle(12.5f, FontWeight.Medium, Sc.MutedLight)
                            .copy(textDecoration = TextDecoration.LineThrough),
                    )
                    Box(
                        Modifier.clip(RoundedCornerShape(10.dp)).background(Sc.buttonBrush)
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                    ) { ScText("−${shop.discountPercent}%", 12f, FontWeight.ExtraBold, Color.White) }
                }
                ScText("/ ${shop.priceUnit}", 11.5f, FontWeight.Medium, Sc.MutedLight)
            }
            shop.promoCode?.let {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(Sc.TintBlue)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) { ScText("Promokod: $it", 12.5f, FontWeight.ExtraBold, Sc.Brand) }
            }
            Spacer(Modifier.height(12.dp))
            ScText("📍 ${shop.location ?: "Yunusobod, Toshkent"}", 12.5f, FontWeight.Medium, Sc.Muted)
        }
    }
}

@Composable
private fun OffersMapSection(
    title: String,
    shops: List<DiscountOffer>,
    onMarkerTap: (String) -> Unit,
    onClose: () -> Unit,
) {
    val located = shops.filter { it.hasLocation }
    val markers = located.map {
        OfferMarker(it.id, it.lat, it.lng, "${it.effectivePrice.priceShort()} so'm", hexRgb(it.bannerAccent), highlight = true)
    }
    val center = if (markers.isEmpty()) MapPoint(41.311081, 69.240562)
    else MapPoint(markers.map { it.lat }.average(), markers.map { it.lng }.average())

    // Fon rangi — xarita sahifasi fon oqimida yig'ilgani uchun birinchi kadrlarda WebView
    // hali yo'q; fonsiz o'sha lahzada ostidagi ro'yxat ko'rinib qolardi.
    Box(Modifier.fillMaxSize().background(Sc.Bg)) {
        OffersMap(markers, center, Sc.IsDark, rememberUserLocation(), 100, Modifier.fillMaxSize(), onMarkerTap = onMarkerTap)
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Sc.ScreenPadding, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            ScCircleButton(ScIcons.ChevronLeft, onClose, size = 46.dp, contentDescription = "Orqaga")
            Box(
                Modifier.clip(RoundedCornerShape(16.dp)).background(Sc.Card)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) { ScText(title, 15f, FontWeight.ExtraBold, Sc.Ink, maxLines = 1) }
        }
    }
}

// "300000" -> "300 000"
private fun Long.sum(): String = toString().reversed().chunked(3).joinToString(" ").reversed()

private fun Long.priceShort(): String = when {
    this >= 1_000_000 -> {
        val w = this / 1_000_000
        val f = (this % 1_000_000) / 100_000
        if (f == 0L) "${w}M" else "$w.${f}M"
    }
    this >= 1_000 -> "${this / 1_000}k"
    else -> "$this"
}

private fun hexRgb(argb: Long): String = "#" + (argb and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()

private fun courseText(course: Int): String = if (course >= 5) "Magistr" else "$course-kurs"
