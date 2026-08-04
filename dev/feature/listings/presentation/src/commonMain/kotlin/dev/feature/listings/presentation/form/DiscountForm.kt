package dev.feature.listings.presentation.form

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.core.common.format.toAmountDigits
import dev.core.uikit.components.AmountVisualTransformation
import dev.core.uikit.components.AppFontFamily
import dev.core.uikit.components.GlassTextField
import dev.core.uikit.media.rememberImagePicker
import dev.core.uikit.theme.AppPalette
import dev.core.uikit.theme.appPalette
import dev.feature.listings.domain.model.BusinessType
import dev.feature.listings.domain.model.ListingCatalog
import dev.feature.listings.domain.model.ListingField
import dev.feature.listings.domain.model.RedemptionMethod
import dev.feature.listings.domain.model.formatSum
import dev.feature.listings.presentation.BranchesSection
import dev.feature.listings.presentation.PostListingUiState
import dev.feature.listings.presentation.PostListingViewModel
import dev.feature.listings.presentation.components.ChipFlow
import dev.feature.listings.presentation.components.DynamicFields
import dev.feature.listings.presentation.components.FormSection
import dev.feature.listings.presentation.components.MiniLabel
import dev.feature.listings.presentation.components.SectionHPad
import dev.feature.listings.presentation.components.SectionHeader
import dev.feature.listings.presentation.components.SelectChip

/**
 * Chegirma / sotuv e'loni formasi — biznes uchun.
 *
 * Forma **ataylab qisqa**: bo'lim, nomi, rasm, narx va chegirma, aloqa, filial. Qolgan
 * tafsilotlar erkin tavsifda, chunki har bir tafsilot uchun alohida maydon qilinsa forma
 * to'ldirib bo'lmas darajada cho'zilib ketadi.
 *
 * Yozuvlar biznes turiga qarab o'zgaradi ([ListingFormCopy]): kafeda "Taom nomi",
 * game club'da "Sessiya", o'quv markazda "Kurs nomi".
 */
@Composable
fun DiscountForm(state: PostListingUiState, palette: AppPalette, vm: PostListingViewModel) {
    val type = state.discount.businessType ?: return
    val copy = ListingFormCopy.of(type)

    val imagePicker = rememberImagePicker { picked ->
        if (picked != null) vm.addImage(picked.bytes, picked.fileName)
    }

    ListingFormShell(copy.screenTitle, copy.screenSubtitle, state, palette, vm) {
        // E'lon turi (Chegirma / Oddiy) — faqat tab belgilamagan bo'lsa.
        if (!state.discount.modeLocked) ListingModeSection(state, vm)

        CategorySection(state, copy, vm)
        CategoryAttributesSection(state, vm)
        AboutSection(
            state, vm,
            sectionTitle = copy.aboutSection,
            titleHint = copy.titleHint,
            descriptionHint = copy.descriptionHint,
        )
        ImagesSection(
            state, vm, imagePicker::pick,
            sectionTitle = copy.imagesSection,
            hint = copy.imagesHint,
        )
        PriceAndDiscountSection(state, vm)
        if (state.discount.isDiscounted) RedemptionSection(state, vm)
        ContactSection(state, vm, subtitle = "Talaba shu raqamga bog'lanadi")
        BranchesSection(
            state, palette, vm,
            title = "Filiallar",
            subtitle = "Talabaga eng yaqini masofasi bilan ko'rsatiladi",
        )
        ValiditySection(state, vm)
    }
}

/**
 * E'LON TURI — chegirmali yoki oddiy. Tanlovga qarab narx bo'limidagi chegirma maydonlari
 * ko'rinadi yoki yashiriladi.
 */
@Composable
fun ListingModeSection(state: PostListingUiState, vm: PostListingViewModel) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.padding(horizontal = SectionHPad)) {
            SectionHeader("E'lon turi", "Chegirmali yoki oddiy e'lon")
        }
        LazyRow(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = SectionHPad),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SelectChip(
                    "Chegirma e'loni",
                    state.discount.isDiscounted,
                    onClick = { vm.updateDiscount { it.copy(isDiscounted = true) } },
                )
            }
            item {
                SelectChip(
                    "Oddiy e'lon",
                    !state.discount.isDiscounted,
                    onClick = { vm.updateDiscount { it.copy(isDiscounted = false) } },
                )
            }
        }
    }
}

/**
 * BO'LIM — formaning eng tepasida, horizontal scroll bilan. Masalan Game Club'da
 * PlayStation / Stol tennis / Billiard bir qatorda suriladi.
 */
@Composable
fun CategorySection(state: PostListingUiState, copy: ListingFormCopy, vm: PostListingViewModel) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.padding(horizontal = SectionHPad)) {
            SectionHeader("Turi", copy.categoryHint)
            state.errorFor(ListingField.CATEGORY)?.let {
                Text(
                    it,
                    style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.5f.sp, fontWeight = FontWeight.Bold, color = appPalette.primary),
                )
            }
        }
        // Edge-to-edge horizontal scroll — chiplar ekran chetigacha suriladi.
        LazyRow(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = SectionHPad),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.discount.categories()) { category ->
                SelectChip(
                    text = category.label,
                    selected = state.discount.categoryKey == category.key,
                    onClick = {
                        // Kategoriya o'zgarsa unga xos maydonlar boshqacha — eskilari tozalanadi.
                        vm.updateDiscount { it.copy(categoryKey = category.key, attributeValues = emptyMap()) }
                    },
                )
            }
        }

        if (state.discount.categoryKey == ListingCatalog.OTHER_KEY) {
            Box(Modifier.padding(horizontal = SectionHPad)) {
                GlassTextField(
                    state.discount.customCategoryName,
                    { value -> vm.updateDiscount { it.copy(customCategoryName = value) } },
                    "Nimaga amal qiladi?",
                    height = 46,
                )
            }
        }
    }
}

/**
 * KATEGORIYAGA XOS MAYDONLAR — masalan Game Club'da "PlayStation" tanlansa model/joystik,
 * "Billiard" tanlansa stol turi so'raladi. Maydon bo'lmasa bo'lim ko'rinmaydi.
 */
@Composable
fun CategoryAttributesSection(state: PostListingUiState, vm: PostListingViewModel) {
    val specs = state.discount.categoryAttributes()
    if (specs.isEmpty()) return

    FormSection(
        title = "Tafsilotlar",
        subtitle = "Tanlangan bo'limga mos ma'lumotlar",
        error = state.errorFor(ListingField.ATTRIBUTES),
    ) {
        DynamicFields(
            specs = specs,
            values = state.discount.attributeValues,
            onChange = { key, value ->
                vm.updateDiscount { discount ->
                    discount.copy(
                        attributeValues = if (value.isBlank()) {
                            discount.attributeValues - key
                        } else {
                            discount.attributeValues + (key to value)
                        },
                    )
                }
            },
        )
    }
}

/**
 * NARX — chegirmada "Oldingi" va "Hozirgi" narx, oddiy e'londa bitta narx.
 *
 * Foiz/summa turlari formada so'ralmaydi: biznes uchun eng tabiiy yozuv "eski narx →
 * yangi narx", foizni esa ilova o'zi hisoblab ko'rsatadi.
 */
@Composable
fun PriceAndDiscountSection(state: PostListingUiState, vm: PostListingViewModel) {
    val palette = appPalette
    val discounted = state.discount.isDiscounted

    FormSection(
        title = "Narx",
        subtitle = if (discounted) "Oldingi va hozirgi (chegirmali) narx" else "E'lon narxi",
        error = state.errorForAny(ListingField.PRICE, ListingField.DISCOUNT),
    ) {
        MiniLabel(if (discounted) "Oldingi narx" else "Narx", palette)
        GlassTextField(
            state.price, vm::onPrice, "Masalan: 50 000",
            height = 48,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            visualTransformation = AmountVisualTransformation(),
            trailing = { Suffix("so'm", palette) },
        )

        if (discounted) {
            MiniLabel("Hozirgi narx (chegirmali)", palette)
            GlassTextField(
                state.discount.discountValue,
                { value -> vm.updateDiscount { it.copy(discountValue = value.toAmountDigits()) } },
                "Masalan: 35 000",
                height = 48,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = AmountVisualTransformation(),
                trailing = { Suffix("so'm", palette) },
            )

            val old = state.price.toLongOrNull() ?: 0
            val new = state.discount.discountValue.toLongOrNull() ?: 0
            if (old > 0 && new in 1 until old) {
                val percent = (old - new) * 100 / old
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(palette.successBg).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Talaba to'laydi: ${new.formatSum()} so'm",
                        style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Black, color = palette.successDeep),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "-$percent%",
                        style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = palette.successDeep),
                    )
                }
            }
        }
    }
}

/** Talaba chegirmani qanday oladi — limitlar so'ralmaydi (odatiy: kuniga 1 marta). */
@Composable
fun RedemptionSection(state: PostListingUiState, vm: PostListingViewModel) {
    FormSection(
        title = "Talaba qanday oladi",
        subtitle = state.discount.redemptionMethod.hint,
        error = state.errorFor(ListingField.PROMO_CODE),
    ) {
        ChipFlow {
            RedemptionMethod.entries.forEach { method ->
                SelectChip(
                    method.label,
                    state.discount.redemptionMethod == method,
                    onClick = { vm.updateDiscount { it.copy(redemptionMethod = method) } },
                )
            }
        }
        if (state.discount.redemptionMethod == RedemptionMethod.PROMO_CODE) {
            GlassTextField(
                state.discount.promoCode,
                { value -> vm.updateDiscount { it.copy(promoCode = value.uppercase()) } },
                "NAVRUZ20",
                height = 46,
            )
        }
    }
}

/** Biznes nomi — biznes profili hali yaratilmagan holat uchun. */
@Composable
fun BusinessNameSection(state: PostListingUiState, copy: ListingFormCopy, vm: PostListingViewModel) {
    FormSection(
        title = copy.businessSection,
        subtitle = "Talaba e'lonni shu nom ostida ko'radi",
        error = state.errorFor(ListingField.BUSINESS_NAME),
    ) {
        GlassTextField(
            state.discount.businessName,
            { value -> vm.updateDiscount { it.copy(businessName = value) } },
            copy.businessHint,
            height = 48,
        )
    }
}

/** Biznes turining narx birliklari — turga mos variantlar orasidan tanlash. */
@Composable
fun PriceUnitRow(state: PostListingUiState, type: BusinessType, vm: PostListingViewModel) {
    val units = ListingCatalog.priceUnits(type)
    if (units.size <= 1) return

    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        units.forEach { unit ->
            SelectChip(unit.label, state.priceUnit == unit, { vm.onPriceUnit(unit) })
        }
    }
}
