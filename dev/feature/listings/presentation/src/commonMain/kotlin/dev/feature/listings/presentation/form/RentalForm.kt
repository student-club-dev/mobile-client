package dev.feature.listings.presentation.form

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.core.uikit.components.GlassTextField
import dev.core.uikit.media.rememberImagePicker
import dev.core.uikit.theme.AppPalette
import dev.feature.listings.domain.model.ListingField
import dev.feature.listings.domain.model.PropertyType
import dev.feature.listings.domain.model.RentPeriod
import dev.feature.listings.domain.model.RentalCatalog
import dev.feature.listings.domain.model.TenantGender
import dev.feature.listings.presentation.BranchesSection
import dev.feature.listings.presentation.PostListingUiState
import dev.feature.listings.presentation.PostListingViewModel
import dev.feature.listings.presentation.components.ChipFlow
import dev.feature.listings.presentation.components.FieldHint
import dev.feature.listings.presentation.components.FormSection
import dev.feature.listings.presentation.components.FormSwitch
import dev.feature.listings.presentation.components.MiniLabel
import dev.feature.listings.presentation.components.MultiSelectChips
import dev.feature.listings.presentation.components.NumberChipsField
import dev.feature.listings.presentation.components.SelectChip
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** Sana yorlig'i uchun oy nomlari — "12-avgust" ko'rinishida yoziladi. */
private val UZ_MONTHS = listOf(
    "yanvar", "fevral", "mart", "aprel", "may", "iyun",
    "iyul", "avgust", "sentabr", "oktabr", "noyabr", "dekabr",
)

/** Ko'chib kirish sanasi sifatida taklif qilinadigan kunlar soni. */
private const val AVAILABLE_DAYS = 14

/**
 * Ijara e'loni formasi.
 *
 * Tartib talabaning o'qish ketma-ketligiga moslangan: avval "qanday uy va kim uchun"
 * (shu ikkisi mos kelmasa qolgani o'qilmaydi), keyin narx, keyin tafsilotlar.
 */
@Composable
fun RentalForm(state: PostListingUiState, palette: AppPalette, vm: PostListingViewModel) {
    val rental = state.rental
    val imagePicker = rememberImagePicker { picked ->
        if (picked != null) vm.addImage(picked.bytes, picked.fileName)
    }

    ListingFormShell(
        title = "Ijara e'loni",
        subtitle = "Turarjoy va sherik izlash",
        state = state,
        palette = palette,
        vm = vm,
    ) {
        FormSection(
            title = "Turarjoy turi",
            error = state.errorFor(ListingField.PROPERTY_TYPE),
        ) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                PropertyType.entries.forEach { type ->
                    SelectChip(
                        "${type.emoji} ${type.label}",
                        rental.propertyType == type,
                        { vm.updateRental { it.copy(propertyType = type) } },
                    )
                }
            }
        }

        FormSection(
            title = "Xonalar va yashovchilar",
            subtitle = "Talaba shu raqamlarga qarab tanlaydi",
            error = state.errorForAny(ListingField.ROOMS, ListingField.TENANTS),
        ) {
            // Savol matni turarjoy turiga bog'liq: koyka joyda "nechi xonalik" ma'nosiz.
            NumberChipsField(
                label = RentalCatalog.roomCountLabel(rental.propertyType),
                value = rental.roomCount,
                options = RentalCatalog.ROOM_COUNTS,
                onSelect = { value -> vm.updateRental { it.copy(roomCount = value) } },
                lastIsPlus = true,
            )

            NumberChipsField(
                label = "Hozir nechi kishi yashaydi",
                value = rental.currentTenants,
                options = RentalCatalog.TENANT_COUNTS,
                onSelect = { value -> vm.updateRental { it.copy(currentTenants = value) } },
                suffix = "kishi",
            )

            NumberChipsField(
                label = "Yana nechi kishi kerak",
                value = rental.neededTenants,
                options = RentalCatalog.TENANT_COUNTS.filter { it > 0 },
                onSelect = { value -> vm.updateRental { it.copy(neededTenants = value) } },
                suffix = "kishi",
            )

            // Jami sonni o'zi hisoblab ko'rsatadi — e'lon beruvchi noto'g'ri raqam
            // kiritganini darrov sezadi.
            val current = rental.currentTenants.toIntOrNull()
            val needed = rental.neededTenants.toIntOrNull()
            if (current != null && needed != null) {
                FieldHint("Uyda jami ${current + needed} kishi bo'ladi", palette)
            }
        }

        FormSection(
            title = "Kim uchun",
            subtitle = "Bu majburiy — talaba birinchi navbatda shuni qaraydi",
            error = state.errorFor(ListingField.GENDER),
        ) {
            ChipFlow {
                TenantGender.entries.forEach { gender ->
                    SelectChip(
                        "${gender.emoji} ${gender.label}",
                        rental.gender == gender,
                        { vm.updateRental { it.copy(gender = gender) } },
                    )
                }
            }
        }

        // Davr narxdan oldin turadi: "oylik"mi yoki "kunlik"mi — narx maydonining
        // yozuvini shu belgilaydi.
        FormSection(title = "To'lov davri") {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                RentPeriod.entries.forEach { period ->
                    SelectChip(
                        period.label,
                        rental.period == period,
                        { vm.updateRental { it.copy(period = period) } },
                    )
                }
            }
        }

        PriceSection(
            state = state,
            vm = vm,
            sectionTitle = "Ijara narxi",
            priceLabel = if (rental.period == RentPeriod.MONTHLY) "Oylik to'lov" else "Kunlik to'lov",
            hint = "2 500 000",
            allowNegotiable = true,
            palette = palette,
        )

        FormSection(
            title = "Qo'shimcha shartlar",
            error = state.errorFor(ListingField.ATTRIBUTES),
        ) {
            FormSwitch(
                "Kommunal to'lov narxga kiradi",
                rental.utilitiesIncluded,
                { checked -> vm.updateRental { it.copy(utilitiesIncluded = checked) } },
                palette,
            )

            NumberChipsField(
                label = "Necha oylik depozit",
                value = rental.depositMonths,
                options = RentalCatalog.DEPOSIT_MONTHS,
                onSelect = { value -> vm.updateRental { it.copy(depositMonths = value) } },
                suffix = "oy",
                palette = palette,
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    MiniLabel("Qavat", palette)
                    GlassTextField(
                        rental.floor,
                        { input -> vm.updateRental { it.copy(floor = input.filter(Char::isDigit)) } },
                        "3",
                        height = 46,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    MiniLabel("Binoda qavat", palette)
                    GlassTextField(
                        rental.totalFloors,
                        { input -> vm.updateRental { it.copy(totalFloors = input.filter(Char::isDigit)) } },
                        "9",
                        height = 46,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
        }

        FormSection(title = "Qulayliklar", subtitle = "Bor narsalarni belgilang") {
            MultiSelectChips(RentalCatalog.AMENITIES, rental.amenities) { key ->
                vm.updateRental { r ->
                    r.copy(amenities = if (key in r.amenities) r.amenities - key else r.amenities + key)
                }
            }
        }

        FormSection(title = "Qachondan ko'chib kirish mumkin") {
            // Sanalar joriy vaqtdan kelib chiqadi, lekin har qayta chizilishda qayta
            // hisoblanishi shart emas — ro'yxat bir marta tuziladi.
            val days = remember {
                val zone = TimeZone.currentSystemDefault()
                val today = Clock.System.now().toLocalDateTime(zone).date
                (0 until AVAILABLE_DAYS).map { offset ->
                    val date = today.plus(DatePeriod(days = offset))
                    val label = "${date.dayOfMonth}-${UZ_MONTHS[date.monthNumber - 1]}"
                    date.atStartOfDayIn(zone).toEpochMilliseconds() to label
                }
            }

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                SelectChip(
                    "Hoziroq",
                    rental.availableFrom == null,
                    { vm.updateRental { it.copy(availableFrom = null) } },
                )
                days.forEach { (millis, label) ->
                    SelectChip(
                        label,
                        rental.availableFrom == millis,
                        { vm.updateRental { it.copy(availableFrom = millis) } },
                    )
                }
            }
        }

        AboutSection(
            state = state,
            vm = vm,
            sectionTitle = "E'lon matni",
            titleHint = "Sarlavha: Chilonzorda 3 xonali kvartira",
            descriptionHint = "Uy sharoiti, qoidalar, metroga masofa...",
        )

        ImagesSection(
            state = state,
            vm = vm,
            onAdd = imagePicker::pick,
            sectionTitle = "Uy rasmlari",
            hint = "Xonalar, oshxona, hammom",
        )

        ContactSection(
            state = state,
            vm = vm,
            subtitle = "Sherik izlayotganlar shu raqamga qo'ng'iroq qiladi",
        )

        BranchesSection(
            state, palette, vm,
            title = "Uy joyi",
            subtitle = "Xaritadan aniq joyni belgilang",
        )

        AudienceSection(state, vm)
        ValiditySection(state, vm)
    }
}
