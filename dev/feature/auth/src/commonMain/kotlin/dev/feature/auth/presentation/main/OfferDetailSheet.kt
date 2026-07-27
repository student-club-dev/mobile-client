package dev.feature.auth.presentation.main

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
import androidx.compose.material3.CircularProgressIndicator
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
import dev.core.uikit.theme.AppPalette
import kotlinx.coroutines.delay

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
    Column(Modifier.fillMaxSize().background(palette.bgBrush)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).scTopInset().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(palette.glass)
                    .border(1.dp, palette.border, RoundedCornerShape(12.dp)).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.ArrowLeft, "Yopish", tint = palette.ink, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.size(12.dp))
            Text(
                "E'lon",
                style = TextStyle(fontFamily = AppFontFamily, fontSize = 20.sp, fontWeight = FontWeight.Black, color = palette.ink),
                modifier = Modifier.weight(1f),
            )
            val d = state.detail
            if (d != null) {
                Icon(
                    AppIcons.Bookmark, "Saqlash",
                    tint = if (saved) palette.primary else palette.inkFaint,
                    modifier = Modifier.size(22.dp).clickable { onToggleSaved(d.id, saved) },
                )
            }
        }

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = palette.primary)
            }
            state.detail != null -> DetailBody(state.detail, palette)
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    state.error ?: "E'lonni yuklab bo'lmadi.",
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
            InfoBanner("Tarmoq yo'q — keshdagi ma'lumot ko'rsatilmoqda.", palette)
        }

        // Narx
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "${(if (d.isDiscount) d.finalPrice else d.originalPrice).spaced()} so'm",
                style = TextStyle(fontFamily = AppFontFamily, fontSize = 22.sp, fontWeight = FontWeight.Black, color = if (d.isDiscount) accent else palette.ink),
            )
            if (d.isDiscount && d.originalPrice > d.finalPrice) {
                Text(
                    d.originalPrice.spaced(),
                    style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, color = palette.inkFaint, textDecoration = TextDecoration.LineThrough),
                )
                Box(Modifier.clip(RoundedCornerShape(9.dp)).background(accent).padding(horizontal = 9.dp, vertical = 4.dp)) {
                    Text(
                        d.discountBadge ?: "−${d.discountPercent}%",
                        style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White),
                    )
                }
            }
            Text("/ ${d.priceUnit}", style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.sp, color = palette.inkFaint))
        }
        if (d.isDiscount && d.savedAmount > 0) {
            Text(
                "${d.savedAmount.spaced()} so'm tejaysiz",
                style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.primary),
            )
        }

        // Olish usuli — promo-kod aynan shu javobda keladi.
        DetailSection("Qanday olinadi", palette) {
            val tagText = if (d.tag == DiscountTag.PROMO_CODE) "Promokod bilan" else "Talaba ID bilan"
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
                        if (copied) "Nusxalandi ✓" else promo,
                        style = TextStyle(fontFamily = AppFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Black, color = palette.primary),
                    )
                    if (!copied) Icon(AppIcons.FileText, "Nusxalash", tint = palette.primary, modifier = Modifier.size(13.dp))
                }
            }
            d.conditions?.let { Caption(it, palette) }
            d.redemptionUrl?.let { Caption("Havola: $it", palette) }
            val limits = listOfNotNull(
                d.perUserLimit?.let { "Har talabaga $it marta" },
                d.remainingForUser?.let { "Sizda $it marta qoldi" },
            ).joinToString(" · ")
            if (limits.isNotBlank()) Caption(limits, palette)
        }

        d.description?.takeIf { it.isNotBlank() }?.let {
            DetailSection("Tavsif", palette) {
                Text(it, style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, color = palette.inkMuted))
            }
        }

        if (d.attributes.isNotEmpty()) {
            DetailSection("Xususiyatlar", palette) {
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
            DetailSection("Filiallar (${d.branches.size})", palette) {
                d.branches.forEach { BranchRow(it, palette) }
            }
        }

        val validity = listOfNotNull(d.validFrom, d.validTo).joinToString(" — ")
        DetailSection("Amal qilish muddati", palette) {
            Text(
                validity.ifBlank { "Ko'rsatilmagan" },
                style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, color = palette.inkMuted),
            )
            val contacts = listOfNotNull(
                d.businessPhone?.let { "☎ $it" },
                d.telegram?.let { "Telegram: $it" },
                d.instagram?.let { "Instagram: $it" },
                d.website,
            )
            contacts.forEach { Caption(it, palette) }
            if (d.viewsCount > 0) Caption("${d.viewsCount} marta ko'rilgan", palette)
        }
    }
}

@Composable
private fun BranchRow(b: OfferBranch, palette: AppPalette) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(palette.glass)
            .border(1.dp, palette.border, RoundedCornerShape(13.dp)).padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(b.name, style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink))
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
private fun Long.spaced(): String = toString().reversed().chunked(3).joinToString(" ").reversed()
