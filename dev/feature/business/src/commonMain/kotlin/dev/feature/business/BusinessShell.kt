package dev.feature.business

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import dev.core.designsystem.components.AppIcons
import dev.core.designsystem.theme.AppPalette
import dev.core.designsystem.theme.appPalette
import dev.feature.discounts.presentation.MyListingsScreen
import dev.feature.discounts.presentation.PostListingScreen

private const val LISTINGS = "listings"
private const val POST_LISTING = "post_listing"
private const val REDEMPTION = "redemption"
private const val PROFILE = "business_profile"
private const val SETTINGS = "settings"

/**
 * Biznesmen karkasi — pastki navigatsiya YO'Q. Asosiy ekran (chegirma e'lonlari) to'liq
 * ko'rinadi; o'ng-yuqorida Skaner va Profil ikonkalari, o'ng-past burchakda aylana "+"
 * tugmasi (yangi e'lon). Talaba bo'limlari umuman yo'q.
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

    Box(Modifier.fillMaxSize().background(palette.bgBrush)) {
        NavHost(navController = nav, startDestination = LISTINGS, modifier = Modifier.fillMaxSize()) {
            composable(LISTINGS) {
                MyListingsScreen(
                    onCreate = { nav.navigate(POST_LISTING) },
                    onEdit = { listingId -> nav.navigate("$POST_LISTING?listingId=$listingId") },
                    // Header "+" va orqaga yashirin — o'rniga o'ng-past FAB va o'ng-yuqori ikonkalar.
                    showHeaderCreate = false,
                )
            }
            composable(
                route = "$POST_LISTING?listingId={listingId}",
                arguments = listOf(
                    navArgument("listingId") { type = NavType.StringType; nullable = true; defaultValue = null },
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
                )
            }
            composable(REDEMPTION) { RedemptionScreen(onBack = { nav.popBackStack() }) }
            composable(PROFILE) {
                BusinessAccountScreen(
                    onBack = { nav.popBackStack() },
                    onOpenListings = { nav.popBackStack() },
                    onOpenSettings = { nav.navigate(SETTINGS) },
                    onLoggedOut = onLoggedOut,
                )
            }
            composable(SETTINGS) {
                settingsContent { nav.popBackStack() }
            }
        }

        // Asosiy ekran (e'lonlar) ustidagi harakatlar — boshqa ekranlarда o'z back tugmasi bor.
        if (current == LISTINGS) {
            // O'ng-yuqori: Skaner + Profil
            Row(
                Modifier.align(Alignment.TopEnd).padding(top = 54.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircleAction(AppIcons.ScanFace, "Skaner", palette) { nav.navigate(REDEMPTION) }
                CircleAction(AppIcons.Store, "Profil", palette) { nav.navigate(PROFILE) }
            }

            // O'ng-past: aylana ichida "+" (yangi e'lon)
            Box(
                Modifier.align(Alignment.BottomEnd).padding(end = 22.dp, bottom = 30.dp)
                    .size(62.dp).clip(CircleShape).background(palette.primaryBrush)
                    .clickable { nav.navigate(POST_LISTING) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Plus, "Yangi e'lon", tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }
    }
}

@Composable
private fun CircleAction(icon: ImageVector, desc: String, palette: AppPalette, onClick: () -> Unit) {
    val bg = if (palette.dark) Color(0xFF1A1630) else Color.White
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(bg).border(1.dp, palette.border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = palette.ink, modifier = Modifier.size(19.dp))
    }
}
