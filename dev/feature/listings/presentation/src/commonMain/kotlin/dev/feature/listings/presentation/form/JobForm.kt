package dev.feature.listings.presentation.form

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.core.designsystem.components.GlassTextField
import dev.core.designsystem.media.rememberImagePicker
import dev.core.designsystem.theme.AppPalette
import dev.feature.listings.domain.model.EmploymentType
import dev.feature.listings.domain.model.ExperienceLevel
import dev.feature.listings.domain.model.JobCatalog
import dev.feature.listings.domain.model.ListingField
import dev.feature.listings.domain.model.TenantGender
import dev.feature.listings.domain.model.WeekDay
import dev.feature.listings.presentation.BranchesSection
import dev.feature.listings.presentation.PostListingUiState
import dev.feature.listings.presentation.PostListingViewModel
import dev.feature.listings.presentation.components.ChipFlow
import dev.feature.listings.presentation.components.FieldHint
import dev.feature.listings.presentation.components.FormSection
import dev.feature.listings.presentation.components.MiniLabel
import dev.feature.listings.presentation.components.NumberChipsField
import dev.feature.listings.presentation.components.SelectChip
import dev.feature.listings.presentation.components.TagListEditor
import dev.feature.listings.presentation.components.TimeRangeField
import dev.feature.listings.presentation.components.WeekDayPicker
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** Sana yorlig'i uchun oy nomlari — `kotlinx-datetime` da lokalizatsiya yo'q. */
private val UZ_MONTHS = listOf(
    "yanvar", "fevral", "mart", "aprel", "may", "iyun",
    "iyul", "avgust", "sentabr", "oktabr", "noyabr", "dekabr",
)

/** Kunlik ish uchun ko'rsatiladigan sanalar soni — ikki haftadan narisiga odam yollanmaydi. */
private const val DATE_CHIP_COUNT = 14

/** Sana chipi: ko'rinadigan yozuv va e'longa yoziladigan qiymat. */
private class WorkDateOption(val label: String, val millis: Long)

/**
 * Ish e'loni formasi.
 *
 * Kunlik va doimiy ish bir xil savollarga javob bermaydi: kunlikda aniq sana va kun oxiridagi
 * to'lov, doimiyda esa hafta grafigi va oylik maosh so'raladi. Shu sabab forma [EmploymentType]
 * ga qarab bo'limlarni almashtiradi — barchasini bir vaqtda ko'rsatib, keraksizini xiralashtirish
 * e'lon beruvchini chalg'itadi.
 */
@Composable
fun JobForm(state: PostListingUiState, palette: AppPalette, vm: PostListingViewModel) {
    val job = state.job
    val imagePicker = rememberImagePicker { picked ->
        if (picked != null) vm.addImage(picked.bytes, picked.fileName)
    }

    val title = if (job.isDaily) "Kunlik ish e'loni" else "Doimiy ish e'loni"
    val subtitle = job.employment.hint

    ListingFormShell(title = title, subtitle = subtitle, state = state, palette = palette, vm = vm) {

        FormSection(
            title = "Ish turi",
            subtitle = "Shartlar shunga qarab o'zgaradi",
            palette = palette,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                EmploymentType.entries.forEach { type ->
                    SelectChip(
                        text = type.label,
                        selected = job.employment == type,
                        onClick = { vm.updateJob { it.copy(employment = type) } },
                        palette = palette,
                    )
                }
            }
            FieldHint(job.employment.hint, palette)
        }

        FormSection(
            title = "Qanday ish",
            subtitle = "Talaba shu bo'yicha qidiradi",
            error = state.errorFor(ListingField.JOB_CATEGORY),
            palette = palette,
        ) {
            ChipFlow {
                JobCatalog.CATEGORIES.forEach { category ->
                    SelectChip(
                        text = category.label,
                        selected = job.categoryKey == category.key,
                        onClick = { vm.updateJob { it.copy(categoryKey = category.key) } },
                        palette = palette,
                    )
                }
            }
            if (job.categoryKey == JobCatalog.OTHER_KEY) {
                GlassTextField(
                    job.customCategoryName,
                    { value -> vm.updateJob { it.copy(customCategoryName = value) } },
                    "Ish nomini yozing",
                    height = 48,
                )
            }
        }

        FormSection(
            title = "Ish beruvchi",
            subtitle = "Tashkilot yoki shaxs nomi",
            error = state.errorFor(ListingField.BUSINESS_NAME),
            palette = palette,
        ) {
            GlassTextField(
                job.companyName,
                { value -> vm.updateJob { it.copy(companyName = value) } },
                "Masalan: Korzinka yoki Aziz aka",
                height = 48,
            )
        }

        FormSection(
            title = "Qachon ishlanadi",
            error = state.errorFor(ListingField.JOB_SCHEDULE),
            palette = palette,
        ) {
            if (job.isDaily) {
                val zone = TimeZone.currentSystemDefault()
                // Sanalar ekran qayta chizilganda o'zgarmasin — ro'yxat "bugun" ga bog'langan.
                val dates = remember {
                    val today = Clock.System.now().toLocalDateTime(zone).date
                    List(DATE_CHIP_COUNT) { offset ->
                        val date = today.plus(offset, DateTimeUnit.DAY)
                        WorkDateOption(
                            label = dateChipLabel(offset, date),
                            millis = date.atStartOfDayIn(zone).toEpochMilliseconds(),
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    dates.forEach { option ->
                        SelectChip(
                            text = option.label,
                            selected = job.workDate == option.millis,
                            onClick = { vm.updateJob { it.copy(workDate = option.millis) } },
                            palette = palette,
                        )
                    }
                }
            } else {
                MiniLabel("Ish kunlari", palette)
                WeekDayPicker(
                    selected = job.days,
                    onToggle = { day ->
                        vm.updateJob { j ->
                            j.copy(days = if (day in j.days) j.days - day else j.days + day)
                        }
                    },
                    onSelectAll = { vm.updateJob { it.copy(days = WeekDay.entries.toSet()) } },
                )
            }
        }

        FormSection(
            title = "Ish smenasi",
            subtitle = job.shift?.hint ?: "Smenani tanlang",
            error = state.errorFor(ListingField.JOB_SHIFT),
            palette = palette,
        ) {
            ChipFlow {
                // Ish turiga mos smenalar: kunlik ishda "2/2" ma'nosiz.
                JobCatalog.shifts(job.employment).forEach { shift ->
                    SelectChip(
                        text = shift.label,
                        selected = job.shift == shift,
                        onClick = { vm.updateJob { it.copy(shift = shift) } },
                        palette = palette,
                    )
                }
            }
        }

        if (job.needsExactTime) {
            FormSection(title = "Ish vaqti", palette = palette) {
                TimeRangeField(
                    startTime = job.startTime,
                    endTime = job.endTime,
                    options = JobCatalog.TIME_OPTIONS,
                    onStart = { value -> vm.updateJob { it.copy(startTime = value) } },
                    onEnd = { value -> vm.updateJob { it.copy(endTime = value) } },
                    palette = palette,
                )
                NumberChipsField(
                    label = "Kuniga necha soat",
                    value = job.hoursPerDay,
                    options = JobCatalog.HOURS_PER_DAY,
                    onSelect = { value -> vm.updateJob { it.copy(hoursPerDay = value) } },
                    suffix = "soat",
                    palette = palette,
                )
            }
        }

        FormSection(
            title = "Nechta odam kerak",
            error = state.errorFor(ListingField.JOB_PAY),
            palette = palette,
        ) {
            NumberChipsField(
                label = "Kerakli xodimlar",
                value = job.vacancies,
                options = JobCatalog.VACANCY_COUNTS,
                onSelect = { value -> vm.updateJob { it.copy(vacancies = value) } },
                suffix = "kishi",
                lastIsPlus = true,
                palette = palette,
            )
        }

        FormSection(title = "To'lov", palette = palette) {
            MiniLabel("To'lov davri", palette)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                // Kunlik ishda oylik maosh taklif qilinmaydi va aksincha.
                JobCatalog.payPeriods(job.employment).forEach { period ->
                    SelectChip(
                        text = period.label,
                        selected = job.payPeriod == period,
                        onClick = { vm.updateJob { it.copy(payPeriod = period) } },
                        palette = palette,
                    )
                }
            }
        }

        PriceSection(
            state = state,
            vm = vm,
            sectionTitle = "Maosh",
            priceLabel = "Maosh",
            hint = "300 000",
            showRange = true,
            allowNegotiable = true,
            palette = palette,
        )

        FormSection(title = "To'lov shartlari", palette = palette) {
            MiniLabel("To'lov qachon beriladi", palette)
            GlassTextField(
                job.payoutNote,
                { value -> vm.updateJob { it.copy(payoutNote = value) } },
                "Ish kuni oxirida / har oy 5-sanasida",
                height = 48,
            )
        }

        FormSection(
            title = "Talablar",
            subtitle = "Nomzoddan nima talab qilinadi",
            error = state.errorFor(ListingField.ATTRIBUTES),
            palette = palette,
        ) {
            MiniLabel("Tajriba", palette)
            ChipFlow {
                ExperienceLevel.entries.forEach { level ->
                    SelectChip(
                        text = level.label,
                        selected = job.experience == level,
                        onClick = { vm.updateJob { it.copy(experience = level) } },
                        palette = palette,
                    )
                }
            }

            MiniLabel("Kimlar uchun", palette)
            ChipFlow {
                TenantGender.entries.forEach { gender ->
                    SelectChip(
                        text = "${gender.emoji} ${gender.label}",
                        selected = job.gender == gender,
                        // Jins talabi ixtiyoriy — tanlanganini qayta bosish uni bekor qiladi.
                        onClick = {
                            vm.updateJob { j -> j.copy(gender = if (j.gender == gender) null else gender) }
                        },
                        palette = palette,
                    )
                }
            }

            MiniLabel("Yosh", palette)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(Modifier.weight(1f)) {
                    GlassTextField(
                        job.ageFrom,
                        { value -> vm.updateJob { it.copy(ageFrom = value.filter { c -> c.isDigit() }) } },
                        "18 dan",
                        height = 48,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                Box(Modifier.weight(1f)) {
                    GlassTextField(
                        job.ageTo,
                        { value -> vm.updateJob { it.copy(ageTo = value.filter { c -> c.isDigit() }) } },
                        "30 gacha",
                        height = 48,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }

            TagListEditor(
                values = job.requirements,
                suggestions = JobCatalog.COMMON_REQUIREMENTS,
                onChange = { values -> vm.updateJob { j -> j.copy(requirements = values) } },
                hint = "Talab qo'shish",
                palette = palette,
            )
        }

        FormSection(title = "Sharoit va imtiyozlar", palette = palette) {
            TagListEditor(
                values = job.benefits,
                suggestions = JobCatalog.COMMON_BENEFITS,
                onChange = { values -> vm.updateJob { j -> j.copy(benefits = values) } },
                hint = "Sharoit qo'shish",
                palette = palette,
            )
        }

        AboutSection(
            state = state,
            vm = vm,
            sectionTitle = "E'lon matni",
            titleHint = "Sarlavha: Kuryer kerak — kunlik to'lov",
            descriptionHint = "Ish tafsiloti, kim mos keladi...",
        )

        ImagesSection(
            state = state,
            vm = vm,
            onAdd = imagePicker::pick,
            sectionTitle = "Rasmlar",
            hint = "Ish joyi surati",
            optional = true,
        )

        ContactSection(state, vm, subtitle = "Nomzodlar shu raqamga qo'ng'iroq qiladi")

        BranchesSection(
            state, palette, vm,
            title = "Ish joyi",
            subtitle = "Xaritadan aniq manzilni belgilang",
        )

        ValiditySection(state, vm)
    }
}

/**
 * Sana chipining yozuvi. Birinchi ikki kun nomi bilan ataladi — kunlik ish deyarli doim
 * shu ikkisiga tegishli va "20-iyul" dan ko'ra "Bugun" tezroq o'qiladi.
 */
private fun dateChipLabel(offset: Int, date: LocalDate): String = when (offset) {
    0 -> "Bugun"
    1 -> "Ertaga"
    else -> {
        val weekDay = WeekDay.entries[date.dayOfWeek.ordinal]
        "${date.dayOfMonth}-${UZ_MONTHS[date.monthNumber - 1]}, ${weekDay.label}"
    }
}
