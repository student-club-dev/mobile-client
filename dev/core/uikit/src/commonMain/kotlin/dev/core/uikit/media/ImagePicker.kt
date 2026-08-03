package dev.core.uikit.media

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/** Galereyadan tanlangan rasm. */
class PickedImage(
    val bytes: ByteArray,
    /** Kengaytmani aniqlash uchun fayl nomi, masalan "avatar.jpg". */
    val fileName: String,
)

/** Rasm tanlashni ishga tushiruvchi. */
fun interface ImagePicker {
    fun pick()
}

/**
 * Galereyadan rasm tanlagich (Android: `PickVisualMedia`, iOS: `PHPickerViewController`).
 * Ikkala platformada ham **ruxsat so'ramaydi** — tizim tanlagichi faqat tanlangan rasmni beradi.
 *
 * [onResult] `null` qaytsa — foydalanuvchi bekor qildi yoki rasmni o'qib bo'lmadi.
 */
@Composable
expect fun rememberImagePicker(onResult: (PickedImage?) -> Unit): ImagePicker

/**
 * **Bir nechta** rasm tanlagich (chat uchun) — bittalik [rememberImagePicker] dan alohida,
 * chunki ikkala platformada ham boshqa API ishlatiladi (Android `PickMultipleVisualMedia`,
 * iOS `PHPickerConfiguration.selectionLimit`).
 *
 * [onResult] **bo'sh ro'yxat** bilan chaqirilsa — foydalanuvchi bekor qildi. Ro'yxat
 * tanlangan tartibda keladi; o'qib bo'lmagan fayllar jimgina tushib qoladi, ya'ni
 * natija tanlanganidan kam bo'lishi mumkin.
 *
 * ⚠️ [maxItems] Android'da kamida **2** bo'lishi shart (tizim talabi) — 1 berilsa 2 ga
 * ko'tariladi.
 */
@Composable
expect fun rememberMultiImagePicker(
    maxItems: Int = DEFAULT_MAX_IMAGES,
    onResult: (List<PickedImage>) -> Unit,
): ImagePicker

/** Bir martada yuboriladigan rasmlar chegarasi — backend albom uchun ham shu sonni kutadi. */
const val DEFAULT_MAX_IMAGES = 10

/** Tanlangan rasm baytlarini darrov ko'rsatish uchun (format qo'llab-quvvatlanmasa `null`). */
expect fun ByteArray.toImageBitmapOrNull(): ImageBitmap?
