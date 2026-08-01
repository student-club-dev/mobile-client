package dev.core.uikit.media

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File

@Composable
actual fun rememberVideoPreparer(): VideoPreparer {
    // ⚠️ `applicationContext` — `Activity` emas: siqish yuborilgandan keyin, ekran
    // yopilganda ham davom etadi (u `viewModelScope` da ketadi). `Activity` ni ushlab
    // tursak, u siqish tugagunicha xotirada qolib ketardi.
    val context = LocalContext.current.applicationContext
    return remember(context) { VideoPreparer { video, onProgress -> context.prepare(video, onProgress) } }
}

/**
 * Tayyorlash — yuborish bosilgandan **keyin**.
 *
 * Ikki ish qilinadi:
 *
 * 1. **Siqish** (12 MB dan kattalarda) — qarang [compressVideo]. U manbani o'zi o'qiydi,
 *    ya'ni galereyadagi fayl bu yergacha umuman ko'chirilmagan bo'lishi mumkin.
 * 2. **Faylni moddiylashtirish** — yuklovchi ([MediaUploader.chatUploadFile]) diskdagi
 *    fayldan oqim oladi va `content://` havolasidan o'qiy olmaydi. Shuning uchun siqilmagan
 *    kichik video aynan shu yerda keshga ko'chiriladi: u 12 MB dan kichik, ya'ni nusxa arzon.
 */
private suspend fun Context.prepare(video: PickedVideo, onProgress: (Float) -> Unit): PickedVideo? {
    // Kichik va allaqachon mos fayl tegilmaydi — qayta kodlash sifatni bekorga tushirardi.
    if (!videoNeedsPreparing(video.sizeBytes)) {
        // Chegaradan katta, lekin siqilmaydigan hajm bo'lishi mumkin emas
        // ([videoNeedsPreparing] chegarasi ancha past) — shunga qaramay tekshiramiz.
        if (video.sizeBytes > MAX_VIDEO_BYTES) return null
        return materialize(video)
    }

    val compressed = compressVideo(
        context = this,
        source = video.sourceUri(),
        video = video,
        onProgress = onProgress,
    )

    if (compressed == null) {
        // Tayyorlab bo'lmadi (kodek qo'llamadi, joy tugadi) — asl fayl faqat chegaradan
        // kichik bo'lsa ketadi, kattasini server baribir `413` bilan rad etadi.
        return if (video.sizeBytes <= MAX_VIDEO_BYTES) materialize(video) else null
    }
    if (compressed.length() !in 1..MAX_VIDEO_BYTES) {
        // Juda uzun yoki juda katta kadrli video — siqib ham sig'madi.
        compressed.delete()
        return null
    }

    // Asl nusxa endi keraksiz — lekin faqat **bizniki** bo'lsa. Galereyadagi `content://`
    // foydalanuvchining fayli: unga tegish mumkin emas.
    if (video.ownsFile) deleteMediaFile(video.path)

    return PickedVideo(
        path = compressed.absolutePath,
        // Natija doim MP4 (H.264) — nom haqiqiy formatga mos bo'lishi shart.
        fileName = "video.mp4",
        durationMs = video.durationMs,
        sizeBytes = compressed.length(),
        posterBytes = video.posterBytes,
        width = video.width,
        // Tayyorlangan video endi chat o'lchamlarida: qayta tekshirilsa ham ikkinchi marta
        // siqishga tushmasin.
        height = video.height.coerceAtMost(PREPARED_MAX_HEIGHT),
        frameRate = video.frameRate.coerceAtMost(PREPARED_MAX_FPS),
        isH264 = true,
    )
}

/**
 * Videoni **diskdagi faylga** aylantiradi.
 *
 * Fayl allaqachon bizniki bo'lsa hech narsa qilinmaydi. `content://` bo'lsa nusxa olinadi:
 * bu faqat siqilmaydigan (12 MB dan kichik) videoda yuz beradi, ya'ni bir necha yuz
 * millisekund.
 */
private fun Context.materialize(video: PickedVideo): PickedVideo? {
    if (video.ownsFile) return video

    val file = copyToCache(Uri.parse(video.path)) ?: return null
    val size = file.length()
    if (size <= 0L) {
        file.delete()
        return null
    }

    return PickedVideo(
        path = file.absolutePath,
        fileName = video.fileName,
        durationMs = video.durationMs,
        sizeBytes = size,
        posterBytes = video.posterBytes,
        width = video.width,
        height = video.height,
        frameRate = video.frameRate,
        isH264 = video.isH264,
    )
}

/** Manbani Media3 tushunadigan havolaga o'giradi — fayl ham, `content://` ham bo'lishi mumkin. */
private fun PickedVideo.sourceUri(): Uri =
    if (ownsFile) Uri.fromFile(File(path)) else Uri.parse(path)

/** Tayyorlangan videoning chegaralari — `VideoCompressor.android.kt` dagilar bilan bir xil. */
private const val PREPARED_MAX_HEIGHT = 720
private const val PREPARED_MAX_FPS = 30f
