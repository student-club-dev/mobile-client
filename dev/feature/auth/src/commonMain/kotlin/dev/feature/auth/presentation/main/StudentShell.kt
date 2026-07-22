package dev.feature.auth.presentation.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.scSoftShadow
import dev.core.uikit.components.scBrandShadow
import dev.core.uikit.theme.Sc
import dev.core.navigation.encodeArg
import dev.core.navigation.PushEnter
import dev.core.navigation.PushExit
import dev.core.navigation.PopEnter
import dev.core.navigation.PopExit
import dev.core.navigation.TabEnter
import dev.core.navigation.TabExit
import dev.core.navigation.navigateSafe
import dev.core.navigation.popSafe
import dev.feature.listings.domain.model.ListingKind
import dev.feature.listings.presentation.browse.ListingsBrowseScreen
import dev.feature.listings.presentation.PostListingScreen
import dev.feature.listings.presentation.detail.ListingDetailScreen
import dev.feature.listings.presentation.platform.rememberPhoneCaller
import dev.feature.students.presentation.StudentsScreen
import dev.feature.notifications.presentation.NotificationsScreen
import dev.feature.clubs.presentation.ClubsScreen
import dev.feature.settings.presentation.SettingsScreen
import dev.feature.university.presentation.MyUniversityScreen
import dev.feature.ads.presentation.PostAdScreen
import dev.feature.chat.presentation.ChatScreen
import dev.feature.home.presentation.HomeScreen
import dev.feature.profile.presentation.EditProfileScreen
import dev.feature.profile.presentation.ProfileScreen

private enum class StudentTab(val route: String, val label: String) {
    HOME("home", "Home"),
    UNIVERSITY("university", "Universitet"),
    // Ilgari faqat "Ishlar" edi; endi bitta ekranda Ijara, Xizmatlar va Ish e'lonlari
    // tab bilan almashadi, shuning uchun umumiy nom.
    LISTINGS("listings", "E'lonlar"),
    CHAT("chat", "Xabarlar"),
}

/**
 * Tab ikonasi. `enum` konstruktorida saqlab bo'lmaydi: ikonalar vektor-drawable
 * resurslaridan o'qiladi va faqat kompozitsiya ichida mavjud bo'ladi.
 */
private val StudentTab.icon: ImageVector
    @Composable get() = when (this) {
        StudentTab.HOME -> ScIcons.Home
        StudentTab.UNIVERSITY -> ScIcons.Cap
        StudentTab.LISTINGS -> ScIcons.FileText
        StudentTab.CHAT -> ScIcons.Message
    }

private const val DISCOUNTS = "discounts"
private const val LISTING_DETAIL = "listing_detail"
private const val POST_LISTING = "post_listing"

/**
 * Talaba qo'ya oladigan e'lon turlari. [ListingKind.DISCOUNT] yo'q — u biznes turini
 * (Kafe, Game Club...) so'raydi va shaxsiy e'longa to'g'ri kelmaydi, u [BusinessShell] da.
 *
 * Yangi tur qo'shilganda shu ro'yxatga ham qo'shish ESDAN CHIQMASIN — bu qo'lda tuzilgan
 * ro'yxat, `ListingKind.entries` emas.
 */
private val studentListingKinds = listOf(
    ListingKind.TASK,
    ListingKind.RENTAL,
    ListingKind.SERVICE,
    ListingKind.JOB,
)
private const val POST_AD = "post_ad"
private const val PROFILE = "profile"
/** Endi tab emas — Home'dagi "Studentlar / Barchasi" dan ochiladi. */
private const val STUDENTS = "students"
private const val NOTIFICATIONS = "notifications"
private const val EDIT_PROFILE = "edit_profile"
private const val SETTINGS = "settings"
private const val CLUBS = "clubs"

private val tabRoutes = StudentTab.entries.map { it.route }.toSet()

/**
 * Route'dagi `?kind=...` qismini tashlaydi.
 *
 * Pastki panel `current in tabRoutes` sharti bilan chiziladi, argumentli route esa
 * "listings?kind={kind}" ko'rinishida keladi — tozalamasak o'z tab'ida panel yo'qolib qoladi.
 */
private fun String.toTabRoute(): String = substringBefore("?")

/** Home'dan kelgan bo'lim nomi. Noma'lum yoki bo'sh bo'lsa — Ish e'lonlari. */
private fun String?.toListingKind(): ListingKind =
    ListingKind.entries.firstOrNull { it.name == this } ?: ListingKind.JOB

/**
 * Talaba karkasi — pastki navigatsiya (Home / Chegirma / Ishlar / Student) + markaziy "Elon" FAB
 * (talaba e'lonlari: ish, sotuv, xizmat). Biznesmen chegirma e'lonlari bu yerda YO'Q — ular
 * [BusinessShell] da.
 */
@Composable
fun StudentShell(onLoggedOut: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route?.toTabRoute() ?: StudentTab.HOME.route

    // Tab almashish — holat saqlanadi/tiklanadi, dublikat yaratmaydi (navigateSafe).
    val selectTab: (String) -> Unit = { route ->
        nav.navigateSafe(route) {
            popUpTo(StudentTab.HOME.route) { saveState = true }
            restoreState = true
        }
    }

    /**
     * E'lonlar tab'ini KONKRET bo'lim ochilgan holda ochadi (Home'dagi "Barchasi" tugmalari).
     *
     * [selectTab] dan farqi — `restoreState` YO'Q. Saqlangan holat tiklanganda Navigation
     * eski `NavBackStackEntry` ni argumentlari bilan qaytaradi va yangi `?kind=...` bekor
     * bo'ladi — ya'ni "Ijara" bosilib Ish tab'i ochilib qolardi. Evaziga bu yo'l bilan
     * kirilganda ekranning scroll holati tiklanmaydi; to'g'ri bo'lim muhimroq.
     */
    val openListingsKind: (ListingKind) -> Unit = { kind ->
        nav.navigateSafe("${StudentTab.LISTINGS.route}?kind=${kind.name}") {
            popUpTo(StudentTab.HOME.route) { saveState = true }
        }
    }

    Box(Modifier.fillMaxSize().background(Sc.Bg)) {
        NavHost(
            navController = nav,
            startDestination = StudentTab.HOME.route,
            modifier = Modifier.fillMaxSize(),
            // Sukut — tafsilot ekranlari uchun push/pop siljishi.
            // Tab bo'limlari o'z composable'ida fade'ga almashtiriladi (pastga qarang).
            enterTransition = PushEnter,
            exitTransition = PushExit,
            popEnterTransition = PopEnter,
            popExitTransition = PopExit,
        ) {
            composable(
                StudentTab.HOME.route,
                enterTransition = TabEnter, exitTransition = TabExit,
                popEnterTransition = TabEnter, popExitTransition = TabExit,
            ) {
                HomeScreen(
                    onOpenProfile = { nav.navigateSafe(PROFILE) },
                    onOpenChat = { selectTab(StudentTab.CHAT.route) },
                    onOpenNotifications = { nav.navigateSafe(NOTIFICATIONS) },
                    onOpenClubs = { nav.navigateSafe(CLUBS) },
                    onOpenDiscounts = { nav.navigateSafe(DISCOUNTS) },
                    // Ikkalasi ham o'sha ekran, lekin darrov kerakli tab ochilgan holda.
                    onOpenJobs = { openListingsKind(ListingKind.JOB) },
                    onOpenRentals = { openListingsKind(ListingKind.RENTAL) },
                    onOpenTasks = { openListingsKind(ListingKind.TASK) },
                    onOpenListing = { id -> nav.navigateSafe("$LISTING_DETAIL/${encodeArg(id)}") },
                    onOpenStudents = { nav.navigateSafe(STUDENTS) },
                )
            }
            composable(
                StudentTab.UNIVERSITY.route,
                enterTransition = TabEnter, exitTransition = TabExit,
                popEnterTransition = TabEnter, popExitTransition = TabExit,
            ) { MyUniversityScreen() }
            composable(DISCOUNTS) {
                // "Siz uchun" — Home'dan ochiladi (endi pastki tab emas). Orqaga qaytadi.
                DiscountsScreen(onBack = { nav.popSafe() })
            }
            // Ijara / Xizmatlar / Ish e'lonlari — uchalasi bitta ekranda, tepadagi tab bilan.
            // `kind` argumenti Home'dan konkret bo'limga o'tish uchun (masalan to'g'ridan-to'g'ri
            // Ijara'ga), bo'sh bo'lsa Ish e'lonlari ochiladi.
            composable(
                route = "${StudentTab.LISTINGS.route}?kind={kind}",
                enterTransition = TabEnter, exitTransition = TabExit,
                popEnterTransition = TabEnter, popExitTransition = TabExit,
                arguments = listOf(
                    navArgument("kind") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                ListingsBrowseScreen(
                    onOpenListing = { id -> nav.navigateSafe("$LISTING_DETAIL/${encodeArg(id)}") },
                    initialKind = entry.arguments?.getString("kind").toListingKind(),
                )
            }
            composable(
                route = "$LISTING_DETAIL/{listingId}",
                arguments = listOf(navArgument("listingId") { type = NavType.StringType }),
            ) { entry ->
                val onCall = rememberPhoneCaller()
                ListingDetailScreen(
                    listingId = entry.arguments?.getString("listingId").orEmpty(),
                    onBack = { nav.popSafe() },
                    onCall = onCall,
                )
            }
            composable(
                StudentTab.CHAT.route,
                enterTransition = TabEnter, exitTransition = TabExit,
                popEnterTransition = TabEnter, popExitTransition = TabExit,
            ) { ChatScreen() } // tab — orqaga tugmasisiz
            composable(
                route = "$POST_AD?adId={adId}",
                arguments = listOf(navArgument("adId") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { entry ->
                // Eski (feature:ads) e'lonlarini TAHRIRLASH uchun qoldirilgan — yangi e'lon
                // bu yerdan qo'yilmaydi, "Elon" tugmasi POST_LISTING ga boradi.
                PostAdScreen(onClose = { nav.popSafe() }, editAdId = entry.arguments?.getString("adId"))
            }
            composable(
                route = "$POST_LISTING?listingId={listingId}",
                arguments = listOf(
                    navArgument("listingId") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                PostListingScreen(
                    onClose = { nav.popSafe() },
                    onPublished = { nav.popSafe() },
                    editListingId = entry.arguments?.getString("listingId"),
                    // Chegirma e'loni biznes turini so'raydi — u BusinessShell'da qoladi.
                    availableKinds = studentListingKinds,
                )
            }
            composable(PROFILE) {
                ProfileScreen(
                    onBack = { nav.popSafe() },
                    onLoggedOut = onLoggedOut,
                    onEditProfile = { nav.navigateSafe(EDIT_PROFILE) },
                    onOpenSettings = { nav.navigateSafe(SETTINGS) },
                    onEditAd = { adId -> nav.navigateSafe("$POST_AD?adId=${encodeArg(adId)}") },
                    // Talabada biznes bo'limi ko'rinmaydi.
                    showMyBusiness = false,
                )
            }
            composable(STUDENTS) { StudentsScreen(onBack = { nav.popSafe() }) }
            composable(NOTIFICATIONS) { NotificationsScreen(onBack = { nav.popSafe() }) }
            composable(CLUBS) { ClubsScreen(onBack = { nav.popSafe() }) }
            composable(EDIT_PROFILE) { EditProfileScreen(onBack = { nav.popSafe() }) }
            composable(SETTINGS) {
                SettingsScreen(
                    onBack = { nav.popSafe() },
                    onEditProfile = { nav.navigateSafe(EDIT_PROFILE) },
                    onLoggedOut = onLoggedOut,
                )
            }
        }

        if (current in tabRoutes) {
            BottomBar(
                current = current,
                onSelect = selectTab,
                onFab = { nav.navigateSafe(POST_LISTING) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun BottomBar(
    current: String,
    onSelect: (String) -> Unit,
    onFab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = remember { NotchedBarShape(BarCorner, FabCradleRadius, FabCenterY) }
    Box(
        modifier.fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth()
                .height(BarHeight)
                .scSoftShadow(14.dp, shape)
                .clip(shape)
                // Karta yuzasi — dark rejimda to'q variantga o'tadi.
                .background(Sc.Card),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavBarItem(StudentTab.HOME, current, onSelect)
            NavBarItem(StudentTab.UNIVERSITY, current, onSelect)
            // O'rtadagi joy FAB uchun bo'sh qoldiriladi (o'yiq shu yerda).
            Spacer(Modifier.weight(1f))
            NavBarItem(StudentTab.LISTINGS, current, onSelect)
            NavBarItem(StudentTab.CHAT, current, onSelect)
        }

        // FAB panelning BOLASI emas — panel `clip` qilingan, ichida bo'lsa kesilib qolardi.
        Box(
            Modifier.align(Alignment.TopCenter)
                .offset(y = FabCenterY - FabSize / 2)
                .size(FabSize)
                .scBrandShadow(16.dp, CircleShape)
                .clip(CircleShape)
                .background(Sc.tileBrush)
                .clickable(onClick = onFab),
            contentAlignment = Alignment.Center,
        ) {
            Icon(ScIcons.Plus, "Elon berish", tint = Color.White, modifier = Modifier.size(26.dp))
        }
    }
}

private val BarHeight = 64.dp

/** To'liq yumaloq chekka — balandlikning yarmi. */
private val BarCorner = BarHeight / 2

private val FabSize = 56.dp

/** FAB bilan o'yiq qirrasi orasidagi bo'shliq. */
private val FabCradleGap = 6.dp
private val FabCradleRadius = FabSize / 2 + FabCradleGap

/** FAB markazi — panelning yuqori qirrasidan shuncha TEPADA (manfiy). */
private val FabCenterY = (-10).dp

/**
 * Panel shakli: to'liq yumaloq to'rtburchakdan yuqori markazda aylana kesib olinadi —
 * FAB atrofida bir tekis bo'shliq qoladi.
 *
 * Eslatma: bunday shakl qavariq (convex) emas. Android API 29 dan boshlab ixtiyoriy
 * yo'l uchun soya chizadi; undan eskisida soya tushmaydi, shakl esa to'g'ri qoladi.
 */
private class NotchedBarShape(
    private val corner: Dp,
    private val notchRadius: Dp,
    private val notchCenterY: Dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val bar = Path().apply {
            addRoundRect(
                RoundRect(
                    Rect(0f, 0f, size.width, size.height),
                    CornerRadius(with(density) { corner.toPx() }),
                ),
            )
        }
        val notch = Path().apply {
            addOval(
                Rect(
                    center = Offset(size.width / 2f, with(density) { notchCenterY.toPx() }),
                    radius = with(density) { notchRadius.toPx() },
                ),
            )
        }
        return Outline.Generic(Path().apply { op(bar, notch, PathOperation.Difference) })
    }
}

@Composable
private fun RowScope.NavBarItem(tab: StudentTab, current: String, onSelect: (String) -> Unit) {
    val active = current == tab.route
    Column(
        Modifier.weight(1f).fillMaxHeight().clickable { onSelect(tab.route) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            tab.icon, tab.label,
            tint = if (active) Sc.Brand else Sc.NavIdle,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier.size(5.dp)
                .background(if (active) Sc.Brand else Color.Transparent, CircleShape),
        )
    }
}
