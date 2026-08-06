package dev.core.uikit.components

import androidx.compose.runtime.Composable

/**
 * Tizimning "orqaga" harakati — ekran ICHIDAGI qatlamni yopish uchun.
 *
 * Navigatsiya bilan chalkashtirmang: bu yerda gap route'ni tark etishda emas, ekranning
 * o'z ustidagi qatlamda (qidiruv rejimi, ochilgan panel) — u yopilishi kerak, ekran esa
 * joyida qolishi kerak.
 *
 * Android'da tizim tugmasi/imo-ishorasi ushlanadi. iOS'da bunday tugma YO'Q va [onBack]
 * hech qachon chaqirilmaydi: u yerda qatlamlar surish yoki ✕ bilan yopiladi, shuning
 * uchun har bir chaqiruv joyida ko'rinadigan yopish yo'li ham bo'lishi shart.
 */
@Composable
expect fun ScBackHandler(enabled: Boolean = true, onBack: () -> Unit)
