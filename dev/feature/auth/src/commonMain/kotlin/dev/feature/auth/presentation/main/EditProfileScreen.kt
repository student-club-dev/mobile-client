package dev.feature.auth.presentation.main

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.core.domain.model.University
import dev.core.domain.model.UserProfile
import dev.feature.auth.presentation.components.AuthFontFamily
import dev.feature.auth.presentation.components.AuthIcons
import dev.feature.auth.presentation.components.GlassTextField
import dev.feature.auth.presentation.components.PrimaryButton
import dev.feature.auth.presentation.theme.AuthPalette
import dev.feature.auth.presentation.theme.authPalette
import org.koin.compose.viewmodel.koinViewModel

private data class CourseOption(val value: String, val label: String)

private val courseOptions = listOf(
    CourseOption("1", "1-kurs"),
    CourseOption("2", "2-kurs"),
    CourseOption("3", "3-kurs"),
    CourseOption("4", "4-kurs"),
    CourseOption("MASTER", "Magistr"),
)

/**
 * Profilni tahrirlash ekrani (A2/C2). Local keshdagi profilni prefill qiladi,
 * o'zgarishlarni [ProfileViewModel.saveProfile] orqali Firestore + local keshga yozadi.
 */
@Composable
fun EditProfileScreen(onBack: () -> Unit, vm: ProfileViewModel = koinViewModel()) {
    val palette = authPalette
    val state by vm.state.collectAsStateWithLifecycle()
    val profile = state.profile

    var firstName by remember(profile) { mutableStateOf(profile?.firstName.orEmpty()) }
    var lastName by remember(profile) { mutableStateOf(profile?.lastName.orEmpty()) }
    var phone by remember(profile) { mutableStateOf(profile?.phoneNumber.orEmpty()) }
    var universityId by remember(profile) { mutableStateOf(profile?.universityId) }
    var courseYear by remember(profile) { mutableStateOf(profile?.courseYear) }

    var uniExpanded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 54.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            IconBoxLocal(AuthIcons.ArrowLeft, palette, onBack)
            Text("Profilni tahrirlash", style = TextStyle(fontFamily = AuthFontFamily, fontSize = 20.sp, fontWeight = FontWeight.Black, color = palette.ink))
        }
        Spacer(Modifier.height(18.dp))

        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FieldLabel("Ism", palette)
            GlassTextField(firstName, { firstName = it }, "Ism", leading = AuthIcons.Pencil)

            FieldLabel("Familiya", palette)
            GlassTextField(lastName, { lastName = it }, "Familiya", leading = AuthIcons.Pencil)

            FieldLabel("Telefon", palette)
            GlassTextField(
                phone, { phone = it }, "+998 90 123 45 67",
                leading = AuthIcons.Phone,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )

            // Universitet tanlash
            FieldLabel("Universitet", palette)
            val selectedUni = state.universities.firstOrNull { it.id == universityId }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(palette.fieldBg)
                    .border(1.dp, palette.border, RoundedCornerShape(14.dp))
                    .clickable { uniExpanded = !uniExpanded }.padding(horizontal = 12.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AuthIcons.GraduationCap, null, tint = palette.inkFaint, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(9.dp))
                Text(
                    selectedUni?.name ?: "Universitetni tanlang",
                    style = TextStyle(fontFamily = AuthFontFamily, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (selectedUni != null) palette.ink else palette.inkFaint),
                    modifier = Modifier.weight(1f),
                )
                Icon(AuthIcons.ChevronDown, null, tint = palette.inkFaint, modifier = Modifier.size(18.dp))
            }
            if (uniExpanded) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(palette.glass).border(1.dp, palette.border, RoundedCornerShape(14.dp)),
                ) {
                    state.universities.forEach { uni ->
                        UniversityRow(uni, selected = uni.id == universityId, palette) {
                            universityId = uni.id
                            uniExpanded = false
                        }
                    }
                }
            }

            // Kurs tanlash
            FieldLabel("Kurs", palette)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                courseOptions.forEach { opt ->
                    val active = opt.value == courseYear
                    Box(
                        Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (active) palette.primary.copy(alpha = 0.14f) else palette.glass)
                            .border(1.dp, if (active) palette.primary else palette.border, RoundedCornerShape(12.dp))
                            .clickable { courseYear = opt.value },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(opt.label, style = TextStyle(fontFamily = AuthFontFamily, fontSize = 11.5f.sp, fontWeight = FontWeight.Bold, color = if (active) palette.primary else palette.inkMuted))
                    }
                }
            }

            if (error != null) {
                Text(error!!, style = TextStyle(fontFamily = AuthFontFamily, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFDC2626)))
            }

            Spacer(Modifier.height(4.dp))
            PrimaryButton(
                text = if (saving) "Saqlanmoqda..." else "Saqlash",
                enabled = !saving,
                onClick = {
                    error = null
                    saving = true
                    val updated = (profile ?: UserProfile()).copy(
                        firstName = firstName.trim().ifBlank { null },
                        lastName = lastName.trim().ifBlank { null },
                        phoneNumber = phone.trim().ifBlank { null },
                        universityId = universityId,
                        courseYear = courseYear,
                    )
                    vm.saveProfile(updated) { err ->
                        saving = false
                        if (err == null) onBack() else error = err
                    }
                },
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun FieldLabel(text: String, palette: AuthPalette) {
    Text(text, style = TextStyle(fontFamily = AuthFontFamily, fontSize = 12.5f.sp, fontWeight = FontWeight.Bold, color = palette.inkMuted))
}

@Composable
private fun UniversityRow(uni: University, selected: Boolean, palette: AuthPalette, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(uni.monogram, style = TextStyle(fontFamily = AuthFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Black, color = palette.primary), modifier = Modifier.width(48.dp))
        Column(Modifier.weight(1f)) {
            Text(uni.name, style = TextStyle(fontFamily = AuthFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.ink))
            Text(uni.city, style = TextStyle(fontFamily = AuthFontFamily, fontSize = 11.sp, color = palette.inkFaint))
        }
        if (selected) Icon(AuthIcons.ShieldCheck, null, tint = palette.primary, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun IconBoxLocal(icon: androidx.compose.ui.graphics.vector.ImageVector, palette: AuthPalette, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(palette.glass).border(1.dp, palette.border, RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, "Orqaga", tint = palette.ink, modifier = Modifier.size(18.dp)) }
}
