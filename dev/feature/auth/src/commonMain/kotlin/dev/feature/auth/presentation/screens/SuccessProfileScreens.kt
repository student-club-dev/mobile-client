package dev.feature.auth.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.core.uikit.theme.Success
import dev.core.uikit.theme.SuccessDeep
import dev.core.uikit.components.AppFontFamily
import dev.core.uikit.components.AppIcons
import dev.core.uikit.components.AppScreenScaffold
import dev.core.uikit.components.BackButton
import dev.core.uikit.components.ErrorText
import dev.core.uikit.components.FieldLabel
import dev.core.uikit.components.GlassTextField
import dev.core.uikit.components.PrimaryButton
import dev.core.uikit.components.ScreenTitle
import dev.core.uikit.components.ScShimmerList
import dev.feature.auth.presentation.flow.AuthFlowState
import dev.feature.auth.presentation.flow.AuthFlowViewModel
import dev.feature.auth.presentation.flow.CourseYear
import dev.feature.auth.presentation.flow.ProfileGender
import dev.feature.university.domain.model.University
import dev.core.uikit.theme.AppPalette
import dev.core.uikit.theme.appPalette

// ===========================================================================
// 1j — SUCCESS / ROLE PICKER
// ===========================================================================

@Composable
fun SuccessScreen(
    onContinue: () -> Unit,
    palette: AppPalette = appPalette,
) {
    AppScreenScaffold(scroll = true, topPadding = 64) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(88.dp)
                    .background(Brush.linearGradient(listOf(Success, SuccessDeep)), RoundedCornerShape(999.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Check, null, tint = Color.White, modifier = Modifier.size(46.dp))
            }
            Spacer(Modifier.height(20.dp))
            ScreenTitle("Tabriklaymiz! 🎉")
            Spacer(Modifier.height(6.dp))
            Text(
                "Hisobingiz tayyor. Endi profilingizni to'ldiramiz.",
                style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, color = palette.inkMuted, textAlign = TextAlign.Center, lineHeight = 19.sp),
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }

        Spacer(Modifier.height(26.dp))
        PrimaryButton("Davom etish", onContinue, trailingIcon = AppIcons.ArrowRight)
    }
}

// ===========================================================================
// 1n — PROFILE COMPLETION
// ===========================================================================

@Composable
fun ProfileScreen(
    state: AuthFlowState,
    vm: AuthFlowViewModel,
    onBack: () -> Unit,
    onPickUniversity: () -> Unit,
    onStart: () -> Unit,
    palette: AppPalette = appPalette,
) {
    AppScreenScaffold(scroll = true, horizontalPadding = 20, topPadding = 54) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            BackButton(onBack)
            Column {
                ScreenTitle("Profilingiz haqida", size = 20)
                Text("Deyarli tayyor — 2-qadam / 2", style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.5f.sp, fontWeight = FontWeight.SemiBold, color = palette.inkFaint))
            }
        }
        Spacer(Modifier.height(14.dp))
        // progress 3 segment, 2 filled
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i ->
                Box(Modifier.weight(1f).height(5.dp).background(if (i < 2) palette.primary else palette.primary.copy(alpha = 0.16f), RoundedCornerShape(3.dp)))
            }
        }

        Spacer(Modifier.height(16.dp))
        FieldLabel("Ism va familya")
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            GlassTextField(state.firstName, vm::onFirstNameChange, "Aziz", Modifier.weight(1f), height = 46)
            GlassTextField(state.lastName, vm::onLastNameChange, "Karimov", Modifier.weight(1f), height = 46)
        }

        Spacer(Modifier.height(14.dp))
        FieldLabel("Universitet")
        Spacer(Modifier.height(8.dp))
        UniversitySelectorRow(state.selectedUniversity, onPickUniversity, palette)

        Spacer(Modifier.height(14.dp))
        FieldLabel("Tug‘ilgan yil")
        Spacer(Modifier.height(8.dp))
        BirthYearRow(state.birthYear, vm::onBirthYearChange, palette)

        Spacer(Modifier.height(14.dp))
        FieldLabel("Nechanchi kurs")
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            CourseYear.entries.forEach { c ->
                CourseOption(c, state.courseYear == c, { vm.onCourseYearChange(c) }, Modifier.weight(1f), palette)
            }
        }

        // Ixtiyoriy: talabalar qidiruvidagi jins filtri aynan shu maydonga tayanadi
        // (ko'rsatilmasa, filtrlangan ro'yxatga tushmaysiz).
        Spacer(Modifier.height(14.dp))
        FieldLabel("Jins (ixtiyoriy)")
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ProfileGender.entries.forEach { g ->
                OptionChip(g.label, state.gender == g, { vm.onGenderChange(g) }, Modifier.weight(1f), palette)
            }
        }

        ErrorText(state.error)

        Spacer(Modifier.height(20.dp))
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Boshlash", onStart, enabled = !state.isLoading, trailingIcon = AppIcons.ArrowRight)
    }
}

/** Universitet tanlash qatori — ham ro'yxatdan o'tishda, ham profil qadamida ishlatiladi. */
@Composable
internal fun UniversitySelectorRow(university: University?, onClick: () -> Unit, palette: AppPalette) {
    val shape = RoundedCornerShape(13.dp)
    Row(
        Modifier.fillMaxWidth().height(52.dp).clip(shape)
            .background(palette.fieldBg)
            .border(1.5.dp, palette.primary, shape)
            .clickableNoRipple(onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(32.dp).background(palette.primary.copy(alpha = 0.12f), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
            Icon(AppIcons.GraduationCap, null, tint = palette.primary, modifier = Modifier.size(17.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                university?.name ?: "Universitetni tanlang",
                style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.5f.sp, fontWeight = FontWeight.Bold, color = palette.ink),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                university?.let { "${it.city} · Universitet" } ?: "Ro‘yxatdan tanlang",
                style = TextStyle(fontFamily = AppFontFamily, fontSize = 10.5f.sp, color = palette.inkFaint),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(AppIcons.ChevronDown, null, tint = palette.primary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun BirthYearRow(year: Int, onSelect: (Int) -> Unit, palette: AppPalette) {
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(13.dp)
    Box {
        Row(
            Modifier.fillMaxWidth().height(46.dp).clip(shape)
                .background(palette.fieldBg)
                .border(1.dp, palette.border, shape)
                .clickableNoRipple { expanded = true }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(AppIcons.Calendar, null, tint = palette.inkFaint, modifier = Modifier.size(16.dp))
            Text("$year", style = TextStyle(fontFamily = AppFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = palette.ink), modifier = Modifier.weight(1f))
            Icon(AppIcons.ChevronDown, null, tint = palette.inkFaint, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 280.dp)) {
            (2010 downTo 1980).forEach { y ->
                DropdownMenuItem(text = { Text("$y", style = TextStyle(fontFamily = AppFontFamily, fontWeight = if (y == year) FontWeight.Bold else FontWeight.Normal)) }, onClick = { onSelect(y); expanded = false })
            }
        }
    }
}

@Composable
private fun CourseOption(course: CourseYear, active: Boolean, onClick: () -> Unit, modifier: Modifier, palette: AppPalette) =
    OptionChip(
        label = course.label,
        active = active,
        onClick = onClick,
        modifier = modifier,
        palette = palette,
        fontSize = if (course == CourseYear.MASTER) 13f else 14f,
    )

/** Kurs va jins tanlovidagi bir xil ko'rinishdagi tugma. */
@Composable
private fun OptionChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    palette: AppPalette,
    fontSize: Float = 14f,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .height(44.dp)
            .clip(shape)
            .background(if (active) palette.primary else palette.glass)
            .then(if (active) Modifier else Modifier.border(1.dp, palette.border, shape))
            .clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = TextStyle(fontFamily = AppFontFamily, fontSize = fontSize.sp, fontWeight = FontWeight.ExtraBold, color = if (active) Color.White else palette.inkMuted),
        )
    }
}

// ===========================================================================
// 1o — UNIVERSITY PICKER
// ===========================================================================

@Composable
fun UniversityPickerScreen(
    state: AuthFlowState,
    vm: AuthFlowViewModel,
    onClose: () -> Unit,
    onSelectDone: () -> Unit,
    palette: AppPalette = appPalette,
) {
    // Ro'yxat prof-emis API'sidan yuklanadi (ekran ochilganda bir marta).
    val picker by vm.universityPicker.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadUniversities() }

    AppScreenScaffold(horizontalPadding = 18, topPadding = 54) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            BackButton(onClose, icon = AppIcons.Close)
            ScreenTitle("Universitetni tanlang", size = 18)
        }
        Spacer(Modifier.height(14.dp))
        GlassTextField(
            value = picker.query,
            onValueChange = vm::onUniversityQueryChange,
            placeholder = "Toshkent",
            leading = AppIcons.Search,
            focused = true,
            height = 46,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            when {
                picker.loading -> "YUKLANMOQDA…"
                picker.error != null -> "RO‘YXATNI YUKLAB BO‘LMADI"
                else -> "${picker.results.size} TA NATIJA"
            },
            style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = palette.inkFaint),
        )
        Spacer(Modifier.height(8.dp))

        if (picker.loading) {
            // Universitet qatorlarining skeleti — ro'yxat kelganda joy o'zgarmaydi.
            ScShimmerList(rows = 6, leading = false, modifier = Modifier.weight(1f), spacing = 10.dp)
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(picker.results, key = { it.id }) { uni ->
                    UniversityRow(uni, state.universityId == uni.id, { vm.onUniversitySelected(uni) }, palette)
                }
            }
        }
        picker.error?.let { ErrorText(it) }

        Spacer(Modifier.height(12.dp))
        PrimaryButton("Tanlash", onSelectDone, enabled = state.universityId != null)
    }
}

@Composable
private fun UniversityRow(uni: University, selected: Boolean, onClick: () -> Unit, palette: AppPalette) {
    val shape = RoundedCornerShape(14.dp)
    val accent = Color(uni.accent)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) palette.primary.copy(alpha = 0.08f) else palette.glass)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) palette.primary else palette.border, shape)
            .clickableNoRipple(onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(36.dp).background(if (selected) palette.primary else accent.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(uni.monogram, style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (selected) Color.White else accent))
        }
        Column(Modifier.weight(1f)) {
            Text(uni.name, style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(uni.city, style = TextStyle(fontFamily = AppFontFamily, fontSize = 10.5f.sp, color = palette.inkFaint))
        }
        if (selected) {
            Box(Modifier.size(20.dp).background(palette.primary, RoundedCornerShape(999.dp)), contentAlignment = Alignment.Center) {
                Icon(AppIcons.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
            }
        }
    }
}
