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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.core.common.push.PushRegistrar
import dev.core.common.push.PushRoute
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScImmersive
import dev.core.uikit.components.LocalScOverlayHost
import dev.core.uikit.components.ScOverlayHost
import dev.core.uikit.components.ScOverlayHostState
import org.koin.compose.koinInject
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
import dev.feature.listings.presentation.MyListingsScreen
import dev.feature.listings.presentation.PostListingScreen
import dev.feature.listings.presentation.detail.ListingDetailScreen
import dev.feature.listings.presentation.platform.rememberPhoneCaller
import dev.feature.connections.domain.model.StudentSummary
import dev.feature.connections.presentation.BlockedStudentsScreen
import dev.feature.connections.presentation.ConnectionsScreen
import dev.feature.connections.presentation.ConnectionsTab
import dev.feature.connections.presentation.StudentProfileSheet
import dev.feature.notifications.domain.model.NotificationTarget
import dev.feature.notifications.domain.repository.NotificationRepository
import dev.feature.notifications.presentation.NotificationsScreen
import dev.feature.settings.presentation.SettingsScreen
import dev.feature.university.presentation.MyUniversityScreen
import dev.feature.chat.presentation.ChatScreen
import dev.feature.chat.presentation.rememberPeerProfileSections
import dev.feature.home.presentation.HomeScreen
import dev.feature.profile.presentation.EditProfileScreen
import dev.feature.profile.presentation.ProfileScreen
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.ReadOnlyComposable
import dev.core.common.locale.AppLocale

private enum class StudentTab(val route: String) {
    HOME("home"),
    UNIVERSITY("university"),
    // Ilgari faqat "Ishlar" edi; endi bitta ekranda Ijara, Xizmatlar va Ish e'lonlari
    // tab bilan almashadi, shuning uchun umumiy nom.
    LISTINGS("listings"),
    /**
     * Bizneslardan keladigan e'lonlar ("Siz uchun" feed'i) — chegirmali va chegirmasiz.
     * [LISTINGS] dan farqi: u yerda TALABALAR e'lonlari (ijara, ish, yordam).
     *
     * Chat tab'i o'rniga keldi: suhbatlar bosh ekran sarlavhasidagi tugmadan ochiladi,
     * ya'ni pastki panelda ikkinchi kirish nuqtasi ortiqcha edi.
     */
    OFFERS("discounts"),
    ;

    /** Panel yorlig'i — enum konstantasiga qotirib bo'lmaydi, til daraxtdan keladi. */
    val label: String
        @Composable @ReadOnlyComposable get() = when (this) {
            HOME -> "Home"
            UNIVERSITY -> AppLocale.pick(en = "University", ru = "Университет", uz = "Universitet")
            LISTINGS -> AppLocale.pick(en = "Listings", ru = "Объявления", uz = "E'lonlar")
            OFFERS -> AppLocale.pick(en = "Offers", ru = "Предложения", uz = "Takliflar")
        }
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
        StudentTab.OFFERS -> ScIcons.DiscountTag
    }

/**
 * Suhbatlar — tab EMAS: bosh ekran sarlavhasidagi tugmadan, "Do'stlar" dagi "Xabar" dan
 * va push bildirishnomasidan ochiladi. Orqaga tugmasi bilan chiziladi.
 */
private const val CHAT = "chat"
private const val LISTING_DETAIL = "listing_detail"
private const val POST_LISTING = "post_listing"

/**
 * "Mening e'lonlarim" — foydalanuvchining O'Z e'lonlari, barcha statuslar bilan.
 * Yon paneldan ochiladi; tab emas, orqaga tugmasi bilan chiziladi.
 */
private const val MY_LISTINGS = "my_listings"

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
private const val PROFILE = "profile"
/**
 * "Do'stlar" — `Connections` bo'limi (qidiruv / so'rovlar / bog'langanlar).
 * Tab emas: Home'dagi "Studentlar → Barchasi" dan ochiladi.
 */
private const val CONNECTIONS = "connections"
private const val NOTIFICATIONS = "notifications"
private const val EDIT_PROFILE = "edit_profile"
private const val SETTINGS = "settings"

/** "Bloklanganlar" — Sozlamalar → Maxfiylik'dan ochiladi (tab emas, tafsilot ekrani). */
private const val BLOCKED = "blocked"

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
 * Talaba karkasi — pastki navigatsiya (Home / Universitet / E'lonlar / Takliflar) + markaziy
 * "E'lon" FAB (talaba e'lonlari: ijara, ish, xizmat, yordam).
 *
 * shellOffers() — bizneslardan keladigan e'lonlar feed'i, faqat O'QISH uchun. Biznesmenning
 * e'lon qo'yish oqimi bu yerda YO'Q — u [BusinessShell] da.
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

    /** `Connections` ekranini konkret bo'lim ochilgan holda ochadi (Home'dagi tugmalar). */
    val openConnectionsTab: (ConnectionsTab) -> Unit = { tab ->
        nav.navigateSafe("$CONNECTIONS?tab=${tab.name}")
    }

    /**
     * Suhbatni konkret talaba bilan ochadi ("Do'stlar" → "Xabar").
     *
     * Chat tab bo'lmagani uchun oddiy tafsilot ekrani kabi stack'ga qo'yiladi — orqaga
     * bosilsa kelingan joyga qaytadi.
     */
    val openChatWith: (String) -> Unit = { studentId ->
        nav.navigateSafe("$CHAT?studentId=${encodeArg(studentId)}")
    }

    // --- Push -----------------------------------------------------------------------------
    //
    // Bu ekran faqat sessiya ochiq bo'lganda ko'rinadi, ya'ni qurilma tokenini backendga
    // yozish uchun to'g'ri joy. `ApiAuthRepository` buni KIRISH paytida qiladi; bu yerdagisi
    // **avtomatik kirish** holati uchun (saqlangan sessiya bilan ochilgan ilova).
    val pushRegistrar = koinInject<PushRegistrar>()
    LaunchedEffect(Unit) { runCatching { pushRegistrar.onSessionStarted() } }

    // Bildirishnoma bosilgan bo'lsa — konvertdagi ekranni ochamiz. Qiymat ilova ishga
    // tushishidan oldin ham qo'yilgan bo'lishi mumkin, shuning uchun oqim sifatida kuzatiladi.
    val notifications = koinInject<NotificationRepository>()
    val pendingPush by PushRoute.pending.collectAsState()
    LaunchedEffect(pendingPush) {
        val payload = pendingPush ?: return@LaunchedEffect
        PushRoute.consume()

        // §2.1 — bosilgan push ro'yxatdagi qatorni ham o'qilgan qiladi. Busiz foydalanuvchi
        // bildirishnomani ko'rgan bo'lsa ham `unreadCount` uni sanashda davom etardi va
        // qo'ng'iroq ikonkasidagi raqam hech qachon nolga tushmasdi.
        payload.notificationId?.takeIf { it.isNotBlank() }?.let { notifications.markRead(it) }

        // Konvertda `targetType` bo'lsa — o'sha; bo'lmasa eski chat push'i (`conversationId`).
        val target = NotificationTarget.of(payload.targetType, payload.targetId)
            .takeIf { it != NotificationTarget.None }
            ?: payload.conversationId
                ?.takeIf { it.isNotBlank() }
                ?.let { NotificationTarget.Chat(it) }
            ?: return@LaunchedEffect

        // Push'dan kelinganda ekran Home ustiga qo'yiladi: orqaga bosilsa ilova yopilmay,
        // bosh ekranga tushadi. (Ro'yxatdan bosilganda esa stack saqlanadi — qarang
        // [openNotificationTarget].)
        nav.openNotificationTarget(target) { popUpTo(StudentTab.HOME.route) }
    }

    /**
     * Ekranlarning MODAL qatlamlari (bosh ekrandagi yon panel) shu yerda chiziladi — pastki
     * navigatsiya panelidan KEYIN. Aks holda panel modal qatlamning ustida qolib, qorayish
     * qatlami uni qoraytirmasdi va tugmalari bosilaverardi (`ScOverlayHost` izohiga q.).
     */
    val overlay = remember { ScOverlayHostState() }

    /**
     * Ochilgan talaba profili — **butun karkas uchun bitta** varaq.
     *
     * Talaba ilovaning ko'p joyida ko'rinadi (bosh ekran, "Do'stlar", universitet, qidiruv),
     * profil varag'ining bo'limlari esa story va chat modullarida yashaydi. Har ekran o'z
     * varag'ini chizsa, o'sha ekranlar chat/story modullariga bog'lanardi — shuning uchun
     * ular faqat "talaba bosildi" deb aytadi, varaqni esa shu yer ochadi.
     */
    var profileStudent by remember { mutableStateOf<StudentSummary?>(null) }

    CompositionLocalProvider(LocalScOverlayHost provides overlay) {
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
                        onOpenChat = { nav.navigateSafe(CHAT) },
                        onOpenNotifications = { nav.navigateSafe(NOTIFICATIONS) },
                        // shellOffers() tab'ini KONKRET bo'lim ochilgan holda ochadi ("Ovqatlar →
                        // Barchasi"). [openListingsKind] dagi kabi `restoreState` YO'Q: saqlangan
                        // holat tiklansa Navigation eski argumentlarni qaytarib, yangi `?group=`
                        // bekor bo'lardi. Kalitsiz — odatdagi to'liq feed.
                        onOpenDiscounts = { groupKey ->
                            val key = encodeArg(groupKey)
                            val route = StudentTab.OFFERS.route
                            nav.navigateSafe(if (key == null) route else "$route?group=$key") {
                                popUpTo(StudentTab.HOME.route) { saveState = true }
                            }
                        },
                        // Karta bosildi — o'sha bo'lim feed'i + darhol ochiladigan tafsilot.
                        onOpenOffer = { offerId, groupKey ->
                            val route = buildString {
                                append(StudentTab.OFFERS.route)
                                append("?group=").append(encodeArg(groupKey).orEmpty())
                                append("&offer=").append(encodeArg(offerId).orEmpty())
                            }
                            nav.navigateSafe(route) {
                                popUpTo(StudentTab.HOME.route) { saveState = true }
                            }
                        },
                        // "Fanlardan yordam" — o'sha ekran, Yordam tab'i ochilgan holda.
                        onOpenTasks = { openListingsKind(ListingKind.TASK) },
                        // shellListings() ekrani, darrov ijara tab'i ochilgan holda.
                        onOpenRentals = { openListingsKind(ListingKind.RENTAL) },
                        onOpenListing = { id -> nav.navigateSafe("$LISTING_DETAIL/${encodeArg(id)}") },
                        onOpenStudents = { nav.navigateSafe(CONNECTIONS) },
                        onOpenStudentSearch = { openConnectionsTab(ConnectionsTab.SEARCH) },
                        onOpenStudentRequests = { openConnectionsTab(ConnectionsTab.REQUESTS) },
                        onOpenChatWith = openChatWith,
                        // Yon paneldagi bo'limlar. Universitet va E'lonlar — pastki paneldagi
                        // TABLAR, shuning uchun oddiy tab almashish (holat saqlanadi).
                        onOpenUniversity = { selectTab(StudentTab.UNIVERSITY.route) },
                        onOpenListings = { selectTab(StudentTab.LISTINGS.route) },
                        onOpenMyListings = { nav.navigateSafe(MY_LISTINGS) },
                        // Sozlamalar — tafsilot ekrani, stack'ga qo'yiladi (orqaga → Home).
                        onOpenSettings = { nav.navigateSafe(SETTINGS) },
                    )
                }
                composable(
                    StudentTab.UNIVERSITY.route,
                    enterTransition = TabEnter, exitTransition = TabExit,
                    popEnterTransition = TabEnter, popExitTransition = TabExit,
                ) {
                    MyUniversityScreen(
                        onOpenListing = { id -> nav.navigateSafe("$LISTING_DETAIL/${encodeArg(id)}") },
                        onOpenTasks = { openListingsKind(ListingKind.TASK) },
                        onOpenStudent = { profileStudent = it },
                    )
                }
                composable(
                    // shellOffers() — bizneslardan keladigan e'lonlar, pastki paneldagi tab.
                    // `?group=` — Home'dagi bo'lim ("Ovqatlar") tugmasidan; bo'sh bo'lsa butun feed.
                    // `?offer=` — Home'dagi kartaning O'ZI bosilgan holat: bo'lim feed'i
                    // ochiladi va ustiga darhol o'sha e'lonning tafsiloti chiqadi.
                    route = "${StudentTab.OFFERS.route}?group={group}&offer={offer}",
                    enterTransition = TabEnter, exitTransition = TabExit,
                    popEnterTransition = TabEnter, popExitTransition = TabExit,
                    arguments = listOf(
                        navArgument("group") { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument("offer") { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) { entry ->
                    // Tab — orqaga tugmasisiz (`onBack` berilmaydi).
                    DiscountsScreen(
                        initialGroupKey = entry.arguments?.getString("group"),
                        initialOfferId = entry.arguments?.getString("offer"),
                    )
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
                    // `?studentId=` — "Do'stlar" ekranidan "Xabar" bosilganda;
                    // `?conversationId=` — push bosilganda. Argumentsiz — suhbatlar ro'yxati.
                    route = "$CHAT?studentId={studentId}&conversationId={conversationId}",
                    arguments = listOf(
                        navArgument("studentId") { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument("conversationId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) { entry ->
                    ChatScreen(
                        // Tab emas — sarlavhada orqaga tugmasi turadi.
                        onBack = { nav.popSafe() },
                        openStudentId = entry.arguments?.getString("studentId"),
                        openConversationId = entry.arguments?.getString("conversationId"),
                        // Yangi suhbat faqat BOG'LANGAN odam bilan boshlanadi — "Do'stlar"
                        // ro'yxatidagi «Xabar» tugmasi uni o'zi ochadi.
                        onNewChat = { nav.navigateSafe(CONNECTIONS) },
                    )
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
                        // Talabada biznes bo'limi ko'rinmaydi.
                        showMyBusiness = false,
                    )
                }
                composable(
                    route = "$CONNECTIONS?tab={tab}",
                    arguments = listOf(
                        navArgument("tab") { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) { entry ->
                    ConnectionsScreen(
                        onBack = { nav.popSafe() },
                        onOpenStudent = { profileStudent = it },
                        // Chat tab'i suhbatni o'zi ochadi (`POST /v1/conversations` idempotent).
                        onOpenChat = { studentId, _ -> openChatWith(studentId) },
                        // Home'dagi tugmalar kerakli bo'limni darrov ochadi. Nomi noto'g'ri
                        // kelsa — sukut bo'yicha "Do'stlar" (ekranning o'z boshlang'ich holati).
                        initialTab = entry.arguments?.getString("tab")
                            ?.let { name -> ConnectionsTab.entries.firstOrNull { it.name == name } },
                    )
                }
                composable(MY_LISTINGS) {
                    MyListingsScreen(
                        onCreate = { nav.navigateSafe(POST_LISTING) },
                        // Tahrirlash — e'lon qo'yish oqimining o'zi, tayyor qiymatlar bilan.
                        onEdit = { id -> nav.navigateSafe("$POST_LISTING?listingId=${encodeArg(id)}") },
                        onBack = { nav.popSafe() },
                        // Chegirmalar biznesniki — talabaning e'lonlari orasida ko'rinmaydi.
                        filterDiscount = false,
                    )
                }
                composable(NOTIFICATIONS) {
                    NotificationsScreen(
                        onBack = { nav.popSafe() },
                        onOpenTarget = { target -> nav.openNotificationTarget(target) },
                    )
                }
                composable(EDIT_PROFILE) { EditProfileScreen(onBack = { nav.popSafe() }) }
                composable(SETTINGS) {
                    SettingsScreen(
                        onBack = { nav.popSafe() },
                        onEditProfile = { nav.navigateSafe(EDIT_PROFILE) },
                        onLoggedOut = onLoggedOut,
                        onOpenBlocked = { nav.navigateSafe(BLOCKED) },
                    )
                }
                composable(BLOCKED) { BlockedStudentsScreen(onBack = { nav.popSafe() }) }
            }

            // Klaviatura ochilganda pastki panel ko'rsatilmaydi: kontent klaviatura ustiga
            // ko'tarilgani uchun panel matn maydonining tagida osilib qolardi va joy egallardi.
            val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
            // Chat endi tab emas — panel u yerda o'z-o'zidan chizilmaydi (`current in tabRoutes`).
            // `ScImmersive` — to'liq ekranli qatlam (xarita) ochiq: u ekranning har bir
            // pikselini talab qiladi va panel uning tugmalariga yopishib turardi.
            if (current in tabRoutes && !keyboardOpen && !ScImmersive.active) {
                BottomBar(
                    current = current,
                    onSelect = selectTab,
                    onFab = { nav.navigateSafe(POST_LISTING) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }

            // Modal qatlamlar — karkasning ENG OXIRGI bolasi, ya'ni pastki panel ham, FAB ham
            // ularning TAGIDA qoladi.
            ScOverlayHost(overlay)
        }

        profileStudent?.let { student ->
            StudentProfileSheet(
                studentId = student.id,
                // Ro'yxatdagi qisqa profil darrov uzatiladi — varaq bo'sh holatda ochilmasin.
                known = student,
                onClose = { profileStudent = null },
                // Bosh ekran va chatdagi profil bilan AYNAN bir xil bo'limlar.
                sections = rememberPeerProfileSections(student.id),
                onOpenChat = { id ->
                    profileStudent = null
                    openChatWith(id)
                },
            )
        }
    }
}

/**
 * Bildirishnoma → ekran.
 *
 * Xarita AYNAN shu yerda: `notifications` moduli navigatsiya grafini ko'rmaydi va ko'rmasligi
 * ham kerak — u faqat "nimaga tegishli" ligini ([NotificationTarget]) biladi, "qaysi route"
 * ekanini emas. Shu sabab route nomlari o'zgarsa yoki ekran ko'chsa, tuzatish faqat shu
 * funksiyada bo'ladi.
 *
 * Ro'yxatdan bosilganda hamma yo'nalish stack'ga QO'YILADI (sukut bo'yicha [options] bo'sh):
 * orqaga bosilganda foydalanuvchi bildirishnomalar ro'yxatiga qaytadi va qolganini ham ko'rib
 * chiqa oladi. Push'dan kelinganda esa chaqiruvchi [options] orqali stack'ni Home'ga tushiradi —
 * aks holda "Orqaga" bosilishi ilovani butunlay yopardi.
 */
private fun NavController.openNotificationTarget(
    target: NotificationTarget,
    options: NavOptionsBuilder.() -> Unit = {},
) {
    when (target) {
        is NotificationTarget.Chat ->
            navigateSafe("$CHAT?conversationId=${encodeArg(target.conversationId)}", options)
        // Suhbat id'si noma'lum — ro'yxatni ochamiz, yangi xabar uning tepasida turadi.
        NotificationTarget.Conversations -> navigateSafe(CHAT, options)
        is NotificationTarget.Listing ->
            navigateSafe("$LISTING_DETAIL/${encodeArg(target.listingId)}", options)
        NotificationTarget.ConnectionRequests ->
            navigateSafe("$CONNECTIONS?tab=${ConnectionsTab.REQUESTS.name}", options)
        NotificationTarget.MyListings -> navigateSafe(MY_LISTINGS, options)
        NotificationTarget.Profile -> navigateSafe(PROFILE, options)
        // Ekran almashmaydi — bildirishnoma faqat o'qilgan bo'ladi (ekran o'zi hal qiladi).
        NotificationTarget.None -> Unit
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
    Box(modifier.fillMaxWidth()) {
        // Panel ostidagi yumshoq o'tish. Bunsiz surilayotgan kontent panelning qirrasiga
        // TIQ etib kelib to'xtardi va panel kontentga "yopishib" turgandek ko'rinardi.
        // Gradient fon rangiga tushadi, ya'ni yorug'/to'q rejimda ham bir xil ishlaydi.
        Box(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(BarScrimHeight)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Sc.Bg.copy(alpha = 0.75f),
                        1f to Sc.Bg,
                    ),
                ),
        )
        Box(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        ) {
        Row(
            Modifier.fillMaxWidth()
                .height(BarHeight)
                .scSoftShadow(18.dp, shape)
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
            NavBarItem(StudentTab.OFFERS, current, onSelect)
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
            Icon(ScIcons.Plus, discountsStrings().postListing, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        }
    }
}

private val BarHeight = 64.dp

/** Panel ostidagi o'tish gradientining balandligi (panel + FAB + navigatsiya paneli). */
private val BarScrimHeight = 132.dp

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
        // Tanlangan tab faqat rang bilan ajratiladi — ikonka ostida nuqta yo'q.
        Icon(
            tab.icon, tab.label,
            tint = if (active) Sc.Brand else Sc.NavIdle,
            modifier = Modifier.size(24.dp),
        )
    }
}
