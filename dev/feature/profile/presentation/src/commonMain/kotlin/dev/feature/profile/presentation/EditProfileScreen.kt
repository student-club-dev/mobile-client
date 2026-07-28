package dev.feature.profile.presentation

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.core.uikit.components.AppIcons
import dev.core.uikit.components.GlassTextField
import dev.core.uikit.components.ScCircleButton
import dev.core.uikit.components.ScGradientButton
import dev.core.uikit.components.ScHeader
import dev.core.uikit.components.ScHeaderTitle
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScText
import dev.core.uikit.components.scCard
import dev.core.uikit.media.rememberImagePicker
import dev.core.uikit.media.toImageBitmapOrNull
import dev.core.uikit.theme.Sc
import dev.feature.profile.domain.model.UserProfile
import dev.feature.profile.presentation.components.ProfileAvatar
import dev.feature.university.domain.model.University
import org.koin.compose.viewmodel.koinViewModel

/** Tanlov tugmasining qiymati (backendga ketadigan) va ekrandagi yorlig'i. */
private data class ChoiceOption(val value: String, val label: String)

private val courseOptions = listOf(
    ChoiceOption("1", "1-kurs"),
    ChoiceOption("2", "2-kurs"),
    ChoiceOption("3", "3-kurs"),
    ChoiceOption("4", "4-kurs"),
    ChoiceOption("MASTER", "Magistr"),
)

private val genderOptions = listOf(
    ChoiceOption("MALE", "Erkak"),
    ChoiceOption("FEMALE", "Ayol"),
)

/**
 * Profilni tahrirlash ekrani (A2/C2). Local keshdagi profilni prefill qiladi,
 * o'zgarishlarni [ProfileViewModel.saveProfile] orqali Firestore + local keshga yozadi.
 */
@Composable
fun EditProfileScreen(onBack: () -> Unit, vm: ProfileViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val profile = state.profile

    var firstName by remember(profile) { mutableStateOf(profile?.firstName.orEmpty()) }
    var lastName by remember(profile) { mutableStateOf(profile?.lastName.orEmpty()) }
    var phone by remember(profile) { mutableStateOf(profile?.phoneNumber.orEmpty()) }
    var universityId by remember(profile) { mutableStateOf(profile?.universityId) }
    var courseYear by remember(profile) { mutableStateOf(profile?.courseYear) }
    var gender by remember(profile) { mutableStateOf(profile?.gender) }

    var uniExpanded by remember { mutableStateOf(false) }
    var uniQuery by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Avatar: tanlangan rasm darrov ko'rinadi, ayni paytda fon rejimida serverga yuklanadi.
    var avatarPreview by remember { mutableStateOf<ImageBitmap?>(null) }
    var avatarUploading by remember { mutableStateOf(false) }
    var avatarError by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberImagePicker { picked ->
        if (picked == null) return@rememberImagePicker // bekor qilindi
        avatarError = null
        avatarPreview = picked.bytes.toImageBitmapOrNull()
        avatarUploading = true
        vm.uploadAvatar(picked.bytes, picked.fileName) { err ->
            avatarUploading = false
            if (err != null) {
                avatarError = err
                avatarPreview = null // yuklanmadi — eski rasmga qaytamiz
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Sc.Bg).verticalScroll(rememberScrollState())) {
        ScHeader(horizontalPadding = 18.dp) {
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                ScCircleButton(ScIcons.ChevronLeft, onBack, contentDescription = "Orqaga")
                ScHeaderTitle("Profilni tahrirlash", size = 21f, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(22.dp))

        // Avatar — bosilganda galereya ochiladi
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                ProfileAvatar(
                    name = state.name,
                    size = 96.dp,
                    fontSize = 36.sp,
                    avatarUrl = profile?.avatarUrl,
                    localPreview = avatarPreview,
                    modifier = Modifier.clickable(enabled = !avatarUploading) { imagePicker.pick() },
                )
                // Kamera nishoni
                Box(
                    Modifier.size(31.dp).clip(CircleShape).background(Sc.Brand)
                        .border(2.dp, Sc.Bg, CircleShape)
                        .clickable(enabled = !avatarUploading) { imagePicker.pick() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Camera, "Rasmni o'zgartirish", tint = Color.White, modifier = Modifier.size(15.dp))
                }
            }

            when {
                avatarUploading -> ScText("Rasm yuklanmoqda...", 12.5f, FontWeight.SemiBold, Sc.Muted)
                avatarError != null -> ScText(avatarError!!, 12.5f, FontWeight.SemiBold, Sc.Danger)
                else -> ScText(
                    "Rasmni o'zgartirish", 12.5f, FontWeight.Bold, Sc.Brand,
                    Modifier.clickable { imagePicker.pick() },
                )
            }
        }
        Spacer(Modifier.height(22.dp))

        Column(
            Modifier.fillMaxWidth().padding(horizontal = Sc.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            FieldLabel("Ism")
            GlassTextField(firstName, { firstName = it }, "Ism", leading = AppIcons.Pencil)

            FieldLabel("Familiya")
            GlassTextField(lastName, { lastName = it }, "Familiya", leading = AppIcons.Pencil)

            FieldLabel("Telefon")
            GlassTextField(
                phone, { phone = it }, "+998 90 123 45 67",
                leading = AppIcons.Phone,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )

            // Universitet tanlash
            FieldLabel("Universitet")
            val selectedUni = state.universities.firstOrNull { it.id == universityId }
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Sc.FieldBg)
                    .border(1.dp, Sc.Border, RoundedCornerShape(16.dp))
                    .clickable { uniExpanded = !uniExpanded }
                    .padding(horizontal = 13.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(ScIcons.Cap, null, tint = Sc.Muted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(9.dp))
                ScText(
                    selectedUni?.name ?: "Universitetni tanlang",
                    14f, FontWeight.SemiBold,
                    if (selectedUni != null) Sc.Ink else Sc.Muted,
                    Modifier.weight(1f),
                    maxLines = 1,
                )
                Icon(ScIcons.ChevronUpDown, null, tint = Sc.Muted, modifier = Modifier.size(17.dp))
            }
            if (uniExpanded) {
                Column(Modifier.fillMaxWidth().scCard(radius = 20.dp).padding(9.dp)) {
                    GlassTextField(uniQuery, { uniQuery = it }, "Universitet qidiring", leading = AppIcons.Search, height = 44)
                    Spacer(Modifier.height(8.dp))
                    // Ro'yxat katta (prof-emis, ~10000) — qidiruv bo'yicha cheklab ko'rsatamiz.
                    val filtered = remember(state.universities, uniQuery) {
                        (
                            if (uniQuery.isBlank()) {
                                state.universities
                            } else {
                                state.universities.filter {
                                    it.name.contains(uniQuery, ignoreCase = true) ||
                                        it.city.contains(uniQuery, ignoreCase = true)
                                }
                            }
                            ).take(40)
                    }
                    filtered.forEach { uni ->
                        UniversityRow(uni, selected = uni.id == universityId) {
                            universityId = uni.id
                            uniExpanded = false
                            uniQuery = ""
                        }
                    }
                    if (filtered.isEmpty()) {
                        ScText("Topilmadi", 12.5f, FontWeight.Medium, Sc.Muted, Modifier.padding(8.dp))
                    }
                }
            }

            // Kurs tanlash
            FieldLabel("Kurs")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                courseOptions.forEach { opt ->
                    ChoiceBox(opt.label, opt.value == courseYear, Modifier.weight(1f)) {
                        courseYear = opt.value
                    }
                }
            }

            // Jins — talabalar qidiruvidagi filtr shu maydonga tayanadi. Ixtiyoriy:
            // tanlangan tugmani qayta bosish tanlovni bekor qiladi.
            FieldLabel("Jins (ixtiyoriy)")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                genderOptions.forEach { opt ->
                    ChoiceBox(opt.label, opt.value == gender, Modifier.weight(1f)) {
                        gender = if (gender == opt.value) null else opt.value
                    }
                }
            }

            if (error != null) {
                ScText(error!!, 12.5f, FontWeight.SemiBold, Sc.Danger)
            }

            Spacer(Modifier.height(4.dp))
            ScGradientButton(
                text = if (saving) "Saqlanmoqda..." else "Saqlash",
                onClick = {
                    if (saving) return@ScGradientButton
                    error = null
                    saving = true
                    val updated = (profile ?: UserProfile()).copy(
                        firstName = firstName.trim().ifBlank { null },
                        lastName = lastName.trim().ifBlank { null },
                        phoneNumber = phone.trim().ifBlank { null },
                        universityId = universityId,
                        courseYear = courseYear,
                        gender = gender,
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
private fun FieldLabel(text: String) {
    ScText(text, 12.5f, FontWeight.Bold, Sc.InkSoft, maxLines = 1)
}

/** Kurs / jins qatoridagi bir xil ko'rinishdagi tanlov tugmasi. */
@Composable
private fun ChoiceBox(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) Sc.TintBlue else Sc.Card)
            .border(1.dp, if (active) Sc.Brand else Sc.Border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ScText(label, 12f, FontWeight.Bold, if (active) Sc.Brand else Sc.InkSoft, maxLines = 1)
    }
}

@Composable
private fun UniversityRow(uni: University, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScText(uni.monogram, 12.5f, FontWeight.ExtraBold, Sc.Brand, Modifier.width(50.dp), maxLines = 1)
        Column(Modifier.weight(1f)) {
            ScText(uni.name, 13.5f, FontWeight.Bold, Sc.Ink, maxLines = 2)
            ScText(uni.city, 11.5f, FontWeight.Medium, Sc.Muted, maxLines = 1)
        }
        if (selected) {
            Icon(AppIcons.Check, null, tint = Sc.Brand, modifier = Modifier.size(17.dp))
        }
    }
}
