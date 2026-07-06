package dev.shared

import androidx.compose.runtime.Composable
import dev.core.designsystem.theme.AppTheme
import dev.feature.auth.presentation.flow.AuthNavHost

/** Ilovaning ildiz Composable'i — Android va iOS bir xil ishlatadi. */
@Composable
fun App() {
    AppTheme {
        AuthNavHost()
    }
}
