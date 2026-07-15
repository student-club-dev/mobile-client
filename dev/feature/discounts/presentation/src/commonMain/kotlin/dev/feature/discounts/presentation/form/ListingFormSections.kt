package dev.feature.discounts.presentation.form

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import dev.core.designsystem.components.AppFontFamily
import dev.core.designsystem.components.GlassTextField
import dev.core.designsystem.theme.AppPalette
import dev.core.designsystem.theme.appPalette
import dev.feature.discounts.domain.model.DiscountType
import dev.feature.discounts.domain.model.ListingCatalog
import dev.feature.discounts.domain.model.ListingField
import dev.feature.discounts.domain.model.ListingValidator
import dev.feature.discounts.domain.model.RedemptionMethod
import dev.feature.discounts.domain.model.formatSum
import dev.feature.discounts.presentation.PostListingUiState
import dev.feature.discounts.presentation.PostListingViewModel
import dev.feature.discounts.presentation.categories
import dev.feature.discounts.presentation.components.AddImageTile
import dev.feature.discounts.presentation.components.ChipFlow
import dev.feature.discounts.presentation.components.FormSection
import dev.feature.discounts.presentation.components.ImageThumb
import dev.feature.discounts.presentation.components.SelectChip

/**
 * E'lon formasining umumiy bloklari. Har bir tur ekrani ([TypeForms]) shu bloklarni
 * o'z matnlari ([ListingFormCopy]) bilan yig'adi.
 *
 * Forma **ataylab qisqa**: biznes nomi, chegirma nimaga amal qilishi, rasm, narx va chegirma,
 * muddat, filial. Qolgan hamma narsa (tarkibi, o'lchamlar, shartlar) — erkin **tavsifda**,
 * chunki har bir tafsilot uchun alohida maydon qilinsa forma to'ldirib bo'lmas darajada
 * cho'zilib ketadi.
 */

/** 1. Biznes nomi va chegirma nimaga amal qilishi. */
@Composable
fun BusinessAndScopeSection(
    state: PostListingUiState,
    copy: ListingFormCopy,
    vm: PostListingViewModel,
) {
    FormSection(
        title = copy.businessSection,
        subtitle = "Talaba e'lonni shu nom ostida ko'radi",
        error = state.errorFor(ListingField.BUSINESS_NAME) ?: state.errorFor(ListingField.CATEGORY),
    ) {
        GlassTextField(state.businessName, vm::onBusinessName, copy.businessHint, height = 48)

        Text(
            copy.categoryHint,
            style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = appPalette.label),
        )

        ChipFlow {
            state.categories().forEach { category ->
                SelectChip(
                    text = category.label,
                    selected = state.categoryKey == category.key,
                    onClick = { vm.onCategory(category.key) },
                )
            }
        }

        if (state.categoryKey == ListingCatalog.OTHER_KEY) {
            GlassTextField(state.customCategoryName, vm::onCustomCategory, "Nimaga amal qiladi?", height = 46)
        }
    }
}

/** 2. Rasmlar. */
@Composable
fun ImagesSection(
    state: PostListingUiState,
    copy: ListingFormCopy,
    vm: PostListingViewModel,
    onAdd: () -> Unit,
) {
    FormSection(
        title = copy.imagesSection,
        subtitle = "${copy.imagesHint} · maksimal ${ListingValidator.MAX_IMAGES} ta",
        error = state.errorFor(ListingField.IMAGES),
    ) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.images.forEachIndexed { index, source ->
                ImageThumb(source, onRemove = { vm.removeImage(index) })
            }
            if (state.images.size < ListingValidator.MAX_IMAGES) {
                AddImageTile(onClick = onAdd, loading = state.uploadingImage)
            }
        }
    }
}

/**
 * 3. Sarlavha va tavsif. Tavsif — **hamma qolgan tafsilot uchun**: tarkibi, o'lchamlar,
 * ish vaqti, qo'shimcha shartlar. Shu sabab alohida o'nlab maydon kerak emas.
 */
@Composable
fun AboutSection(
    state: PostListingUiState,
    copy: ListingFormCopy,
    vm: PostListingViewModel,
) {
    FormSection(
        title = copy.aboutSection,
        subtitle = "Tafsilotlarni shu yerga erkin yozing — alohida maydonlar kerak emas",
        error = state.errorFor(ListingField.TITLE),
    ) {
        GlassTextField(state.title, vm::onTitle, copy.titleHint, height = 48)
        GlassTextField(state.description, vm::onDescription, copy.descriptionHint, height = 110)
    }
}

/** 4. Narx va chegirma — formaning asosiy qismi. Yakuniy narx darrov ko'rinadi. */
@Composable
fun PriceAndDiscountSection(
    state: PostListingUiState,
    copy: ListingFormCopy,
    vm: PostListingViewModel,
) {
    val palette = appPalette

    FormSection(
        title = copy.priceSection,
        subtitle = "Chegirmasiz narx va talabaga beriladigan chegirma",
        error = state.errorFor(ListingField.PRICE) ?: state.errorFor(ListingField.DISCOUNT),
    ) {
        GlassTextField(
            state.originalPrice,
            vm::onPrice,
            copy.priceHint,
            height = 48,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            trailing = { Suffix("so'm", palette) },
        )

        ChipFlow {
            DiscountType.entries.forEach { type ->
                SelectChip(type.label, state.discountType == type, { vm.onDiscountType(type) })
            }
        }

        if (state.discountType != DiscountType.FREE_ITEM) {
            GlassTextField(
                state.discountValue,
                vm::onDiscountValue,
                state.discountType.hint,
                height = 48,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                trailing = { Suffix(state.discountType.valueLabel, palette) },
            )
        }

        val price = state.originalPrice.toLongOrNull() ?: 0
        if (price > 0 && state.finalPrice in 1 until price) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(palette.successBg).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Talaba to'laydi:",
                    style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.inkMuted),
                )
                Text(
                    "${state.finalPrice.formatSum()} so'm",
                    style = TextStyle(fontFamily = AppFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Black, color = palette.successDeep),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${price.formatSum()} o'rniga",
                    style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.sp, color = palette.inkFaint),
                )
            }
        }

        GlassTextField(state.conditions, vm::onConditions, copy.conditionsHint, height = 46)
    }
}

/** 5. Qanday ishlatiladi — uchta variant, limitlar yo'q (odatiy: kuniga 1 marta). */
@Composable
fun RedemptionSection(state: PostListingUiState, vm: PostListingViewModel) {
    FormSection(
        title = "Talaba qanday oladi",
        subtitle = state.redemptionMethod.hint,
        error = state.errorFor(ListingField.PROMO_CODE),
    ) {
        ChipFlow {
            RedemptionMethod.entries.forEach { method ->
                SelectChip(method.label, state.redemptionMethod == method, { vm.onRedemptionMethod(method) })
            }
        }
        if (state.redemptionMethod == RedemptionMethod.PROMO_CODE) {
            GlassTextField(state.promoCode, vm::onPromoCode, "NAVRUZ20", height = 46)
        }
    }
}

private val durationOptions = listOf(7, 14, 30, 60, 90)

/** 6. Amal qilish muddati. */
@Composable
fun ValiditySection(state: PostListingUiState, vm: PostListingViewModel) {
    FormSection(
        title = "Qancha vaqt amal qiladi",
        subtitle = "Bugundan boshlab ${state.durationDays} kun",
        error = state.errorFor(ListingField.VALIDITY),
    ) {
        ChipFlow {
            durationOptions.forEach { days ->
                SelectChip("$days kun", state.durationDays == days, { vm.onDuration(days) })
            }
        }
    }
}

@Composable
private fun Suffix(text: String, palette: AppPalette) {
    Text(
        text,
        style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.5f.sp, fontWeight = FontWeight.Bold, color = palette.inkFaint),
    )
}

@Composable
fun SectionSpacer() = Box(Modifier.padding(top = 0.dp))
