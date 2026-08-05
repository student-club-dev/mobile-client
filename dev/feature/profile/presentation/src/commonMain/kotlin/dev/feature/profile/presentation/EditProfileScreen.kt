package dev.feature.profile.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.core.common.format.UZ_PHONE_CODE
import dev.core.common.format.isUzPhoneComplete
import dev.core.common.format.toUzPhoneDigits
import dev.core.common.format.toUzPhoneE164
import dev.core.uikit.components.AppIcons
import dev.core.uikit.components.GlassTextField
import dev.core.uikit.components.PhonePrefix
import dev.core.uikit.components.PhoneVisualTransformation
import dev.core.uikit.components.ScCircleButton
import dev.core.uikit.components.ScGradientButton
import dev.core.uikit.components.ScHeader
import dev.core.uikit.components.ScHeaderTitle
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScMonogramTile
import dev.core.uikit.components.ScText
import dev.core.uikit.components.ScUploadRing
import dev.core.uikit.components.scUploadPercent
import dev.core.uikit.components.scCard
import dev.core.uikit.media.rememberImagePicker
import dev.core.uikit.media.toImageBitmapOrNull
import coil3.compose.AsyncImage
import dev.core.uikit.theme.Sc
import dev.feature.profile.domain.model.ProfilePhoto
import dev.feature.profile.domain.model.UserProfile
import dev.feature.profile.domain.model.bioRejectionReason
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditProfileScreen(onBack: () -> Unit, vm: ProfileViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val profile = state.profile

    var firstName by remember(profile) { mutableStateOf(profile?.firstName.orEmpty()) }
    var lastName by remember(profile) { mutableStateOf(profile?.lastName.orEmpty()) }
    // Maydonda faqat 9 xonali milliy raqam turadi — "+998" prefiksi maydondan tashqarida.
    var phone by remember(profile) { mutableStateOf(profile?.phoneNumber.orEmpty().toUzPhoneDigits()) }
    var universityId by remember(profile) { mutableStateOf(profile?.universityId) }
    var courseYear by remember(profile) { mutableStateOf(profile?.courseYear) }
    var gender by remember(profile) { mutableStateOf(profile?.gender) }
    var bio by remember(profile) { mutableStateOf(profile?.bio.orEmpty()) }

    var uniExpanded by remember { mutableStateOf(false) }
    var uniQuery by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Avatar: tanlangan rasm darrov ko'rinadi, ayni paytda fon rejimida serverga yuklanadi.
    var avatarPreview by remember { mutableStateOf<ImageBitmap?>(null) }

    val photos by vm.photos.collectAsStateWithLifecycle()

    // Ekran ochilganda ro'yxatni bir marta o'qiymiz — rasmlar local keshda saqlanmaydi.
    LaunchedEffect(Unit) { vm.loadPhotos() }

    /**
     * Rasm tanlangan — u **to'plamga** qo'shiladi va serverda **birinchi o'ringa** tushadi,
     * ya'ni shu bilan avatar ham almashadi (`handoff/08-PROFILE.md` §2). Ilgari bu yerda
     * alohida "avatar yuklash" chaqiruvi bor edi; endi u kerak emas — `avatarUrl` hosila
     * maydon va uni server o'zi yangilaydi.
     */
    val imagePicker = rememberImagePicker { picked ->
        if (picked == null) return@rememberImagePicker // bekor qilindi
        avatarPreview = picked.bytes.toImageBitmapOrNull()
        vm.addPhoto(picked.bytes, picked.fileName)
    }

    // Klaviatura ochilganda ustun uning ustiga ko'tariladi — pastdagi maydonlar va
    // "Saqlash" tugmasi klaviatura ostida qolib ketmasin.
    Column(Modifier.fillMaxSize().background(Sc.Bg).imePadding().verticalScroll(rememberScrollState())) {
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
                Box(contentAlignment = Alignment.Center) {
                    ProfileAvatar(
                        name = state.name,
                        size = 96.dp,
                        fontSize = 36.sp,
                        avatarUrl = profile?.avatarUrl,
                        localPreview = avatarPreview,
                        modifier = Modifier.clickable(enabled = photos.canAdd) { imagePicker.pick() },
                    )
                    // Tanlangan rasm ([avatarPreview]) darhol ko'rinadi, foiz esa uning
                    // ustida aylanadi — Telegramdagidek.
                    if (photos.uploading) {
                        ScUploadRing(photos.progress, size = 96.dp, stroke = 3.dp)
                    }
                }
                // Kamera nishoni
                Box(
                    Modifier.size(31.dp).clip(CircleShape).background(Sc.Brand)
                        .border(2.dp, Sc.Bg, CircleShape)
                        .clickable(enabled = photos.canAdd) { imagePicker.pick() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Camera, "Rasmni o'zgartirish", tint = Color.White, modifier = Modifier.size(15.dp))
                }
            }

            when {
                photos.uploading && photos.progress != null -> ScText(
                    "Rasm yuklanmoqda ${scUploadPercent(photos.progress!!)}",
                    12.5f, FontWeight.SemiBold, Sc.Muted,
                )
                // Fayl ketib bo'lgan — endi server rasmni saqlab, profilni yangilamoqda.
                photos.uploading -> ScText("Rasm saqlanmoqda...", 12.5f, FontWeight.SemiBold, Sc.Muted)
                photos.error != null -> ScText(photos.error!!, 12.5f, FontWeight.SemiBold, Sc.Danger)
                photos.canAdd -> ScText(
                    "Rasm qo'shish", 12.5f, FontWeight.Bold, Sc.Brand,
                    Modifier.clickable { imagePicker.pick() },
                )
                // Chegaraga yetildi — tugmani ko'rsatib turish faqat 422 ga olib borardi.
                else -> ScText(
                    "${ProfilePhoto.MAX_PHOTOS} tadan ko'p rasm bo'lmaydi",
                    12.5f, FontWeight.SemiBold, Sc.Muted,
                )
            }

            if (photos.items.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                ProfilePhotoStrip(
                    photos = photos.items,
                    onMakeMain = vm::setMainPhoto,
                    onDelete = vm::deletePhoto,
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
                phone, { phone = it.toUzPhoneDigits() }, "90 123 45 67",
                leadingContent = { PhonePrefix() },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                visualTransformation = PhoneVisualTransformation(),
            )
            // Chala raqam saqlanmaydi — shu yerda ogohlantiramiz.
            if (phone.isNotEmpty() && !phone.isUzPhoneComplete()) {
                ScText(
                    "Raqamni to'liq kiriting: $UZ_PHONE_CODE 90 123 45 67",
                    11.5f,
                    FontWeight.Medium,
                    Sc.Danger,
                )
            }

            // Tarjimayi hol — 140 belgi, havola/telefon TAQIQLANGAN.
            FieldLabel("Tarjimayi hol")
            GlassTextField(
                bio,
                { if (it.length <= UserProfile.MAX_BIO) bio = it },
                "5/5 · Dasturiy injiniring",
                leading = AppIcons.Pencil,
            )
            /**
             * Xato **yozayotganda** ko'rsatiladi, saqlashda emas: server baribir
             * `422 BIO_NOT_ALLOWED` beradi, lekin unga qadar foydalanuvchi butun formani
             * to'ldirib bo'lardi (`handoff/08-PROFILE.md` §5).
             */
            val bioError = remember(bio) { bioRejectionReason(bio) }
            ScText(
                bioError ?: "${bio.length}/${UserProfile.MAX_BIO}",
                11.5f,
                FontWeight.Medium,
                if (bioError != null) Sc.Danger else Sc.Muted,
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
                    selectedUni?.shortName ?: "Universitetni tanlang",
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
                                // Qisqartma bo'yicha ham: "TATU" → Toshkent axborot…
                                state.universities.filter { it.matches(uniQuery) }
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
                    val bioError = bioRejectionReason(bio)
                    if (bioError != null) {
                        saving = false
                        error = bioError
                        return@ScGradientButton
                    }
                    // Chala raqam bilan saqlab bo'lmaydi: raqam yo to'liq, yo umuman yo'q.
                    if (phone.isNotEmpty() && !phone.isUzPhoneComplete()) {
                        saving = false
                        error = "Telefon raqamini to'liq kiriting"
                        return@ScGradientButton
                    }
                    val updated = (profile ?: UserProfile()).copy(
                        firstName = firstName.trim().ifBlank { null },
                        lastName = lastName.trim().ifBlank { null },
                        // Saqlashda doim yagona ko'rinish: "+998901234567".
                        phoneNumber = phone.toUzPhoneE164(),
                        universityId = universityId,
                        courseYear = courseYear,
                        gender = gender,
                        // Bo'sh satr — serverda "tozalash" degani, `null` esa "tegilmasin".
                        bio = bio.trim(),
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

/**
 * Profil rasmlari tasmasi — **birinchisi avatar** (`handoff/08-PROFILE.md` §2).
 *
 * Bosish — asosiy qilish, uzoq bosish — o'chirish. Nega uzoq bosish: har katakda alohida
 * "×" tugmasi 64 dp li kvadratda barmoq uchun juda mayda bo'lardi va tasodifiy o'chirish
 * xavfi tug'ilardi.
 *
 * Birinchi rasmda "Asosiy" yorlig'i turadi; uni bosish hech narsa qilmaydi (u allaqachon
 * asosiy) — server ham shu holatda `PUT …/main` ni no-op deb qabul qiladi.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfilePhotoStrip(
    photos: List<ProfilePhoto>,
    onMakeMain: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    LazyRow(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Sc.ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(photos, key = { _, item -> item.id }) { index, photo ->
            val main = index == 0
            Box(
                Modifier.size(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Sc.Chip)
                    .combinedClickable(
                        onClick = { if (!main) onMakeMain(photo.id) },
                        onLongClick = { onDelete(photo.id) },
                    ),
            ) {
                AsyncImage(
                    model = photo.previewUrl,
                    contentDescription = if (main) "Asosiy rasm" else "Profil rasmi",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (main) {
                    Box(
                        Modifier.align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        ScText("Asosiy", 9.5f, FontWeight.Bold, Color.White, maxLines = 1)
                    }
                }
            }
        }
    }
    ScText(
        "Bosish — asosiy qilish, uzoq bosish — o'chirish",
        11.5f,
        FontWeight.Medium,
        Sc.MutedLight,
    )
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
        // Qat'iy 50.dp li matn o'rniga tile: qisqartma 6 belgigacha bo'lishi mumkin va
        // kengligi belgilanmagan matn qo'shni ustunni surib yuborardi.
        ScMonogramTile(uni.monogram, Sc.TintBlue, Sc.Brand, size = 42.dp, radius = 13.dp, fontSize = 13f)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            ScText(uni.shortName, 13.5f, FontWeight.Bold, Sc.Ink, lineHeight = 17f, maxLines = 2)
            if (uni.display.subtitle.isNotBlank()) {
                ScText(uni.display.subtitle, 11.5f, FontWeight.Medium, Sc.Muted, maxLines = 1)
            }
        }
        if (selected) {
            Icon(AppIcons.Check, null, tint = Sc.Brand, modifier = Modifier.size(17.dp))
        }
    }
}
