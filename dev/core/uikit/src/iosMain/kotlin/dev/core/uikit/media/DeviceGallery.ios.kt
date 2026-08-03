package dev.core.uikit.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSSortDescriptor
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Photos.PHAsset
import platform.Photos.PHAssetMediaTypeVideo
import platform.Photos.PHAssetResource
import platform.Photos.PHAssetResourceManager
import platform.Photos.PHAssetResourceRequestOptions
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHAuthorizationStatusNotDetermined
import platform.Photos.PHFetchOptions
import platform.Photos.PHImageManager
import platform.Photos.PHImageRequestOptions
import platform.Photos.PHImageRequestOptionsDeliveryModeHighQualityFormat
import platform.Photos.PHImageRequestOptionsResizeModeExact
import platform.Photos.PHPhotoLibrary
import platform.Photos.PHAccessLevelReadWrite
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.Foundation.NSURL.Companion.URLWithString
import platform.UIKit.UIApplicationOpenSettingsURLString

/** To'r katagi uchun JPEG sifati — ekranda kichik ko'rinadi. */
private const val THUMB_JPEG_QUALITY = 0.8

@Composable
actual fun rememberDeviceGallery(): DeviceGallery {
    val gallery = remember { IosDeviceGallery() }
    gallery.refreshAccess()
    return gallery
}

/**
 * `PHPhotoLibrary` ustidagi galereya.
 *
 * ⚠️ iOS'da «limited» holati Android 14 dagidek: foydalanuvchi ayrim rasmlarni tanlagan
 * bo'ladi va `PHAsset.fetchAssets` faqat o'shalarni qaytaradi. Bu **xato emas** — to'r
 * ishlaydi, faqat ro'yxat qisqa; varaq bunda "yana tanlash" yo'lini ko'rsatadi.
 */
private class IosDeviceGallery : DeviceGallery {

    override var access: GalleryAccess by mutableStateOf(GalleryAccess.UNKNOWN)
        private set

    /** Fetch natijasi keshlanmaydi: galereya ilova fonda turganda ham o'zgarishi mumkin. */
    fun refreshAccess() {
        access = when (PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelReadWrite)) {
            PHAuthorizationStatusAuthorized -> GalleryAccess.GRANTED
            PHAuthorizationStatusLimited -> GalleryAccess.LIMITED
            PHAuthorizationStatusNotDetermined -> GalleryAccess.UNKNOWN
            else -> GalleryAccess.DENIED
        }
    }

    override fun requestAccess() {
        // ⚠️ «Limited» dan «to'liq» ga API bilan o'tib bo'lmaydi: `requestAuthorization`
        // faqat o'sha tanlangan rasmlar ro'yxatini qayta ochadi. Yagona yo'l — Sozlamalar.
        if (access == GalleryAccess.DENIED || access == GalleryAccess.LIMITED) {
            // Rad etilgandan keyin tizim oynasi boshqa chiqmaydi — sozlamalarga yuboramiz.
            URLWithString(UIApplicationOpenSettingsURLString)?.let {
                UIApplication.sharedApplication.openURL(it)
            }
            return
        }
        PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelReadWrite) { refreshAccess() }
    }

    override suspend fun page(offset: Int, limit: Int): List<GalleryItem> =
        withContext(Dispatchers.Default) {
            if (access == GalleryAccess.DENIED || access == GalleryAccess.UNKNOWN) {
                return@withContext emptyList()
            }
            val options = PHFetchOptions().apply {
                // Eng yangisidan boshlab — Android tomoni bilan bir xil tartib.
                sortDescriptors = listOf(NSSortDescriptor("creationDate", ascending = false))
            }
            val assets = PHAsset.fetchAssetsWithOptions(options)
            val total = assets.count.toInt()
            if (offset >= total) return@withContext emptyList()

            (offset until minOf(offset + limit, total)).mapNotNull { index ->
                (assets.objectAtIndex(index.toULong()) as? PHAsset)?.let { asset ->
                    GalleryItem(
                        id = asset.localIdentifier,
                        isVideo = asset.mediaType == PHAssetMediaTypeVideo,
                        durationMs = (asset.duration * 1000).toInt(),
                    )
                }
            }
        }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun thumbnail(item: GalleryItem, sizePx: Int): ImageBitmap? {
        val asset = assetOf(item.id) ?: return null
        val data = CompletableDeferred<ByteArray?>()
        val options = PHImageRequestOptions().apply {
            // Sinxron emas: kutish korutinada, UI oqimi bo'sh qoladi.
            setNetworkAccessAllowed(true)
            setDeliveryMode(PHImageRequestOptionsDeliveryModeHighQualityFormat)
            setResizeMode(PHImageRequestOptionsResizeModeExact)
        }
        PHImageManager.defaultManager().requestImageForAsset(
            asset = asset,
            targetSize = CGSizeMake(sizePx.toDouble(), sizePx.toDouble()),
            contentMode = platform.Photos.PHImageContentModeAspectFill,
            options = options,
        ) { image, _ ->
            data.complete((image as? UIImage)?.let { UIImageJPEGRepresentation(it, THUMB_JPEG_QUALITY) }?.toByteArray())
        }
        val bytes = data.await() ?: return null
        return withContext(Dispatchers.Default) {
            runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
        }
    }

    override suspend fun load(items: List<GalleryItem>): PickedMedia {
        val images = mutableListOf<PickedImage>()
        val videos = mutableListOf<PickedVideo>()
        var skipped = 0

        items.forEach { item ->
            val asset = assetOf(item.id)
            val picked = when {
                asset == null -> null
                item.isVideo -> asset.exportVideo()?.let { staged ->
                    withContext(Dispatchers.Default) { describeVideo(staged) }?.also(videos::add)
                }
                else -> asset.exportImage()?.also(images::add)
            }
            if (picked == null) skipped += 1
        }
        return PickedMedia(images = images, videos = videos, skipped = skipped)
    }

    private fun assetOf(localIdentifier: String): PHAsset? =
        PHAsset.fetchAssetsWithLocalIdentifiers(listOf(localIdentifier), options = null)
            .firstObject as? PHAsset
}

/**
 * Rasm baytlari — **asl fayl**, qayta kodlanmasdan.
 *
 * `requestImageDataAndOrientation` HEIC'ni ham o'z holicha beradi; server uni qabul qiladi
 * va o'zi WebP ga o'giradi, ya'ni bu yerda konvertatsiya qilish bekor ish bo'lardi.
 */
private suspend fun PHAsset.exportImage(): PickedImage? {
    val resource = (PHAssetResource.assetResourcesForAsset(this).firstOrNull() as? PHAssetResource)
        ?: return null
    val extension = resource.originalFilename.substringAfterLast('.', "jpg").lowercase()

    val chunks = mutableListOf<ByteArray>()
    val done = CompletableDeferred<Boolean>()
    val options = PHAssetResourceRequestOptions().apply { setNetworkAccessAllowed(true) }

    PHAssetResourceManager.defaultManager().requestDataForAssetResource(
        resource = resource,
        options = options,
        dataReceivedHandler = { data -> data?.toByteArray()?.let(chunks::add) },
        completionHandler = { error -> done.complete(error == null) },
    )
    if (!done.await()) return null

    val bytes = chunks.reduceOrNull { acc, next -> acc + next } ?: return null
    return PickedImage(bytes = bytes, fileName = "image.$extension")
}

/**
 * Videoni ilova keshiga chiqaradi.
 *
 * `AVAsset` emas, **resurs fayli** ko'chiriladi: `AVAsset` iCloud'dagi lavhada `nil`
 * bo'lishi mumkin, resurs esa tarmoq orqali yuklab beriladi.
 */
@OptIn(ExperimentalForeignApi::class)
private suspend fun PHAsset.exportVideo(): NSURL? {
    val resource = (PHAssetResource.assetResourcesForAsset(this).firstOrNull() as? PHAssetResource)
        ?: return null
    val extension = resource.originalFilename.substringAfterLast('.', "mp4").lowercase()
        .takeIf { it == "mov" || it == "mp4" } ?: "mp4"

    val target = NSURL.fileURLWithPath(
        NSTemporaryDirectory() + "gallery_video_${NSUUID().UUIDString}.$extension",
    )
    val done = CompletableDeferred<Boolean>()
    val options = PHAssetResourceRequestOptions().apply { setNetworkAccessAllowed(true) }

    PHAssetResourceManager.defaultManager().writeDataForAssetResource(
        resource = resource,
        toFile = target,
        options = options,
    ) { error -> done.complete(error == null) }

    return if (done.await()) target else null
}
