package dev.feature.business

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.core.designsystem.components.AppFontFamily
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.core.designsystem.components.AppIcons
import dev.core.designsystem.theme.AppPalette
import dev.core.designsystem.theme.appPalette
import dev.feature.discounts.presentation.MyListingsScreen
import dev.feature.discounts.presentation.PostListingScreen

private const val LISTINGS = "listings"
private const val POST_LISTING = "post_listing"
private const val PROFILE = "business_profile"
private const val BUSINESS_EDIT = "business_edit"
private const val SETTINGS = "settings"

/**
 * Biznesmen karkasi — pastki navigatsiya YO'Q. Asosiy ekranda ikkita tur bor:
 * **Chegirma** va **E'lon**, tepadagi segment selektor bilan almashtiriladi. Har biri
 * o'z ro'yxatini va o'z yaratish oqimini ochadi. Yuqorida gradient profil tugmasi,
 * o'ng-past burchakda "Yangi" pill tugmasi. Talaba bo'limlari umuman yo'q.
 */
@Composable
fun BusinessShell(
    onLoggedOut: () -> Unit,
    // Sozlamalar ekrani auth modulida — shu slot orqali beriladi (business auth'ga bog'lanmaydi).
    settingsContent: @Composable (onBack: () -> Unit) -> Unit,
) {
    val palette = appPalette
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route ?: LISTINGS

    // Tepadagi ikki tur: Chegirma (true) yoki E'lon (false). Ro'yxat va yaratish shunga qarab.
    var discountTab by remember { mutableStateOf(true) }

    Box(Modifier.fillMaxSize().background(palette.bgBrush)) {
        NavHost(navController = nav, startDestination = LISTINGS, modifier = Modifier.fillMaxSize()) {
            composable(LISTINGS) {
                Column(Modifier.fillMaxSize()) {
                    BusinessTopBar(
                        discount = discountTab,
                        onSelect = { discountTab = it },
                        onProfile = { nav.navigate(PROFILE) },
                        palette = palette,
                    )
                    MyListingsScreen(
                        onCreate = { nav.navigate("$POST_LISTING?discount=$discountTab") },
                        onEdit = { listingId -> nav.navigate("$POST_LISTING?listingId=$listingId") },
                        showHeaderCreate = false,
                        showHeader = false,
                        filterDiscount = discountTab,
                    )
                }
            }
            composable(
                route = "$POST_LISTING?listingId={listingId}&discount={discount}",
                arguments = listOf(
                    navArgument("listingId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("discount") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                PostListingScreen(
                    onClose = { nav.popBackStack() },
                    onPublished = {
                        nav.navigate(LISTINGS) {
                            popUpTo(LISTINGS) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    editListingId = entry.arguments?.getString("listingId"),
                    initialDiscount = entry.arguments?.getString("discount")?.toBooleanStrictOrNull(),
                )
            }
            composable(PROFILE) {
                BusinessAccountScreen(
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(BUSINESS_EDIT) },
                    onOpenListings = { nav.popBackStack() },
                    onOpenSettings = { nav.navigate(SETTINGS) },
                    onLoggedOut = onLoggedOut,
                )
            }
            composable(BUSINESS_EDIT) {
                BusinessEditScreen(onBack = { nav.popBackStack() })
            }
            composable(SETTINGS) {
                settingsContent { nav.popBackStack() }
            }
        }

        // O'ng-past: "Yangi" extended pill — tanlangan turga qarab yaratadi.
        if (current == LISTINGS) {
            CreateFab(
                discount = discountTab,
                palette = palette,
                onClick = { nav.navigate("$POST_LISTING?discount=$discountTab") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 26.dp),
            )
        }
    }
}

/** Yuqori panel: label + tur sarlavhasi + gradient profil tugmasi + segment selektor. */
@Composable
private fun BusinessTopBar(
    discount: Boolean,
    onSelect: (Boolean) -> Unit,
    onProfile: () -> Unit,
    palette: AppPalette,
) {
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 52.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "BIZNES MARKAZI",
                    style = TextStyle(
                        fontFamily = AppFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.4.sp,
                        color = palette.primary,
                    ),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (discount) "Chegirmalarim" else "E'lonlarim",
                    style = TextStyle(
                        fontFamily = AppFontFamily,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Black,
                        color = palette.ink,
                    ),
                )
            }
            // Gradient profil tugmasi.
            Box(
                Modifier.size(46.dp)
                    .shadow(10.dp, CircleShape, spotColor = palette.primary.copy(alpha = 0.5f))
                    .clip(CircleShape).background(palette.primaryBrush)
                    .clickable(onClick = onProfile),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Store, "Profil", tint = Color.White, modifier = Modifier.size(21.dp))
            }
        }

        SegmentedTabs(discount, onSelect, palette)
    }
}

/** Tepadagi segment selektor — ikonli, full-width, animatsion tanlov. Chegirma | E'lon. */
@Composable
private fun SegmentedTabs(discount: Boolean, onSelect: (Boolean) -> Unit, palette: AppPalette) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(palette.fieldBg)
            .border(1.dp, palette.border, RoundedCornerShape(16.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SegChip(Modifier.weight(1f), AppIcons.Tag, "Chegirma", discount, { onSelect(true) }, palette)
        SegChip(Modifier.weight(1f), AppIcons.FileText, "E'lon", !discount, { onSelect(false) }, palette)
    }
}

@Composable
private fun SegChip(
    modifier: Modifier,
    icon: ImageVector,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    palette: AppPalette,
) {
    val bg by animateColorAsState(if (selected) palette.primary else Color.Transparent)
    val fg by animateColorAsState(if (selected) palette.onPrimary else palette.inkMuted)
    val elevation by animateDpAsState(if (selected) 6.dp else 0.dp)
    Row(
        modifier
            .shadow(elevation, RoundedCornerShape(12.dp), spotColor = palette.primary.copy(alpha = 0.45f))
            .clip(RoundedCornerShape(12.dp)).background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
        Text(
            text,
            style = TextStyle(
                fontFamily = AppFontFamily,
                fontSize = 13.5f.sp,
                fontWeight = FontWeight.ExtraBold,
                color = fg,
            ),
        )
    }
}

/** O'ng-past extended FAB — "Yangi" + turi. */
@Composable
private fun CreateFab(
    discount: Boolean,
    palette: AppPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .shadow(16.dp, RoundedCornerShape(20.dp), spotColor = palette.primary.copy(alpha = 0.6f))
            .clip(RoundedCornerShape(20.dp)).background(palette.primaryBrush)
            .clickable(onClick = onClick)
            .padding(start = 18.dp, end = 22.dp, top = 15.dp, bottom = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(AppIcons.Plus, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(
            if (discount) "Chegirma" else "E'lon",
            style = TextStyle(
                fontFamily = AppFontFamily,
                fontSize = 14.5f.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
            ),
        )
    }
}
