package dev.feature.listings.presentation.browse

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.Color
import dev.core.uikit.components.ScGradientButton
import dev.core.uikit.components.ScSheetClose
import dev.core.uikit.components.ScSheetHandle
import dev.core.uikit.components.ScSoftButton
import dev.core.uikit.components.ScText
import dev.core.uikit.theme.Sc
import dev.core.uikit.theme.AppPalette
import dev.feature.listings.domain.model.EmploymentType
import dev.feature.listings.domain.model.JobCatalog
import dev.feature.listings.domain.model.ListingFilters
import dev.feature.listings.domain.model.ListingKind
import dev.feature.listings.domain.model.PropertyType
import dev.feature.listings.domain.model.ServiceFormat
import dev.feature.listings.domain.model.ServiceType
import dev.feature.listings.domain.model.TaskCatalog
import dev.feature.listings.domain.model.TaskCategory
import dev.feature.listings.domain.model.TaskFormat
import dev.feature.listings.domain.model.TenantGender
import dev.feature.listings.domain.model.WorkShift
import dev.feature.listings.domain.model.formatSum
import dev.feature.listings.presentation.components.ChipFlow
import dev.feature.listings.presentation.components.SelectChip

/**
 * Filtr oynasi.
 *
 * "Qoralama" mantig'i: bu yerdagi o'zgarishlar `draft` ga tushadi va ro'yxatga faqat
 * "Qo'llash" bosilganda ko'chadi. Shu sabab foydalanuvchi bir nechta shartni tinch
 * belgilab chiqadi, ro'yxat esa har bosishda sakrab yangilanmaydi — lekin tugmadagi son
 * jonli hisoblanadi va nechta natija chiqishini oldindan ko'rsatadi.
 *
 * Ko'rsatiladigan maydonlar **turga qarab** butunlay boshqacha: ijarada jins va xona,
 * ishda smena va bandlik turi.
 */
@Composable
fun ListingFilterSheet(
    state: ListingFilterUiState,
    palette: AppPalette,
    vm: ListingsBrowseViewModel,
    onApply: () -> Unit,
    onClose: () -> Unit,
) {
    // Dizaynda filtr — modal bottom sheet: pastda oq varaq, orqasi qoraytirilgan.
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0B1622).copy(alpha = 0.42f)).clickable(onClick = onClose))
        Column(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 60.dp)
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(Color.White),
        ) {
            ScSheetHandle()
            Row(
                Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScText("Filtrlar", 20f, FontWeight.ExtraBold, Sc.Ink, Modifier.weight(1f), letterSpacing = -0.3f)
                ScSheetClose(onClose)
            }

            Column(
                Modifier.weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp)
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                when (state.kind) {
                    ListingKind.RENTAL -> RentalFilters(state.draft, palette, vm)
                    ListingKind.SERVICE -> ServiceFilters(state.draft, palette, vm)
                    ListingKind.JOB -> JobFilters(state.draft, palette, vm)
                    ListingKind.TASK -> TaskFilters(state.draft, palette, vm)
                    ListingKind.DISCOUNT -> Unit
                }

                PriceFilter(state.kind, state.draft, palette, vm)
                Spacer(Modifier.height(2.dp))
            }

            Row(
                Modifier.fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                ScSoftButton("Tozalash", vm::resetDraft, Modifier.weight(1f))
                ScGradientButton(
                    "Qo'llash · ${state.previewCount}",
                    onApply,
                    Modifier.weight(1.6f),
                    radius = 16.dp,
                    fontSize = 15f,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Ijara
// ---------------------------------------------------------------------------

@Composable
private fun RentalFilters(draft: ListingFilters, palette: AppPalette, vm: ListingsBrowseViewModel) {
    FilterSection("Kim uchun", palette, "Talabalar eng ko'p shu bo'yicha qidiradi") {
        ChipFlow {
            TenantGender.entries.forEach { gender ->
                SelectChip("${gender.emoji} ${gender.label}", draft.gender == gender, onClick = {
                    vm.updateDraft { it.copy(gender = it.gender.toggle(gender)) }
                })
            }
        }
    }

    FilterSection("Turarjoy turi", palette) {
        ChipFlow {
            PropertyType.entries.forEach { type ->
                SelectChip(type.label, draft.propertyType == type, onClick = {
                    vm.updateDraft { it.copy(propertyType = it.propertyType.toggle(type)) }
                })
            }
        }
    }

    FilterSection("Kamida nechi xonali", palette) {
        ChipFlow {
            ListingFilters.ROOM_OPTIONS.forEach { rooms ->
                SelectChip("$rooms+ xona", draft.minRooms == rooms, onClick = {
                    vm.updateDraft { it.copy(minRooms = it.minRooms.toggle(rooms)) }
                })
            }
        }
    }

    FilterSection("Qo'shimcha", palette) {
        ChipFlow {
            SelectChip("Bo'sh joyi bor", draft.onlyAvailable, onClick = {
                vm.updateDraft { it.copy(onlyAvailable = !it.onlyAvailable) }
            })
        }
    }
}

// ---------------------------------------------------------------------------
// Xizmat
// ---------------------------------------------------------------------------

@Composable
private fun TaskFilters(draft: ListingFilters, palette: AppPalette, vm: ListingsBrowseViewModel) {
    FilterSection("Ish yo'nalishi", palette, "Qaysi turdagi topshiriqlarni ko'rmoqchisiz") {
        ChipFlow {
            TaskCategory.entries.forEach { category ->
                SelectChip("${category.emoji} ${category.label}", draft.taskCategory == category, onClick = {
                    vm.updateDraft { it.copy(taskCategory = it.taskCategory.toggle(category), taskTypeKey = null) }
                })
            }
        }
    }

    // Turlar faqat yo'nalish tanlanganda — ular aynan undan kelib chiqadi.
    draft.taskCategory?.let { category ->
        FilterSection("Ish turi", palette) {
            ChipFlow {
                TaskCatalog.types(category).forEach { type ->
                    SelectChip(type.label, draft.taskTypeKey == type.key, onClick = {
                        vm.updateDraft { it.copy(taskTypeKey = it.taskTypeKey.toggle(type.key)) }
                    })
                }
            }
        }
    }

    FilterSection("Qanday topshiriladi", palette) {
        ChipFlow {
            TaskFormat.entries.forEach { format ->
                SelectChip(format.label, draft.taskFormat == format, onClick = {
                    vm.updateDraft { it.copy(taskFormat = it.taskFormat.toggle(format)) }
                })
            }
        }
    }
}

@Composable
private fun ServiceFilters(draft: ListingFilters, palette: AppPalette, vm: ListingsBrowseViewModel) {
    FilterSection("Xizmat sohasi", palette) {
        ChipFlow {
            ServiceType.entries.forEach { type ->
                SelectChip("${type.emoji} ${type.label}", draft.serviceType == type, onClick = {
                    vm.updateDraft { it.copy(serviceType = it.serviceType.toggle(type)) }
                })
            }
        }
    }

    FilterSection("Qanday ko'rsatiladi", palette) {
        ChipFlow {
            ServiceFormat.entries.forEach { format ->
                SelectChip(format.label, draft.serviceFormat == format, onClick = {
                    vm.updateDraft { it.copy(serviceFormat = it.serviceFormat.toggle(format)) }
                })
            }
        }
    }

    FilterSection("Qo'shimcha", palette) {
        ChipFlow {
            SelectChip("Sinov bepul", draft.onlyFreeTrial, onClick = {
                vm.updateDraft { it.copy(onlyFreeTrial = !it.onlyFreeTrial) }
            })
        }
    }
}

// ---------------------------------------------------------------------------
// Ish
// ---------------------------------------------------------------------------

@Composable
private fun JobFilters(draft: ListingFilters, palette: AppPalette, vm: ListingsBrowseViewModel) {
    FilterSection("Ish turi", palette) {
        ChipFlow {
            EmploymentType.entries.forEach { type ->
                SelectChip(type.label, draft.employment == type, onClick = {
                    vm.updateDraft {
                        // Ish turi o'zgarsa smena ro'yxati ham boshqacha — mos kelmagani tushadi.
                        val next = it.employment.toggle(type)
                        val shift = it.shift?.takeIf { s -> next == null || s in JobCatalog.shifts(next) }
                        it.copy(employment = next, shift = shift)
                    }
                })
            }
        }
    }

    FilterSection("Qanday ish", palette) {
        ChipFlow {
            JobCatalog.CATEGORIES.forEach { category ->
                SelectChip(category.label, draft.jobCategoryKey == category.key, onClick = {
                    vm.updateDraft { it.copy(jobCategoryKey = it.jobCategoryKey.toggle(category.key)) }
                })
            }
        }
    }

    val shifts = draft.employment?.let { JobCatalog.shifts(it) } ?: WorkShift.entries
    FilterSection("Smena", palette) {
        ChipFlow {
            shifts.forEach { shift ->
                SelectChip(shift.label, draft.shift == shift, onClick = {
                    vm.updateDraft { it.copy(shift = it.shift.toggle(shift)) }
                })
            }
        }
    }

    FilterSection("Qo'shimcha", palette) {
        ChipFlow {
            SelectChip("Tajriba shart emas", draft.noExperienceOnly, onClick = {
                vm.updateDraft { it.copy(noExperienceOnly = !it.noExperienceOnly) }
            })
        }
    }
}

// ---------------------------------------------------------------------------
// Umumiy
// ---------------------------------------------------------------------------

@Composable
private fun PriceFilter(
    kind: ListingKind,
    draft: ListingFilters,
    palette: AppPalette,
    vm: ListingsBrowseViewModel,
) {
    val options = ListingFilters.priceOptions(kind)
    if (options.isEmpty()) return

    FilterSection("Narx chegarasi", palette, "Kelishilgan narxli e'lonlar baribir ko'rinadi") {
        ChipFlow {
            options.forEach { limit ->
                SelectChip("${limit.formatSum()} gacha", draft.maxPrice == limit, onClick = {
                    vm.updateDraft { it.copy(maxPrice = it.maxPrice.toggle(limit)) }
                })
            }
        }
    }
}

@Composable
private fun FilterSection(
    title: String,
    palette: AppPalette,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ScText(title, 13f, FontWeight.ExtraBold, Sc.Ink)
            if (subtitle != null) {
                ScText(subtitle, 11.5f, FontWeight.Medium, Sc.Muted)
            }
        }
        content()
    }
}

/**
 * Tanlangan chipni qayta bosish uni bekor qiladi.
 *
 * Filtrda "hech qanday" degan alohida chip qo'yish qatorni uzaytiradi va har bir bo'limda
 * takrorlanadi; bekor qilish esa tabiiyroq — foydalanuvchi bosgan narsasini yana bosadi.
 */
private fun <T> T?.toggle(value: T): T? = if (this == value) null else value
