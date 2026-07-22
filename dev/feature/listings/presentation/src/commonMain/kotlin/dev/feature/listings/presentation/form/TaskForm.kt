package dev.feature.listings.presentation.form

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.core.uikit.components.GlassTextField
import dev.core.uikit.media.rememberImagePicker
import dev.core.uikit.theme.AppPalette
import dev.feature.listings.domain.model.ListingField
import dev.feature.listings.domain.model.PriceUnit
import dev.feature.listings.domain.model.TaskCatalog
import dev.feature.listings.domain.model.TaskCategory
import dev.feature.listings.domain.model.TaskFormat
import dev.feature.listings.presentation.BranchesSection
import dev.feature.listings.presentation.PostListingUiState
import dev.feature.listings.presentation.PostListingViewModel
import dev.feature.listings.presentation.components.ChipFlow
import dev.feature.listings.presentation.components.FieldHint
import dev.feature.listings.presentation.components.FormSection
import dev.feature.listings.presentation.components.MiniLabel
import dev.feature.listings.presentation.components.SelectChip
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** Muddat chipi: ko'rinadigan yozuv va sana. */
private class DeadlineDay(val label: String, val date: LocalDate)

private val UZ_MONTHS = listOf(
    "yanvar", "fevral", "mart", "aprel", "may", "iyun",
    "iyul", "avgust", "sentabr", "oktabr", "noyabr", "dekabr",
)

/** Muddat uchun sana chiplari — bugundan boshlab ikki hafta. */
private const val DAY_CHIP_COUNT = 14

/** Odatiy topshirish vaqtlari. Sukut — 23:59 ("shu kun ichida"). */
private val TIME_OPTIONS = listOf("09:00", "12:00", "18:00", "23:59")
private const val DEFAULT_TIME = "23:59"

/**
 * "Fanlardan yordam" — bir martalik topshiriq formasi.
 *
 * Boshqa turlardan farqi: bu **so'rov**, taklif emas. Shu sabab uchta narsa markazda —
 * qanday ish, uning **sharti** va **muddati**. Rasm ixtiyoriy (masala surati foydali,
 * lekin majburiy qilinsa e'lon qo'yishga to'siq bo'ladi), manzil esa faqat yuzma-yuz
 * topshiriladigan ishda so'raladi.
 */
@Composable
fun TaskForm(state: PostListingUiState, palette: AppPalette, vm: PostListingViewModel) {
    val task = state.task
    val category = task.category

    val imagePicker = rememberImagePicker { picked ->
        if (picked != null) vm.addImage(picked.bytes, picked.fileName)
    }

    ListingFormShell(
        title = category?.label ?: "Fanlardan yordam",
        subtitle = "Qanday ish kerakligini va muddatini aniq yozing",
        state = state,
        palette = palette,
        vm = vm,
    ) {
        // 1) Yo'nalish — talaba o'ylaydigan tabiiy bo'linish.
        FormSection(
            title = "Ish yo'nalishi",
            subtitle = "Qaysi turdagi yordam kerak",
            error = state.errorFor(ListingField.TASK_SUBJECT),
        ) {
            ChipFlow {
                TaskCategory.entries.forEach { item ->
                    SelectChip(
                        "${item.emoji} ${item.label}",
                        selected = category == item,
                        onClick = { vm.updateTask { t -> t.copy(category = item) } },
                    )
                }
            }
        }

        // Qolgani yo'nalish tanlangandan keyin — turlar aynan undan kelib chiqadi.
        if (category != null) {
            FormSection(
                title = "Ish turi",
                subtitle = "Aniq nima kerakligini tanlang",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    ChipFlow {
                        task.types().forEach { type ->
                            SelectChip(
                                type.label,
                                selected = task.typeKey == type.key,
                                onClick = { vm.updateTask { t -> t.copy(typeKey = type.key) } },
                            )
                        }
                    }
                    if (task.needsCustomType) {
                        GlassTextField(
                            task.customTypeName,
                            { v -> vm.updateTask { t -> t.copy(customTypeName = v) } },
                            "Ish turini yozing",
                            height = 48,
                        )
                    }
                }
            }

            // 2) Shart — topshiriqning o'zagi. Validator uni majburiy qiladi.
            AboutSection(
                state = state,
                vm = vm,
                sectionTitle = "Topshiriq sharti",
                titleHint = "Matematikadan 12 ta masala",
                descriptionHint = "Masala shartini, talablarni va formatni yozing. " +
                    "Masalan: \"Ehtimollar nazariyasi, 3-mavzu, qo'lda yechilsin\"",
                subtitle = "Bajaruvchi shu matnga qarab baho beradi — qanchalik aniq bo'lsa, shuncha yaxshi",
            )

            FormSection(title = "Ish hajmi", subtitle = "Ixtiyoriy, lekin narxni aniqlashda yordam beradi") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlassTextField(
                        task.volume,
                        { v -> vm.updateTask { t -> t.copy(volume = v) } },
                        TaskCatalog.volumeHint(category, task.typeKey),
                        height = 48,
                    )
                    FieldHint("Masalan: ${TaskCatalog.volumeHint(category, task.typeKey)}", palette)
                }
            }

            // 3) Muddat — MAJBURIY. Muddatsiz topshiriq bajaruvchi uchun ma'nosiz.
            DeadlineSection(state, palette, vm)

            FormSection(title = "Qanday topshiriladi") {
                ChipFlow {
                    TaskFormat.entries.forEach { item ->
                        SelectChip(
                            item.label,
                            selected = task.format == item,
                            onClick = { vm.updateTask { t -> t.copy(format = item) } },
                        )
                    }
                }
            }

            // Manzil faqat yuzma-yuz ishда — onlayn topshiriqning joyi yo'q.
            if (task.needsLocation) {
                BranchesSection(state, palette, vm)
            }

            ImagesSection(
                state, vm, imagePicker::pick,
                sectionTitle = "Masala surati",
                hint = "Topshiriq varag'i yoki ekran surati — ko'p narsani tushuntiradi",
                optional = true,
            )

            PriceSection(
                state = state,
                vm = vm,
                sectionTitle = "Qancha to'laysiz",
                priceLabel = "Narx",
                hint = "50000",
                subtitle = "Butun ish uchun. Aniq bilmasangiz \"kelishilgan\" ni belgilang",
                priceUnits = listOf(PriceUnit.PER_ITEM),
            )

            ContactSection(state, vm, "Bajaruvchi shu raqamga bog'lanadi")
        }
    }
}

/**
 * Topshirish muddati — sana + vaqt.
 *
 * Ikkita alohida qator: sana chiplari (bugundan 2 hafta) va vaqt chiplari. Kalendar
 * dialogi emas, chunki topshiriq odatda yaqin kunlarga beriladi va bitta bosish
 * dialog ochib-yopishdan tezroq.
 */
@Composable
private fun DeadlineSection(state: PostListingUiState, palette: AppPalette, vm: PostListingViewModel) {
    val task = state.task
    val zone = remember { TimeZone.currentSystemDefault() }

    // Sanalar ekran qayta chizilganda sakramasin — ro'yxat "bugun" ga bog'langan.
    val days = remember {
        val today = Clock.System.now().toLocalDateTime(zone).date
        List(DAY_CHIP_COUNT) { offset ->
            val date = today.plus(offset, DateTimeUnit.DAY)
            DeadlineDay(label = dayChipLabel(offset, date), date = date)
        }
    }

    // Tanlangan muddatdan sana va vaqtni ajratib olamiz (chiplarni belgilash uchun).
    val selected = task.deadline?.let { millis ->
        kotlinx.datetime.Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone)
    }
    val selectedDate = selected?.date
    val selectedTime = selected?.let {
        "${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}"
    } ?: DEFAULT_TIME

    fun apply(date: LocalDate, time: String) {
        val parts = time.split(":")
        val at = LocalDateTime(date, LocalTime(parts[0].toInt(), parts[1].toInt()))
        vm.updateTask { it.copy(deadline = at.toInstant(zone).toEpochMilliseconds()) }
    }

    FormSection(
        title = "Qachongacha kerak",
        subtitle = "Muddatsiz topshiriqni hech kim olmaydi",
        error = state.errorFor(ListingField.TASK_DEADLINE),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
            MiniLabel("Sana", palette)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                days.forEach { day ->
                    SelectChip(
                        text = day.label,
                        selected = selectedDate == day.date,
                        onClick = { apply(day.date, selectedTime) },
                        palette = palette,
                    )
                }
            }

            MiniLabel("Soat", palette)
            ChipFlow {
                TIME_OPTIONS.forEach { time ->
                    SelectChip(
                        text = time,
                        selected = selectedDate != null && selectedTime == time,
                        // Sana tanlanmagan bo'lsa — bugunga qo'yamiz.
                        onClick = { apply(selectedDate ?: days.first().date, time) },
                        palette = palette,
                    )
                }
            }
        }
    }
}

private fun dayChipLabel(offset: Int, date: LocalDate): String = when (offset) {
    0 -> "Bugun"
    1 -> "Ertaga"
    else -> "${date.dayOfMonth}-${UZ_MONTHS[date.monthNumber - 1]}"
}
