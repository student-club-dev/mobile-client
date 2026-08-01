package dev.core.uikit.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVAssetImageGenerator
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.duration
import platform.CoreGraphics.CGImageRelease
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

/** Har qanday video formati — PHPicker shu identifikator bo'yicha fayl beradi. */
private const val UTI_MOVIE = "public.movie"

/** Poster JPEG sifati — Android bilan bir xil bo'lsin (chat ro'yxatida kichik ko'rinadi). */
private const val POSTER_JPEG_QUALITY = 0.8

@Composable
actual fun rememberVideoPicker(onResult: (PickedVideo?) -> Unit): VideoPicker {
    val scope = rememberCoroutineScope()

    // ⚠️ Delegate Compose qayta chizilishlari orasida saqlanishi kerak — aks holda
    // PHPicker javob qaytarguncha u yig'ib yuboriladi va callback hech qachon kelmaydi.
    val delegate = remember { VideoPickerDelegate() }
    delegate.onStaged = { staged -> scope.launchStaging(staged, onResult) }

    return remember(delegate) {
        VideoPicker {
            val config = PHPickerConfiguration().apply {
                setFilter(PHPickerFilter.videosFilter())
                setSelectionLimit(1)
            }
            val picker = PHPickerViewController(configuration = config)
            picker.delegate = delegate

            UIApplication.sharedApplication.keyWindow?.rootViewController
                ?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

/**
 * Ko'chirib olingan fayldan [PickedVideo] yasaydi — galereya ham, kamera ham shu yerdan
 * o'tadi.
 *
 * ⚠️ Bu yerda video **siqilmaydi**. Siqish yuborish bosilgandan keyin, xabarning o'z halqasi
 * ichida ketadi ([VideoPreparer]) — aks holda foydalanuvchi tanlagandan keyin bir necha o'n
 * soniya ekran oldida kutib turishi kerak bo'lardi.
 */
internal fun CoroutineScope.launchStaging(staged: NSURL?, onResult: (PickedVideo?) -> Unit): Job = launch {
    if (staged == null) return@launch onResult(null)
    onResult(withContext(Dispatchers.Default) { describeVideo(staged) })
}

private class VideoPickerDelegate : NSObject(), PHPickerViewControllerDelegateProtocol {

    /** Ilova keshiga ko'chirilgan fayl; bekor qilinsa yoki ko'chirib bo'lmasa `null`. */
    var onStaged: (NSURL?) -> Unit = {}

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)

        val provider = (didFinishPicking.firstOrNull() as? PHPickerResult)?.itemProvider
        if (provider == null) {
            onStaged(null) // bekor qilindi
            return
        }

        // Rasmdan farqli o'laroq `loadDataRepresentation` emas: u butun videoni darrov
        // xotiraga soladi va hajmni tekshirishga imkon qolmaydi. Fayl varianti esa avval
        // diskdagi nusxaning o'lchamini bilishga imkon beradi.
        provider.loadFileRepresentationForTypeIdentifier(UTI_MOVIE) { url, _ ->
            // ⚠️ `url` faqat SHU BLOK ichida yashaydi — chiqishimiz bilan tizim vaqtinchalik
            // nusxani o'chiradi. Siqish va yuklash esa bir necha daqiqa davom etadi, shuning
            // uchun fayl darrov o'zimizning papkaga ko'chiriladi.
            val staged = url?.copyToTemporary()
            // Callback fon oqimida keladi — UI holatiga faqat asosiy oqimdan tegamiz.
            dispatch_async(dispatch_get_main_queue()) { onStaged(staged) }
        }
    }
}

/**
 * Fayldan yuboriladigan videoni tavsiflaydi: davomiyligi, hajmi va poster kadri.
 *
 * Hajm bu yerda **tekshirilmaydi**: 64 MB dan kattasi ham qabul qilinadi, chunki uni
 * yuborishdan oldin siqish chegaraga sig'diradi ([VideoPreparer]). Siqib ham sig'masa —
 * o'sha yerda rad etiladi.
 */
private fun describeVideo(staged: NSURL): PickedVideo? {
    val asset = AVURLAsset(uRL = staged, options = null)
    val durationMs = asset.durationMsOrNull()
    // Uzun videoni siqib ham chegaraga sig'dirib bo'lmaydi — vaqtni yo'qotmaymiz.
    if (durationMs != null && durationMs > MAX_VIDEO_MS) return staged.deleteAndNull()

    val sizeBytes = staged.fileSizeOrNull()?.takeIf { it > 0L } ?: return staged.deleteAndNull()

    return PickedVideo(
        path = staged.path ?: return staged.deleteAndNull(),
        // PHPicker nusxaga haqiqiy formatga mos kengaytma beradi (kameradan — `.mov`,
        // yuklab olinganlaridan — `.mp4`); notanish bo'lsa `mp4` ga tushamiz.
        fileName = "video.${staged.videoExtension()}",
        durationMs = durationMs,
        sizeBytes = sizeBytes,
        posterBytes = asset.firstFrameJpegOrNull(),
    )
}

/** Tizimning vaqtinchalik nusxasini o'zimizning papkaga ko'chiradi. */
@OptIn(ExperimentalForeignApi::class)
internal fun NSURL.copyToTemporary(): NSURL? {
    val target = NSURL.fileURLWithPath(
        NSTemporaryDirectory() + "picked_video_${NSUUID().UUIDString}.${videoExtension()}",
    )
    val copied = NSFileManager.defaultManager.copyItemAtURL(this, target, error = null)
    return if (copied) target else null
}

@OptIn(ExperimentalForeignApi::class)
internal fun NSURL.delete() {
    NSFileManager.defaultManager.removeItemAtURL(this, error = null)
}

/** O'chirib `null` qaytaradi — chiqish yo'llarida keshda keraksiz fayl qolmasligi uchun. */
private fun NSURL.deleteAndNull(): PickedVideo? {
    delete()
    return null
}

/** Diskdagi vaqtinchalik nusxaning o'lchami; atributlarni o'qib bo'lmasa `null`. */
@OptIn(ExperimentalForeignApi::class)
internal fun NSURL.fileSizeOrNull(): Long? {
    val path = path ?: return null
    // Xatolik sababi bizga kerak emas (nima bo'lsa ham video yuborilmaydi) — `error = null`.
    val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null)
    return (attributes?.get(NSFileSize) as? NSNumber)?.longLongValue
}

@OptIn(ExperimentalForeignApi::class)
private fun AVURLAsset.durationMsOrNull(): Int? {
    // Oqim (stream) yoki buzuq faylda `CMTimeGetSeconds` NaN/Infinity qaytaradi — o'shanda
    // davomiylikni "noma'lum" deb qoldiramiz va chegarani serverga tekshirtiramiz.
    val seconds = CMTimeGetSeconds(duration)
    if (seconds.isNaN() || seconds.isInfinite() || seconds <= 0.0) return null
    return (seconds * 1000).toInt()
}

/**
 * Birinchi kadr (poster). Kodek kadrni bermasligi mumkin — poster ixtiyoriy, shu sabab
 * xatolik butun tanlovni yiqitmaydi.
 */
@OptIn(ExperimentalForeignApi::class)
private fun AVURLAsset.firstFrameJpegOrNull(): ByteArray? {
    val generator = AVAssetImageGenerator(asset = this).apply {
        // Aks holda telefonda vertikal olingan video poster'da yonboshlab chiqadi.
        appliesPreferredTrackTransform = true
    }
    val cgImage = runCatching {
        generator.copyCGImageAtTime(
            requestedTime = CMTimeMakeWithSeconds(0.0, preferredTimescale = 600),
            actualTime = null,
            error = null,
        )
    }.getOrNull() ?: return null

    val jpeg = UIImageJPEGRepresentation(UIImage.imageWithCGImage(cgImage), POSTER_JPEG_QUALITY)
    // ⚠️ `copyCGImage...` +1 retain bilan qaytaradi va K/N uni avtomatik bo'shatmaydi —
    // qo'lda bo'shatmasak har bir tanlovda bir necha MB oqib ketadi.
    CGImageRelease(cgImage)
    return jpeg?.toKotlinBytes()
}

/** Kengaytma faqat kutilgan ikkitasidan biri bo'lsin — noma'lumi serverda muammo qiladi. */
private fun NSURL.videoExtension(): String =
    when (pathExtension?.lowercase()) {
        "mov" -> "mov"
        else -> "mp4"
    }

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toKotlinBytes(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }
}
