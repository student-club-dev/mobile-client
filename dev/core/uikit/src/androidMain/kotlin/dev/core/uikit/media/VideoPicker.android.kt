package dev.core.uikit.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

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
        scope.launchStaging(context, uri, onResult = onResult)
    }

    return remember(launcher) {
        VideoPicker {
            launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        }
    }
}

/**
 * Tanlangan **yoki kamerada yozilgan** videoni tavsiflab, natijani topshiradi.
 *
 * Galereya ([rememberVideoPicker]) bilan kamera ([rememberVideoCapture]) faqat faylni
 * qayerdan olishi bilan farq qiladi — undan keyingi hamma narsa bir xil.
 *
 * ⚠️ Bu yerda video **siqilmaydi** va odatda **ko'chirilmaydi** ham: qarang [stageVideo].
 * Siqish yuborish bosilgandan keyin, xabarning o'z halqasi ichida ketadi ([VideoPreparer]) —
 * aks holda foydalanuvchi tanlagandan keyin bir necha o'n soniya ekran oldida kutib turishi
 * kerak bo'lardi.
 *
 * [ownedFile] — kamera yozgan fayl (u allaqachon bizning keshimizda). Galereyada `null`.
 */
internal fun CoroutineScope.launchStaging(
    context: Context,
    uri: Uri,
    ownedFile: File? = null,
    onResult: (PickedVideo?) -> Unit,
): Job = launch {
    // ⚠️ Metama'lumot ham arzon emas (retriever poster kadrini dekodlaydi) — asosiy oqimni
    // bloklamasligi uchun IO'da.
    val picked = withContext(Dispatchers.IO) { context.stagePickedVideo(uri, ownedFile) }
    onResult(picked)
}

/**
 * [launchStaging] ning **bloklovchi o'zagi** — chaqiruvchi o'z oqimini o'zi tanlaydi.
 *
 * Rasm+video tanlagichiga ([rememberMultiMediaPicker]) shu ko'rinishda kerak: u bir nechta
 * faylni bitta `withContext(IO)` ichida ketma-ket qayta ishlaydi va har biri uchun alohida
 * korutina ochish bekor ish bo'lardi.
 *
 * Qaytishi `null` — video yaroqsiz (3 daqiqadan uzun, buzuq yoki o'qib bo'lmadi).
 */
internal fun Context.stagePickedVideo(uri: Uri, ownedFile: File? = null): PickedVideo? =
    runCatching {
        val meta = readVideoMeta(uri) ?: return@runCatching null
        stageVideo(uri, meta, ownedFile)
    }.getOrNull()

/** Baytlarni o'qishdan oldin ma'lum bo'ladigan narsalar. */
private class VideoMeta(
    val durationMs: Int?,
    val declaredSize: Long?,
    val poster: ByteArray?,
    val width: Int,
    val height: Int,
    val frameRate: Float,
    val isH264: Boolean,
)

/**
 * Metama'lumot — **baytlarsiz**.
 *
 * Ajratilishining sababi: davomiylik chegarasi (`≤ 3 daq`) va siqish kerakligi hajm bo'yicha
 * hal qilinadi, ya'ni 100 MB faylni xotiraga o'qishdan oldin bilib olish kerak.
 *
 * O'lcham, kadr tezligi va kodek ham shu yerda o'qiladi: ularsiz siqish har safar "ehtiyot
 * uchun" to'liq qayta kodlashga tushardi — aynan shu bir necha daqiqa olardi.
 */
private fun Context.readVideoMeta(uri: Uri): VideoMeta? {
    val retriever = MediaMetadataRetriever()
    val durationMs: Int?
    val poster: ByteArray?
    val width: Int
    val height: Int
    try {
        retriever.setDataSource(this, uri)
        durationMs = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
        width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
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

    // Uzun videoni siqib ham chegaraga sig'dirib bo'lmaydi — vaqtni yo'qotmaymiz.
    if (durationMs != null && durationMs > MAX_VIDEO_MS) return null

    val track = readVideoTrack(uri)

    return VideoMeta(
        durationMs = durationMs,
        declaredSize = declaredSizeOrNull(uri),
        poster = poster,
        width = width,
        height = height,
        frameRate = track.frameRate,
        isH264 = track.isH264,
    )
}

/** Video yo'lagi haqida [MediaExtractor] beradigan ma'lumot. */
private class VideoTrack(val isH264: Boolean, val frameRate: Float)

/**
 * Video yo'lagining kodeki va kadr tezligi.
 *
 * ⚠️ `MediaMetadataRetriever` da bularning ikkalasi ham yo'q: kodek kaliti faqat API 31+ da,
 * kadr tezligi esa umuman yo'q (`METADATA_KEY_CAPTURE_FRAMERATE` — sekin tortish tezligi,
 * boshqa narsa). Shuning uchun [MediaExtractor] — u faylning faqat boshini o'qiydi.
 *
 * Aniqlab bo'lmagan qiymatlar "eng ehtiyotkor" tomonga tushadi: kodek noma'lum bo'lsa
 * "H.264 emas" (ya'ni video qayta kodlanadi), kadr tezligi noma'lum bo'lsa `0f` (ya'ni
 * kadr tashlanmaydi).
 */
private fun Context.readVideoTrack(uri: Uri): VideoTrack {
    val extractor = MediaExtractor()
    return try {
        contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            extractor.setDataSource(descriptor.fileDescriptor)
            val format = (0 until extractor.trackCount)
                .map { index -> extractor.getTrackFormat(index) }
                .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
                ?: return@use UNKNOWN_TRACK

            VideoTrack(
                isH264 = format.getString(MediaFormat.KEY_MIME) == MediaFormat.MIMETYPE_VIDEO_AVC,
                // ⚠️ `KEY_FRAME_RATE` manbaga qarab `Int` yoki `Float` bo'lib keladi va
                // noto'g'ri turda so'ralsa `ClassCastException` tashlaydi — ikkalasi ham
                // sinab ko'riladi.
                frameRate = format.frameRateOrZero(),
            )
        } ?: UNKNOWN_TRACK
    } catch (_: Exception) {
        UNKNOWN_TRACK
    } finally {
        extractor.release()
    }
}

private fun MediaFormat.frameRateOrZero(): Float {
    if (!containsKey(MediaFormat.KEY_FRAME_RATE)) return 0f
    return runCatching { getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() }
        .recoverCatching { getFloat(MediaFormat.KEY_FRAME_RATE) }
        .getOrDefault(0f)
}

private val UNKNOWN_TRACK = VideoTrack(isH264 = false, frameRate = 0f)

/**
 * Videoni yuborishga tayyor holatga keltiradi — **odatda faylga tegmasdan**.
 *
 * Uchta yo'l bor:
 *
 * 1. **Kamera** — fayl allaqachon bizning keshimizda, shuning uchun faqat nomi
 *    o'zgartiriladi (`renameTo`, bir lahza). Ilgari u yana bir marta to'liq ko'chirilardi,
 *    ya'ni 180 MB lik lavha keshda IKKI marta yozilardi.
 * 2. **Galereya, hajmi ma'lum** — hech narsa ko'chirilmaydi, `content://` havolasining
 *    o'zi saqlanadi. Siqish manbani o'zi o'qib natijani keshga yozadi; siqilmaydigan
 *    (12 MB dan kichik) videoda nusxa [VideoPreparer] ichida olinadi.
 * 3. **Galereya, hajmi noma'lum** — provayder `SIZE` ustunini bermagan. Hajmni faqat
 *    ko'chirib bilib olish mumkin (u siqish kerakligini hal qiladi), shuning uchun bu
 *    kamdan-kam holatda eski yo'l qoladi.
 *
 * Hajm bu yerda **tekshirilmaydi**: 64 MB dan kattasi ham qabul qilinadi, chunki uni
 * yuborishdan oldin siqish chegaraga sig'diradi ([VideoPreparer]). Siqib ham sig'masa —
 * o'sha yerda rad etiladi.
 */
private fun Context.stageVideo(uri: Uri, meta: VideoMeta, ownedFile: File?): PickedVideo? {
    val declared = meta.declaredSize?.takeIf { it > 0L }

    val (path, sizeBytes) = when {
        ownedFile != null -> {
            val file = moveIntoCache(ownedFile)
            val size = file.length()
            if (size <= 0L) return null
            file.absolutePath to size
        }

        declared != null -> uri.toString() to declared

        else -> {
            val file = copyToCache(uri) ?: return null
            val size = file.length()
            if (size <= 0L) {
                file.delete()
                return null
            }
            file.absolutePath to size
        }
    }

    return PickedVideo(
        path = path,
        fileName = "video.${videoExtension(uri)}",
        durationMs = meta.durationMs,
        sizeBytes = sizeBytes,
        posterBytes = meta.poster,
        width = meta.width,
        height = meta.height,
        frameRate = meta.frameRate,
        isH264 = meta.isH264,
    )
}

/**
 * Kamera yozgan faylni keshning "yuboriladigan" nomiga o'tkazadi.
 *
 * `renameTo` — bir xil fayl tizimi ichida bu faqat katalog yozuvini o'zgartiradi, ya'ni
 * fayl hajmi ahamiyatsiz. Nomlash muvaffaqiyatsiz bo'lsa fayl **o'z joyida** ishlatiladi:
 * u baribir bizniki va o'qish uchun yaroqli.
 */
private fun Context.moveIntoCache(source: File): File {
    val target = File(cacheDir, "outgoing_video_${System.currentTimeMillis()}.${source.extension}")
    return if (runCatching { source.renameTo(target) }.getOrDefault(false)) target else source
}

/**
 * Tanlangan faylni ilova keshiga ko'chiradi — baytlarni xotiraga solmasdan.
 *
 * ⚠️ Bu **qimmat** amal (fayl hajmicha o'qish + yozish), shuning uchun u faqat ikki joyda
 * chaqiriladi: hajmi noma'lum bo'lgan tanlovda ([stageVideo]) va siqilmaydigan kichik
 * videoni yuborishdan oldin ([VideoPreparer]).
 */
internal fun Context.copyToCache(uri: Uri): File? {
    val target = File(cacheDir, "outgoing_video_${System.currentTimeMillis()}.${videoExtension(uri)}")
    val copied = runCatching {
        contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } != null
    }.getOrDefault(false)

    if (!copied) {
        // Yarim ko'chirilgan fayl qolib ketmasin — u yuborilsa buzuq video bo'lardi.
        target.delete()
        return null
    }
    return target
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
