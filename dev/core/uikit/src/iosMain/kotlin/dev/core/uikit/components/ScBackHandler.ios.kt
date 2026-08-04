package dev.core.uikit.components

import androidx.compose.runtime.Composable

/** iOS'da tizim "orqaga" tugmasi yo'q — qatlam ✕ yoki surish bilan yopiladi. */
@Composable
actual fun ScBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
