package dev.feature.listings.presentation.detail

import dev.core.uikit.components.ScBackButton
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import dev.core.common.format.formatUzPhoneFull
import dev.core.uikit.components.ScCircleButton
import dev.core.uikit.components.ScGradientButton
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScSoftButton
import dev.core.uikit.components.ScText
import dev.core.uikit.components.scCard
import dev.core.uikit.components.scStyle
import dev.core.uikit.components.scTopInset
import dev.core.uikit.theme.Sc
import dev.core.uikit.components.ScShimmerLine
import dev.core.uikit.components.ScShimmerBox
import dev.feature.listings.domain.model.JobCatalog
import dev.feature.listings.domain.model.Listing
import dev.feature.listings.domain.model.ListingDetails
import dev.feature.listings.domain.model.RentalCatalog
import dev.feature.listings.domain.model.ServiceCatalog
import dev.feature.listings.domain.model.formatSum
import dev.feature.listings.presentation.components.ListingImage
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import dev.feature.listings.presentation.lt
import dev.feature.listings.presentation.currency
import dev.feature.listings.presentation.Lt

/**
 * E'lonni to'liq ko'rish ekrani — talaba ro'yxatdan bosganda ochiladi.
 *
 * To'rtala tur bitta ekranda ko'rsatiladi: umumiy qism (rasm, sarlavha, narx, tavsif, manzil)
 * hamma turda bir xil, turga xos qism esa [ListingDetails] bo'yicha tarmoqlanadi. Nega bitta
 * ekran: e'lonning **umumiy** qismi katta va u to'rt marta ko'chirilsa, har bir dizayn
 * o'zgarishi to'rt joyda takrorlanishi kerak bo'lardi.
 */
@Composable
fun ListingDetailScreen(
    listingId: String,
    onBack: () -> Unit,
    onCall: (phone: String) -> Unit,
    vm: ListingDetailViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(listingId) { vm.load(listingId) }

    val listing = state.listing

    when {
        // Skelet faqat KO'RSATADIGAN HECH NARSA bo'lmaganda. Keshdagi nusxa bo'lsa u
        // darrov chiziladi va serverdan to'liq varianti (telefon raqami, ko'rishlar soni)
        // ustiga tushadi — tayyor sahifani skeletga qaytarish orqaga qadam bo'lardi.
        state.loading && listing == null -> Column(
            Modifier.fillMaxSize().background(Sc.Bg),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScShimmerBox(Modifier.fillMaxWidth().height(260.dp), RoundedCornerShape(0.dp))
            Column(
                Modifier.padding(horizontal = Sc.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ScShimmerLine(0.7f, 18.dp)
                ScShimmerLine(0.35f, 15.dp)
                ScShimmerLine(0.9f, 11.dp)
                ScShimmerLine(0.8f, 11.dp)
                ScShimmerLine(0.5f, 11.dp)
            }
        }

        state.notFound || listing == null -> {
            // Ikki sabab, ikki javob: e'lon YO'Q (o'chirilgan, muddati o'tgan yoki sizga
            // ko'rinmaydi — server ataylab `404` beradi) yoki shunchaki YETIB BORMADI.
            // Birinchisida qayta urinish ma'nosiz, ikkinchisida — yagona to'g'ri harakat.
            val networkError = state.error
            Column(
                Modifier.fillMaxSize().background(Sc.Bg).padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(if (networkError != null) "📡" else "🔍", style = TextStyle(fontSize = 40.sp))
                Spacer(Modifier.height(12.dp))
                ScText(
                    if (networkError != null) lt("Yuklab bo'lmadi") else lt("E'lon topilmadi"),
                    19f, FontWeight.ExtraBold, Sc.Ink,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    networkError ?: lt("E'lon o'chirilgan yoki havola eskirgan bo'lishi mumkin."),
                    style = scStyle(13.5f, FontWeight.Medium, Sc.Muted, lineHeight = 20f)
                        .copy(textAlign = TextAlign.Center),
                )
                Spacer(Modifier.height(20.dp))
                if (networkError != null) {
                    ScSoftButton(lt("Qayta urinish"), vm::retry, Modifier.width(180.dp))
                    Spacer(Modifier.height(10.dp))
                }
                ScSoftButton(lt("Orqaga"), onBack, Modifier.width(180.dp))
            }
        }

        else -> Column(Modifier.fillMaxSize().background(Sc.Bg)) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                GallerySection(listing, onBack)
                TitleSection(listing)
                PriceSection(listing)
                DetailsSection(listing)
                DescriptionSection(listing)
                BranchesSection(listing)
                Spacer(Modifier.height(12.dp))
            }

            CallBar(listing, onCall)
        }
    }
}

// ---------------------------------------------------------------------------
// 1. Rasm galereyasi
// ---------------------------------------------------------------------------

@Composable
private fun GallerySection(listing: Listing, onBack: () -> Unit) {
    val accent = Color(listing.accent)

    Box(Modifier.fillMaxWidth()) {
        if (listing.images.isEmpty()) {
            // Rasm yo'q — tur rangi va emoji'si bilan o'rin egallaydi, ekran "sinmaydi".
            Box(
                Modifier.fillMaxWidth().height(190.dp).background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(listing.emoji, style = TextStyle(fontSize = 44.sp))
            }
        } else {
            LazyRow(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Sc.ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(listing.images) { _, source ->
                    ListingImage(
                        source,
                        Modifier.width(260.dp).height(200.dp).clip(RoundedCornerShape(22.dp)),
                    )
                }
            }
        }

        Box(Modifier.align(Alignment.TopStart).scTopInset().padding(start = Sc.ScreenPadding)) {
            ScBackButton(onBack, contentDescription = lt("Orqaga"))
        }
    }
}

// ---------------------------------------------------------------------------
// 2. Sarlavha
// ---------------------------------------------------------------------------

@Composable
private fun TitleSection(listing: Listing) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Sc.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        ScText(listing.title, 21f, FontWeight.ExtraBold, Sc.Ink, letterSpacing = -0.4f)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            DetailPill(listing.categoryLabel, Color(listing.accent))
            DetailPill(listing.kind.label)
        }
    }
}

// ---------------------------------------------------------------------------
// 3. Narx
// ---------------------------------------------------------------------------

@Composable
private fun PriceSection(listing: Listing) {
    val priceMax = listing.priceMax
    // "Kelishilgan holda" bo'lsa raqam umuman ma'nosiz — narx o'rniga shu yozuv chiqadi.
    val priceText = when {
        listing.isNegotiable -> lt("Kelishilgan holda")
        // Maosh vilkasi ("3–5 mln") — yuqori chegara pastdan katta bo'lsagina oraliq chiqadi.
        priceMax != null && priceMax > listing.price ->
            "${listing.price.formatSum()} — ${priceMax.formatSum()} ${currency()}"

        else -> "${listing.price.formatSum()} ${currency()}"
    } + " / ${listing.priceUnit.suffix}"

    Column(Modifier.fillMaxWidth().padding(horizontal = Sc.ScreenPadding)) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Sc.TintGreen)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ScText(priceText, 22f, FontWeight.ExtraBold, Sc.Success, letterSpacing = -0.4f)
            // Chegirmada asl narx — talaba qancha yutayotganini ko'rishi kerak.
            if (listing.isDiscount && listing.price != listing.finalPrice) {
                Text(
                    "${listing.price.formatSum()} ${currency()}",
                    style = scStyle(13.5f, FontWeight.Medium, Sc.Muted)
                        .copy(textDecoration = TextDecoration.LineThrough),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 4. Turga xos tafsilotlar
// ---------------------------------------------------------------------------

@Composable
private fun DetailsSection(listing: Listing) {
    when (val d = listing.details) {
        is ListingDetails.Rental -> RentalDetails(d)
        is ListingDetails.Service -> ServiceDetails(d)
        is ListingDetails.Job -> JobDetails(d)
        is ListingDetails.Task -> TaskDetails(d)
        is ListingDetails.Discount -> DiscountDetails(d)
    }
}

@Composable
private fun RentalDetails(d: ListingDetails.Rental) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DetailSection(lt("Turarjoy")) {
            DetailRow(lt("Turi"), d.propertyType?.label)
            DetailRow(RentalCatalog.roomCountLabel(d.propertyType), d.roomCount?.toString())
            DetailRow(lt("Hozir yashaydi"), d.currentTenants?.let { Lt.people(it) })
            DetailRow(lt("Nechi kishi kerak"), d.neededTenants?.let { Lt.people(it) })
            // Talaba uchun asosiy filtr — shuning uchun ajratib ko'rsatiladi.
            DetailRow(lt("Kim uchun"), d.gender?.label, accented = true)
            DetailRow(lt("To'lov davri"), d.period.label)
            DetailRow(lt("Kommunal narxga kiradi"), if (d.utilitiesIncluded) lt("Ha") else lt("Yo'q"))
            DetailRow(lt("Depozit"), d.depositMonths?.let { Lt.months(it) })
            DetailRow(lt("Qavat"), d.floor?.let { floor -> d.totalFloors?.let { "$floor / $it" } })
        }

        val amenities = d.amenities.mapNotNull { RentalCatalog.amenity(it)?.label }
        if (amenities.isNotEmpty()) {
            DetailSection(lt("Qulayliklar")) {
                PillFlow(amenities)
            }
        }
    }
}

@Composable
private fun ServiceDetails(d: ListingDetails.Service) {
    val type = d.serviceType
    val subjectKey = d.fields[ServiceCatalog.SUBJECT_KEY]
    // "Boshqa" yo'nalishida katalogda yorliq yo'q — foydalanuvchi yozgan erkin nom olinadi.
    val subject = when {
        type == null || subjectKey == null -> null
        subjectKey == ServiceCatalog.OTHER_SUBJECT_KEY -> d.fields[ServiceCatalog.CUSTOM_SUBJECT_KEY]
        else -> ServiceCatalog.subject(type, subjectKey)?.label
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DetailSection(lt("Xizmat")) {
            DetailRow(lt("Soha"), type?.label)
            DetailRow(type?.let { ServiceCatalog.subjectLabel(it) } ?: lt("Yo'nalish"), subject, accented = true)
            DetailRow(lt("Ish shakli"), d.format.label)
            DetailRow(lt("Tajriba"), d.experienceYears?.let { Lt.years(it) })
            DetailRow(lt("Qabul vaqti"), d.workingHours)
            DetailRow(lt("Mijoz joyiga boradi"), if (d.hasHomeVisit) lt("Ha") else lt("Yo'q"))
            DetailRow(lt("Sinov bepul"), if (d.hasFreeTrial) lt("Ha") else lt("Yo'q"))
        }

        val filled = d.filledFields()
        if (filled.isNotEmpty()) {
            DetailSection(lt("Tafsilotlar")) {
                filled.forEach { (label, value) -> DetailRow(label, value) }
            }
        }
    }
}

@Composable
private fun TaskDetails(d: ListingDetails.Task) {
    DetailSection(lt("Topshiriq")) {
        DetailRow(lt("Yo'nalish"), d.category?.label)
        DetailRow(lt("Ish turi"), d.typeLabel(), accented = true)
        DetailRow(lt("Hajmi"), d.volume)
        DetailRow(lt("Qanday topshiriladi"), d.format.label)
        DetailRow(lt("Muddat"), taskDeadlineText(d.deadline), accented = true)
    }
}

@Composable
private fun JobDetails(d: ListingDetails.Job) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DetailSection(lt("Ish sharti")) {
            DetailRow(lt("Ish turi"), d.employment.label)
            DetailRow(lt("Yo'nalish"), JobCatalog.category(d.categoryKey)?.label, accented = true)
            DetailRow(lt("Kompaniya"), d.companyName)
            DetailRow(lt("Smena"), d.shift?.label)
            DetailRow(lt("Ish vaqti"), d.schedule.timeRange())
            DetailRow(lt("Ish kunlari"), d.schedule.daysLabel())
            DetailRow(lt("Kuniga"), d.schedule.hoursPerDay?.let { Lt.hours(it) })
            DetailRow(lt("Nechta odam kerak"), d.vacancies?.let { Lt.people(it) })
            DetailRow(lt("Tajriba"), d.experience.label)
            DetailRow(lt("Kimlar uchun"), d.gender?.label)
            DetailRow(lt("Yosh"), d.ageFrom?.let { from -> d.ageTo?.let { "$from–$it" } })
            DetailRow(lt("To'lov"), d.payPeriod.label)
            DetailRow(lt("To'lov qachon"), d.payoutNote)
        }

        if (d.requirements.isNotEmpty()) {
            DetailSection(lt("Talablar")) {
                d.requirements.forEach { BulletRow(it) }
            }
        }

        if (d.benefits.isNotEmpty()) {
            DetailSection(lt("Sharoit va imtiyozlar")) {
                d.benefits.forEach { BulletRow(it) }
            }
        }
    }
}

@Composable
private fun DiscountDetails(d: ListingDetails.Discount) {
    DetailSection(lt("Chegirma")) {
        DetailRow(lt("Biznes"), d.businessName)
        DetailRow(lt("Chegirma"), d.badge(), accented = true)
        DetailRow(lt("Shartlar"), d.conditions)
        DetailRow(lt("Qanday olinadi"), d.redemption.method.label)
        DetailRow("Promokod", d.redemption.promoCode, accented = true)
    }
}

// ---------------------------------------------------------------------------
// 5-6. Tavsif va manzil
// ---------------------------------------------------------------------------

@Composable
private fun DescriptionSection(listing: Listing) {
    val description = listing.description?.takeIf { it.isNotBlank() } ?: return
    DetailSection(lt("Tavsif")) {
        ScText(description, 13.5f, FontWeight.Medium, Sc.InkSoft, lineHeight = 20f)
    }
}

@Composable
private fun BranchesSection(listing: Listing) {
    if (listing.branches.isEmpty()) return
    DetailSection(lt("Manzil")) {
        listing.branches.forEachIndexed { index, branch ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier.size(27.dp).clip(RoundedCornerShape(10.dp)).background(Sc.TintBlue),
                    contentAlignment = Alignment.Center,
                ) {
                    ScText("${index + 1}", 12f, FontWeight.ExtraBold, Sc.Brand, maxLines = 1)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ScText(branch.display(), 13f, FontWeight.Bold, Sc.Ink)
                    // Mo'ljal (eng yaqin metro) — manzilning o'zi emas, shuning uchun
                    // ikkinchi qator va o'chiqroq rangda.
                    branch.landmark?.takeIf { it.isNotBlank() }?.let {
                        ScText(it, 12f, FontWeight.Medium, Sc.InkSoft)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Pastki panel
// ---------------------------------------------------------------------------

@Composable
private fun CallBar(listing: Listing, onCall: (String) -> Unit) {
    val phone = listing.contactPhone
    Column(
        Modifier.fillMaxWidth()
            .background(Sc.Card)
            .navigationBarsPadding()
            .padding(horizontal = Sc.ScreenPadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!phone.isNullOrBlank()) {
            // Ko'rsatishda qolip, qo'ng'iroqqa esa xom raqam ketadi (`tel:` sxemasi).
            ScText(formatUzPhoneFull(phone), 12.5f, FontWeight.Bold, Sc.Muted, maxLines = 1)
        }
        if (phone.isNullOrBlank()) {
            ScSoftButton(lt("Telefon ko'rsatilmagan"), onClick = {})
        } else {
            ScGradientButton(lt("Qo'ng'iroq qilish"), onClick = { onCall(phone) })
        }
    }
}

// ---------------------------------------------------------------------------
// Yordamchi elementlar
// ---------------------------------------------------------------------------

/** Ko'rish ekranining bo'limi — oq karta, ichida sarlavha va qatorlar. */
@Composable
private fun DetailSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Sc.ScreenPadding)) {
        Column(
            Modifier.fillMaxWidth().scCard(radius = 22.dp).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ScText(title, 16f, FontWeight.ExtraBold, Sc.Ink, letterSpacing = -0.2f)
            content()
        }
    }
}

/**
 * Yorliq — qiymat satri. Qiymat bo'sh bo'lsa satr **umuman chizilmaydi**: e'lon qoralama
 * holatda yarim to'ldirilgan bo'lishi mumkin va bo'sh maydonlar ro'yxatni ma'nosiz uzaytiradi.
 */
@Composable
private fun DetailRow(
    label: String,
    value: String?,
    accented: Boolean = false,
) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        ScText(label, 12.5f, FontWeight.Medium, Sc.Muted)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = scStyle(
                13f,
                if (accented) FontWeight.ExtraBold else FontWeight.Bold,
                if (accented) Sc.Brand else Sc.Ink,
            ).copy(textAlign = TextAlign.End),
        )
    }
}

@Composable
private fun DetailPill(text: String, accent: Color? = null) {
    val color = accent ?: Sc.InkSoft
    Box(
        Modifier.clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        ScText(text, 11.5f, FontWeight.Bold, color, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PillFlow(items: List<String>) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        items.forEach { DetailPill(it) }
    }
}

@Composable
private fun BulletRow(text: String) {
    ScText("• $text", 13f, FontWeight.Medium, Sc.InkSoft, lineHeight = 19f)
}

/** "24-dekabr, 18:00" — detal ekranida to'liq muddat. */
private fun taskDeadlineText(deadline: Long?): String? {
    if (deadline == null) return null
    val at = Instant.fromEpochMilliseconds(deadline).toLocalDateTime(TimeZone.currentSystemDefault())
    val time = "${at.hour.toString().padStart(2, '0')}:${at.minute.toString().padStart(2, '0')}"
    return "${at.date.dayOfMonth}.${at.date.monthNumber}.${at.date.year}, $time"
}
