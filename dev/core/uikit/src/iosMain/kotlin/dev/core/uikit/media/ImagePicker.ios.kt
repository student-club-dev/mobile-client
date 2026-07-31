package dev.core.uikit.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.Foundation.NSItemProvider
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

/** Har qanday rasm formati — PHPicker shu identifikator bo'yicha ma'lumot beradi. */
private const val UTI_IMAGE = "public.image"

/** GIF ning UTI'si — PHPicker uni `public.image` ostida qaytaradi, turini alohida so'raymiz. */
private const val UTI_GIF = "com.compuserve.gif"
private const val UTI_PNG = "public.png"
private const val UTI_WEBP = "org.webmproject.webp"

/**
 * Tanlangan elementning kengaytmasi.
 *
 * ⚠️ **GIF alohida muhim.** PHPicker GIF'ni ham oddiy rasm sifatida beradi, lekin chat
 * qatlami aynan kengaytmaga qarab uni `kind = GIF` bilan yuklaydi (server uni ovozsiz MP4
 * ga o'giradi, ~20 barobar yengil). Ilgari bu yerda hamma narsa `jpg` deb atalardi va GIF
 * jimgina oddiy rasmga aylanib, **animatsiyasini yo'qotardi**.
 */
private fun NSItemProvider.pickedExtension(): String = when {
    hasItemConformingToTypeIdentifier(UTI_GIF) -> "gif"
    hasItemConformingToTypeIdentifier(UTI_PNG) -> "png"
    hasItemConformingToTypeIdentifier(UTI_WEBP) -> "webp"
    else -> "jpg"
}

@Composable
actual fun rememberImagePicker(onResult: (PickedImage?) -> Unit): ImagePicker {
    // Delegate Compose qayta chizilishlari orasida saqlanishi kerak — aks holda
    // PHPicker javob qaytarguncha u yig'ib yuboriladi va callback hech qachon kelmaydi.
    val delegate = remember { PhotoPickerDelegate() }
    delegate.onResult = onResult

    return remember(delegate) {
        ImagePicker {
            val config = PHPickerConfiguration().apply {
                setFilter(PHPickerFilter.imagesFilter())
                setSelectionLimit(1)
            }
            val picker = PHPickerViewController(configuration = config)
            picker.delegate = delegate

            UIApplication.sharedApplication.keyWindow?.rootViewController
                ?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

private class PhotoPickerDelegate : NSObject(), PHPickerViewControllerDelegateProtocol {

    var onResult: (PickedImage?) -> Unit = {}

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)

        val provider = (didFinishPicking.firstOrNull() as? PHPickerResult)?.itemProvider
        if (provider == null) {
            onResult(null) // bekor qilindi
            return
        }

        provider.loadDataRepresentationForTypeIdentifier(UTI_IMAGE) { data, _ ->
            val picked = data?.toByteArray()?.let { PickedImage(it, "image." + provider.pickedExtension()) }
            // Callback fon oqimida keladi — UI holatiga faqat asosiy oqimdan tegamiz.
            dispatch_async(dispatch_get_main_queue()) { onResult(picked) }
        }
    }
}

@Composable
actual fun rememberMultiImagePicker(
    maxItems: Int,
    onResult: (List<PickedImage>) -> Unit,
): ImagePicker {
    val delegate = remember { MultiPhotoPickerDelegate() }
    delegate.onResult = onResult

    return remember(delegate, maxItems) {
        ImagePicker {
            val config = PHPickerConfiguration().apply {
                setFilter(PHPickerFilter.imagesFilter())
                setSelectionLimit(maxItems.coerceAtLeast(1).toLong())
            }
            val picker = PHPickerViewController(configuration = config)
            picker.delegate = delegate

            UIApplication.sharedApplication.keyWindow?.rootViewController
                ?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

/**
 * Ko'p rasmli PHPicker delegati.
 *
 * `loadDataRepresentation` har bir element uchun **alohida va asinxron** tugaydi, tartibi
 * ham kafolatlanmagan. Shuning uchun natijalar indeks bo'yicha oldindan o'lchamli massivga
 * yoziladi (tanlangan tartib saqlanadi) va faqat **oxirgisi** kelganda callback chaqiriladi.
 */
private class MultiPhotoPickerDelegate : NSObject(), PHPickerViewControllerDelegateProtocol {

    var onResult: (List<PickedImage>) -> Unit = {}

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)

        val providers = didFinishPicking.mapNotNull { (it as? PHPickerResult)?.itemProvider }
        if (providers.isEmpty()) {
            onResult(emptyList()) // bekor qilindi
            return
        }

        val slots = arrayOfNulls<PickedImage>(providers.size)
        var remaining = providers.size

        providers.forEachIndexed { index, provider ->
            provider.loadDataRepresentationForTypeIdentifier(UTI_IMAGE) { data, _ ->
                val image = data?.toByteArray()
                    ?.let { PickedImage(it, "image_$index." + provider.pickedExtension()) }
                // Yig'ish ham, callback ham FAQAT asosiy oqimda: `remaining` ustida poyga
                // bo'lmaydi va atomik hisoblagich kerak emas.
                dispatch_async(dispatch_get_main_queue()) {
                    slots[index] = image
                    remaining -= 1
                    if (remaining == 0) onResult(slots.filterNotNull())
                }
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }
}

actual fun ByteArray.toImageBitmapOrNull(): ImageBitmap? =
    runCatching { Image.makeFromEncoded(this).toComposeImageBitmap() }.getOrNull()
