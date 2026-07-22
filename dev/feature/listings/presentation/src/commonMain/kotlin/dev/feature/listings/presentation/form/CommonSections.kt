package dev.feature.listings.presentation.form

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import dev.core.uikit.components.AppFontFamily
import dev.core.uikit.components.GlassTextField
import dev.core.uikit.theme.AppPalette
import dev.core.uikit.theme.appPalette
import dev.feature.listings.domain.model.ListingField
import dev.feature.listings.domain.model.ListingValidator
import dev.feature.listings.domain.model.PriceUnit
import dev.feature.listings.domain.model.formatSum
import dev.feature.listings.presentation.PostListingUiState
import dev.feature.listings.presentation.PostListingViewModel
import dev.feature.listings.presentation.components.AddImageTile
import dev.feature.listings.presentation.components.FieldHint
import dev.feature.listings.presentation.components.FormSection
import dev.feature.listings.presentation.components.ImageThumb
import dev.feature.listings.presentation.components.MiniLabel
import dev.feature.listings.presentation.components.SelectChip

/**
 * Hamma e'lon turida bir xil ishlaydigan bloklar: nomi, rasmlar, narx, aloqa, muddat.
 *
 * Yozuvlari parametr sifatida beriladi (`titleHint`, `priceLabel`...), chunki bir xil blok
 * turli turlarda boshqacha atalishi kerak: chegirmada "Taom nomi", ijarada "E'lon sarlavhasi",
 * ishda "Vakansiya nomi". Mantiq esa bitta joyda turadi va takrorlanmaydi.
 */

/** Sarlavha va tavsif. Tavsif — barcha qolgan tafsilotlar uchun erkin maydon. */
@Composable
fun AboutSection(
    state: PostListingUiState,
    vm: PostListingViewModel,
    sectionTitle: String,
    titleHint: String,
    descriptionHint: String,
    subtitle: String = "Tafsilotlarni shu yerga erkin yozing",
) {
    FormSection(
        title = sectionTitle,
        subtitle = subtitle,
        error = state.errorFor(ListingField.TITLE),
    ) {
        GlassTextField(state.title, vm::onTitle, titleHint, height = 48)
        GlassTextField(state.description, vm::onDescription, descriptionHint, height = 110)
    }
}

/** Rasmlar. Ish e'lonida ixtiyoriy — buni [optional] bildiradi. */
@Composable
fun ImagesSection(
    state: PostListingUiState,
    vm: PostListingViewModel,
    onAdd: () -> Unit,
    sectionTitle: String,
    hint: String,
    optional: Boolean = false,
) {
    FormSection(
        title = sectionTitle + if (optional) " (ixtiyoriy)" else "",
        subtitle = "$hint · maksimal ${ListingValidator.MAX_IMAGES} ta",
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
 * Narx. Uchta ko'rinishda ishlaydi:
 * - oddiy narx (ijara, xizmat);
 * - narx oralig'i ([showRange] — ish e'lonidagi maosh vilkasi "3–5 mln");
 * - "kelishilgan holda" ([allowNegotiable]) — summa umuman kiritilmasligi mumkin.
 */
@Composable
fun PriceSection(
    state: PostListingUiState,
    vm: PostListingViewModel,
    sectionTitle: String,
    priceLabel: String,
    hint: String,
    subtitle: String? = null,
    showRange: Boolean = false,
    allowNegotiable: Boolean = true,
    priceUnits: List<PriceUnit> = emptyList(),
    palette: AppPalette = appPalette,
) {
    FormSection(
        title = sectionTitle,
        subtitle = subtitle,
        error = state.errorFor(ListingField.PRICE),
    ) {
        if (priceUnits.size > 1) {
            MiniLabel("Narx nimaga nisbatan", palette)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                priceUnits.forEach { unit ->
                    SelectChip(unit.label, state.priceUnit == unit, { vm.onPriceUnit(unit) })
                }
            }
        }

        if (!state.isNegotiable) {
            MiniLabel(if (showRange) "$priceLabel — dan" else priceLabel, palette)
            GlassTextField(
                state.price, vm::onPrice, hint,
                height = 48,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                trailing = { Suffix("so'm", palette) },
            )

            if (showRange) {
                MiniLabel("$priceLabel — gacha (ixtiyoriy)", palette)
                GlassTextField(
                    state.priceMax, vm::onPriceMax, "Yuqori chegara",
                    height = 48,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    trailing = { Suffix("so'm", palette) },
                )
            }

            PriceEcho(state, palette)
        }

        if (allowNegotiable) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SelectChip("Aniq narx", !state.isNegotiable, { vm.onNegotiable(false) })
                SelectChip("Kelishilgan holda", state.isNegotiable, { vm.onNegotiable(true) })
            }
        }
    }
}

/** Kiritilgan summani o'qiladigan ko'rinishda qaytaradi — nol qo'shib yuborishning oldini oladi. */
@Composable
private fun PriceEcho(state: PostListingUiState, palette: AppPalette) {
    val from = state.price.toLongOrNull() ?: return
    if (from <= 0) return
    val to = state.priceMax.toLongOrNull()

    val text = if (to != null && to > from) {
        "${from.formatSum()} — ${to.formatSum()} so'm / ${state.priceUnit.suffix}"
    } else {
        "${from.formatSum()} so'm / ${state.priceUnit.suffix}"
    }

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(palette.successBg).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Black, color = palette.successDeep),
        )
    }
}

/** Aloqa — telefon raqami. Hamma turda majburiy. */
@Composable
fun ContactSection(state: PostListingUiState, vm: PostListingViewModel, subtitle: String) {
    FormSection(
        title = "Telefon raqami",
        subtitle = subtitle,
        error = state.errorFor(ListingField.CONTACT),
    ) {
        GlassTextField(
            state.contactPhone, vm::onContactPhone, "+998 90 123 45 67",
            height = 48,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
    }
}

private val durationOptions = listOf(7, 14, 30, 60, 90)

/** E'lon qancha vaqt ro'yxatda turadi. */
@Composable
fun ValiditySection(state: PostListingUiState, vm: PostListingViewModel) {
    FormSection(
        title = "E'lon qancha turadi",
        subtitle = "Bugundan boshlab ${state.durationDays} kun",
        error = state.errorFor(ListingField.VALIDITY),
    ) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            durationOptions.forEach { days ->
                SelectChip("$days kun", state.durationDays == days, { vm.onDuration(days) })
            }
        }
    }
}

/** Maydon oxiridagi o'lchov birligi ("so'm"). */
@Composable
fun Suffix(text: String, palette: AppPalette) {
    Text(
        text,
        style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.5f.sp, fontWeight = FontWeight.Bold, color = palette.inkFaint),
    )
}

/** Bo'lim ichidagi izohli ajratgich. */
@Composable
fun SectionNote(text: String, palette: AppPalette = appPalette) {
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.fillMaxWidth())
        FieldHint(text, palette)
    }
}
