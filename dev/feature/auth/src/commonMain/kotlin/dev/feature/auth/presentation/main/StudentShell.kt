package dev.feature.auth.presentation.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.core.designsystem.components.AppFontFamily
import dev.core.designsystem.components.AppIcons
import dev.core.designsystem.theme.AppPalette
import dev.core.designsystem.theme.appPalette
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

private enum class StudentTab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", AppIcons.Home),
    UNIVERSITY("university", "Universitet", AppIcons.GraduationCap),
    // Ilgari faqat "Ishlar" edi; endi bitta ekranda Ijara, Xizmatlar va Ish e'lonlari
    // tab bilan almashadi, shuning uchun umumiy nom.
    LISTINGS("listings", "E'lonlar", AppIcons.FileText),
    STUDENTS("students", "Student", AppIcons.Users),
}

private const val DISCOUNTS = "discounts"
private const val LISTING_DETAIL = "listing_detail"
private const val POST_LISTING = "post_listing"

/** Talaba qo'ya oladigan e'lon turlari — chegirma biznesnikidir. */
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
private const val CHAT = "chat"
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
    val palette = appPalette
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

    Box(Modifier.fillMaxSize().background(palette.bgBrush)) {
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
                    onOpenChat = { nav.navigateSafe(CHAT) },
                    onOpenNotifications = { nav.navigateSafe(NOTIFICATIONS) },
                    onOpenClubs = { nav.navigateSafe(CLUBS) },
                    onOpenDiscounts = { nav.navigateSafe(DISCOUNTS) },
                    // Ikkalasi ham o'sha ekran, lekin darrov kerakli tab ochilgan holda.
                    onOpenJobs = { openListingsKind(ListingKind.JOB) },
                    onOpenRentals = { openListingsKind(ListingKind.RENTAL) },
                    onOpenListing = { id -> nav.navigateSafe("$LISTING_DETAIL/${encodeArg(id)}") },
                    onOpenStudents = { selectTab(StudentTab.STUDENTS.route) },
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
                StudentTab.STUDENTS.route,
                enterTransition = TabEnter, exitTransition = TabExit,
                popEnterTransition = TabEnter, popExitTransition = TabExit,
            ) { StudentsScreen() }
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
            composable(CHAT) { ChatScreen(onBack = { nav.popSafe() }) }
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
                palette = palette,
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
    palette: AppPalette,
    modifier: Modifier = Modifier,
) {
    val barColor = if (palette.dark) Color(0xFF1A1630) else Color.White
    Box(modifier.fillMaxWidth().height(88.dp)) {
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .height(66.dp)
                .background(barColor, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavBarItem(StudentTab.HOME, current, onSelect, palette, Modifier.weight(1f))
            NavBarItem(StudentTab.UNIVERSITY, current, onSelect, palette, Modifier.weight(1f))
            Spacer(Modifier.weight(1f)) // markaziy FAB uchun joy
            NavBarItem(StudentTab.LISTINGS, current, onSelect, palette, Modifier.weight(1f))
            NavBarItem(StudentTab.STUDENTS, current, onSelect, palette, Modifier.weight(1f))
        }

        // Markaziy "Elon" FAB — talaba e'loni (ish/sotuv/xizmat)
        Column(
            Modifier.align(Alignment.TopCenter).offset(y = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(54.dp).clip(RoundedCornerShape(18.dp))
                    .background(palette.primaryBrush)
                    .clickable(onClick = onFab),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Plus, "Elon berish", tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(3.dp))
            Text("Elon", style = TextStyle(fontFamily = AppFontFamily, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = palette.primary))
        }
    }
}

@Composable
private fun NavBarItem(
    tab: StudentTab,
    current: String,
    onSelect: (String) -> Unit,
    palette: AppPalette,
    modifier: Modifier = Modifier,
) {
    val active = current == tab.route
    val tint = if (active) palette.primary else palette.inkFaint
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).clickable { onSelect(tab.route) }.padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(tab.icon, tab.label, tint = tint, modifier = Modifier.size(21.dp))
        Text(tab.label, style = TextStyle(fontFamily = AppFontFamily, fontSize = 9.sp, fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Bold, color = tint))
    }
}
