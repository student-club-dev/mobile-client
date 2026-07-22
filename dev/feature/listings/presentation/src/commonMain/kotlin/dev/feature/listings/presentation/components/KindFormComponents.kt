package dev.feature.listings.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.core.uikit.components.AppFontFamily
import dev.core.uikit.components.AppIcons
import dev.core.uikit.components.GlassTextField
import dev.core.uikit.theme.AppPalette
import dev.core.uikit.theme.appPalette
import dev.feature.listings.domain.model.AttributeKind
import dev.feature.listings.domain.model.AttributeSpec
import dev.feature.listings.domain.model.ListingCategory
import dev.feature.listings.domain.model.WeekDay

/**
 * Ijara, xizmat va ish formalarining umumiy g'ishtlari.
 *
 * Chegirma formasidagi bloklar (`form/DiscountForm.kt`) bitta turga bog'langan; bu
 * yerdagilar esa turdan mustaqil — sanoq, ko'p tanlov, vaqt oralig'i kabi har uch
 * formada takrorlanadigan naqshlar. Hamma turga umumiy bo'limlar esa
 * `form/CommonSections.kt` da.
 */

/** Maydon ustidagi kichik yorliq. */
@Composable
fun MiniLabel(text: String, palette: AppPalette = appPalette) {
    Text(
        text,
        style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.label),
    )
}

/** Maydon ostidagi izoh. */
@Composable
fun FieldHint(text: String, palette: AppPalette = appPalette) {
    Text(
        text,
        style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.sp, color = palette.inkFaint),
    )
}

/**
 * Son tanlash: tayyor chiplar + "Boshqa" tugmasi orqali erkin kiritish.
 *
 * Nega chip: "nechi xonalik", "nechi kishi kerak" kabi savollarda javob deyarli doim
 * 1–5 oralig'ida bo'ladi va bitta bosish klaviatura ochishdan tezroq. Chegaradan tashqari
 * qiymat kerak bo'lganda "Boshqa" maydonni ochadi — imkoniyat yopilmaydi.
 */
@Composable
fun NumberChipsField(
    label: String,
    value: String,
    options: List<Int>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    hint: String? = null,
    /** Oxirgi chip "5+" ko'rinishida bo'lsinmi. */
    lastIsPlus: Boolean = false,
    palette: AppPalette = appPalette,
) {
    // Qiymat chiplar orasida yo'q bo'lsa — u qo'lda kiritilgan, maydon ochiq turishi kerak.
    val isCustom = value.isNotBlank() && value.toIntOrNull() !in options
    var manual by remember(value) { mutableStateOf(isCustom) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MiniLabel(label, palette)

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            options.forEachIndexed { index, option ->
                val text = if (lastIsPlus && index == options.lastIndex) "$option+" else "$option"
                SelectChip(
                    text = suffix?.let { "$text $it" } ?: text,
                    selected = !manual && value == option.toString(),
                    onClick = {
                        manual = false
                        onSelect(option.toString())
                    },
                )
            }
            SelectChip(text = "Boshqa", selected = manual, onClick = { manual = true })
        }

        if (manual) {
            GlassTextField(
                value,
                { onSelect(it.filter { c -> c.isDigit() }) },
                hint ?: "Sonni kiriting",
                height = 46,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
    }
}

/** Bir nechtasini tanlash mumkin bo'lgan chiplar (qulayliklar, talablar). */
@Composable
fun MultiSelectChips(
    options: List<ListingCategory>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    ChipFlow {
        options.forEach { option ->
            SelectChip(
                text = option.label,
                selected = option.key in selected,
                onClick = { onToggle(option.key) },
            )
        }
    }
}

/** Hafta kunlarini tanlash — doimiy ish grafigi uchun. */
@Composable
fun WeekDayPicker(
    selected: Set<WeekDay>,
    onToggle: (WeekDay) -> Unit,
    onSelectAll: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            WeekDay.entries.forEach { day ->
                SelectChip(day.shortLabel, day in selected, { onToggle(day) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SelectChip("Har kuni", selected.size == WeekDay.entries.size, onSelectAll)
        }
    }
}

/**
 * Ish vaqti oralig'i — ikkita soat ro'yxati. Erkin matn emas, chunki "9 dan 5 gacha",
 * "09:00-17:00", "ertalab 9" kabi turli yozuvlar keyinchalik filtrlab bo'lmaydigan
 * ma'lumotga aylanadi.
 */
@Composable
fun TimeRangeField(
    startTime: String,
    endTime: String,
    options: List<String>,
    onStart: (String) -> Unit,
    onEnd: (String) -> Unit,
    palette: AppPalette = appPalette,
) {
    val choices = remember(options) { options.map { it to it } }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MiniLabel("Boshlanishi", palette)
            FormDropdown(
                value = startTime.ifBlank { null },
                placeholder = "09:00",
                options = choices,
                onSelect = onStart,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MiniLabel("Tugashi", palette)
            FormDropdown(
                value = endTime.ifBlank { null },
                placeholder = "18:00",
                options = choices,
                onSelect = onEnd,
            )
        }
    }
}

/**
 * Ro'yxat muharriri — talablar, sharoitlar, qulayliklar uchun.
 *
 * Tayyor takliflar bosib qo'shiladi (eng ko'p uchraydiganlari), bundan tashqari
 * erkin matn ham kiritiladi. Qo'shilganlari o'chirish tugmasi bilan ko'rinadi.
 */
@Composable
fun TagListEditor(
    values: List<String>,
    suggestions: List<String>,
    onChange: (List<String>) -> Unit,
    hint: String,
    palette: AppPalette = appPalette,
) {
    var draft by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        if (values.isNotEmpty()) {
            ChipFlow {
                values.forEach { value ->
                    RemovableChip(value, onRemove = { onChange(values - value) }, palette = palette)
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                GlassTextField(draft, { draft = it }, hint, height = 46)
            }
            AddButton(
                enabled = draft.isNotBlank(),
                onClick = {
                    val trimmed = draft.trim()
                    // Takrorlanmasin — bir xil talab ikki marta ko'rinishi e'lonni tartibsiz qiladi.
                    if (trimmed.isNotBlank() && trimmed !in values) onChange(values + trimmed)
                    draft = ""
                },
                palette = palette,
            )
        }

        val unused = suggestions.filter { it !in values }
        if (unused.isNotEmpty()) {
            FieldHint("Tez tanlash:", palette)
            ChipFlow {
                unused.forEach { suggestion ->
                    SelectChip(suggestion, selected = false, onClick = { onChange(values + suggestion) })
                }
            }
        }
    }
}

/** Qo'shilgan element — bosilganda o'chadi. */
@Composable
private fun RemovableChip(text: String, onRemove: () -> Unit, palette: AppPalette) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.height(36.dp).clip(shape)
            .background(palette.primary.copy(alpha = 0.12f))
            .clickable(onClick = onRemove)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text,
            style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.5f.sp, fontWeight = FontWeight.Bold, color = palette.primary),
        )
        Icon(AppIcons.Close, "O'chirish", tint = palette.primary, modifier = Modifier.size(12.dp))
    }
}

@Composable
private fun AddButton(enabled: Boolean, onClick: () -> Unit, palette: AppPalette) {
    Box(
        Modifier.size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) palette.primary else palette.fieldBg)
            .border(1.dp, palette.border, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            AppIcons.Plus,
            "Qo'shish",
            tint = if (enabled) palette.onPrimary else palette.inkFaint,
            modifier = Modifier.size(17.dp),
        )
    }
}

/**
 * Spetsifikatsiyadan forma quruvchi blok — [AttributeSpec] ro'yxatini maydonlarga aylantiradi.
 *
 * Xizmat sohalari aynan shu tarzda ishlaydi: repetitor uchun "Fan", "Daraja", "Dars
 * davomiyligi"; chop etish uchun "Format", "Rang", "Zichlik". Har bir soha uchun alohida
 * ekran yozilmaydi — spetsifikatsiya o'zgarsa forma o'zi o'zgaradi.
 */
@Composable
fun DynamicFields(
    specs: List<AttributeSpec>,
    values: Map<String, String>,
    onChange: (key: String, value: String) -> Unit,
    palette: AppPalette = appPalette,
) {
    specs.forEach { spec ->
        val value = values[spec.key].orEmpty()

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                MiniLabel(spec.label + if (spec.suffix != null) " (${spec.suffix})" else "", palette)
                if (spec.required) {
                    Text(
                        "*",
                        style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Black, color = ErrorColor),
                    )
                }
            }

            when (spec.kind) {
                AttributeKind.TEXT, AttributeKind.TAGS ->
                    GlassTextField(
                        value,
                        { onChange(spec.key, it) },
                        spec.hint.ifBlank { spec.label },
                        height = 46,
                    )

                AttributeKind.NUMBER ->
                    GlassTextField(
                        value,
                        { onChange(spec.key, it.filter { c -> c.isDigit() }) },
                        spec.hint.ifBlank { "0" },
                        height = 46,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )

                AttributeKind.SELECT ->
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        spec.options.forEach { option ->
                            SelectChip(option, value == option, { onChange(spec.key, option) })
                        }
                    }

                AttributeKind.BOOLEAN ->
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        SelectChip("Ha", value == "true", { onChange(spec.key, "true") })
                        SelectChip("Yo'q", value == "false", { onChange(spec.key, "false") })
                    }
            }

            if (spec.kind == AttributeKind.TAGS) {
                FieldHint("Vergul bilan ajrating", palette)
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}
