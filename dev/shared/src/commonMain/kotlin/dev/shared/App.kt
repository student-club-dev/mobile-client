package dev.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import dev.core.data.seed.LocalDataSeeder
import dev.core.designsystem.theme.AppTheme
import dev.feature.auth.presentation.flow.AuthNavHost
import org.koin.compose.koinInject

/** Ilovaning ildiz Composable'i — Android va iOS bir xil ishlatadi. */
@Composable
fun App() {
    // Local bazani dizayndagi namuna ma'lumot bilan to'ldiramiz (bo'sh bo'lsagina).
    val seeder = koinInject<LocalDataSeeder>()
    LaunchedEffect(Unit) { seeder.seedIfEmpty() }

    AppTheme {
        AuthNavHost()
    }
}
