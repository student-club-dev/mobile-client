package dev.core.uikit.media

import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVAsset
import platform.AVFoundation.AVAssetExportPreset1280x720
import platform.AVFoundation.AVAssetExportPreset1920x1080
import platform.AVFoundation.AVAssetExportSession
import platform.AVFoundation.AVAssetExportSessionStatusCompleted
import platform.AVFoundation.AVFileTypeMPEG4
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import kotlin.coroutines.resume

/**
 * Yuborishdan oldin videoni **siqadi** — Android'dagi [compressVideo] ning iOS ko'zgusi.
 *
 * Nega kerak: iPhone 4K/60 da suratga oladi va bu sekundiga ~6 MB. Siqishsiz 10 soniyalik
 * lavha ham serverning 64 MB chegarasidan oshadi va xabar **umuman ketmaydi** — ilgari iOS'da
 * aynan shunday bo'lardi: katta video jimgina rad etilardi.
 *
 * ⚠️ Android'dan farqi — bitreyt **aniq** so'ralmaydi. `AVAssetExportSession` faqat tayyor
 * presetlarni biladi; ixtiyoriy bitreyt uchun `AVAssetWriter` bilan qo'lda qayta kodlash
 * kerak bo'lardi (bir necha yuz qator va har bir kodek uchun alohida ishlov). Shuning uchun
 * bu yerda o'lcham presetlar orasidan tanlanadi, hajm esa taxminan mos keladi — chegaradan
 * oshib ketgan holat chaqiruvchida (`stageVideoFile` dagi kabi) baribir ushlanadi.
 */
@OptIn(ExperimentalForeignApi::class)
internal suspend fun compressVideo(
    asset: AVAsset,
    durationMs: Int?,
    /** Siqish ulushi (`0f..1f`) — ekrandagi foiz uchun. */
    onProgress: (Float) -> Unit = {},
): NSURL? {
    val seconds = ((durationMs ?: 0) / 1000).coerceAtLeast(1)
    // Android bilan bir xil qoida: uzun videoda bitreyt baribir past bo'ladi va 1080p da u
    // "kvadratchalar" bo'lib ko'rinardi — 720p da o'sha bitreyt toza chiqadi.
    val bitrate = (TARGET_BYTES * BITS_PER_BYTE / seconds).coerceIn(MIN_BITRATE, MAX_BITRATE)
    val preset = if (bitrate >= HD_BITRATE) AVAssetExportPreset1920x1080 else AVAssetExportPreset1280x720

    val output = NSURL.fileURLWithPath(NSTemporaryDirectory() + "outgoing_video_${NSUUID().UUIDString}.mp4")

    val session = AVAssetExportSession(asset = asset, presetName = preset) ?: return null
    session.outputURL = output
    session.outputFileType = AVFileTypeMPEG4
    // `moov` atomini faylning BOSHIGA ko'chiradi. Usiz pleyer videoni o'ynatishdan oldin
    // butun faylni yuklab olishi kerak bo'lardi — qabul qiluvchida video "uzoq o'ylab"
    // turgandek ko'rinardi.
    session.shouldOptimizeForNetworkUse = true

    return withContext(Dispatchers.Default) {
        val progressJob = launchProgress(session, onProgress)
        try {
            suspendCancellableCoroutine { continuation ->
                session.exportAsynchronouslyWithCompletionHandler {
                    val done = session.status == AVAssetExportSessionStatusCompleted
                    if (!done) {
                        // Siqib bo'lmadi (kodek qo'llamadi, joy tugadi, bekor qilindi) —
                        // chaqiruvchi asl faylga qaytadi, ya'ni yuborish to'xtamaydi.
                        NSFileManager.defaultManager.removeItemAtURL(output, error = null)
                    }
                    if (continuation.isActive) continuation.resume(if (done) output else null)
                }

                continuation.invokeOnCancellation {
                    // Ekran yopilsa yoki foydalanuvchi bekor qilsa siqish ham to'xtaydi —
                    // aks holda kodek fonda ishlab, batareyani yeb turardi.
                    session.cancelExport()
                    NSFileManager.defaultManager.removeItemAtURL(output, error = null)
                }
            }
        } finally {
            progressJob.cancel()
        }
    }
}

/**
 * Jarayonni **so'rab** oladi.
 *
 * `AVAssetExportSession` da "har kadrda" hodisa yo'q — faqat o'qiladigan `progress` bor,
 * shuning uchun Android'dagidek davriy so'rov qilinadi.
 */
private fun CoroutineScope.launchProgress(session: AVAssetExportSession, onProgress: (Float) -> Unit) =
    launch {
        while (isActive) {
            delay(PROGRESS_TICK_MS)
            onProgress(session.progress.coerceIn(0f, 1f))
        }
    }

/**
 * Siqilgan faylning **maqsadli hajmi** — Android bilan bir xil.
 *
 * Serverning chegarasi 64 MB, lekin unga tiralish maqsad emas: yuborish vaqti to'g'ridan
 * to'g'ri hajmga bog'liq va mobil internetda 50 MB bir necha daqiqa ketardi.
 */
private const val TARGET_BYTES = 20L * 1024 * 1024
private const val BITS_PER_BYTE = 8L

/** Bitreyt chegaralari — bu yerda faqat **preset tanlash** uchun, qarang [compressVideo]. */
private const val MIN_BITRATE = 600_000L
private const val MAX_BITRATE = 3_500_000L

/** Shundan past bitreytda 1080p emas, 720p tanlanadi. */
private const val HD_BITRATE = 2_000_000L

/** Jarayonni so'rash oralig'i — Android bilan bir xil. */
private const val PROGRESS_TICK_MS = 250L
