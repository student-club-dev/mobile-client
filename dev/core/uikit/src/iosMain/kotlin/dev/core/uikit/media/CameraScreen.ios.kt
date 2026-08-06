package dev.core.uikit.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap

/**
 * iOS: **tizim kamerasi**.
 *
 * Ilova ichidagi kamera (AVFoundation sessiyasi + Compose ustidagi boshqaruv) hozircha
 * yozilmagan; uning o'rniga `UIImagePickerController` kamera manbasi bilan ochiladi —
 * ya'ni foydalanuvchi uchun natija bir xil: «+» bosildi → kamera ochildi, tanlov oynasi
 * chiqmadi. Galereya rasmchasi tizim oynasining o'zida bo'lmaydi, shuning uchun
 * bekor qilinganda chaqiruvchining galereya yo'li ishga tushadi.
 */
@Composable
actual fun ScCameraScreen(
    onPhoto: (PickedImage) -> Unit,
    onVideo: (PickedVideo) -> Unit,
    onOpenGallery: () -> Unit,
    onClose: () -> Unit,
    galleryThumbnail: ImageBitmap?,
    allowVideo: Boolean,
) {
    // Tizim oynasi bir marta ochiladi: `LaunchedEffect(Unit)` qayta kompozitsiyada
    // takrorlanmaydi, aks holda bekor qilingan oyna cheksiz qayta ochilardi.
    var launched by remember { mutableStateOf(false) }
    val camera = rememberImageCapture { picked ->
        if (picked != null) onPhoto(picked) else onClose()
    }
    LaunchedEffect(Unit) {
        if (!launched) {
            launched = true
            camera.pick()
        }
    }
}

/** iOS'da rasmcha ko'rsatiladigan o'z kamera ekranimiz yo'q — kichik nusxa ham kerak emas. */
@Composable
actual fun rememberLatestGalleryThumbnail(sizePx: Int): ImageBitmap? = null
