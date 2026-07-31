package dev.core.uikit.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
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
import platform.Foundation.NSURL
import platform.Foundation.create
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
    // ⚠️ Delegate Compose qayta chizilishlari orasida saqlanishi kerak — aks holda
    // PHPicker javob qaytarguncha u yig'ib yuboriladi va callback hech qachon kelmaydi.
    val delegate = remember { VideoPickerDelegate() }
    delegate.onResult = onResult

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

private class VideoPickerDelegate : NSObject(), PHPickerViewControllerDelegateProtocol {

    var onResult: (PickedVideo?) -> Unit = {}

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)

        val provider = (didFinishPicking.firstOrNull() as? PHPickerResult)?.itemProvider
        if (provider == null) {
            onResult(null) // bekor qilindi
            return
        }

        // Rasmdan farqli o'laroq `loadDataRepresentation` emas: u butun videoni darrov
        // xotiraga soladi va hajmni tekshirishga imkon qolmaydi. Fayl varianti esa avval
        // diskdagi nusxaning o'lchamini bilishga imkon beradi.
        provider.loadFileRepresentationForTypeIdentifier(UTI_MOVIE) { url, _ ->
            // ⚠️ `url` faqat shu blok ichida yashaydi — chiqishimiz bilan tizim vaqtinchalik
            // nusxani o'chiradi, shuning uchun baytlar ham, poster ham SHU YERDA olinadi.
            val picked = url?.let { readVideoOrNull(it) }
            // Callback fon oqimida keladi — UI holatiga faqat asosiy oqimdan tegamiz.
            dispatch_async(dispatch_get_main_queue()) { onResult(picked) }
        }
    }
}

private fun readVideoOrNull(url: NSURL): PickedVideo? {
    // ⚠️ Hajm baytlarni o'qishdan OLDIN tekshiriladi: 64 MB dan kattasi xotiraga tushmasin.
    val sizeBytes = url.fileSizeOrNull() ?: return null
    if (sizeBytes > MAX_VIDEO_BYTES) return null

    val asset = AVURLAsset(uRL = url, options = null)
    val durationMs = asset.durationMsOrNull()
    if (durationMs != null && durationMs > MAX_VIDEO_MS) return null

    val bytes = NSData.create(contentsOfURL = url)?.toKotlinBytes() ?: return null

    return PickedVideo(
        bytes = bytes,
        // PHPicker nusxaga haqiqiy formatga mos kengaytma beradi (kameradan — `.mov`,
        // yuklab olinganlaridan — `.mp4`); notanish bo'lsa `mp4` ga tushamiz.
        fileName = "video.${url.videoExtension()}",
        durationMs = durationMs,
        sizeBytes = sizeBytes,
        posterBytes = asset.firstFrameJpegOrNull(),
    )
}

/** Diskdagi vaqtinchalik nusxaning o'lchami; atributlarni o'qib bo'lmasa `null`. */
@OptIn(ExperimentalForeignApi::class)
private fun NSURL.fileSizeOrNull(): Long? {
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
