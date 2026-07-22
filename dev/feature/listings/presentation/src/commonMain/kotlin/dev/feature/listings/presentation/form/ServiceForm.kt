package dev.feature.listings.presentation.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.core.uikit.components.GlassTextField
import dev.core.uikit.media.rememberImagePicker
import dev.core.uikit.theme.AppPalette
import dev.feature.listings.domain.model.ListingField
import dev.feature.listings.domain.model.ServiceCatalog
import dev.feature.listings.domain.model.ServiceFormat
import dev.feature.listings.domain.model.ServiceType
import dev.feature.listings.presentation.BranchesSection
import dev.feature.listings.presentation.PostListingUiState
import dev.feature.listings.presentation.PostListingViewModel
import dev.feature.listings.presentation.components.ChipFlow
import dev.feature.listings.presentation.components.DynamicFields
import dev.feature.listings.presentation.components.FormSection
import dev.feature.listings.presentation.components.FormSwitch
import dev.feature.listings.presentation.components.MiniLabel
import dev.feature.listings.presentation.components.SelectChip

/**
 * Xizmat e'loni formasi.
 *
 * Boshqa turlardan farqi — bu yerda maydonlar **qo'lda yozilmagan**: soha ([ServiceType]) va
 * yo'nalish tanlangach, [ServiceCatalog] spetsifikatsiyasi [DynamicFields] orqali formaga
 * aylanadi. Shu sababli yangi soha qo'shish uchun ekranga tegilmaydi — katalog yetarli.
 */
@Composable
fun ServiceForm(state: PostListingUiState, palette: AppPalette, vm: PostListingViewModel) {
    val service = state.service
    val type = service.serviceType

    val imagePicker = rememberImagePicker { picked ->
        if (picked != null) vm.addImage(picked.bytes, picked.fileName)
    }

    ListingFormShell(
        title = if (type != null) "${type.label} xizmati" else "Xizmat e'loni",
        subtitle = if (type != null) "Tafsilotlarni to'ldiring — mijoz shunga qarab tanlaydi" else "Qanday xizmat ko'rsatasiz",
        state = state,
        palette = palette,
        vm = vm,
    ) {
        FormSection(
            title = "Xizmat sohasi",
            subtitle = "Nima bilan shug'ullanasiz",
            error = state.errorFor(ListingField.SERVICE_TYPE),
        ) {
            ChipFlow {
                ServiceType.entries.forEach { item ->
                    SelectChip(
                        "${item.emoji} ${item.label}",
                        selected = type == item,
                        onClick = { vm.updateService { s -> s.copy(serviceType = item) } },
                    )
                }
            }
        }

        // Qolgan bloklar soha tanlangandan keyingina chiqadi: maydonlar aynan sohadan
        // kelib chiqadi, tanlovsiz ko'rsatishga hech narsa yo'q.
        if (type != null) {
            if (type.hasSubjects) {
                FormSection(
                    title = ServiceCatalog.subjectLabel(type),
                    subtitle = "Aniq yo'nalishni tanlang — qidiruvda shunga qarab topiladi",
                    error = state.errorFor(ListingField.SERVICE_SUBJECT),
                ) {
                    ChipFlow {
                        service.subjects().forEach { subject ->
                            SelectChip(
                                subject.label,
                                selected = service.subjectKey == subject.key,
                                onClick = { vm.updateService { s -> s.copy(subjectKey = subject.key) } },
                            )
                        }
                    }
                    if (service.subjectKey == ServiceCatalog.OTHER_SUBJECT_KEY) {
                        GlassTextField(
                            service.customSubject,
                            { value -> vm.updateService { s -> s.copy(customSubject = value) } },
                            "Yo'nalish nomini yozing",
                            height = 46,
                        )
                    }
                }
            }

            FormSection(
                title = "Tafsilotlar",
                subtitle = "Sohaga xos ma'lumotlar",
                error = state.errorFor(ListingField.ATTRIBUTES),
            ) {
                // specs() soha maydonlari va yo'nalish maydonlarini o'zi birlashtiradi.
                DynamicFields(service.specs(), service.fields, vm::onServiceField)
            }

            FormSection(title = "Xizmat qanday ko'rsatiladi") {
                ChipFlow {
                    ServiceFormat.entries.forEach { format ->
                        SelectChip(
                            format.label,
                            selected = service.format == format,
                            onClick = { vm.updateService { s -> s.copy(format = format) } },
                        )
                    }
                }

                FormSwitch(
                    "Mijoz joyiga borib bajaraman",
                    service.hasHomeVisit,
                    onChange = { checked -> vm.updateService { s -> s.copy(hasHomeVisit = checked) } },
                )
                FormSwitch(
                    "Birinchi dars / sinov bepul",
                    service.hasFreeTrial,
                    onChange = { checked -> vm.updateService { s -> s.copy(hasFreeTrial = checked) } },
                )

                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    MiniLabel("Qabul qilish vaqti", palette)
                    GlassTextField(
                        service.workingHours,
                        { value -> vm.updateService { s -> s.copy(workingHours = value) } },
                        "09:00 — 21:00",
                        height = 46,
                    )
                }

                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    MiniLabel("Tajriba (yil)", palette)
                    GlassTextField(
                        service.experienceYears,
                        { value -> vm.updateService { s -> s.copy(experienceYears = value) } },
                        "3",
                        height = 46,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }

            // Xizmatda aniq narx kamdan-kam bo'ladi — ko'pincha "50 000 dan 150 000 gacha".
            PriceSection(
                state, vm,
                sectionTitle = "Xizmat narxi",
                priceLabel = "Narx",
                hint = "50 000",
                showRange = true,
                allowNegotiable = true,
                priceUnits = ServiceCatalog.priceUnits(type),
            )
        }

        AboutSection(
            state, vm,
            sectionTitle = "E'lon matni",
            titleHint = "Sarlavha: IELTS repetitor — 7.5 ball",
            descriptionHint = "Tajriba, natijalar, dars uslubi...",
        )

        ImagesSection(
            state, vm, imagePicker::pick,
            sectionTitle = "Rasmlar",
            hint = "Ish namunalari, sertifikatlar",
        )

        ContactSection(state, vm, subtitle = "Mijozlar shu raqamga bog'lanadi")

        BranchesSection(
            state, palette, vm,
            title = "Qayerda xizmat ko'rsatasiz",
            subtitle = "Xaritadan joyni belgilang",
        )

        ValiditySection(state, vm)
    }
}
