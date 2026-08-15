package dev.feature.auth.presentation.main

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import dev.core.common.format.formatIsoDate
import dev.core.common.format.formatUzPhoneFull
import dev.core.uikit.components.ScBackButton
import dev.core.uikit.components.ScFavoriteButton
import dev.core.uikit.components.ScHideBottomBar
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScNetworkImage
import dev.core.uikit.map.rememberShowOnMap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.core.domain.model.DiscountTag
import dev.core.domain.model.OfferBranch
import dev.core.domain.model.OfferDetail
import dev.core.uikit.components.AppFontFamily
import dev.core.uikit.components.AppIcons
import dev.core.uikit.components.scTopInset
import dev.core.uikit.components.ScShimmerBox
import dev.core.uikit.components.ScShimmerLine
import dev.core.uikit.theme.AppPalette
import kotlinx.coroutines.delay
import dev.core.common.format.formatAmount
import dev.core.uikit.locale.uiStrings

/**
 * E'lon tafsiloti — `POST /v1/discounts/detail` javobi. Feed kartasi ko'rsatolmaydigan hamma
 * narsa shu yerda: promo-kodning o'zi, chegirma shartlari, foydalanish chegarasi, filiallar va
 * biznes kontaktlari.
 *
 * Tarmoq bo'lmasa repository keshdagi kartadan minimal variant beradi
 * ([OfferDetail.fromNetwork] = `false`) — shunda oyna baribir ochiladi, tepada ogohlantirish bilan.
 */
@Composable
fun OfferDetailSheet(
    state: OfferDetailState,
    saved: Boolean,
    palette: AppPalette,
    onToggleSaved: (String, Boolean) -> Unit,
    onClose: () -> Unit,
) {
    // Tafsilot — to'liq ekranli qatlam: karkasning pastki paneli va «+» tugmasi uning
    // ustida qolib, kontentni to'sardi (bug hisoboti #42 skrinshoti).
    ScHideBottomBar()
    Column(Modifier.fillMaxSize().background(palette.bgBrush)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).scTopInset().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScBackButton(onClose, contentDescription = uiStrings().close)
            Spacer(Modifier.size(12.dp))
            Text(
                discountsStrings().listing,
                style = TextStyle(fontFamily = AppFontFamily, fontSize = 20.sp, fontWeight = FontWeight.Black, color = palette.ink),
                modifier = Modifier.weight(1f),
            )
            val d = state.detail
            if (d != null) {
                // Arxiv qutisi o'rniga yurak: ikonaning vazifasi ko'rinishidan
                // tushunilishi kerak edi (bug hisoboti #35).
                ScFavoriteButton(
                    saved = saved,
                    onToggle = { onToggleSaved(d.id, it) },
                    idleTint = palette.inkFaint,
                    contentDescription = discountsStrings().save,
                )
            }
        }

        when {
            // Tafsilot kelguncha — o'sha tafsilotning skeleti (banner + matn qatorlari),
            // shunda ma'lumot kelganda oyna ichi sakramaydi.
            state.loading -> Column(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ScShimmerBox(Modifier.fillMaxWidth().height(150.dp), RoundedCornerShape(18.dp))
                ScShimmerLine(0.65f, 16.dp)
                ScShimmerLine(0.4f, 13.dp)
                ScShimmerLine(0.85f, 11.dp)
                ScShimmerLine(0.7f, 11.dp)
            }
            state.detail != null -> DetailBody(state.detail, palette)
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    state.error ?: discountsStrings().loadFailed,
                    style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, color = palette.inkFaint),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailBody(d: OfferDetail, palette: AppPalette) {
    val accent = Color(d.bannerAccent)
    val clipboard = LocalClipboardManager.current
    var copied by remember(d.id) { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1500); copied = false } }


    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Rasmlar — server `images` massivini yuboradi, ilgari u umuman o'qilmasdi.
        if (d.images.isNotEmpty()) DetailImages(d.images, palette)

        // Sarlavha bloki
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(15.dp)).background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { Text(d.emoji, style = TextStyle(fontSize = 28.sp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    d.merchant,
                    style = TextStyle(fontFamily = AppFontFamily, fontSize = 15.sp, fontWeight = FontWeight.Black, color = palette.ink),
                )
                val sub = listOfNotNull(
                    d.subcategory.takeIf { it.isNotBlank() },
                    d.businessRating?.let { "★ $it" },
                ).joinToString(" · ")
                if (sub.isNotBlank()) {
                    Text(sub, style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.5f.sp, fontWeight = FontWeight.Bold, color = accent))
                }
            }
        }

        Text(
            d.title,
            style = TextStyle(fontFamily = AppFontFamily, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink),
        )

        if (!d.fromNetwork) {
            InfoBanner(discountsStrings().offlineCache, palette)
        }

        // Narx. Tekislash BAZA CHIZIG'I bo'yicha (`alignByBaseline`), pastki chekka
        // bo'yicha emas: 22sp narx bilan 12sp «/ dona» ning pastki chekkalari mos kelsa
        // ham harflar bir chiziqda turmasdi — kichik matn yuqoriga qalqib chiqardi
        // (bug hisoboti #35).
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "${(if (d.isDiscount) d.finalPrice else d.originalPrice).formatAmount()} ${uiStrings().currency}",
                style = TextStyle(fontFamily = AppFontFamily, fontSize = 22.sp, fontWeight = FontWeight.Black, color = if (d.isDiscount) accent else palette.ink),
                modifier = Modifier.alignByBaseline(),
            )
            if (d.isDiscount && d.originalPrice > d.finalPrice) {
                Text(
                    d.originalPrice.formatAmount(),
                    style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, color = palette.inkFaint, textDecoration = TextDecoration.LineThrough),
                    modifier = Modifier.alignByBaseline(),
                )
                Box(
                    Modifier.align(Alignment.CenterVertically).clip(RoundedCornerShape(9.dp))
                        .background(accent).padding(horizontal = 9.dp, vertical = 4.dp),
                ) {
                    Text(
                        d.discountBadge ?: "−${d.discountPercent}%",
                        style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White),
                    )
                }
            }
            Text(
                "/ ${d.priceUnit}",
                style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.sp, color = palette.inkFaint),
                modifier = Modifier.alignByBaseline(),
            )
        }
        if (d.isDiscount && d.savedAmount > 0) {
            Text(
                discountsStrings().youSave("${d.savedAmount.formatAmount()} ${uiStrings().currency}"),
                style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.primary),
            )
        }

        // Olish usuli — promo-kod aynan shu javobda keladi.
        DetailSection(discountsStrings().howToGet, palette) {
            val tagText = if (d.tag == DiscountTag.PROMO_CODE) discountsStrings().withPromoCode else discountsStrings().withStudentId
            Text(tagText, style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.ink))
            val promo = d.promoCode
            if (promo != null) {
                Row(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(palette.primary.copy(alpha = 0.10f))
                        .clickable { clipboard.setText(AnnotatedString(promo)); copied = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        if (copied) discountsStrings().copied else promo,
                        style = TextStyle(fontFamily = AppFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Black, color = palette.primary),
                    )
                    if (!copied) Icon(AppIcons.FileText, discountsStrings().copy, tint = palette.primary, modifier = Modifier.size(13.dp))
                }
            }
            d.conditions?.let { Caption(it, palette) }
            d.redemptionUrl?.let { Caption(discountsStrings().link(it), palette) }
            val limits = listOfNotNull(
                d.perUserLimit?.let { discountsStrings().perStudent(it.toString()) },
                d.remainingForUser?.let { discountsStrings().youHaveLeft(it.toString()) },
            ).joinToString(" · ")
            if (limits.isNotBlank()) Caption(limits, palette)
        }

        d.description?.takeIf { it.isNotBlank() }?.let {
            DetailSection(discountsStrings().description, palette) {
                Text(it, style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, color = palette.inkMuted))
            }
        }

        if (d.attributes.isNotEmpty()) {
            DetailSection(discountsStrings().attributes, palette) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    d.attributes.forEach { attr ->
                        Box(
                            Modifier.clip(RoundedCornerShape(10.dp)).background(palette.glass)
                                .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                "${attr.label}: ${attr.value}",
                                style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.5f.sp, fontWeight = FontWeight.Bold, color = palette.inkMuted),
                            )
                        }
                    }
                }
            }
        }

        if (d.branches.isNotEmpty()) {
            // Ro'yxat uzun bo'lishi mumkin (ba'zi tarmoqlarda 20+ filial) — birinchi
            // uchtasi ko'rinadi, qolganlari «Yana ko'rsatish» bilan ochiladi.
            var allBranches by remember(d.id) { mutableStateOf(false) }
            val visible = if (allBranches) d.branches else d.branches.take(BRANCH_PREVIEW)
            DetailSection(discountsStrings().branchesLabel(d.branches.size), palette) {
                visible.forEach { branch ->
                    // Filial bosilsa — xaritada ko'rsatiladi (manzil matni o'zi
                    // "qayerda ekan?" degan savolga javob bermaydi). Xaritani karkasdagi
                    // umumiy ko'ruvchi ochadi — ilovada bitta yo'l.
                    BranchRow(
                        b = branch,
                        palette = palette,
                        onClick = rememberShowOnMap(branch.name, branch.lat, branch.lng),
                    )
                }
                if (!allBranches && d.branches.size > BRANCH_PREVIEW) {
                    Text(
                        discountsStrings().showMore(d.branches.size - BRANCH_PREVIEW),
                        style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.5f.sp, fontWeight = FontWeight.Bold, color = palette.primary),
                        modifier = Modifier.clickable { allBranches = true }.padding(vertical = 4.dp),
                    )
                }
            }
        }

        // `2026-08-07` → `07.08.2026` (bug hisoboti #35: sana ISO ko'rinishida chiqardi).
        val validity = listOfNotNull(formatIsoDate(d.validFrom), formatIsoDate(d.validTo))
            .joinToString(" – ")
        DetailSection(discountsStrings().validUntil, palette) {
            Text(
                validity.ifBlank { discountsStrings().notSpecified },
                style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, color = palette.inkMuted),
            )
            val contacts = listOfNotNull(
                // Raqam ham formatlanadi: `+998941229005` o'qilmaydi (#35).
                d.businessPhone?.let { "☎ ${formatUzPhoneFull(it)}" },
                d.telegram?.let { discountsStrings().telegram(it) },
                d.instagram?.let { discountsStrings().instagram(it) },
                d.website,
            )
            contacts.forEach { Caption(it, palette) }
            if (d.viewsCount > 0) Caption(discountsStrings().viewsCount(d.viewsCount), palette)
        }
    }

}

/**
 * E'lon rasmlari — gorizontal karusel.
 *
 * Bitta rasm bo'lsa u butun kenglikni oladi; bir nechtasi bo'lsa surib ko'riladi va
 * ostida nuqtalar turadi. Ilgari tafsilotda rasm UMUMAN chizilmasdi.
 */
@Composable
private fun DetailImages(images: List<String>, palette: AppPalette) {
    val listState = rememberLazyListState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            flingBehavior = rememberSnapFlingBehavior(listState),
        ) {
            items(images, key = { it }) { url ->
                ScNetworkImage(
                    url = url,
                    modifier = Modifier
                        .fillParentMaxWidth(if (images.size > 1) 0.92f else 1f)
                        .height(IMAGE_HEIGHT),
                    shape = RoundedCornerShape(18.dp),
                )
            }
        }
        if (images.size > 1) {
            val current by remember { derivedStateOf { listState.firstVisibleItemIndex } }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
            ) {
                repeat(images.size) { index ->
                    Box(
                        Modifier.size(if (index == current) 7.dp else 5.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(if (index == current) palette.primary else palette.border),
                    )
                }
            }
        }
    }
}

/** Tafsilotda darrov ko'rsatiladigan filiallar soni; qolgani «Yana ko'rsatish» ostida. */
private const val BRANCH_PREVIEW = 3

/** Filial markerining rangi — brend ko'ki (xarita ustidagi yagona nuqta). */
private const val BRANCH_MARKER_COLOR = "#00AEEF"

/** Karusel rasmining balandligi. */
private val IMAGE_HEIGHT = 210.dp

@Composable
private fun BranchRow(b: OfferBranch, palette: AppPalette, onClick: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(palette.glass)
            .border(1.dp, palette.border, RoundedCornerShape(13.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                b.name,
                style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink),
                modifier = Modifier.weight(1f),
            )
            if (onClick != null) {
                Icon(ScIcons.Map, discountsStrings().onMap, tint = palette.primary, modifier = Modifier.size(15.dp))
            }
        }
        val line = listOfNotNull(
            b.address.takeIf { it.isNotBlank() },
            b.tradeCenterName,
            b.landmark,
            b.distanceMeters?.let { if (it >= 1000) "${it / 1000} km" else "$it m" },
        ).joinToString(" · ")
        if (line.isNotBlank()) {
            Text(line, style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.5f.sp, color = palette.inkFaint))
        }
    }
}

@Composable
private fun DetailSection(title: String, palette: AppPalette, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.5f.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink))
        content()
    }
}

@Composable
private fun Caption(text: String, palette: AppPalette) {
    Text(text, style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.5f.sp, color = palette.inkFaint))
}

@Composable
private fun InfoBanner(text: String, palette: AppPalette) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(palette.glass)
            .border(1.dp, palette.border, RoundedCornerShape(11.dp)).padding(10.dp),
    ) {
        Text(text, style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.5f.sp, color = palette.inkMuted))
    }
}

// "55000" → "55 000"
