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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.feature.auth.presentation.components.AuthFontFamily
import dev.feature.auth.presentation.components.AuthIcons
import dev.feature.auth.presentation.theme.AuthPalette
import dev.feature.auth.presentation.theme.authPalette

private enum class MainTab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", AuthIcons.Home),
    DISCOUNTS("discounts", "Chegirma", AuthIcons.Tag),
    JOBS("jobs", "Ishlar", AuthIcons.Briefcase),
    STUDENTS("students", "Student", AuthIcons.Users),
}

private const val POST_AD = "post_ad"
private const val PROFILE = "profile"
private const val CHAT = "chat"

private val tabRoutes = MainTab.entries.map { it.route }.toSet()

/** Asosiy ilova karkasi — pastki navigatsiya (4 tab) + markaziy "Elon" FAB. */
@Composable
fun MainShell(onLoggedOut: () -> Unit) {
    val palette = authPalette
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route ?: MainTab.HOME.route

    Box(Modifier.fillMaxSize().background(palette.bgBrush)) {
        NavHost(navController = nav, startDestination = MainTab.HOME.route, modifier = Modifier.fillMaxSize()) {
            composable(MainTab.HOME.route) {
                HomeScreen(onOpenProfile = { nav.navigate(PROFILE) }, onOpenChat = { nav.navigate(CHAT) })
            }
            composable(MainTab.DISCOUNTS.route) { DiscountsScreen() }
            composable(MainTab.JOBS.route) { JobsScreen() }
            composable(MainTab.STUDENTS.route) { StudentsScreen() }
            composable(POST_AD) { PostAdScreen(onClose = { nav.popBackStack() }) }
            composable(PROFILE) { ProfileScreen(onBack = { nav.popBackStack() }, onLoggedOut = onLoggedOut) }
            composable(CHAT) { ChatScreen(onBack = { nav.popBackStack() }) }
        }

        if (current in tabRoutes) {
            BottomBar(
                current = current,
                onSelect = { route ->
                    nav.navigate(route) {
                        popUpTo(MainTab.HOME.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onFab = { nav.navigate(POST_AD) { launchSingleTop = true } },
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
    palette: AuthPalette,
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
            NavBarItem(MainTab.HOME, current, onSelect, palette, Modifier.weight(1f))
            NavBarItem(MainTab.DISCOUNTS, current, onSelect, palette, Modifier.weight(1f))
            Spacer(Modifier.weight(1f)) // markaziy FAB uchun joy
            NavBarItem(MainTab.JOBS, current, onSelect, palette, Modifier.weight(1f))
            NavBarItem(MainTab.STUDENTS, current, onSelect, palette, Modifier.weight(1f))
        }

        // Markaziy "Elon" FAB — ko'tarilgan
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
                Icon(AuthIcons.Plus, "Elon berish", tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(3.dp))
            Text("Elon", style = TextStyle(fontFamily = AuthFontFamily, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = palette.primary))
        }
    }
}

@Composable
private fun NavBarItem(
    tab: MainTab,
    current: String,
    onSelect: (String) -> Unit,
    palette: AuthPalette,
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
        Text(tab.label, style = TextStyle(fontFamily = AuthFontFamily, fontSize = 9.sp, fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Bold, color = tint))
    }
}

/** Hali qurilmagan bo'limlar uchun vaqtincha ekran. */
@Composable
private fun PlaceholderTab(title: String, icon: ImageVector, palette: AuthPalette) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(palette.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = palette.primary, modifier = Modifier.size(30.dp)) }
            Text(title, style = TextStyle(fontFamily = AuthFontFamily, fontSize = 18.sp, fontWeight = FontWeight.Black, color = palette.ink))
            Text("Tez orada tayyor bo'ladi", style = TextStyle(fontFamily = AuthFontFamily, fontSize = 13.sp, color = palette.inkMuted))
        }
    }
}
