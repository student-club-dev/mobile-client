package dev.feature.auth.presentation.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import dev.core.domain.model.FriendStatus
import dev.core.domain.model.Student
import dev.core.domain.model.University
import dev.core.designsystem.components.AppFontFamily
import dev.core.designsystem.components.AppIcons
import dev.core.designsystem.components.GlassTextField
import dev.core.designsystem.theme.AppPalette
import dev.core.designsystem.theme.appPalette
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MyUniversityScreen(vm: MyUniversityViewModel = koinViewModel()) {
    val palette = appPalette
    val state by vm.state.collectAsStateWithLifecycle()
    val picker by vm.picker.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }

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
                    Text("Universitetim talabalari", style = TextStyle(fontFamily = AppFontFamily, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink), modifier = Modifier.padding(top = 4.dp))
                }
                if (state.mates.isEmpty()) {
                    item {
                        Text("Bu universitetdan hozircha talaba yo'q.", style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.5f.sp, color = palette.inkFaint))
                    }
                } else {
                    items(state.mates, key = { it.id }) { s ->
                        MateCard(s, Color(uni.accent), palette, onToggleFriend = { vm.toggleFriend(s) })
                    }
                }
            }
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

@Composable
private fun MateCard(student: Student, accent: Color, palette: AppPalette, onToggleFriend: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(palette.glass).border(1.dp, palette.border, RoundedCornerShape(16.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(student.initial, style = TextStyle(fontFamily = AppFontFamily, fontSize = 17.sp, fontWeight = FontWeight.Black, color = accent))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(student.fullName, style = TextStyle(fontFamily = AppFontFamily, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${student.faculty} · ${courseText(student.course)}", style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.5f.sp, color = palette.inkMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val pending = student.friendStatus == FriendStatus.PENDING
        Box(
            Modifier.clip(RoundedCornerShape(11.dp))
                .background(if (pending) palette.glassStrong else palette.primary)
                .border(1.dp, if (pending) palette.border else palette.primary, RoundedCornerShape(11.dp))
                .clickable(onClick = onToggleFriend).padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                if (pending) "Kutilmoqda" else "+ Do'st",
                style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.5f.sp, fontWeight = FontWeight.Bold, color = if (pending) palette.inkMuted else Color.White),
            )
        }
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

private fun courseText(course: Int): String = if (course >= 5) "Magistr" else "$course-kurs"
