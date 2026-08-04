@file:OptIn(ExperimentalForeignApi::class)

package dev.core.uikit.media

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVAssetImageGenerator
import platform.AVFoundation.AVURLAsset
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.Foundation.writeToFile

/**
 * iOS: kadr [AVAssetImageGenerator] bilan ajratiladi.
 *
 * Himoyalangan havola uchun `Authorization` sarlavhasi `AVURLAsset` ning
 * `AVURLAssetHTTPHeaderFieldsKey` opsiyasi orqali beriladi — usiz server `401` qaytarardi
 * va kadr umuman chiqmasdi.
 */
actual suspend fun videoPosterUrl(
    source: String,
    headers: Map<String, String>,
    cacheKey: String,
): String? = withContext(Dispatchers.Default) {
    val manager = NSFileManager.defaultManager
    val folder = NSTemporaryDirectory() + POSTER_DIR
    if (!manager.fileExistsAtPath(folder)) {
        manager.createDirectoryAtPath(folder, withIntermediateDirectories = true, attributes = null, error = null)
    }
    val target = "$folder/$cacheKey.jpg"
    // Bir marta hisoblanadi.
    if (manager.fileExistsAtPath(target)) return@withContext localFileUrl(target)

    val url = if (source.startsWith("file://") || source.contains("://")) {
        NSURL(string = source)
    } else {
        NSURL.fileURLWithPath(source)
    }

    val options = if (headers.isEmpty()) null else mapOf<Any?, Any?>(HTTP_HEADERS_KEY to headers)
    val asset = AVURLAsset(uRL = url, options = options)
    val generator = AVAssetImageGenerator(asset).apply {
        // Kadr videoning o'z nisbatida chiqsin — pufak uni o'zi kesadi.
        appliesPreferredTrackTransform = true
    }

    val cgImage = runCatching {
        generator.copyCGImageAtTime(
            requestedTime = CMTimeMake(value = 0, timescale = 1),
            actualTime = null,
            error = null,
        )
    }.getOrNull() ?: return@withContext null

    val data = UIImageJPEGRepresentation(UIImage.imageWithCGImage(cgImage), POSTER_QUALITY)
        ?: return@withContext null
    if (!data.writeToFile(target, atomically = true)) return@withContext null

    localFileUrl(target)
}

private const val POSTER_DIR = "video_posters"

/** Pufakdagi kichik rasm uchun yetarli. */
private const val POSTER_QUALITY = 0.85

/** `AVURLAssetHTTPHeaderFieldsKey` — Kotlin/Native bindingida ochiq turmagan xususiy kalit. */
private const val HTTP_HEADERS_KEY = "AVURLAssetHTTPHeaderFieldsKey"
