package dev.feature.home.presentation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.core.uikit.components.ScGlyph
import dev.core.uikit.components.ScGradientButton
import dev.core.uikit.components.ScHeader
import dev.core.uikit.components.ScIconTile
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScMonogramTile
import dev.core.uikit.components.ScSectionHeader
import dev.core.uikit.components.ScSoftButton
import dev.core.uikit.components.ScText
import dev.core.uikit.components.scCard
import dev.core.uikit.components.scSoftShadow
import dev.core.uikit.components.scStyle
import dev.core.uikit.theme.Sc
import dev.core.domain.model.DiscountOffer
import dev.core.domain.model.DiscountTag
import dev.feature.clubs.domain.model.Club
import dev.feature.listings.domain.model.Listing
import dev.feature.listings.domain.model.formatSum
import dev.feature.students.domain.model.FriendStatus
import dev.feature.students.domain.model.Student
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.runtime.ReadOnlyComposable

/** Dizayndagi `cubic-bezier(.4,0,.2,1)` — topbar kichrayishi shu egri bilan. */
private val ScEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

/** Shu masofadan ortiq scroll qilinganda topbar siqiladi (maketda `scrollTop > 36`). */
private val CondenseThreshold = 36.dp

@Composable
fun HomeScreen(
    onOpenProfile: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenClubs: () -> Unit = {},
    onOpenDiscounts: () -> Unit = {},
    onOpenJobs: () -> Unit = {},
    onOpenRentals: () -> Unit = {},
    onOpenTasks: () -> Unit = {},
    onOpenListing: (String) -> Unit = {},
    onOpenStudents: () -> Unit = {},
    vm: HomeViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    // `scroll.value` — piksel, chegara esa dp: to'g'ridan-to'g'ri solishtirsak zich
    // ekranlarda topbar 12dp scroll'dayoq siqilib ketardi.
    val thresholdPx = with(LocalDensity.current) { CondenseThreshold.roundToPx() }
    val condensed = scroll.value > thresholdPx
    val p by animateFloatAsState(
        if (condensed) 1f else 0f, tween(300, easing = ScEasing), label = "condense"
    )

    Column(Modifier.fillMaxSize().background(Sc.Bg)) {
        HomeHeader(state, p, onOpenProfile, onOpenChat, onOpenNotifications)
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll).padding(top = 22.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            OfferSection("Ovqatlar", "Kafe, restoran va oziq-ovqat", state.foodOffers, onOpenDiscounts)
            OfferSection("Kiyim-kechak", "Talabalar uchun chegirmalar", state.clothingOffers, onOpenDiscounts)
            OfferSection("Dam olish", "Barcha o'yin klublari va kino", state.leisureOffers, onOpenDiscounts)
            TasksSection(state.tasks, onOpenTasks, onOpenListing)
            ClubsSection(state.clubs, onOpenClubs)
            RentalsSection(state.rentals, onOpenRentals, onOpenListing)
            JobsSection(state.jobs, onOpenJobs, onOpenListing)
            StudentsSection(state.students, onOpenStudents) { vm.toggleFriend(it) }
            // Pastki navigatsiya + FAB uchun joy.
            Spacer(Modifier.height(96.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Topbar — scroll'da kichrayadi
// ---------------------------------------------------------------------------

/**
 * Gradient topbar. [p] — siqilish darajasi (0 = to'liq, 1 = siqilgan):
 * salomlashish, universitet chipi va chat tugmasi yo'qoladi, avatar 54→40 ga
 * kichrayadi, ism 20→16 sp bo'lib markazga suriladi.
 */
@Composable
private fun HomeHeader(
    state: HomeUiState,
    p: Float,
    onOpenProfile: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenNotifications: () -> Unit,
) {
    val fade = 1f - p
    ScHeader(
        bottomRadius = lerp(36.dp, 26.dp, p),
        bottomPadding = lerp(28.dp, 12.dp, p),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = lerp(22.dp, 6.dp, p)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            val avatarSize = lerp(54.dp, 40.dp, p)
            Box(
                Modifier.size(avatarSize).clip(RoundedCornerShape(lerp(20.dp, 14.dp, p)))
                    .background(Color.White.copy(alpha = 0.22f)).clickable(onClick = onOpenProfile),
                contentAlignment = Alignment.Center,
            ) {
                // Profil rasmi bo'lsa — o'sha; bo'lmasa ismning bosh harfi.
                if (!state.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = state.avatarUrl,
                        contentDescription = "Profil rasmi",
                        modifier = Modifier.size(avatarSize),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    ScText(
                        state.userName.take(1).uppercase(),
                        size = 22f - 5f * p,
                        weight = FontWeight.ExtraBold,
                        color = Color.White,
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                // "Assalomu alaykum 👋" — siqilganda balandligi 0 ga tushadi.
                CollapsingRow(p, fullHeight = 18.dp) {
                    ScText(
                        "Assalomu alaykum 👋",
                        13f,
                        FontWeight.Medium,
                        Color.White.copy(alpha = 0.85f),
                        maxLines = 1
                    )
                }
                // Siqilganda ism markazga suriladi (dizayndagi `.cond .sc-name`).
                Text(
                    state.userName,
                    style = scStyle(
                        20f - 4f * p,
                        FontWeight.ExtraBold,
                        Color.White,
                        lineHeight = 26f,
                        letterSpacing = -0.3f
                    ).copy(textAlign = if (p > 0.5f) TextAlign.Center else TextAlign.Start),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                )
                val badge =
                    listOfNotNull(state.universityMonogram, state.courseLabel).joinToString(" · ")
                if (badge.isNotBlank()) {
                    CollapsingRow(p, fullHeight = 30.dp) {
                        Row(
                            Modifier.padding(top = 7.dp).clip(RoundedCornerShape(20.dp))
                                .background(Color.White.copy(alpha = 0.2f))
                                .padding(horizontal = 11.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                ScIcons.CapBadge,
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            ScText(badge, 12f, FontWeight.Bold, Color.White, maxLines = 1)
                        }
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Chat tugmasi siqilganda butunlay yo'qoladi (eni 0 ga tushadi).
                Box(Modifier.width(lerp(42.dp, 0.dp, p)).clipToBounds().alpha(fade)) {
                    HeaderCircleButton(ScIcons.ChatRound, "Xabarlar", onOpenChat)
                }
                HeaderCircleButton(
                    ScIcons.Bell,
                    "Bildirishnomalar",
                    onOpenNotifications,
                    badge = state.hasUnreadNotifications
                )
            }
        }
    }
}

/** Gradient topbar ichidagi oq aylana tugma (soyasi topbar gradientida ko'rinmaydi). */
@Composable
private fun HeaderCircleButton(
    icon: ImageVector, label: String, onClick: () -> Unit, badge: Boolean = false
) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        Modifier.size(42.dp).scSoftShadow(6.dp, shape).clip(shape).background(Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = Sc.BrandDark, modifier = Modifier.size(20.dp))
        if (badge) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 7.dp, end = 8.dp).size(8.dp)
                    .background(Sc.Danger, shape),
            )
        }
    }
}

/** Siqilganda balandligi va shaffofligi bilan yo'qoladigan blok. */
@Composable
private fun CollapsingRow(p: Float, fullHeight: Dp, content: @Composable () -> Unit) {
    Box(
        Modifier.height(lerp(fullHeight, 0.dp, p)).clipToBounds().alpha(1f - p),
        contentAlignment = Alignment.CenterStart,
    ) { content() }
}

// ---------------------------------------------------------------------------
// Umumiy o'ramlar
// ---------------------------------------------------------------------------

/** Ekran chetigacha siljiydigan gorizontal lenta — kartalar chetda kesilmaydi. */
@Composable
private fun <T> EdgeRow(items: List<T>, spacing: Dp = 13.dp, item: @Composable (Int, T) -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Sc.ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        itemsIndexed(items) { index, value -> item(index, value) }
    }
}

@Composable
private fun PaddedHeader(
    title: String,
    subtitle: String? = null,
    action: String? = "Barchasi",
    actionIcon: ImageVector? = null,
    onAction: (() -> Unit)? = null,
) {
    ScSectionHeader(
        title,
        Modifier.padding(horizontal = Sc.ScreenPadding),
        subtitle = subtitle,
        action = action,
        actionIcon = actionIcon,
        onAction = onAction,
    )
}

// ---------------------------------------------------------------------------
// Chegirma bo'limlari — Ovqatlar / Kiyim-kechak / Dam olish
// ---------------------------------------------------------------------------

private data class CategoryVisual(val icon: ImageVector, val tint: Color, val accent: Color)

/** Biznes turi id'siga qarab ikonka va rang (`listings.json` dagi `categoryId`). */
@Composable
private fun categoryVisual(categoryId: String): CategoryVisual = when (categoryId) {
    "game" -> CategoryVisual(ScIcons.Gamepad, Sc.TintBlue, Sc.Brand)
    "kino" -> CategoryVisual(ScIcons.Gamepad, Sc.TintViolet, Sc.Violet)
    "ovqat" -> CategoryVisual(ScIcons.Coffee, Sc.TintOrange, Sc.Orange)
    "market" -> CategoryVisual(ScIcons.Cart, Sc.TintGreen, Sc.Success)
    "kiyim" -> CategoryVisual(ScIcons.Cart, Sc.TintViolet, Sc.Violet)
    "kurslar" -> CategoryVisual(ScIcons.FileText, Sc.TintPink, Sc.Pink)
    else -> CategoryVisual(ScIcons.Cart, Sc.TintAmber, Sc.Amber)
}

/**
 * Bitta chegirma bo'limi — sarlavha + gorizontal lenta. Bo'limda e'lon bo'lmasa
 * umuman chizilmaydi (bo'sh sarlavha osilib qolmasin).
 */
@Composable
private fun OfferSection(
    title: String,
    subtitle: String,
    offers: List<DiscountOffer>,
    onSeeAll: () -> Unit,
) {
    if (offers.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        PaddedHeader(title, subtitle, onAction = onSeeAll)
        EdgeRow(offers.take(8), spacing = 11.dp) { _, offer -> OfferCard(offer, onSeeAll) }
    }
}

@Composable
private fun OfferCard(offer: DiscountOffer, onClick: () -> Unit) {
    val visual = categoryVisual(offer.categoryId)
    Column(
        Modifier.width(212.dp).scCard(radius = 22.dp, elevation = 6.dp, onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ScIconTile(visual.tint, size = 42.dp, radius = 15.dp) {
                Icon(visual.icon, null, tint = visual.accent, modifier = Modifier.size(22.dp))
            }
            if (offer.isDiscount && offer.discountPercent > 0) {
                Box(
                    Modifier.clip(RoundedCornerShape(13.dp)).background(Sc.buttonBrush)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    ScText("−${offer.discountPercent}%", 12.5f, FontWeight.ExtraBold, Color.White, maxLines = 1)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            ScText(offer.merchant, 15f, FontWeight.ExtraBold, Sc.Ink, letterSpacing = -0.2f, maxLines = 1)
            ScText(offer.title, 12.5f, FontWeight.Medium, Sc.Muted, maxLines = 2)
        }
        val tag = if (offer.tag == DiscountTag.STUDENT_ID) "Talaba ID bilan" else "Promokod"
        ScText(
            listOfNotNull(tag, offer.expiry).joinToString(" · "),
            11.5f, FontWeight.SemiBold, visual.accent, maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------
// Yordam e'lonlari
// ---------------------------------------------------------------------------

@Composable
private fun TasksSection(tasks: List<Listing>, onSeeAll: () -> Unit, onOpen: (String) -> Unit) {
    if (tasks.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        PaddedHeader(
            "Yordam e'lonlari", "Referat, masala, qo'lyozma va IT ishlari", onAction = onSeeAll
        )
        Column(
            Modifier.padding(horizontal = Sc.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            tasks.take(3).forEach { TaskCard(it, onOpen) }
        }
    }
}

@Composable
private fun TaskCard(listing: Listing, onOpen: (String) -> Unit) {
    val book = ScIcons.Book
    Row(
        Modifier.fillMaxWidth().scCard(radius = 24.dp, onClick = { onOpen(listing.id) })
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        ScIconTile(Sc.TintPink, size = 52.dp, radius = 18.dp) { ScGlyph(book, 26.dp) }
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScText(
                    listing.title,
                    15.5f,
                    FontWeight.ExtraBold,
                    Sc.Ink,
                    Modifier.weight(1f),
                    maxLines = 1
                )
                deadlineLabel(listing.taskDetails?.deadline)?.let { deadline ->
                    Box(
                        Modifier.clip(RoundedCornerShape(10.dp)).background(Sc.TintPink)
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                    ) { ScText(deadline, 11f, FontWeight.Bold, Sc.PinkDeep, maxLines = 1) }
                }
            }
            val summary = listing.taskDetails?.summary().orEmpty()
            if (summary.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                ScText(summary, 12.5f, FontWeight.Medium, Sc.Muted, maxLines = 1)
            }
            Spacer(Modifier.height(7.dp))
            ScText(listing.priceLabel(), 14.5f, FontWeight.ExtraBold, Sc.Success, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------------------
// Klublar
// ---------------------------------------------------------------------------

/** Klub kartalari dizaynda navbat bilan ko'k / binafsha / yashil bo'ladi. */
@Composable
private fun clubVisual(index: Int): Triple<Color, Color, ImageVector> = when (index.mod(3)) {
    0 -> Triple(Sc.TintBlue, Sc.Brand, ScIcons.Laptop)
    1 -> Triple(Sc.TintViolet, Sc.Violet, ScIcons.MessageLines)
    else -> Triple(Sc.TintGreen, Sc.Success, ScIcons.Medal)
}

@Composable
private fun ClubsSection(clubs: List<Club>, onOpenClubs: () -> Unit) {
    if (clubs.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PaddedHeader("Klublar", onAction = onOpenClubs)
        EdgeRow(clubs.take(6), spacing = 12.dp) { _, club ->
            val (tint, accent, icon) = clubVisual((club.id - 1).toInt())
            Column(
                Modifier.width(138.dp).scCard(radius = 24.dp, onClick = onOpenClubs).padding(15.dp),
            ) {
                ScIconTile(tint, size = 48.dp, radius = 17.dp) {
                    Icon(icon, null, tint = accent, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.height(14.dp))
                ScText(club.name, 15.5f, FontWeight.ExtraBold, Sc.Ink, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                ScText("${club.membersCount} a'zo", 13f, FontWeight.Bold, accent, maxLines = 1)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Ijara kvartiralar
// ---------------------------------------------------------------------------

@Composable
private fun RentalsSection(rentals: List<Listing>, onSeeAll: () -> Unit, onOpen: (String) -> Unit) {
    if (rentals.isEmpty()) return
    val house = ScIcons.HouseFilled
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        PaddedHeader("Ijara kvartiralar", "Sherik izlayotgan uylar", onAction = onSeeAll)
        EdgeRow(rentals.take(6)) { _, listing ->
            Column(
                Modifier.width(250.dp).scCard(radius = 26.dp, onClick = { onOpen(listing.id) })
                    .padding(16.dp),
            ) {
                ScIconTile(Sc.TintOrange, size = 50.dp, radius = 18.dp) { ScGlyph(house, 26.dp) }
                Spacer(Modifier.height(14.dp))
                ScText(listing.title, 16f, FontWeight.ExtraBold, Sc.Ink, maxLines = 1)
                val meta = listing.rentalDetails?.summary()?.takeIf { it.isNotBlank() }
                    ?: listing.branches.firstOrNull()?.address.orEmpty()
                if (meta.isNotBlank()) {
                    Spacer(Modifier.height(5.dp))
                    ScText(meta, 12.5f, FontWeight.Medium, Sc.Muted, lineHeight = 19f, maxLines = 2)
                }
                Spacer(Modifier.height(10.dp))
                ScText(listing.rentLabel(), 15f, FontWeight.ExtraBold, Sc.Success, maxLines = 1)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Ish e'lonlari
// ---------------------------------------------------------------------------

@Composable
private fun JobsSection(jobs: List<Listing>, onSeeAll: () -> Unit, onOpen: (String) -> Unit) {
    if (jobs.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        PaddedHeader("Ish e'lonlari", "Kunlik va doimiy ishlar", onAction = onSeeAll)
        Column(
            Modifier.padding(horizontal = Sc.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            jobs.take(3).forEach { listing ->
                Row(
                    Modifier.fillMaxWidth().scCard(radius = 24.dp, onClick = { onOpen(listing.id) })
                        .padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(13.dp),
                ) {
                    ScIconTile(Sc.TintBlue, size = 52.dp, radius = 18.dp) {
                        Icon(
                            ScIcons.Briefcase,
                            null,
                            tint = Sc.Brand,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        ScText(listing.title, 15.5f, FontWeight.ExtraBold, Sc.Ink, maxLines = 1)
                        val meta = listOfNotNull(
                            listing.branches.firstOrNull()?.address,
                            listing.jobDetails?.companyName?.takeIf { it.isNotBlank() },
                            listing.categoryLabel,
                        ).joinToString(" · ")
                        if (meta.isNotBlank()) {
                            Spacer(Modifier.height(3.dp))
                            ScText(meta, 12.5f, FontWeight.Medium, Sc.Muted, maxLines = 1)
                        }
                        Spacer(Modifier.height(7.dp))
                        ScText(
                            listing.salaryLabel(),
                            14.5f,
                            FontWeight.ExtraBold,
                            Sc.Success,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Studentlar
// ---------------------------------------------------------------------------

/** Talaba kartasidagi monogramma plitkasi navbat bilan uch rangda. */
private val studentVisuals: List<Pair<Color, Color>>
    @Composable @ReadOnlyComposable get() = listOf(
    Sc.TintViolet to Sc.Violet,
    Sc.TintBlue to Sc.Brand,
    Sc.TintGreen to Sc.Success,
)

@Composable
private fun StudentsSection(
    students: List<Student>, onSeeAll: () -> Unit, onFriend: (Student) -> Unit
) {
    if (students.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        PaddedHeader(
            "Studentlar",
            "Universitet bo'yicha do'st toping",
            action = "Ko'proq",
            onAction = onSeeAll
        )
        EdgeRow(students.take(6)) { index, student ->
            val (tint, accent) = studentVisuals[index.mod(studentVisuals.size)]
            Column(
                Modifier.width(150.dp).scCard(radius = 26.dp)
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ScMonogramTile(student.initial, tint, accent)
                Spacer(Modifier.height(12.dp))
                ScText(student.firstName, 16f, FontWeight.ExtraBold, Sc.Ink, maxLines = 1)
                Spacer(Modifier.height(3.dp))
                ScText(
                    student.universityMonogram, 12.5f, FontWeight.SemiBold, Sc.Muted, maxLines = 1
                )
                Spacer(Modifier.height(14.dp))
                FriendButton(student, onFriend)
            }
        }
    }
}

@Composable
private fun FriendButton(student: Student, onFriend: (Student) -> Unit) {
    when (student.friendStatus) {
        FriendStatus.NONE -> ScGradientButton(
            "+ Do'st", { onFriend(student) },
            radius = 16.dp, verticalPadding = 10.dp, fontSize = 13.5f, weight = FontWeight.Bold,
        )

        FriendStatus.PENDING -> ScSoftButton(
            "Kutilmoqda", { onFriend(student) },
            radius = 16.dp, verticalPadding = 10.dp, fontSize = 13.5f,
            background = Sc.TintBlue, color = Sc.Brand,
        )

        FriendStatus.FRIENDS -> ScSoftButton(
            "Do'st", { onFriend(student) },
            radius = 16.dp, verticalPadding = 10.dp, fontSize = 13.5f,
            background = Sc.TintBlue, color = Sc.Brand,
        )
    }
}

// ---------------------------------------------------------------------------
// Matn yordamchilari
// ---------------------------------------------------------------------------

/** "50 000 so'm" yoki "Kelishilgan". */
private fun Listing.priceLabel(): String =
    if (isNegotiable) "Kelishilgan" else "${price.formatSum()} so'm"

/** "1 500 000 so'm / oy". */
private fun Listing.rentLabel(): String {
    if (isNegotiable) return "Kelishilgan"
    val suffix = rentalDetails?.period?.priceUnit?.suffix
    return listOfNotNull("${price.formatSum()} so'm", suffix).joinToString(" / ")
}

/** "40 000 so'm / kun" yoki oraliq. */
private fun Listing.salaryLabel(): String {
    if (isNegotiable) return "Kelishilgan"
    val suffix = jobDetails?.payPeriod?.suffix
    val amount = priceMax?.takeIf { it > price }?.let { "${price.formatSum()} — ${it.formatSum()}" }
        ?: price.formatSum()
    return listOfNotNull("$amount so'm", suffix).joinToString(" / ")
}

/**
 * Muddat yorlig'i: "Bugun 18:00", "Ertaga 12:00", "5 kundan keyin" yoki "24.12".
 * O'tib ketgan muddat ko'rsatilmaydi.
 */
private fun deadlineLabel(deadline: Long?): String? {
    if (deadline == null) return null
    val zone = TimeZone.currentSystemDefault()
    val at = Instant.fromEpochMilliseconds(deadline).toLocalDateTime(zone)
    val today = Clock.System.now().toLocalDateTime(zone).date
    val days = at.date.toEpochDays() - today.toEpochDays()
    val time = "${at.hour.toString().padStart(2, '0')}:${at.minute.toString().padStart(2, '0')}"
    return when {
        days < 0 -> null
        days == 0 -> "Bugun $time"
        days == 1 -> "Ertaga $time"
        days in 2..13 -> "$days kundan keyin"
        else -> "${at.date.dayOfMonth}.${at.date.monthNumber}"
    }
}
