package dev.feature.home.presentation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
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
import dev.core.uikit.components.scGlassButtonColors
import dev.core.uikit.components.scSoftShadow
import dev.core.uikit.components.scStyle
import dev.core.uikit.components.ScNetworkImage
import dev.core.uikit.components.ScShimmerLine
import dev.core.uikit.components.ScShimmerCard
import dev.core.uikit.theme.Sc
import dev.feature.stories.presentation.StoriesCollapsed
import dev.feature.chat.presentation.rememberPeerProfileSections
import dev.feature.connections.domain.model.StudentSummary
import dev.feature.connections.presentation.StudentProfileSheet
import dev.feature.stories.presentation.StoriesRow
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import dev.core.domain.model.DiscountOffer
import dev.feature.listings.domain.model.Listing
import dev.feature.listings.domain.model.formatSum
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import dev.feature.connections.domain.model.ConnectionView
import dev.feature.connections.domain.model.SearchedStudent
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.runtime.ReadOnlyComposable
import dev.core.common.format.formatAmount

/**
 * Dizayndagi `cubic-bezier(.4,0,.2,1)` — topbar kichrayishi shu egri bilan.
 * `internal`: yon panel ham ([HomeSideNav]) aynan shu egridan foydalanadi.
 */
internal val ScEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

/** Shu masofadan ortiq scroll qilinganda topbar siqiladi (maketda `scrollTop > 36`). */
private val CondenseThreshold = 36.dp

/** Topbardagi yig'ilgan story to'plamining eni — 3 ta ustma-ust doira + oraliq. */
private val CollapsedStoriesWidth = 78.dp

@Composable
fun HomeScreen(
    onOpenProfile: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    /**
     * "Siz uchun" feed'i. Argument — katalog guruhi kaliti ([HomeOfferSection.key]): bo'lim
     * sarlavhasidagi "Barchasi" AYNAN o'sha bo'limni (masalan "Ovqatlar") ochadi. `null` —
     * filtrsiz butun feed.
     */
    onOpenDiscounts: (String?) -> Unit = {},
    /** "Fanlardan yordam" bo'limi — talaba e'lonlari ekrani, Yordam tab'i bilan. */
    onOpenTasks: () -> Unit = {},
    onOpenRentals: () -> Unit = {},
    onOpenListing: (String) -> Unit = {},
    /** "Do'stlar" — `Connections` ekrani bog'langanlar bo'limi bilan. */
    onOpenStudents: () -> Unit = {},
    /** Qidiruv maydoni — o'sha ekran, Qidiruv bo'limi ochilgan holda. */
    onOpenStudentSearch: () -> Unit = {},
    /** "Kutilayotganlar" — o'sha ekran, So'rovlar bo'limi ochilgan holda. */
    onOpenStudentRequests: () -> Unit = {},
    /** Bog'langan talaba kartasidagi "Xabar" — chat tab'ini o'sha odam bilan ochadi. */
    onOpenChatWith: (String) -> Unit = {},
    /** Yon paneldagi "Universitetim" — pastki paneldagi o'sha tab. */
    onOpenUniversity: () -> Unit = {},
    /** Yon paneldagi "E'lonlar" — talaba e'lonlari tab'i (ijara, xizmat, ish). */
    onOpenListings: () -> Unit = {},
    /** Yon paneldagi "Mening e'lonlarim" — foydalanuvchining O'Z e'lonlari, barcha statuslar. */
    onOpenMyListings: () -> Unit = {},
    /** Yon paneldagi "Sozlamalar". Ekranda boshqa kirish nuqtasi yo'q (profil ichida). */
    onOpenSettings: () -> Unit = {},
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

    val scope = rememberCoroutineScope()

    /**
     * Ochilgan talaba profili — story lentasidagi muallif ustiga bosilganda.
     *
     * Varaq shu yerda chiziladi, story modulida emas: undagi «Media / Fayllar /
     * Havolalar» bo'limlari chat modulida yashaydi va story chatga bog'lanolmaydi
     * (chat allaqachon story'ga bog'langan — «Postlar» bo'limi uchun).
     */
    var profileStudent by remember { mutableStateOf<StudentSummary?>(null) }

    /**
     * Yon panel ([HomeSideNav]) holati: `»` tutqichi bosilganda ham, chap chetdan
     * o'ngga surilganda ham shu bitta qiymat harakatlantiradi.
     */
    val sideNav = rememberSideNavState()

    // Surish ishlovchisi ILDIZ `Box` da — u butun ekranni qamraydi, lekin harakat
    // aniq gorizontal bo'lgunicha hech narsani yutmaydi (`sideNavEdgeDrag` izohiga q.).
    Box(Modifier.fillMaxSize().background(Sc.Bg).sideNavEdgeDrag(sideNav)) {
        Column(Modifier.fillMaxSize()) {
            HomeHeader(
                state = state,
                p = p,
                onOpenProfile = onOpenProfile,
                onOpenChat = onOpenChat,
                onOpenNotifications = onOpenNotifications,
                // Yig'ilgan to'plam bosilsa — lenta yana ko'rinsin.
                onOpenStories = { scope.launch { scroll.animateScrollTo(0) } },
            )
            // Yon panel tutqichi — topbar bilan story lentasi CHEGARASIDA, chap chetda.
            // Ustunda joy EGALLAMAYDI (`sideNavHandleSeam`): kontentni pastga surmaydi,
            // uning ustida "suzadi". Scroll qilinadigan ustundan tashqarida — ro'yxat
            // surilganda ham joyida qoladi.
            SideNavHandle(sideNav::open, Modifier.sideNavHandleSeam())

            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll).padding(top = 22.dp),
                verticalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                // Story lentasi — eng tepada, bo'limlardan oldin (`handoff/07-STORIES.md` §2).
                // O'z holatini o'zi boshqaradi: lenta bo'sh bo'lsa ham «Lavham» katakchasi
                // qoladi, ya'ni bu yerda shart tekshirilmaydi.
                StoriesRow(
                    myName = state.userName,
                    myAvatarUrl = state.avatarUrl,
                    // Lavha muallifi ustiga bosilganda uning profili — CHATDAGI bilan bir xil
                    // varaq va bir xil bo'limlar (`rememberPeerProfileSections`). Varaqni shu
                    // yerda chizamiz: story moduli chat moduliga bog'lanolmaydi.
                    onOpenProfile = { author -> profileStudent = author },
                )

                // Birinchi yuklanish, keshda hech narsa yo'q — bo'limlar o'rniga skelet.
                if (state.loading) {
                    HomeSkeleton()
                }
                // Bo'limlar TARTIBI qat'iy: Ovqatlanish → Kiyim-kechak → Fanlardan yordam →
                // Ijara kvartiralar. Birinchi ikkitasi katalogdan keladi (sarlavhalar
                // serverniki), keyingi ikkitasi — talaba e'lonlari. Qolgan turlar
                // ("Siz uchun", ish, xizmat) o'z ekranlarida.
                state.offerSections.forEach { section ->
                    OfferSection(section, onOpenDiscounts)
                }
                TasksSection(state.tasks, onOpenTasks, onOpenListing)
                RentalsSection(state.rentals, onOpenRentals, onOpenListing)
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

        HomeSideNav(
            nav = sideNav,
            state = state,
            onOpenProfile = onOpenProfile,
            onOpenUniversity = onOpenUniversity,
            onOpenListings = onOpenListings,
            // Kalitsiz — butun "Takliflar" feed'i (bo'lim filtri yo'q).
            onOpenOffers = { onOpenDiscounts(null) },
            onOpenRentals = onOpenRentals,
            onOpenChat = onOpenChat,
            onOpenNotifications = onOpenNotifications,
            onOpenStudents = onOpenStudents,
            onOpenStudentSearch = onOpenStudentSearch,
            onOpenStudentRequests = onOpenStudentRequests,
            onOpenMyListings = onOpenMyListings,
            onOpenSettings = onOpenSettings,
        )
    }

    profileStudent?.let { author ->
        StudentProfileSheet(
            studentId = author.id,
            known = author,
            onClose = { profileStudent = null },
            // Chatdagi profil bilan AYNAN bir xil bo'limlar.
            sections = rememberPeerProfileSections(author.id),
            onOpenChat = { id ->
                profileStudent = null
                onOpenChatWith(id)
            },
        )
    }
}

/**
 * Bosh ekranning skeleti: ikkita "bo'lim" — sarlavha qatori va gorizontal kartalar lentasi.
 * Haqiqiy bo'limlar bilan bir xil o'lchamda, shuning uchun ma'lumot kelganda ekran
 * joyidan sakramaydi.
 */
@Composable
private fun HomeSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(26.dp)) {
        repeat(2) {
            Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Box(Modifier.padding(horizontal = Sc.ScreenPadding)) {
                    ScShimmerLine(0.42f, 17.dp)
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Sc.ScreenPadding),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    repeat(2) {
                        ScShimmerCard(
                            Modifier.width(OfferCardWidth),
                            imageHeight = OfferImageHeight,
                            radius = 22.dp,
                        )
                    }
                }
            }
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
    onOpenStories: () -> Unit,
) {
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
            // Avatar va uning yonidagi yig'ilgan story to'plami bitta bo'lak: shunda
            // tashqi qatorning 13dp oralig'i to'plam ko'rinmaganda ham qo'shilmaydi.
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Ilovadagi yagona avatar komponenti: bosh harf DOIM orqada chiziladi, rasm
                // uning ustiga tushadi. Ilgari bu yerda `AsyncImage` to'g'ridan-to'g'ri
                // ishlatilardi va havola buzuq bo'lsa (masalan server rasmni 404 qaytarsa)
                // avatar BO'SH kvadrat bo'lib qolardi.
                ScAvatar(
                    name = state.userName,
                    size = avatarSize,
                    modifier = Modifier.clickable(onClick = onOpenProfile),
                    avatarUrl = state.avatarUrl,
                    fontSize = 22f - 5f * p,
                    background = Color.White.copy(alpha = 0.22f),
                    initialColor = Color.White,
                    shape = RoundedCornerShape(lerp(20.dp, 14.dp, p)),
                )
                // Topbar siqilganda lenta ekrandan chiqib ketadi — o'shanda uning o'rnini
                // Telegramdagidek kichik, ustma-ust tushgan avatarlar egallaydi.
                Box(
                    Modifier.width(lerp(0.dp, CollapsedStoriesWidth, p)).clipToBounds().alpha(p),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    StoriesCollapsed(
                        myName = state.userName,
                        myAvatarUrl = state.avatarUrl,
                        onClick = onOpenStories,
                        modifier = Modifier.padding(start = 10.dp),
                        // Ko'k gradient ustida halqa oq bo'lishi kerak — brend ko'ki fonda
                        // yo'qolib ketardi.
                        ringBrush = Brush.linearGradient(listOf(Color.White, Color.White)),
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
                // Chat tugmasi HAR DOIM ko'rinadi — siqilganda ham. Suhbatlar pastki
                // panelda tab emas (u yerda "Takliflar" turibdi), ya'ni bu ilovadagi
                // YAGONA kirish nuqtasi: ilgarigidek scroll'da yo'qolsa, foydalanuvchi
                // xabarlarga tepaga qaytmasdan kirolmay qolardi.
                HeaderCircleButton(ScIcons.ChatRound, "Xabarlar", onOpenChat)
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

/**
 * Gradient topbar ichidagi aylana tugma (soyasi topbar gradientida ko'rinmaydi).
 *
 * Rangi ilovadagi boshqa topbar tugmalari bilan bir xil ([scGlassButtonColors]): yorug'da
 * oq doira + ko'k belgi, to'q rejimda shaffof doira + oq belgi.
 */
@Composable
private fun HeaderCircleButton(
    icon: ImageVector, label: String, onClick: () -> Unit, badge: Boolean = false
) {
    val shape = RoundedCornerShape(percent = 50)
    val (background, tint) = scGlassButtonColors()
    Box(
        Modifier.size(42.dp).scSoftShadow(6.dp, shape).clip(shape).background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(20.dp))
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
 *
 * "Barchasi" ham, kartaning o'zi ham feed'ni SHU bo'lim filtri bilan ochadi — foydalanuvchi
 * "Ovqatlar" ni bosib, aralash "Siz uchun" ro'yxatiga tushib qolmasin.
 */
@Composable
private fun OfferSection(section: HomeOfferSection, onSeeAll: (String?) -> Unit) {
    val openSection = { onSeeAll(section.key) }
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        PaddedHeader("${section.emoji} ${section.title}".trim(), onAction = openSection)
        EdgeRow(section.offers.take(8), spacing = 12.dp) { _, offer ->
            OfferCard(offer, openSection)
        }
    }
}

/** Karta o'lchami. Rasm — kartaning katta qismi; ostida faqat bitta qator (biznes nomi). */
private val OfferImageHeight = 186.dp
private val OfferCardWidth = 178.dp

/**
 * "Siz uchun" e'loni kartasi.
 *
 * Ma'lumotlar RASM USTIDA: chap-tepada chegirma nishoni, pastda tur yorlig'i ("Pitsa"),
 * narx va manzil. Rasm OSTIDA esa faqat **biznes nomi** qoladi.
 *
 * E'lon NOMI va tavsifi kartada YO'Q — ular tafsilot oynasida; kartada esa "nima,
 * qanchaga, qayerda" ni turi, narxi va manzili aytadi.
 *
 * Matn o'qilishi uchun pastdan qora gradient (scrim) tushiriladi — u rasm ustida ham,
 * emoji fon ustida ham matnni ajratib turadi.
 *
 * "Talaba ID / Promokod" yorlig'i va amal qilish muddati ATAYLAB yo'q — ular tafsilot
 * oynasida; lentadagi karta bir qarashda "nima, qanchaga, qayerda" ni ko'rsatadi.
 */
@Composable
private fun OfferCard(offer: DiscountOffer, onClick: () -> Unit) {
    val visual = categoryVisual(offer.bannerAccent)
    Column(
        Modifier.width(OfferCardWidth).scCard(radius = 22.dp, elevation = 6.dp, onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(OfferImageHeight)) {
            OfferImage(offer, visual)
            OfferScrim()

            if (offer.isDiscount && offer.discountPercent > 0) {
                Box(
                    Modifier.align(Alignment.TopStart).padding(9.dp)
                        .clip(RoundedCornerShape(11.dp)).background(Sc.buttonBrush)
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                ) {
                    ScText("−${offer.discountPercent}%", 12f, FontWeight.ExtraBold, Color.White, maxLines = 1)
                }
            }

            Column(
                Modifier.align(Alignment.BottomStart).padding(horizontal = 11.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                // Tur yorlig'i — kiyimda aynan shu "Futbolka / Ko'ylak / Poyabzal" ni ko'rsatadi.
                // Nomning USTIDA: tor kartada tepadagi chegirma nishoni bilan to'qnashib,
                // kesilib qolardi.
                val type = offer.typeLabel()
                if (type.isNotBlank()) {
                    Box(
                        Modifier.clip(RoundedCornerShape(9.dp))
                            .background(Color.White.copy(alpha = 0.94f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        // Oq yorliq fon rasmidan qat'i nazar oq — matn ham doim to'q.
                        ScText(type, 10.5f, FontWeight.ExtraBold, InkOnLight, maxLines = 1)
                    }
                }
                OfferPrice(offer)
                offer.location?.takeIf { it.isNotBlank() }?.let { location ->
                    ScText("📍 $location", 10.5f, FontWeight.SemiBold, Color.White.copy(alpha = 0.78f), maxLines = 1)
                }
            }
        }

        // Rasm ostidagi yagona qator — biznes nomi.
        ScText(
            offer.merchant, 12f, FontWeight.Bold, visual.accent,
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp), maxLines = 1,
        )
    }
}

/**
 * Matn ostidagi qorayish. Yuqori yarmi shaffof — rasm ko'rinib turadi; pastga borgan sari
 * to'qlashadi, shunda oq matn HAR QANDAY rasmda (och rasmda ham) o'qiladi.
 */
@Composable
private fun BoxScope.OfferScrim() {
    Box(
        Modifier.matchParentSize().background(
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.42f to Color.Black.copy(alpha = 0.12f),
                1f to Color.Black.copy(alpha = 0.82f),
            ),
        ),
    )
}

/** Kartaning rasm qismi — rasm (yoki emoji fon). */
@Composable
private fun OfferImage(offer: DiscountOffer, visual: CategoryVisual) {
    Box(Modifier.fillMaxSize().background(visual.tint)) {
        // Rasm yuklanayotganda kulrang shimmer turadi; havola yo'q yoki buzuq bo'lsa —
        // turning emoji si (karta hech qachon bo'sh qolmaydi).
        ScNetworkImage(
            url = offer.imageUrl,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (offer.emoji.isNotBlank()) {
                    Text(offer.emoji, style = TextStyle(fontSize = 52.sp))
                } else {
                    Icon(ScIcons.Cart, null, tint = visual.accent, modifier = Modifier.size(40.dp))
                }
            }
        }
    }
}

/** Narx qatori — rasm ustida, shuning uchun oq; eski narx ustidan chizilgan holda yonida. */
@Composable
private fun OfferPrice(offer: DiscountOffer) {
    if (offer.effectivePrice <= 0) return
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        ScText(
            "${offer.effectivePrice.formatAmount()} so'm", 15.5f, FontWeight.ExtraBold, Color.White,
            letterSpacing = -0.2f, maxLines = 1,
        )
        if (offer.isDiscount && offer.originalPrice > offer.finalPrice) {
            Text(
                offer.originalPrice.formatAmount(),
                style = scStyle(11f, FontWeight.SemiBold, Color.White.copy(alpha = 0.7f))
                    .copy(textDecoration = TextDecoration.LineThrough),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else if (offer.priceUnit.isNotBlank()) {
            ScText("/ ${offer.priceUnit}", 11f, FontWeight.SemiBold, Color.White.copy(alpha = 0.7f), maxLines = 1)
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

/** Oq yorliq ustidagi matn — mavzudan qat'i nazar to'q (fon doim oq). */
private val InkOnLight = Color(0xFF0F2A43)

// ---------------------------------------------------------------------------
// Fanlardan yordam — bir martalik topshiriqlar
// ---------------------------------------------------------------------------

/**
 * "Fanlardan yordam" — talabalar qo'ygan topshiriq e'lonlari (referat, masala, qo'lyozma,
 * IT ishi). Chegirma bo'limlaridan farqli: bu **so'rov**, taklif emas, shuning uchun
 * gorizontal lenta emas, vertikal qatorlar — har qatorda muddat ko'rinib turadi.
 *
 * E'lon bo'lmasa bo'lim butunlay yashiriladi (Home'da bo'sh sarlavha osilib qolmaydi).
 */
@Composable
private fun TasksSection(tasks: List<Listing>, onSeeAll: () -> Unit, onOpen: (String) -> Unit) {
    if (tasks.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        PaddedHeader(
            "📚 Fanlardan yordam",
            "Referat, masala, qo'lyozma va IT ishlari",
            onAction = onSeeAll,
        )
        Column(
            Modifier.padding(horizontal = Sc.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            tasks.take(3).forEach { listing -> TaskRow(listing, onOpen) }
        }
    }
}

@Composable
private fun TaskRow(listing: Listing, onOpen: (String) -> Unit) {
    val task = listing.taskDetails
    Row(
        Modifier.fillMaxWidth().scCard(radius = 20.dp, onClick = { onOpen(listing.id) }).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        ScIconTile(Sc.TintPink, size = 44.dp, radius = 15.dp) {
            Text(listing.emoji, style = TextStyle(fontSize = 20.sp))
        }
        Column(Modifier.weight(1f)) {
            ScText(listing.title, 14f, FontWeight.ExtraBold, Sc.Ink, maxLines = 1)
            // "Referat · 20 bet · Onlayn"
            val summary = task?.summary().orEmpty()
            if (summary.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                ScText(summary, 11.5f, FontWeight.Medium, Sc.Muted, maxLines = 1)
            }
            Spacer(Modifier.height(5.dp))
            ScText(listing.taskPriceLabel(), 13f, FontWeight.ExtraBold, Sc.Success, maxLines = 1)
        }
        // Muddat — bajaruvchi uchun eng muhim ma'lumot, shuning uchun o'ng chekkada ajratilgan.
        deadlineLabel(task?.deadline)?.let { deadline ->
            Box(
                Modifier.clip(RoundedCornerShape(10.dp)).background(Sc.TintPink)
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            ) {
                ScText(deadline, 10.5f, FontWeight.ExtraBold, Sc.Pink, maxLines = 1)
            }
        }
    }
}

/** "150 000 so'm" yoki "Kelishilgan". */
private fun Listing.taskPriceLabel(): String =
    if (isNegotiable) "Kelishilgan" else "${price.formatSum()} so'm"

/**
 * Muddat yorlig'i: "Bugun 18:00", "Ertaga 12:00", "5 kundan keyin" yoki "24.12".
 * O'tib ketgan muddat ko'rsatilmaydi — e'lon hali faol bo'lsa ham foyda bermaydi.
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
                // Nom + narx + manzil; xonalar/sherik tavsifi tafsilotda qoladi.
                ScIconTile(Sc.TintOrange, size = 50.dp, radius = 18.dp) { ScGlyph(house, 26.dp) }
                Spacer(Modifier.height(14.dp))
                ScText(listing.title, 16f, FontWeight.ExtraBold, Sc.Ink, maxLines = 1)
                Spacer(Modifier.height(10.dp))
                ScText(listing.rentLabel(), 15f, FontWeight.ExtraBold, Sc.Success, maxLines = 1)
                listing.locationLabel()?.let { location ->
                    Spacer(Modifier.height(4.dp))
                    ScText("📍 $location", 11.5f, FontWeight.SemiBold, Sc.MutedLight, maxLines = 1)
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

/**
 * Kartadagi manzil — birinchi filial manzili. Manzil ko'rsatilmagan e'londa `null`
 * (qator umuman chizilmaydi).
 */
private fun Listing.locationLabel(): String? =
    branches.firstOrNull()?.address?.takeIf { it.isNotBlank() }

/** "1 500 000 so'm / oy". */
private fun Listing.rentLabel(): String {
    if (isNegotiable) return "Kelishilgan"
    val suffix = rentalDetails?.period?.priceUnit?.suffix
    return listOfNotNull("${price.formatSum()} so'm", suffix).joinToString(" / ")
}

