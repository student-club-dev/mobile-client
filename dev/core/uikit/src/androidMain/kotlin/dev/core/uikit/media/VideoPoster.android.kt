package dev.core.uikit.media

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android: kadr [MediaMetadataRetriever] bilan ajratiladi.
 *
 * Tarmoq havolasida ham ishlaydi — retriever faqat kerakli qismni (moov + birinchi
 * kadr) o'qiydi, butun videoni yuklab olmaydi.
 */
actual suspend fun videoPosterUrl(
    source: String,
    headers: Map<String, String>,
    cacheKey: String,
): String? = withContext(Dispatchers.IO) {
    val context = scMediaContext ?: return@withContext null

    val folder = File(context.cacheDir, POSTER_DIR).apply { mkdirs() }
    val target = File(folder, "$cacheKey.jpg")
    // Bir marta hisoblanadi — keyingi chaqiruvlar tayyor faylni oladi.
    if (target.isFile && target.length() > 0) return@withContext localFileUrl(target.absolutePath)

    val retriever = MediaMetadataRetriever()
    val frame = runCatching {
        when {
            source.startsWith("content://") -> retriever.setDataSource(context, Uri.parse(source))
            source.startsWith("file://") -> retriever.setDataSource(source.removePrefix("file://"))
            !source.contains("://") -> retriever.setDataSource(source)
            else -> retriever.setDataSource(source, headers)
        }
        // `OPTION_CLOSEST_SYNC` — eng yaqin kalit kadr. Aniq vaqt (`OPTION_CLOSEST`)
        // dekoderni kadrma-kadr yurishga majburlaydi va sekundlab ishlaydi.
        retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    }.getOrNull()

    runCatching { retriever.release() }
    if (frame == null) return@withContext null

    runCatching {
        // Yarim yozilgan fayl qolmasin: avval vaqtinchalik nomga, keyin o'z nomiga.
        val temp = File(folder, "$cacheKey.jpg.part")
        temp.outputStream().use { out -> frame.compress(Bitmap.CompressFormat.JPEG, POSTER_QUALITY, out) }
        temp.renameTo(target)
    }.onFailure {
        frame.recycle()
        return@withContext null
    }
    frame.recycle()

    localFileUrl(target.absolutePath)
}

private const val POSTER_DIR = "video_posters"

/** Pufakdagi kichik rasm uchun yetarli — 85 va 100 orasidagi farq ko'rinmaydi, hajm esa ikki barobar. */
private const val POSTER_QUALITY = 85
