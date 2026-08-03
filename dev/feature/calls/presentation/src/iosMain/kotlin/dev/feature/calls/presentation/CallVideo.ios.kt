package dev.feature.calls.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * iOS'da video hali chizilmaydi — media qatlami `WebRTC.framework` ga bog'liq va u
 * loyihaga qo'shilmagan (`CallEngineFactory.ios.kt`).
 *
 * Hech narsa chizilmaydi, ekran esa avatar bilan ishlashda davom etadi: qo'ng'iroq
 * ekranining qolgan hamma qismi (holat, taymer, tugmalar) `commonMain` da va o'zgarishsiz
 * ishlaydi.
 */
@Composable
actual fun CallVideo(local: Boolean, mirror: Boolean, modifier: Modifier) = Unit
