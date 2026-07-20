package dev.feature.listings.presentation.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.core.designsystem.components.AppFontFamily
import dev.core.designsystem.theme.AppPalette
import dev.feature.listings.domain.model.ExperienceLevel
import dev.feature.listings.domain.model.JobCatalog
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import dev.feature.listings.domain.model.Listing
import dev.feature.listings.domain.model.ListingDetails
import dev.feature.listings.domain.model.RentalCatalog
import dev.feature.listings.domain.model.ServiceCatalog
import dev.feature.listings.domain.model.formatSum
import dev.feature.listings.presentation.components.ListingImage

/**
 * Talabaga ko'rinadigan e'lon kartochkasi — turini o'zi aniqlaydi.
 *
 * Nega bitta kirish nuqtasi: ro'yxat ekranlari (izlash, filtr natijasi, "siz uchun") aralash
 * e'lonlarni ko'rsatadi va har bir ekranda `when (details)` ni takrorlash — bir xil mantiqni
 * to'rt joyda saqlash demak. Tur qo'shilsa faqat shu fayl o'zgaradi.
 *
 * [distanceLabel] va [branchLabel] tashqaridan keladi, chunki masofa talabaning joylashuviga
 * bog'liq va uni kartochka o'zi bilmaydi ([Listing.nearestBranch] ga qarang).
 */
@Composable
fun ListingCard(
    listing: Listing,
    distanceLabel: String?,
    branchLabel: String?,
    palette: AppPalette,
    onClick: () -> Unit,
) {
    when (val d = listing.details) {
        is ListingDetails.Rental -> RentalCard(listing, d, distanceLabel, branchLabel, palette, onClick)
        is ListingDetails.Service -> ServiceCard(listing, d, distanceLabel, branchLabel, palette, onClick)
        is ListingDetails.Job -> JobCard(listing, d, distanceLabel, branchLabel, palette, onClick)
        is ListingDetails.Task -> TaskCard(listing, d, distanceLabel, branchLabel, palette, onClick)
        is ListingDetails.Discount -> DiscountBrowseCard(listing, d, distanceLabel, branchLabel, palette, onClick)
    }
}

// ---------------------------------------------------------------------------
// Ijara
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RentalCard(
    listing: Listing,
    details: ListingDetails.Rental,
    distanceLabel: String?,
    branchLabel: String?,
    palette: AppPalette,
    onClick: () -> Unit,
) {
    val accent = Color(listing.accent)

    Row(
        cardModifier(palette, onClick),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Cover(listing)

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            CardTitle(listing.title, palette)

            // Raqamlar ijarada asosiy narsa: talaba avval "nechi kishi kerak" ni qidiradi,
            // shuning uchun ular matn satri emas, alohida ko'rinadigan pilllar.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                details.roomCount?.let { StatPill("$it xona", palette) }
                details.currentTenants?.let { StatPill("$it kishi bor", palette) }
                details.neededTenants?.let { StatPill("$it kishi kerak", palette) }
                // Jins — talaba uchun birinchi filtr, shuning uchun rangi bilan ajratilgan.
                details.gender?.let { AccentPill("${it.emoji} ${it.label}", accent) }
            }

            PriceLine(priceText(listing, details.period.priceUnit.suffix), palette)

            val amenities = details.amenities.mapNotNull { RentalCatalog.amenity(it)?.label }
            if (amenities.isNotEmpty()) {
                val shown = amenities.take(3).joinToString(" · ")
                val extra = amenities.size - 3
                FaintLine(if (extra > 0) "$shown · +$extra" else shown, palette)
            }

            MetaLine(distanceLabel, branchLabel, palette)
        }
    }
}

// ---------------------------------------------------------------------------
// Xizmatlar
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServiceCard(
    listing: Listing,
    details: ListingDetails.Service,
    distanceLabel: String?,
    branchLabel: String?,
    palette: AppPalette,
    onClick: () -> Unit,
) {
    val accent = Color(listing.accent)

    Row(
        cardModifier(palette, onClick),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Cover(listing)

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            CardTitle(listing.title, palette)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                details.serviceType?.let { StatPill(it.label, palette) }
                subjectLabel(details)?.let { StatPill(it, palette) }
                // Sohaga xos maydonlar ko'p bo'lishi mumkin — kartochkada faqat birinchi
                // ikkitasi, qolganini e'lon sahifasida ko'radi.
                details.filledFields().take(2).forEach { (label, value) ->
                    StatPill("$label: $value", palette)
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                StatPill(details.format.label, palette)
                if (details.hasFreeTrial) AccentPill("Sinov bepul", accent)
                if (details.hasHomeVisit) StatPill("Mijoz joyiga boradi", palette)
            }

            PriceLine(priceText(listing, listing.priceUnit.suffix), palette)

            MetaLine(distanceLabel, branchLabel, palette)
        }
    }
}

/** Yo'nalish nomi: "Boshqa" tanlangan bo'lsa katalogda emas, foydalanuvchi yozgan matnda. */
private fun subjectLabel(details: ListingDetails.Service): String? {
    val type = details.serviceType ?: return null
    val key = details.fields[ServiceCatalog.SUBJECT_KEY]?.takeIf { it.isNotBlank() } ?: return null
    if (key == ServiceCatalog.OTHER_SUBJECT_KEY) {
        return details.fields[ServiceCatalog.CUSTOM_SUBJECT_KEY]?.takeIf { it.isNotBlank() }
    }
    return ServiceCatalog.subject(type, key)?.label
}

// ---------------------------------------------------------------------------
// Ish e'loni
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskCard(
    listing: Listing,
    details: ListingDetails.Task,
    distanceLabel: String?,
    branchLabel: String?,
    palette: AppPalette,
    onClick: () -> Unit,
) {
    val accent = Color(listing.accent)

    Row(cardModifier(palette, onClick), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Cover(listing)

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            CardTitle(listing.title, palette)

            details.category?.let { category ->
                Text(
                    "${category.emoji} ${category.label}",
                    style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.5f.sp, fontWeight = FontWeight.Bold, color = palette.inkMuted),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                AccentPill(details.typeLabel(), accent)
                details.volume?.takeIf { it.isNotBlank() }?.let { StatPill(it, palette) }
                StatPill(details.format.label, palette)
                // Muddat — bajaruvchi uchun eng muhim ma'lumot, ajratib ko'rsatiladi.
                deadlineLabel(details.deadline)?.let { AccentPill(it, accent) }
            }

            PriceLine(priceText(listing, null), palette)

            MetaLine(distanceLabel, branchLabel, palette)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JobCard(
    listing: Listing,
    details: ListingDetails.Job,
    distanceLabel: String?,
    branchLabel: String?,
    palette: AppPalette,
    onClick: () -> Unit,
) {
    val accent = Color(listing.accent)

    Row(
        cardModifier(palette, onClick),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Cover(listing)

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            CardTitle(listing.title, palette)

            val company = details.companyName.takeIf { it.isNotBlank() }
                ?: JobCatalog.category(details.categoryKey)?.label
            if (company != null) {
                Text(
                    company,
                    style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.5f.sp, fontWeight = FontWeight.Bold, color = palette.inkMuted),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                AccentPill(details.employment.label, accent)
                details.shift?.let { StatPill(it.label, palette) }
                details.schedule.timeRange()?.let { StatPill(it, palette) }
                details.schedule.daysLabel()?.let { StatPill(it, palette) }
                details.vacancies?.let { StatPill("$it kishi kerak", palette) }
                // Talaba uchun eng kuchli sotuv argumenti — ko'rinib turishi kerak.
                if (details.experience == ExperienceLevel.NONE) {
                    AccentPill(ExperienceLevel.NONE.label, accent)
                }
            }

            PriceLine(priceText(listing, details.payPeriod.suffix), palette)

            if (details.benefits.isNotEmpty()) {
                FaintLine(details.benefits.take(2).joinToString(" · "), palette)
            }

            MetaLine(distanceLabel, branchLabel, palette)
        }
    }
}

// ---------------------------------------------------------------------------
// Chegirma
// ---------------------------------------------------------------------------

@Composable
private fun DiscountBrowseCard(
    listing: Listing,
    details: ListingDetails.Discount,
    distanceLabel: String?,
    branchLabel: String?,
    palette: AppPalette,
    onClick: () -> Unit,
) {
    val accent = Color(listing.accent)

    Row(
        cardModifier(palette, onClick),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Cover(listing)

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val name = details.businessName.takeIf { it.isNotBlank() }
            CardTitle(if (name != null) "$name — ${listing.title}" else listing.title, palette)

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "${listing.finalPrice.formatSum()} so'm",
                    style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Black, color = palette.successDeep),
                )
                // Chizilgan asl narx faqat chegirma bo'lganda ma'noli.
                if (listing.price != listing.finalPrice) {
                    Text(
                        listing.price.formatSum(),
                        style = TextStyle(
                            fontFamily = AppFontFamily,
                            fontSize = 11.sp,
                            color = palette.inkFaint,
                            textDecoration = TextDecoration.LineThrough,
                        ),
                    )
                }
            }

            MetaLine(distanceLabel, branchLabel, palette)
        }

        // Chegirmasiz oddiy e'londa badge() `null` — yorliq umuman chizilmaydi.
        val badge = details.badge()
        if (badge != null) {
            Box(
                Modifier.clip(RoundedCornerShape(10.dp)).background(accent).padding(horizontal = 9.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    badge,
                    style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Umumiy qismlar
// ---------------------------------------------------------------------------

private fun cardModifier(palette: AppPalette, onClick: () -> Unit): Modifier =
    Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(18.dp))
        .background(palette.glass)
        .border(1.dp, palette.border, RoundedCornerShape(18.dp))
        .clickable(onClick = onClick)
        .padding(12.dp)

@Composable
private fun Cover(listing: Listing) {
    Box(
        Modifier.size(64.dp).clip(RoundedCornerShape(14.dp))
            .background(Color(listing.accent).copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        val cover = listing.images.firstOrNull()
        if (cover != null) {
            ListingImage(cover, Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)))
        } else {
            Text(listing.emoji, style = TextStyle(fontSize = 24.sp))
        }
    }
}

@Composable
private fun CardTitle(text: String, palette: AppPalette) {
    Text(
        text,
        style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.5f.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun PriceLine(text: String, palette: AppPalette) {
    Text(
        text,
        style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Black, color = palette.successDeep),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun FaintLine(text: String, palette: AppPalette) {
    Text(
        text,
        style = TextStyle(fontFamily = AppFontFamily, fontSize = 10.5f.sp, color = palette.inkFaint),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** "📍 640 m · Chilonzor 9-kvartal" — talabaning asosiy savoli: qayerda va qancha uzoq. */
@Composable
private fun MetaLine(distanceLabel: String?, branchLabel: String?, palette: AppPalette) {
    val meta = listOfNotNull(
        distanceLabel?.let { "📍 $it" },
        branchLabel?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    if (meta.isNotBlank()) FaintLine(meta, palette)
}

@Composable
private fun StatPill(text: String, palette: AppPalette) {
    Box(
        Modifier.clip(RoundedCornerShape(9.dp)).background(palette.fieldBg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            style = TextStyle(fontFamily = AppFontFamily, fontSize = 10.5f.sp, fontWeight = FontWeight.Bold, color = palette.inkMuted),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** [StatPill] bilan bir xil, lekin e'lon rangida — eng muhim bitta-ikkita belgi uchun. */
@Composable
private fun AccentPill(text: String, accent: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(9.dp)).background(accent.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            style = TextStyle(fontFamily = AppFontFamily, fontSize = 10.5f.sp, fontWeight = FontWeight.Bold, color = accent),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Narx satri: "2 500 000 — 3 000 000 so'm / oy" yoki "Kelishilgan".
 *
 * Kelishilgan narxda raqam umuman ko'rsatilmaydi — aks holda `price` dagi qoralama qiymati
 * haqiqiy narxdek ko'rinib qolardi.
 */
private fun priceText(listing: Listing, unitSuffix: String?): String {
    if (listing.isNegotiable) return "Kelishilgan"

    val max = listing.priceMax
    val amount = if (max != null && max > listing.price) {
        "${listing.price.formatSum()} — ${max.formatSum()} so'm"
    } else {
        "${listing.price.formatSum()} so'm"
    }
    return if (unitSuffix.isNullOrBlank()) amount else "$amount / $unitSuffix"
}


/**
 * Muddat yorlig'i: "Bugun 18:00", "Ertaga 12:00" yoki "24-dekabr". Bajaruvchi uchun eng
 * muhim ma'lumot — shuning uchun kartochkada ajratib ko'rsatiladi.
 */
private fun deadlineLabel(deadline: Long?): String? {
    if (deadline == null) return null
    val zone = TimeZone.currentSystemDefault()
    val at = Instant.fromEpochMilliseconds(deadline).toLocalDateTime(zone)
    val today = Clock.System.now().toLocalDateTime(zone).date
    val days = at.date.toEpochDays() - today.toEpochDays()
    val time = "${at.hour.toString().padStart(2, '0')}:${at.minute.toString().padStart(2, '0')}"
    return when (days) {
        0 -> "Bugun $time"
        1 -> "Ertaga $time"
        in 2..13 -> "$days kundan keyin"
        else -> "${at.date.dayOfMonth}.${at.date.monthNumber}"
    }
}
