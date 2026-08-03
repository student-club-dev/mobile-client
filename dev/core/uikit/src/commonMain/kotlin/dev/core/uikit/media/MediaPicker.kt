package dev.core.uikit.media

import androidx.compose.runtime.Composable

/**
 * Galereyadan **birga** tanlangan rasm va videolar.
 *
 * Ikkalasi bitta tanlovda kelishi mumkin (Telegramdagi kabi) va ular ikki xil yo'l bilan
 * yuklanadi: rasm baytlari bilan (`kind = IMAGE`, 12 MB), video esa fayl yo'li bilan
 * (`kind = VIDEO`, 64 MB, siqish bosqichi bor). Shu sabab ular bitta ro'yxatga
 * qo'shilmasdan, alohida saqlanadi.
 */
class PickedMedia(
    val images: List<PickedImage>,
    val videos: List<PickedVideo>,
    /**
     * Tanlangan, lekin **qabul qilinmagan** fayllar soni: 3 daqiqadan uzun video, buzuq
     * yoki o'qib bo'lmagan element.
     *
     * Chaqiruvchi buni foydalanuvchiga aytishi kerak — aks holda odam 5 ta fayl tanlab,
     * chatda 4 tasini ko'radi va nima bo'lganini bilmaydi.
     */
    val skipped: Int = 0,
) {
    val isEmpty: Boolean get() = images.isEmpty() && videos.isEmpty()

    companion object {
        val Empty = PickedMedia(emptyList(), emptyList())
    }
}

/** Rasm+video tanlashni ishga tushiruvchi. */
fun interface MediaPicker {
    fun pick()
}

/**
 * Galereyadan **rasm va videoni birga** tanlash — chatdagi asosiy biriktirish yo'li.
 *
 * Android: `PickMultipleVisualMedia(ImageAndVideo)`, iOS: `PHPickerViewController` da
 * `images ∪ videos` filtri. [rememberMultiImagePicker] kabi **ruxsat so'ramaydi** — tizim
 * tanlagichi faqat tanlangan fayllarni beradi.
 *
 * ⚠️ Video tanlanganda natija darrov kelmaydi: har biri uchun davomiylik, poster kadri va
 * hajm o'qiladi (fon oqimida). Uzun videolar (> 3 daqiqa) ro'yxatdan **jimgina tushadi** —
 * ularni siqib ham server chegarasiga sig'dirib bo'lmaydi; chaqiruvchi tanlangan va
 * qaytgan sonni solishtirib foydalanuvchiga ayta oladi.
 *
 * [onResult] bo'sh [PickedMedia] bilan chaqirilsa — foydalanuvchi bekor qildi.
 */
@Composable
expect fun rememberMultiMediaPicker(
    maxItems: Int = DEFAULT_MAX_IMAGES,
    onResult: (PickedMedia) -> Unit,
): MediaPicker
