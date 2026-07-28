package dev.core.uikit.media

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberImagePicker(onResult: (PickedImage?) -> Unit): ImagePicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) {
            onResult(null) // bekor qilindi
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val picked = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@runCatching null
                    val ext = if (context.contentResolver.getType(uri) == "image/png") "png" else "jpg"
                    PickedImage(bytes = bytes, fileName = "image.$ext")
                }.getOrNull()
            }
            onResult(picked)
        }
    }

    return remember(launcher) {
        ImagePicker {
            launcher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }
    }
}

@Composable
actual fun rememberMultiImagePicker(
    maxItems: Int,
    onResult: (List<PickedImage>) -> Unit,
): ImagePicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // `PickMultipleVisualMedia` 1 ni qabul qilmaydi (IllegalArgumentException) va tizim
    // chegarasidan oshsa ham yiqiladi — ikkala tomondan qisamiz. `getPickImagesMaxLimit()`
    // faqat Android 13+ da bor; undan pastda tanlagich `ACTION_OPEN_DOCUMENT` ga tushadi
    // va chegara qo'llanmaydi.
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
            onResult(emptyList()) // bekor qilindi
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val picked = withContext(Dispatchers.IO) {
                // O'qib bo'lmagani (ruxsat bekor qilindi, fayl o'chirilgan) butun tanlovni
                // yiqitmasin — shunchaki ro'yxatdan tushadi.
                uris.mapNotNull { uri -> runCatching { context.readImage(uri) }.getOrNull() }
            }
            onResult(picked)
        }
    }

    return remember(launcher) {
        ImagePicker {
            launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
}

private fun Context.readImage(uri: Uri): PickedImage? {
    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val ext = when (contentResolver.getType(uri)) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "jpg"
    }
    return PickedImage(bytes = bytes, fileName = "image.$ext")
}

actual fun ByteArray.toImageBitmapOrNull(): ImageBitmap? =
    BitmapFactory.decodeByteArray(this, 0, size)?.asImageBitmap()
