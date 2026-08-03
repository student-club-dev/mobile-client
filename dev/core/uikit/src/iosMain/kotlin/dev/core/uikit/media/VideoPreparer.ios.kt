package dev.core.uikit.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVURLAsset
import platform.Foundation.NSURL

@Composable
actual fun rememberVideoPreparer(): VideoPreparer =
    remember { VideoPreparer { video, onProgress -> prepare(video, onProgress) } }

/**
 * Siqish — yuborish bosilgandan **keyin** (Android'dagi bilan bir xil qoida).
 *
 * Nega kerak: iPhone 4K/60 da suratga oladi va bu sekundiga ~6 MB. Siqishsiz 10 soniyalik
 * lavha ham serverning 64 MB chegarasidan oshadi va xabar umuman ketmaydi.
 */
@OptIn(ExperimentalForeignApi::class)
private suspend fun prepare(video: PickedVideo, onProgress: (Float) -> Unit): PickedVideo? {
    // Kichik va allaqachon mos fayl tegilmaydi — qayta kodlash sifatni bekorga tushirardi.
    if (!videoNeedsPreparing(video.sizeBytes)) {
        return video.takeIf { it.sizeBytes <= MAX_VIDEO_BYTES }
    }

    val source = NSURL.fileURLWithPath(video.path)
    val compressed = compressVideo(
        asset = AVURLAsset(uRL = source, options = null),
        durationMs = video.durationMs,
        onProgress = onProgress,
    )

    if (compressed == null) {
        // Siqib bo'lmadi (kodek qo'llamadi, joy tugadi) — asl fayl faqat chegaradan kichik
        // bo'lsa ketadi, kattasini server baribir `413` bilan rad etadi.
        return video.takeIf { it.sizeBytes <= MAX_VIDEO_BYTES }
    }

    val size = compressed.fileSizeOrNull() ?: 0L
    if (size <= 0L || size > MAX_VIDEO_BYTES) {
        // Juda uzun yoki juda katta kadrli video — siqib ham sig'madi.
        compressed.delete()
        return null
    }

    // Asl nusxa endi keraksiz: yuboriladigani siqilgani.
    source.delete()
    return PickedVideo(
        path = compressed.path ?: return null,
        // Siqilgani doim MP4 (H.264/AAC) — nom haqiqiy formatga mos bo'lishi shart.
        fileName = "video.mp4",
        durationMs = video.durationMs,
        sizeBytes = size,
        posterBytes = video.posterBytes,
    )
}
