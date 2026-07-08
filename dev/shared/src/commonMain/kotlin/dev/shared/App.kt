package dev.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.core.data.seed.LocalDataSeeder
import dev.core.designsystem.theme.AppTheme
import dev.core.domain.model.ThemeMode
import dev.core.domain.repository.SettingsRepository
import dev.feature.auth.presentation.flow.AuthNavHost
import dev.feature.auth.presentation.theme.authPalette
import org.koin.compose.koinInject

/** Ilovaning ildiz Composable'i — Android va iOS bir xil ishlatadi. */
@Composable
fun App() {
    // Local bazani dizayndagi namuna ma'lumot bilan to'ldiramiz (bo'sh bo'lsagina).
    val seeder = koinInject<LocalDataSeeder>()
    LaunchedEffect(Unit) { seeder.seedIfEmpty() }

    // Foydalanuvchi tanlagan mavzu (Sozlamalar). SYSTEM bo'lsa qurilma rejimiga ergashadi.
    val settings = koinInject<SettingsRepository>()
    val themeMode by settings.observeThemeMode().collectAsState(initial = ThemeMode.SYSTEM)
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    AppTheme(darkTheme = isDark) {
        // Butun ilova pastki tizim navigatsiya paneli (3 tugma) / iOS home indikatori
        // ortida qolmasligi uchun global inset. Fon gradienti panel ostida ham to'liq chiziladi.
        Box(Modifier.fillMaxSize().background(authPalette.bgBrush)) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) {
                AuthNavHost()
            }
        }
    }
}
