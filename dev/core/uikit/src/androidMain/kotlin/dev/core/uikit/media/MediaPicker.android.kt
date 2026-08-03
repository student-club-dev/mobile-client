package dev.core.uikit.media

import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberMultiMediaPicker(
    maxItems: Int,
    onResult: (PickedMedia) -> Unit,
): MediaPicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // `PickMultipleVisualMedia` 1 ni qabul qilmaydi (IllegalArgumentException) va tizim
    // chegarasidan oshsa ham yiqiladi — ikkala tomondan qisamiz (qarang
    // `rememberMultiImagePicker` dagi bir xil izoh).
    val limit = remember(maxItems) {
        val systemMax = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            MediaStore.getPickImagesMaxLimit()
        } else {
            Int.MAX_VALUE
        }
        maxItems.coerceAtLeast(2).coerceAtMost(systemMax)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(limit),
    ) { uris ->
        if (uris.isEmpty()) {
            onResult(PickedMedia.Empty) // bekor qilindi
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val picked = withContext(Dispatchers.IO) {
                val images = mutableListOf<PickedImage>()
                val videos = mutableListOf<PickedVideo>()
                var skipped = 0
                uris.forEach { uri ->
                    // Turni MIME aytadi: tanlagich rasm va videoni bitta ro'yxatda beradi,
                    // ular esa butunlay boshqa yo'l bilan yuklanadi (baytlar / fayl yo'li).
                    val picked = if (context.contentResolver.getType(uri).orEmpty().startsWith("video/")) {
                        // O'qib bo'lmagani (uzun video, buzuq fayl) butun tanlovni
                        // yiqitmasin — shunchaki ro'yxatdan tushadi va sanaladi.
                        context.stagePickedVideo(uri)?.also(videos::add)
                    } else {
                        runCatching { context.readImage(uri) }.getOrNull()?.also(images::add)
                    }
                    if (picked == null) skipped += 1
                }
                PickedMedia(images = images, videos = videos, skipped = skipped)
            }
            onResult(picked)
        }
    }

    return remember(launcher) {
        MediaPicker {
            launcher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
            )
        }
    }
}
