package dev.feature.listings.presentation.platform

import androidx.compose.runtime.Composable

/**
 * Telefon terish oynasini ochadi.
 *
 * `ACTION_CALL` emas, **terish oynasi** ochiladi: raqam foydalanuvchiga ko'rinadi va u
 * qo'ng'iroqni o'zi tasdiqlaydi. Shu sabab `CALL_PHONE` ruxsati ham kerak emas — ilova
 * hech qachon foydalanuvchi bilmagan holda qo'ng'iroq qilmaydi.
 */
@Composable
expect fun rememberPhoneCaller(): (phone: String) -> Unit
