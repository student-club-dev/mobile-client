package dev.feature.listings.presentation.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.core.designsystem.components.AppFontFamily
import dev.core.designsystem.components.AppIcons
import dev.core.designsystem.components.OutlineButton
import dev.core.designsystem.components.PrimaryButton
import dev.core.designsystem.theme.AppPalette
import dev.core.designsystem.theme.appPalette
import dev.feature.listings.domain.model.JobCatalog
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import dev.feature.listings.domain.model.Listing
import dev.feature.listings.domain.model.ListingDetails
import dev.feature.listings.domain.model.RentalCatalog
import dev.feature.listings.domain.model.ServiceCatalog
import dev.feature.listings.domain.model.formatSum
import dev.feature.listings.presentation.components.IconSquareButton
import dev.feature.listings.presentation.components.ListingImage
import org.koin.compose.viewmodel.koinViewModel

/**
 * E'lonni to'liq ko'rish ekrani — talaba ro'yxatdan bosganда ochiladi.
 *
 * To'rtala tur bitta ekranда ko'rsatiladi: umumiy qism (rasm, sarlavha, narx, tavsif, manzil)
 * hamma turда bir xil, turga xos qism esa [ListingDetails] bo'yicha tarmoqlanadi. Nega bitta
 * ekran: e'lonning **umumiy** qismi katta va u to'rt marta ko'chirilsa, har bir dizayn
 * o'zgarishi to'rt joyда takrorlanishi kerak bo'lardi.
 */
@Composable
fun ListingDetailScreen(
    listingId: String,
    onBack: () -> Unit,
    onCall: (phone: String) -> Unit,
    vm: ListingDetailViewModel = koinViewModel(),
) {
    val palette = appPalette
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(listingId) { vm.load(listingId) }

    val listing = state.listing

    when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Yuklanmoqda...",
                style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.inkFaint),
            )
        }

        state.notFound || listing == null -> Column(
            Modifier.fillMaxSize().padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🔍", style = TextStyle(fontSize = 40.sp))
            Spacer(Modifier.height(12.dp))
            Text(
                "E'lon topilmadi",
                style = TextStyle(fontFamily = AppFontFamily, fontSize = 17.sp, fontWeight = FontWeight.Black, color = palette.ink),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "E'lon o'chirilgan yoki havola eskirgan bo'lishi mumkin.",
                style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.5f.sp, color = palette.inkMuted, lineHeight = 18.sp),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            OutlineButton("Orqaga", onBack)
        }

        else -> Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                GallerySection(listing, palette, onBack)
                TitleSection(listing, palette)
                PriceSection(listing, palette)
                DetailsSection(listing, palette)
                DescriptionSection(listing, palette)
                BranchesSection(listing, palette)
                Spacer(Modifier.height(20.dp))
            }

            CallBar(listing, palette, onCall)
        }
    }
}

// ---------------------------------------------------------------------------
// 1. Rasm galereyasi
// ---------------------------------------------------------------------------

@Composable
private fun GallerySection(listing: Listing, palette: AppPalette, onBack: () -> Unit) {
    val accent = Color(listing.accent)

    Box(Modifier.fillMaxWidth()) {
        if (listing.images.isEmpty()) {
            // Rasm yo'q — tur rangi va emoji'si bilan o'rin egallaydi, ekran "sinmaydi".
            Box(
                Modifier.fillMaxWidth().height(160.dp).background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(listing.emoji, style = TextStyle(fontSize = 44.sp))
            }
        } else {
            LazyRow(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                itemsIndexed(listing.images) { _, source ->
                    ListingImage(
                        source,
                        Modifier.width(260.dp).height(200.dp).clip(RoundedCornerShape(18.dp)),
                    )
                }
            }
        }

        Box(Modifier.align(Alignment.TopStart).padding(top = 54.dp, start = 16.dp)) {
            IconSquareButton(onBack, AppIcons.ArrowLeft, palette)
        }
    }
}

// ---------------------------------------------------------------------------
// 2. Sarlavha
// ---------------------------------------------------------------------------

@Composable
private fun TitleSection(listing: Listing, palette: AppPalette) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(
            listing.title,
            style = TextStyle(fontFamily = AppFontFamily, fontSize = 20.sp, fontWeight = FontWeight.Black, color = palette.ink),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            DetailPill(listing.categoryLabel, palette, Color(listing.accent))
            DetailPill(listing.kind.label, palette)
        }
    }
}

// ---------------------------------------------------------------------------
// 3. Narx
// ---------------------------------------------------------------------------

@Composable
private fun PriceSection(listing: Listing, palette: AppPalette) {
    val priceMax = listing.priceMax
    // "Kelishilgan holda" bo'lsa raqam umuman ma'nosiz — narx o'rniga shu yozuv chiqadi.
    val priceText = when {
        listing.isNegotiable -> "Kelishilgan holda"
        // Maosh vilkasi ("3–5 mln") — yuqori chegara pastdan katta bo'lsagina oraliq chiqadi.
        priceMax != null && priceMax > listing.price ->
            "${listing.price.formatSum()} — ${priceMax.formatSum()} so'm"

        else -> "${listing.price.formatSum()} so'm"
    } + " / ${listing.priceUnit.suffix}"

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(palette.successBg).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                priceText,
                style = TextStyle(fontFamily = AppFontFamily, fontSize = 22.sp, fontWeight = FontWeight.Black, color = palette.successDeep),
            )
            // Chegirmада asl narx — talaba qancha yutayotganini ko'rishi kerak.
            if (listing.isDiscount && listing.price != listing.finalPrice) {
                Text(
                    "${listing.price.formatSum()} so'm",
                    style = TextStyle(
                        fontFamily = AppFontFamily,
                        fontSize = 13.sp,
                        color = palette.inkFaint,
                        textDecoration = TextDecoration.LineThrough,
                    ),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 4. Turga xos tafsilotlar
// ---------------------------------------------------------------------------

@Composable
private fun DetailsSection(listing: Listing, palette: AppPalette) {
    when (val d = listing.details) {
        is ListingDetails.Rental -> RentalDetails(d, palette)
        is ListingDetails.Service -> ServiceDetails(d, palette)
        is ListingDetails.Job -> JobDetails(d, palette)
        is ListingDetails.Task -> TaskDetails(d, palette)
        is ListingDetails.Discount -> DiscountDetails(d, palette)
    }
}

@Composable
private fun RentalDetails(d: ListingDetails.Rental, palette: AppPalette) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        DetailSection("Turarjoy", palette) {
            DetailRow("Turi", d.propertyType?.label, palette)
            DetailRow(RentalCatalog.roomCountLabel(d.propertyType), d.roomCount?.toString(), palette)
            DetailRow("Hozir yashaydi", d.currentTenants?.let { "$it kishi" }, palette)
            DetailRow("Nechi kishi kerak", d.neededTenants?.let { "$it kishi" }, palette)
            // Talaba uchun asosiy filtr — shuning uchun ajratib ko'rsatiladi.
            DetailRow("Kim uchun", d.gender?.label, palette, accented = true)
            DetailRow("To'lov davri", d.period.label, palette)
            DetailRow("Kommunal narxga kiradi", if (d.utilitiesIncluded) "Ha" else "Yo'q", palette)
            DetailRow("Depozit", d.depositMonths?.let { "$it oylik" }, palette)
            DetailRow("Qavat", d.floor?.let { floor -> d.totalFloors?.let { "$floor / $it" } }, palette)
        }

        val amenities = d.amenities.mapNotNull { RentalCatalog.amenity(it)?.label }
        if (amenities.isNotEmpty()) {
            DetailSection("Qulayliklar", palette) {
                PillFlow(amenities, palette)
            }
        }
    }
}

@Composable
private fun ServiceDetails(d: ListingDetails.Service, palette: AppPalette) {
    val type = d.serviceType
    val subjectKey = d.fields[ServiceCatalog.SUBJECT_KEY]
    // "Boshqa" yo'nalishida katalogда yorliq yo'q — foydalanuvchi yozgan erkin nom olinadi.
    val subject = when {
        type == null || subjectKey == null -> null
        subjectKey == ServiceCatalog.OTHER_SUBJECT_KEY -> d.fields[ServiceCatalog.CUSTOM_SUBJECT_KEY]
        else -> ServiceCatalog.subject(type, subjectKey)?.label
    }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        DetailSection("Xizmat", palette) {
            DetailRow("Soha", type?.label, palette)
            DetailRow(type?.let { ServiceCatalog.subjectLabel(it) } ?: "Yo'nalish", subject, palette, accented = true)
            DetailRow("Ish shakli", d.format.label, palette)
            DetailRow("Tajriba", d.experienceYears?.let { "$it yil" }, palette)
            DetailRow("Qabul vaqti", d.workingHours, palette)
            DetailRow("Mijoz joyiga boradi", if (d.hasHomeVisit) "Ha" else "Yo'q", palette)
            DetailRow("Sinov bepul", if (d.hasFreeTrial) "Ha" else "Yo'q", palette)
        }

        val filled = d.filledFields()
        if (filled.isNotEmpty()) {
            DetailSection("Tafsilotlar", palette) {
                filled.forEach { (label, value) -> DetailRow(label, value, palette) }
            }
        }
    }
}

@Composable
private fun TaskDetails(d: ListingDetails.Task, palette: AppPalette) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        DetailSection("Topshiriq", palette) {
            DetailRow("Yo'nalish", d.category?.label, palette)
            DetailRow("Ish turi", d.typeLabel(), palette, accented = true)
            DetailRow("Hajmi", d.volume, palette)
            DetailRow("Qanday topshiriladi", d.format.label, palette)
            DetailRow("Muddat", taskDeadlineText(d.deadline), palette, accented = true)
        }
    }
}

@Composable
private fun JobDetails(d: ListingDetails.Job, palette: AppPalette) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        DetailSection("Ish sharti", palette) {
            DetailRow("Ish turi", d.employment.label, palette)
            DetailRow("Yo'nalish", JobCatalog.category(d.categoryKey)?.label, palette, accented = true)
            DetailRow("Kompaniya", d.companyName, palette)
            DetailRow("Smena", d.shift?.label, palette)
            DetailRow("Ish vaqti", d.schedule.timeRange(), palette)
            DetailRow("Ish kunlari", d.schedule.daysLabel(), palette)
            DetailRow("Kuniga", d.schedule.hoursPerDay?.let { "$it soat" }, palette)
            DetailRow("Nechta odam kerak", d.vacancies?.let { "$it kishi" }, palette)
            DetailRow("Tajriba", d.experience.label, palette)
            DetailRow("Kimlar uchun", d.gender?.label, palette)
            DetailRow("Yosh", d.ageFrom?.let { from -> d.ageTo?.let { "$from–$it" } }, palette)
            DetailRow("To'lov", d.payPeriod.label, palette)
            DetailRow("To'lov qachon", d.payoutNote, palette)
        }

        if (d.requirements.isNotEmpty()) {
            DetailSection("Talablar", palette) {
                d.requirements.forEach { BulletRow(it, palette) }
            }
        }

        if (d.benefits.isNotEmpty()) {
            DetailSection("Sharoit va imtiyozlar", palette) {
                d.benefits.forEach { BulletRow(it, palette) }
            }
        }
    }
}

@Composable
private fun DiscountDetails(d: ListingDetails.Discount, palette: AppPalette) {
    DetailSection("Chegirma", palette) {
        DetailRow("Biznes", d.businessName, palette)
        DetailRow("Chegirma", d.badge(), palette, accented = true)
        DetailRow("Shartlar", d.conditions, palette)
        DetailRow("Qanday olinadi", d.redemption.method.label, palette)
        DetailRow("Promokod", d.redemption.promoCode, palette, accented = true)
    }
}

// ---------------------------------------------------------------------------
// 5-6. Tavsif va manzil
// ---------------------------------------------------------------------------

@Composable
private fun DescriptionSection(listing: Listing, palette: AppPalette) {
    val description = listing.description?.takeIf { it.isNotBlank() } ?: return
    DetailSection("Tavsif", palette) {
        Text(
            description,
            style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, color = palette.inkMuted, lineHeight = 19.sp),
        )
    }
}

@Composable
private fun BranchesSection(listing: Listing, palette: AppPalette) {
    if (listing.branches.isEmpty()) return
    DetailSection("Manzil", palette) {
        listing.branches.forEachIndexed { index, branch ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Box(
                    Modifier.size(26.dp).clip(RoundedCornerShape(9.dp)).background(palette.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${index + 1}",
                        style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.5f.sp, fontWeight = FontWeight.Black, color = palette.primary),
                    )
                }
                Text(
                    branch.display(),
                    style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.5f.sp, fontWeight = FontWeight.Bold, color = palette.ink),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Pastki panel
// ---------------------------------------------------------------------------

@Composable
private fun CallBar(listing: Listing, palette: AppPalette, onCall: (String) -> Unit) {
    val phone = listing.contactPhone
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!phone.isNullOrBlank()) {
            Text(
                phone,
                style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.inkMuted),
            )
        }
        PrimaryButton(
            "Qo'ng'iroq qilish",
            onClick = { listing.contactPhone?.let(onCall) },
            enabled = !phone.isNullOrBlank(),
        )
    }
}

// ---------------------------------------------------------------------------
// Yordamchi elementlar
// ---------------------------------------------------------------------------

/** Ko'rish ekranining bo'limi — flat, kartasiz (forma bo'limlari bilan bir xil ritmда). */
@Composable
private fun DetailSection(
    title: String,
    palette: AppPalette,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(
            title,
            style = TextStyle(fontFamily = AppFontFamily, fontSize = 15.sp, fontWeight = FontWeight.Black, color = palette.ink),
        )
        content()
    }
}

/**
 * Yorliq — qiymat satri. Qiymat bo'sh bo'lsa satr **umuman chizilmaydi**: e'lon qoralama
 * holatда yarim to'ldirilgan bo'lishi mumkin va bo'sh maydonlar ro'yxatni ma'nosiz uzaytiradi.
 */
@Composable
private fun DetailRow(
    label: String,
    value: String?,
    palette: AppPalette,
    accented: Boolean = false,
) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.sp, color = palette.inkFaint),
        )
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = TextStyle(
                fontFamily = AppFontFamily,
                fontSize = 12.5f.sp,
                fontWeight = if (accented) FontWeight.ExtraBold else FontWeight.Bold,
                color = if (accented) palette.primary else palette.ink,
            ),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun DetailPill(text: String, palette: AppPalette, accent: Color? = null) {
    val color = accent ?: palette.inkMuted
    Box(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PillFlow(items: List<String>, palette: AppPalette) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        items.forEach { DetailPill(it, palette) }
    }
}

@Composable
private fun BulletRow(text: String, palette: AppPalette) {
    Text(
        "• $text",
        style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.5f.sp, color = palette.inkMuted, lineHeight = 18.sp),
    )
}


/** "24-dekabr, 18:00" — detal ekranida to'liq muddat. */
private fun taskDeadlineText(deadline: Long?): String? {
    if (deadline == null) return null
    val at = Instant.fromEpochMilliseconds(deadline).toLocalDateTime(TimeZone.currentSystemDefault())
    val time = "${at.hour.toString().padStart(2, '0')}:${at.minute.toString().padStart(2, '0')}"
    return "${at.date.dayOfMonth}.${at.date.monthNumber}.${at.date.year}, $time"
}
