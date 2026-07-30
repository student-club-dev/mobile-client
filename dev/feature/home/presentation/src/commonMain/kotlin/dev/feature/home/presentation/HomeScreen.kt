package dev.feature.home.presentation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.core.uikit.components.ScGlyph
import dev.core.uikit.components.ScGradientButton
import dev.core.uikit.components.ScHeader
import dev.core.uikit.components.ScIconTile
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScAvatar
import dev.core.uikit.components.ScSectionHeader
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
import dev.feature.connections.domain.model.ConnectionView
import dev.feature.connections.domain.model.SearchedStudent
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
    /** "Do'stlar" — `Connections` ekrani bog'langanlar bo'limi bilan. */
    onOpenStudents: () -> Unit = {},
    /** Qidiruv maydoni — o'sha ekran, Qidiruv bo'limi ochilgan holda. */
    onOpenStudentSearch: () -> Unit = {},
    /** "Kutilayotganlar" — o'sha ekran, So'rovlar bo'limi ochilgan holda. */
    onOpenStudentRequests: () -> Unit = {},
    /** Bog'langan talaba kartasidagi "Xabar" — chat tab'ini o'sha odam bilan ochadi. */
    onOpenChatWith: (String) -> Unit = {},
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
            // Bo'limlar ro'yxati backend katalogidan (`/v1/catalog/groups`) — ilovada qat'iy
            // yozilmagan, tartib ham serverniki.
            state.offerSections.forEach { section ->
                OfferSection(section, onOpenDiscounts)
            }
            TasksSection(state.tasks, onOpenTasks, onOpenListing)
            ClubsSection(state.clubs, onOpenClubs)
            RentalsSection(state.rentals, onOpenRentals, onOpenListing)
            JobsSection(state.jobs, onOpenJobs, onOpenListing)
            StudentsSearchSection(onOpenStudentSearch, onOpenStudents, onOpenStudentRequests)
            StudentsSection(
                title = "Universitetimda",
                subtitle = "Bir universitetda o'qiyotgan talabalar",
                students = state.universityStudents,
                onSeeAll = onOpenStudentSearch,
                onConnect = vm::connect,
                onMessage = onOpenChatWith,
            )
            StudentsSection(
                title = "Barcha talabalar",
                subtitle = "Yangi qo'shilganlar birinchi",
                students = state.allStudents,
                onSeeAll = onOpenStudentSearch,
                onConnect = vm::connect,
                onMessage = onOpenChatWith,
            )
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
// Chegirma bo'limlari — katalog guruhlari (Ovqatlanish, Sport, Ta'lim...)
// ---------------------------------------------------------------------------

private data class CategoryVisual(val tint: Color, val accent: Color)

/**
 * Karta rangi e'lonning O'Z turidan keladi (`DiscountOfferEntity.bannerAccent` — backend'dagi
 * `CatalogTypeDto.accentColor`), shuning uchun ilovada tur→rang jadvali yo'q: yangi biznes
 * turi qo'shilsa ham karta o'z rangida chiziladi. Rang kelmagan bo'lsa — mavzuning asosiysi.
 */
@Composable
private fun categoryVisual(accentArgb: Long): CategoryVisual {
    val accent = if (accentArgb == 0L) Sc.Brand else Color(accentArgb.toULong().toLong())
    // Fon — o'sha rangning yengil qatlami (mavzuga qarab ochroq/to'qroq ko'rinadi).
    return CategoryVisual(tint = accent.copy(alpha = 0.14f), accent = accent)
}

/**
 * Bitta chegirma bo'limi — sarlavha + gorizontal lenta. Sarlavha va rang katalog
 * guruhidan keladi ([HomeOfferSection]); bo'sh bo'lim ViewModel'da tashlab yuboriladi.
 */
@Composable
private fun OfferSection(section: HomeOfferSection, onSeeAll: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        PaddedHeader("${section.emoji} ${section.title}".trim(), onAction = onSeeAll)
        EdgeRow(section.offers.take(8), spacing = 12.dp) { _, offer -> OfferCard(offer, onSeeAll) }
    }
}

/** Karta rasmining balandligi — kenglikka nisbatan ~4:3. */
private val OfferImageHeight = 132.dp
private val OfferCardWidth = 178.dp

/**
 * "Siz uchun" e'loni kartasi: tepada RASM, ustida chegirma nishoni va tur yorlig'i
 * (kiyimda — "Futbolka", ovqatda — "Pitsa"), pastida do'kon nomi, e'lon nomi va
 * YASHIL narx.
 *
 * Rasm ([DiscountOffer.imageUrl]) faqat backend feed'ida bor; local seed'da yo'q, shuning
 * uchun uning o'rniga turning rangli foni + emoji chiziladi — karta hech qachon bo'sh
 * kulrang to'rtburchak bo'lib qolmaydi.
 */
@Composable
private fun OfferCard(offer: DiscountOffer, onClick: () -> Unit) {
    val visual = categoryVisual(offer.bannerAccent)
    Column(
        Modifier.width(OfferCardWidth).scCard(radius = 22.dp, elevation = 6.dp, onClick = onClick),
    ) {
        OfferImage(offer, visual)
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ScText(offer.merchant, 11.5f, FontWeight.Bold, visual.accent, maxLines = 1)
                // `minLines = 2` — lentadagi kartalar bir xil balandlikda tursin (bir qatorli
                // nom bilan ikki qatorli nom yonma-yon kelganda qator "arralanib" ketmasin).
                Text(
                    offer.title,
                    style = scStyle(14.5f, FontWeight.ExtraBold, Sc.Ink, lineHeight = 18f, letterSpacing = -0.2f),
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OfferPrice(offer)
            val tag = if (offer.tag == DiscountTag.STUDENT_ID) "Talaba ID" else "Promokod"
            ScText(
                listOfNotNull(tag, offer.location, offer.expiry).joinToString(" · "),
                11f, FontWeight.SemiBold, Sc.MutedLight, maxLines = 1,
            )
        }
    }
}

/** Kartaning rasm qismi: rasm (yoki emoji), chegirma nishoni va tur yorlig'i. */
@Composable
private fun OfferImage(offer: DiscountOffer, visual: CategoryVisual) {
    Box(Modifier.fillMaxWidth().height(OfferImageHeight).background(visual.tint)) {
        // Emoji DOIM chiziladi, rasm esa uning ustiga tushadi — yuklanayotganda ham,
        // havola buzuq bo'lganda ham karta bo'sh qolmaydi (ScAvatar bilan bir xil usul).
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (offer.emoji.isNotBlank()) {
                Text(offer.emoji, style = TextStyle(fontSize = 46.sp))
            } else {
                Icon(ScIcons.Cart, null, tint = visual.accent, modifier = Modifier.size(40.dp))
            }
        }
        if (!offer.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = offer.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (offer.isDiscount && offer.discountPercent > 0) {
            Box(
                Modifier.align(Alignment.TopStart).padding(9.dp)
                    .clip(RoundedCornerShape(11.dp)).background(Sc.buttonBrush)
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            ) {
                ScText("−${offer.discountPercent}%", 12f, FontWeight.ExtraBold, Color.White, maxLines = 1)
            }
        }

        // Tur yorlig'i — kiyimda aynan shu "Futbolka / Ko'ylak / Poyabzal" ni ko'rsatadi.
        val type = offer.typeLabel()
        if (type.isNotBlank()) {
            Box(
                Modifier.align(Alignment.BottomStart).padding(9.dp)
                    .clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.94f))
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            ) {
                // Oq yorliq fon rasmidan qat'i nazar oq bo'lgani uchun matn ham doim to'q.
                ScText(type, 11f, FontWeight.ExtraBold, InkOnLight, maxLines = 1)
            }
        }
    }
}

/** Narx qatori — chegirmali narx YASHIL, ustidan chizilgan eski narx va o'lchov birligi. */
@Composable
private fun OfferPrice(offer: DiscountOffer) {
    if (offer.effectivePrice <= 0) return
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        ScText(
            "${offer.effectivePrice.spaced()} so'm", 15.5f, FontWeight.ExtraBold, Sc.Success,
            letterSpacing = -0.2f, maxLines = 1,
        )
        if (offer.isDiscount && offer.originalPrice > offer.finalPrice) {
            Text(
                offer.originalPrice.spaced(),
                style = scStyle(11f, FontWeight.SemiBold, Sc.MutedLight)
                    .copy(textDecoration = TextDecoration.LineThrough),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else if (offer.priceUnit.isNotBlank()) {
            ScText("/ ${offer.priceUnit}", 11f, FontWeight.SemiBold, Sc.MutedLight, maxLines = 1)
        }
    }
}

/**
 * Kartadagi tur yorlig'i. Kiyimda jins ham qo'shiladi ("Erkaklar · Ko'ylak") — bir xil
 * turdagi erkak/ayol modellari lentada aralashib ketmasin.
 *
 * Backend'da jinsning O'ZI kategoriya bo'lishi mumkin (`categoryKey = MEN` → "Erkaklar"),
 * shunda yorliq "Erkaklar · Erkaklar" bo'lib qolardi — takror olib tashlanadi.
 */
private fun DiscountOffer.typeLabel(): String {
    val genderLabel = when (gender.uppercase()) {
        "MALE" -> "Erkaklar"
        "FEMALE" -> "Ayollar"
        else -> null
    }
    val type = subcategory.takeIf { it.isNotBlank() }
    if (type != null && type.equals(genderLabel, ignoreCase = true)) return type
    return listOfNotNull(genderLabel, type).joinToString(" · ")
}

/** "30 000" — uch xonadan bo'sh joy bilan. */
private fun Long.spaced(): String = toString().reversed().chunked(3).joinToString(" ").reversed()

/** Oq yorliq ustidagi matn — mavzudan qat'i nazar to'q (fon doim oq). */
private val InkOnLight = Color(0xFF0F2A43)

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

/**
 * Talabalar bloki: qidiruvga kirish + "Do'stlar" / "Kutilayotganlar" ga o'tish.
 *
 * Qidiruv maydoni bu yerda **ishlamaydi** — bosilganda `Connections` ekrani Qidiruv bo'limi
 * ochilgan holda keladi. Sabab: to'liq qidiruv (debounce, filtrlar, bog'lanish tugmalari,
 * "⋮" menyusi, blok/shikoyat) o'sha ekranda allaqachon bor, uni Home'da takrorlash ikki
 * nusxa kod bo'lardi.
 */
@Composable
private fun StudentsSearchSection(
    onOpenSearch: () -> Unit,
    onOpenConnected: () -> Unit,
    onOpenRequests: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        PaddedHeader("Talabalar", "Do'st toping va bog'laning", action = null)

        // Haqiqiy maydon emas — butun qatorning o'zi tugma.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Sc.ScreenPadding)
                .clip(RoundedCornerShape(16.dp))
                .background(Sc.FieldBg)
                .border(1.dp, Sc.Border, RoundedCornerShape(16.dp))
                .clickable(onClick = onOpenSearch)
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(ScIcons.Search, null, tint = Sc.Muted, modifier = Modifier.size(18.dp))
            ScText(
                "Ism yoki username…", 14.5f, FontWeight.Medium, Sc.NavIdle,
                maxLines = 1, modifier = Modifier.weight(1f),
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = Sc.ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            NavTile("Do'stlar", ScIcons.Users, Sc.TintBlue, Sc.Brand, Modifier.weight(1f), onOpenConnected)
            // "Kutilayotganlar" kartaga sig'maydi; Connections ekranidagi tab ham "So'rovlar".
            NavTile("So'rovlar", ScIcons.Bell, Sc.TintViolet, Sc.Violet, Modifier.weight(1f), onOpenRequests)
        }
    }
}

/** Talabalar blokidagi o'tish kartasi — ikona + yorliq + ">" belgisi. */
@Composable
private fun NavTile(
    label: String,
    icon: ImageVector,
    tint: Color,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier.scCard(radius = 18.dp).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScIconTile(tint, size = 36.dp, radius = 13.dp) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
        }
        ScText(label, 13.5f, FontWeight.Bold, Sc.Ink, maxLines = 1, modifier = Modifier.weight(1f))
        Icon(ScIcons.ChevronRight, null, tint = Sc.MutedLight, modifier = Modifier.size(15.dp))
    }
}

/**
 * Talabalar kartalari qatori — Home'dagi ikkala bo'lim ham shu (faqat sarlavha va manba
 * ro'yxati boshqa).
 *
 * Ro'yxat bo'sh bo'lsa bo'lim butunlay yashiriladi: Home'da xato ko'rsatilmaydi, ya'ni
 * so'rov yiqilganda bo'sh sarlavha osilib qolmasin.
 *
 * Kartadagi amal munosabatga qarab: hali bog'lanmagan bo'lsa «Bog'lanish», bog'langan
 * bo'lsa «Xabar» (chat faqat bog'langanlar uchun ochiq), so'rov yuborilgan bo'lsa —
 * o'chirilgan yozuv (yuborilgan so'rovni bekor qilish endpointi yo'q).
 */
@Composable
private fun StudentsSection(
    title: String,
    subtitle: String,
    students: List<SearchedStudent>,
    onSeeAll: () -> Unit,
    onConnect: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    if (students.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        PaddedHeader(title, subtitle, action = "Ko'proq", onAction = onSeeAll)
        EdgeRow(students.take(10)) { index, result ->
            val student = result.student
            val (tint, accent) = studentVisuals[index.mod(studentVisuals.size)]
            Column(
                Modifier.width(150.dp).scCard(radius = 26.dp)
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ScAvatar(
                    name = student.displayName,
                    size = 62.dp,
                    avatarUrl = student.avatarUrl,
                    background = tint,
                    initialColor = accent,
                    shape = RoundedCornerShape(24.dp),
                )
                Spacer(Modifier.height(12.dp))
                ScText(student.displayName, 16f, FontWeight.ExtraBold, Sc.Ink, maxLines = 1)
                Spacer(Modifier.height(3.dp))
                ScText(
                    student.username?.let { "@$it" }.orEmpty(),
                    12.5f, FontWeight.SemiBold, Sc.Muted, maxLines = 1,
                )
                Spacer(Modifier.height(14.dp))
                when (result.connectionStatus) {
                    ConnectionView.CONNECTED -> ScGradientButton(
                        "Xabar", { onMessage(student.id) },
                        radius = 16.dp, verticalPadding = 10.dp, fontSize = 13.5f,
                        weight = FontWeight.Bold,
                    )
                    // Kiruvchi so'rovga ham shu tugma javob beradi: server qarshi so'rovni
                    // darhol qabul qilingan bog'lanishga aylantiradi (C1).
                    ConnectionView.NONE, ConnectionView.PENDING_IN -> ScGradientButton(
                        "Bog'lanish", { onConnect(student.id) },
                        radius = 16.dp, verticalPadding = 10.dp, fontSize = 13.5f,
                        weight = FontWeight.Bold,
                    )
                    ConnectionView.PENDING_OUT -> ScText(
                        "Yuborildi", 12.5f, FontWeight.Bold, Sc.Muted, maxLines = 1,
                    )
                }
            }
        }
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
