package dev.feature.university.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.core.domain.model.DiscountOffer
import dev.feature.students.domain.model.FriendStatus
import dev.feature.students.domain.model.Student
import dev.feature.university.domain.model.University
import dev.core.designsystem.components.AppFontFamily
import dev.core.designsystem.components.AppIcons
import dev.core.designsystem.components.GlassTextField
import dev.core.designsystem.theme.AppPalette
import dev.core.designsystem.theme.appPalette
import dev.core.designsystem.map.MapPoint
import dev.core.designsystem.map.OfferMarker
import dev.core.designsystem.map.OffersMap
import dev.core.designsystem.map.rememberUserLocation
import androidx.compose.ui.text.style.TextDecoration
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MyUniversityScreen(vm: MyUniversityViewModel = koinViewModel()) {
    val palette = appPalette
    val state by vm.state.collectAsStateWithLifecycle()
    val picker by vm.picker.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }
    var showStudents by remember { mutableStateOf(false) }
    var showPrintMap by remember { mutableStateOf(false) }
    var showFoodMap by remember { mutableStateOf(false) }
    var selectedOffer by remember { mutableStateOf<DiscountOffer?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 54.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Mening universitetim", style = TextStyle(fontFamily = AppFontFamily, fontSize = 24.sp, fontWeight = FontWeight.Black, color = palette.ink))
            }
            item {
                UniversitiesButton(palette) { vm.loadUniversities(); showPicker = true }
            }

            val uni = state.university
            if (uni == null) {
                item { EmptyUniversity(palette, loading = state.loading) }
            } else {
                item { UniversityHero(uni, state.mates.size, palette) }
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Do'stlashish uchun talabalar", style = TextStyle(fontFamily = AppFontFamily, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink), modifier = Modifier.weight(1f))
                        if (state.mates.isNotEmpty()) {
                            Row(Modifier.clip(RoundedCornerShape(9.dp)).clickable { showStudents = true }.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Barchasi", style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.5f.sp, fontWeight = FontWeight.Bold, color = palette.primary))
                                Icon(AppIcons.ChevronRight, null, tint = palette.primary, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }
                if (state.mates.isEmpty()) {
                    item {
                        Text("Hozircha talaba yo'q.", style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.5f.sp, color = palette.inkFaint))
                    }
                } else {
                    item {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                            state.mates.forEach { s ->
                                MateConnectCard(s, palette, onFriend = { vm.toggleFriend(s) })
                            }
                        }
                    }
                }
            }

            nearbySection(
                title = "Ovqatlar",
                offers = state.foods,
                palette = palette,
                onMap = { showFoodMap = true },
                onOffer = { selectedOffer = it },
            )
            nearbySection(
                title = "Printerxonalar",
                offers = state.printShops,
                palette = palette,
                onMap = { showPrintMap = true },
                onOffer = { selectedOffer = it },
            )
        }

        if (showPicker) {
            UniversitySheet(
                picker = picker,
                palette = palette,
                onQuery = vm::onUniversityQuery,
                onSelect = { vm.selectUniversity(it); showPicker = false },
                onDismiss = { showPicker = false },
            )
        }
        if (showStudents) {
            StudentsOverlay(
                students = state.mates,
                palette = palette,
                onFriend = { vm.toggleFriend(it) },
                onClose = { showStudents = false },
            )
        }
        if (showPrintMap) {
            OffersMapSection(
                title = "Printerxonalar xaritada",
                shops = state.printShops,
                palette = palette,
                onMarkerTap = { id -> selectedOffer = state.printShops.firstOrNull { it.id == id } },
                onClose = { showPrintMap = false },
            )
        }
        if (showFoodMap) {
            OffersMapSection(
                title = "Ovqatlar xaritada",
                shops = state.foods,
                palette = palette,
                onMarkerTap = { id -> selectedOffer = state.foods.firstOrNull { it.id == id } },
                onClose = { showFoodMap = false },
            )
        }
        selectedOffer?.let { offer ->
            OfferDetailSheet(offer, palette, onClose = { selectedOffer = null })
        }
    }
}

/** LazyColumn ichida universitet atrofidagi joylar bo'limi (horizontal + Xarita). */
private fun androidx.compose.foundation.lazy.LazyListScope.nearbySection(
    title: String,
    offers: List<DiscountOffer>,
    palette: AppPalette,
    onMap: () -> Unit,
    onOffer: (DiscountOffer) -> Unit,
) {
    item {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = TextStyle(fontFamily = AppFontFamily, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink), modifier = Modifier.weight(1f))
            if (offers.isNotEmpty()) {
                Row(Modifier.clip(RoundedCornerShape(9.dp)).clickable(onClick = onMap).padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("🗺", style = TextStyle(fontSize = 13.sp))
                    Text("Xarita", style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.5f.sp, fontWeight = FontWeight.Bold, color = palette.primary))
                }
            }
        }
    }
    if (offers.isNotEmpty()) {
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                offers.forEach { NearbyOfferCard(it, palette, onClick = { onOffer(it) }) }
            }
        }
    }
}

@Composable
private fun UniversitiesButton(palette: AppPalette, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp))
            .background(palette.primary.copy(alpha = 0.12f)).border(1.dp, palette.primary.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(AppIcons.GraduationCap, null, tint = palette.primary, modifier = Modifier.size(18.dp))
        Text("Universitetlar", style = TextStyle(fontFamily = AppFontFamily, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = palette.primary), modifier = Modifier.weight(1f))
        Icon(AppIcons.ChevronRight, null, tint = palette.primary, modifier = Modifier.size(16.dp))
    }
}

// ---------------------------------------------------------------------------
// Universitet tanlash BottomSheet — prof-emis.edu.uz ro'yxati, qidiruvli
// ---------------------------------------------------------------------------
@Composable
private fun UniversitySheet(
    picker: UniversityPickerState,
    palette: AppPalette,
    onQuery: (String) -> Unit,
    onSelect: (University) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetBg = if (palette.dark) Color(0xFF15122A) else Color.White
    // Tanlangan universitet — "Tanlash" tugmasi bosilguncha faqat belgilanadi (darrov qo'llanmaydi).
    var selected by remember { mutableStateOf<University?>(null) }

    Box(Modifier.fillMaxSize()) {
        // Fon (scrim) — bosilsa yopiladi
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(onClick = onDismiss))

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(sheetBg).padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(palette.border))
            Spacer(Modifier.height(12.dp))
            Text("Universitetni tanlang", style = TextStyle(fontFamily = AppFontFamily, fontSize = 17.sp, fontWeight = FontWeight.Black, color = palette.ink))
            Spacer(Modifier.height(11.dp))
            GlassTextField(picker.query, onQuery, "Universitet qidiring", leading = AppIcons.Search, height = 46)
            Spacer(Modifier.height(12.dp))

            Box(Modifier.fillMaxWidth().weight(1f)) {
                when {
                    picker.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = palette.primary)
                    }
                    picker.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Ro'yxatni yuklab bo'lmadi.\nInternetni tekshiring.", style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, color = palette.inkFaint))
                    }
                    picker.results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Universitet topilmadi.", style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, color = palette.inkFaint))
                    }
                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(picker.results, key = { it.id }) { uni ->
                            UniversityPickRow(uni, selected = uni.id == selected?.id, palette, onClick = { selected = uni })
                        }
                    }
                }
            }

            // Pastki tasdiqlash tugmasi — tanlangan bo'lsagina faol. Tab paneli ustida ko'rinadi.
            Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 96.dp)) {
                val enabled = selected != null
                Box(
                    Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (enabled) palette.primary else palette.glassStrong)
                        .then(if (enabled) Modifier.clickable { onSelect(selected!!) } else Modifier)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (enabled) "Tanlash — ${selected!!.monogram}" else "Universitetni tanlang",
                        style = TextStyle(fontFamily = AppFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Black, color = if (enabled) Color.White else palette.inkFaint),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun UniversityPickRow(uni: University, selected: Boolean, palette: AppPalette, onClick: () -> Unit) {
    val accent = Color(uni.accent)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (selected) accent.copy(alpha = 0.12f) else palette.glass)
            .border(if (selected) 1.6.dp else 1.dp, if (selected) accent else palette.border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Text(uni.monogram, style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Black, color = accent))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(uni.name, style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.5f.sp, fontWeight = FontWeight.Bold, color = palette.ink), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (uni.city.isNotBlank()) {
                Text(uni.city, style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.sp, color = palette.inkFaint), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // Tanlangan bo'lsa — belgi
        Box(
            Modifier.size(24.dp).clip(RoundedCornerShape(12.dp))
                .background(if (selected) accent else Color.Transparent)
                .border(1.5.dp, if (selected) accent else palette.border, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(AppIcons.Check, "Tanlangan", tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun UniversityHero(uni: University, matesCount: Int, palette: AppPalette) {
    val accent = Color(uni.accent)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(palette.glass).border(1.dp, palette.border, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(uni.monogram, style = TextStyle(fontFamily = AppFontFamily, fontSize = 17.sp, fontWeight = FontWeight.Black, color = accent))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(uni.name, style = TextStyle(fontFamily = AppFontFamily, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink), maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (uni.city.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(AppIcons.Building, null, tint = palette.inkFaint, modifier = Modifier.size(13.dp))
                        Text(uni.city, style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.sp, color = palette.inkMuted), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        uni.faculty?.let {
            Box(Modifier.clip(RoundedCornerShape(9.dp)).background(accent.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                Text(it, style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.5f.sp, fontWeight = FontWeight.Bold, color = accent))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip(AppIcons.Users, "$matesCount talaba", palette, Modifier.weight(1f))
            StatChip(AppIcons.GraduationCap, uni.monogram, palette, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, palette: AppPalette, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(palette.glassStrong).border(1.dp, palette.border, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(icon, null, tint = palette.primary, modifier = Modifier.size(16.dp))
        Text(label, style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.5f.sp, fontWeight = FontWeight.Bold, color = palette.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// Horizontal talaba kartasi (HomeScreen uslubida) — do'stlashish uchun.
@Composable
private fun MateConnectCard(student: Student, palette: AppPalette, onFriend: (Student) -> Unit) {
    Column(
        Modifier.width(132.dp).clip(RoundedCornerShape(16.dp)).background(palette.glass).border(1.dp, palette.border, RoundedCornerShape(16.dp)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(999.dp)).background(palette.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(student.initial, style = TextStyle(fontFamily = AppFontFamily, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = palette.primary))
        }
        Text(student.firstName, style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${student.faculty} · ${courseText(student.course)}", style = TextStyle(fontFamily = AppFontFamily, fontSize = 10.5f.sp, color = palette.inkFaint), maxLines = 1, overflow = TextOverflow.Ellipsis)
        FriendPill(student, palette, onFriend)
    }
}

@Composable
private fun FriendPill(student: Student, palette: AppPalette, onFriend: (Student) -> Unit) {
    val pending = student.friendStatus == FriendStatus.PENDING
    val friends = student.friendStatus == FriendStatus.FRIENDS
    val label = when {
        friends -> "Do'st"
        pending -> "Kutilmoqda"
        else -> "+ Do'st"
    }
    val bg = if (pending || friends) palette.primary.copy(alpha = 0.12f) else palette.primary
    val fg = if (pending || friends) palette.primary else Color.White
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(bg).clickable { onFriend(student) }.padding(vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = fg), maxLines = 1)
    }
}

// ---------------------------------------------------------------------------
// Talabalar (batafsil) — qidiruv + kurs filtri, do'stlashish
// ---------------------------------------------------------------------------
@Composable
private fun StudentsOverlay(students: List<Student>, palette: AppPalette, onFriend: (Student) -> Unit, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var course by remember { mutableStateOf<Int?>(null) }
    val filtered = remember(students, query, course) {
        students.filter {
            (query.isBlank() || it.fullName.contains(query, ignoreCase = true) || it.faculty.contains(query, ignoreCase = true)) &&
                (course == null || it.course == course)
        }
    }

    Column(Modifier.fillMaxSize().background(palette.bgBrush)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 54.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(palette.glass).border(1.dp, palette.border, RoundedCornerShape(13.dp)).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.ArrowLeft, "Orqaga", tint = palette.ink, modifier = Modifier.size(18.dp)) }
            Box(Modifier.weight(1f)) {
                GlassTextField(query, { query = it }, "Ism yoki fakultet qidiring", leading = AppIcons.Search, height = 46)
            }
        }

        // Kurs filtri
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CoursePill("Hammasi", course == null, palette) { course = null }
            listOf(1, 2, 3, 4, 5).forEach { c ->
                CoursePill(courseText(c), course == c, palette) { course = c }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("${filtered.size} ta talaba", style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.sp, color = palette.inkMuted), modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(filtered, key = { it.id }) { s -> DetailedStudentCard(s, palette, onFriend) }
            if (filtered.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                        Text("Talaba topilmadi.", style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, color = palette.inkFaint))
                    }
                }
            }
        }
    }
}

@Composable
private fun CoursePill(label: String, selected: Boolean, palette: AppPalette, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(11.dp))
            .background(if (selected) palette.primary else palette.glass)
            .border(1.dp, if (selected) palette.primary else palette.border, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.5f.sp, fontWeight = FontWeight.Bold, color = if (selected) Color.White else palette.ink), maxLines = 1)
    }
}

@Composable
private fun DetailedStudentCard(student: Student, palette: AppPalette, onFriend: (Student) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(palette.glass).border(1.dp, palette.border, RoundedCornerShape(16.dp)).padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(999.dp)).background(palette.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(student.initial, style = TextStyle(fontFamily = AppFontFamily, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = palette.primary))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(student.fullName, style = TextStyle(fontFamily = AppFontFamily, fontSize = 14.5f.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${student.faculty} · ${courseText(student.course)}", style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.5f.sp, color = palette.inkMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("⭐ ${student.rating}", style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.inkFaint))
                    Text("👥 ${student.friendsCount} do'st", style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.sp, color = palette.inkFaint))
                    Text("${student.universityMonogram}", style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.sp, color = palette.inkFaint))
                }
            }
            CompactFriendButton(student, palette, onFriend)
        }
        if (student.interests.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                student.interests.take(5).forEach { interest ->
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(palette.primary.copy(alpha = 0.08f)).padding(horizontal = 9.dp, vertical = 4.dp)) {
                        Text(interest, style = TextStyle(fontFamily = AppFontFamily, fontSize = 10.5f.sp, fontWeight = FontWeight.Medium, color = palette.primary), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactFriendButton(student: Student, palette: AppPalette, onFriend: (Student) -> Unit) {
    val pending = student.friendStatus == FriendStatus.PENDING
    val friends = student.friendStatus == FriendStatus.FRIENDS
    val label = when { friends -> "Do'st"; pending -> "Kutilmoqda"; else -> "+ Do'st" }
    val active = !pending && !friends
    Box(
        Modifier.clip(RoundedCornerShape(11.dp))
            .background(if (active) palette.primary else palette.glassStrong)
            .border(1.dp, if (active) palette.primary else palette.border, RoundedCornerShape(11.dp))
            .clickable { onFriend(student) }.padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.5f.sp, fontWeight = FontWeight.Bold, color = if (active) Color.White else palette.inkMuted), maxLines = 1)
    }
}

@Composable
private fun EmptyUniversity(palette: AppPalette, loading: Boolean) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(palette.glass).border(1.dp, palette.border, RoundedCornerShape(20.dp)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(palette.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(AppIcons.GraduationCap, null, tint = palette.primary, modifier = Modifier.size(26.dp))
        }
        Text(
            if (loading) "Yuklanmoqda…" else "Universitet tanlanmagan",
            style = TextStyle(fontFamily = AppFontFamily, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink),
        )
        if (!loading) {
            Text(
                "Yuqoridagi \"Universitetlar\" tugmasidan universitetingizni tanlang.",
                style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.5f.sp, color = palette.inkMuted),
            )
        }
        Spacer(Modifier.height(2.dp))
    }
}

// ---------------------------------------------------------------------------
// Universitet atrofidagi joy kartasi (horizontal) — bosilsa kengayadi
// ---------------------------------------------------------------------------
@Composable
private fun NearbyOfferCard(shop: DiscountOffer, palette: AppPalette, onClick: () -> Unit) {
    val accent = Color(shop.bannerAccent)
    Column(
        Modifier.width(180.dp).clip(RoundedCornerShape(16.dp)).background(palette.glass).border(1.dp, palette.border, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Text(shop.emoji, style = TextStyle(fontSize = 20.sp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(shop.merchant, style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.5f.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (shop.subcategory.isNotBlank()) {
                    Text(shop.subcategory, style = TextStyle(fontFamily = AppFontFamily, fontSize = 10.5f.sp, fontWeight = FontWeight.Bold, color = accent), maxLines = 1)
                }
            }
        }
        Text(shop.title, style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.sp, color = palette.inkMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${shop.effectivePrice.sum()} so'm / ${shop.priceUnit}", style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Black, color = accent))
        Text("📍 ${shop.location ?: "Yunusobod"}", style = TextStyle(fontFamily = AppFontFamily, fontSize = 10.sp, color = palette.inkFaint), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// Kengaytirilgan e'lon kartasi (pastdan chiqadi).
@Composable
private fun OfferDetailSheet(shop: DiscountOffer, palette: AppPalette, onClose: () -> Unit) {
    val accent = Color(shop.bannerAccent)
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(onClick = onClose))
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(if (palette.dark) Color(0xFF15122A) else Color.White).padding(16.dp).padding(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Box(Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Text(shop.emoji, style = TextStyle(fontSize = 25.sp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(shop.merchant, style = TextStyle(fontFamily = AppFontFamily, fontSize = 15.sp, fontWeight = FontWeight.Black, color = palette.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (shop.subcategory.isNotBlank()) Text(shop.subcategory, style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.5f.sp, fontWeight = FontWeight.Bold, color = accent))
                }
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(palette.glass).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Text("✕", style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Black, color = palette.inkMuted))
                }
            }
            Text(shop.title, style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.5f.sp, fontWeight = FontWeight.SemiBold, color = palette.ink))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("${shop.effectivePrice.sum()} so'm", style = TextStyle(fontFamily = AppFontFamily, fontSize = 17.sp, fontWeight = FontWeight.Black, color = accent))
                if (shop.isDiscount && shop.originalPrice > shop.finalPrice) {
                    Text("${shop.originalPrice.sum()}", style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.sp, color = palette.inkFaint, textDecoration = TextDecoration.LineThrough))
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(accent).padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text("−${shop.discountPercent}%", style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White))
                    }
                }
                Text("/ ${shop.priceUnit}", style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.sp, color = palette.inkFaint))
            }
            shop.promoCode?.let {
                Box(Modifier.clip(RoundedCornerShape(9.dp)).background(palette.primary.copy(alpha = 0.10f)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text("Promokod: $it", style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = palette.primary))
                }
            }
            Text("📍 ${shop.location ?: "Yunusobod, Toshkent"}", style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.sp, color = palette.inkMuted))
        }
    }
}

// Joylashuvlar xaritada.
@Composable
private fun OffersMapSection(title: String, shops: List<DiscountOffer>, palette: AppPalette, onMarkerTap: (String) -> Unit, onClose: () -> Unit) {
    val located = shops.filter { it.hasLocation }
    val markers = located.map { OfferMarker(it.id, it.lat, it.lng, "${it.effectivePrice.priceShort()} so'm", hexRgb(it.bannerAccent), highlight = true) }
    val center = if (markers.isEmpty()) MapPoint(41.311081, 69.240562)
    else MapPoint(markers.map { it.lat }.average(), markers.map { it.lng }.average())

    // Full-bleed xarita (kulrang bandsiz). Tab panel (88dp) uchun control'lar pastdan ko'tarilgan.
    Box(Modifier.fillMaxSize()) {
        OffersMap(markers, center, palette.dark, rememberUserLocation(), 100, Modifier.fillMaxSize(), onMarkerTap = onMarkerTap)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 54.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(palette.glass).border(1.dp, palette.border, RoundedCornerShape(13.dp)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(AppIcons.ArrowLeft, "Orqaga", tint = palette.ink, modifier = Modifier.size(18.dp))
            }
            Box(Modifier.clip(RoundedCornerShape(11.dp)).background(palette.glass).border(1.dp, palette.border, RoundedCornerShape(11.dp)).padding(horizontal = 12.dp, vertical = 9.dp)) {
                Text(title, style = TextStyle(fontFamily = AppFontFamily, fontSize = 15.sp, fontWeight = FontWeight.Black, color = palette.ink))
            }
        }
    }
}

// "300" -> "300", "3000" -> "3k"
private fun Long.sum(): String = toString().reversed().chunked(3).joinToString(" ").reversed()
private fun Long.priceShort(): String = when {
    this >= 1_000_000 -> { val w = this / 1_000_000; val f = (this % 1_000_000) / 100_000; if (f == 0L) "${w}M" else "$w.${f}M" }
    this >= 1_000 -> "${this / 1_000}k"
    else -> "$this"
}
private fun hexRgb(argb: Long): String = "#" + (argb and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()

private fun courseText(course: Int): String = if (course >= 5) "Magistr" else "$course-kurs"
