package dev.core.uikit.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
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
import java.io.ByteArrayOutputStream

/** Poster JPEG sifati — chat ro'yxatida kichik ko'rinadi, 80 dan yuqorisi behuda trafik. */
private const val POSTER_JPEG_QUALITY = 80

@Composable
actual fun rememberVideoPicker(onResult: (PickedVideo?) -> Unit): VideoPicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) {
            onResult(null) // bekor qilindi
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            // ⚠️ Fayl o'nlab MB bo'lishi mumkin — o'qish ham, metama'lumot ham (retriever
            // faylni dekodlaydi) asosiy oqimni bloklaydi.
            val picked = withContext(Dispatchers.IO) {
                runCatching { context.readVideo(uri) }.getOrNull()
            }
            onResult(picked)
        }
    }

    return remember(launcher) {
        VideoPicker {
            launcher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
            )
        }
    }
}

private fun Context.readVideo(uri: Uri): PickedVideo? {
    // ⚠️ Hajmni baytlarni o'qishdan OLDIN bilamiz: 64 MB dan kattasi hech qachon xotiraga
    // tushmasin. Provayder hajmni bermasa (-1) — o'qib bo'lgach qayta tekshiramiz.
    val declaredSize = declaredSizeOrNull(uri)
    if (declaredSize != null && declaredSize > MAX_VIDEO_BYTES) return null

    val retriever = MediaMetadataRetriever()
    val durationMs: Int?
    val poster: ByteArray?
    try {
        retriever.setDataSource(this, uri)
        durationMs = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
        // Kodek kadrni bermasligi mumkin (masalan HEVC eski qurilmada) — poster ixtiyoriy,
        // shu sabab butun tanlovni yiqitmaydi.
        poster = runCatching { retriever.getFrameAtTime(0)?.toJpegBytes() }.getOrNull()
    } catch (_: RuntimeException) {
        // `setDataSource` buzuq/qo'llab-quvvatlanmaydigan faylda RuntimeException tashlaydi.
        return null
    } finally {
        // `release()` — `close()` faqat API 29+ da bor.
        retriever.release()
    }

    if (durationMs != null && durationMs > MAX_VIDEO_MS) return null

    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    if (bytes.size > MAX_VIDEO_BYTES) return null // hajm noma'lum bo'lgan holat uchun

    return PickedVideo(
        bytes = bytes,
        fileName = "video.${videoExtension(uri)}",
        durationMs = durationMs,
        sizeBytes = bytes.size.toLong(),
        posterBytes = poster,
    )
}

/** Provayder e'lon qilgan hajm; ustun bo'sh yoki so'rovni qo'llamasa `null`. */
private fun Context.declaredSizeOrNull(uri: Uri): Long? =
    runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index < 0 || cursor.isNull(index)) null else cursor.getLong(index)
        }
    }.getOrNull()

/** Nom kengaytmasi haqiqiy formatga mos bo'lsin — MIME'ni provayderdan olamiz. */
private fun Context.videoExtension(uri: Uri): String =
    when (contentResolver.getType(uri)) {
        "video/quicktime", "video/x-quicktime" -> "mov"
        else -> "mp4"
    }

private fun Bitmap.toJpegBytes(): ByteArray =
    ByteArrayOutputStream().use { out ->
        compress(Bitmap.CompressFormat.JPEG, POSTER_JPEG_QUALITY, out)
        out.toByteArray()
    }
