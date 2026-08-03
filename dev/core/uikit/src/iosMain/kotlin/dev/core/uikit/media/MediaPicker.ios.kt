package dev.core.uikit.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private const val UTI_IMAGE = "public.image"
private const val UTI_MOVIE = "public.movie"

@Composable
actual fun rememberMultiMediaPicker(
    maxItems: Int,
    onResult: (PickedMedia) -> Unit,
): MediaPicker {
    val scope = rememberCoroutineScope()

    // ⚠️ Delegate Compose qayta chizilishlari orasida saqlanishi kerak — aks holda PHPicker
    // javob qaytarguncha u yig'ib yuboriladi va callback hech qachon kelmaydi.
    val delegate = remember { MultiMediaPickerDelegate() }
    delegate.onResult = onResult
    delegate.scope = scope

    return remember(delegate, maxItems) {
        MediaPicker {
            val config = PHPickerConfiguration().apply {
                // Rasm ∪ video — Telegramdagi kabi bitta ro'yxatda ko'rinadi.
                setFilter(
                    PHPickerFilter.anyFilterMatchingSubfilters(
                        listOf(PHPickerFilter.imagesFilter(), PHPickerFilter.videosFilter()),
                    ),
                )
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
 * Rasm+video PHPicker delegati.
 *
 * Har bir element **alohida va asinxron** yuklanadi, tartibi kafolatlanmagan: shuning uchun
 * natijalar indeks bo'yicha oldindan o'lchamli massivlarga yoziladi (tanlangan tartib
 * saqlanadi) va callback faqat **oxirgisi** kelganda chaqiriladi.
 *
 * Hisoblagich va massivlarga faqat **asosiy oqimdan** tegiladi — atomik hisoblagich kerak
 * emas va poyga bo'lmaydi.
 */
private class MultiMediaPickerDelegate : NSObject(), PHPickerViewControllerDelegateProtocol {

    var onResult: (PickedMedia) -> Unit = {}

    /** Videoni tavsiflash (poster kadri, davomiylik) uchun — u fon oqimida bajariladi. */
    var scope: CoroutineScope? = null

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)

        val providers = didFinishPicking.mapNotNull { (it as? PHPickerResult)?.itemProvider }
        if (providers.isEmpty()) {
            onResult(PickedMedia.Empty) // bekor qilindi
            return
        }

        val images = arrayOfNulls<PickedImage>(providers.size)
        val videos = arrayOfNulls<PickedVideo>(providers.size)
        var remaining = providers.size

        // Faqat asosiy oqimdan chaqiriladi.
        fun finishOne() {
            remaining -= 1
            if (remaining == 0) {
                val kept = images.filterNotNull()
                val keptVideos = videos.filterNotNull()
                onResult(
                    PickedMedia(
                        images = kept,
                        videos = keptVideos,
                        // Qabul qilinmaganlar: uzun video, buzuq fayl, o'qib bo'lmagani.
                        skipped = providers.size - kept.size - keptVideos.size,
                    ),
                )
            }
        }

        providers.forEachIndexed { index, provider ->
            if (provider.hasItemConformingToTypeIdentifier(UTI_MOVIE)) {
                // Rasmdan farqli o'laroq `loadDataRepresentation` emas: u butun videoni
                // darrov xotiraga solardi (qarang `VideoPicker.ios.kt`).
                provider.loadFileRepresentationForTypeIdentifier(UTI_MOVIE) { url, _ ->
                    // ⚠️ `url` faqat shu blok ichida yashaydi — tizim nusxani chiqishimiz
                    // bilan o'chiradi, shuning uchun darrov o'zimizning papkaga ko'chiramiz.
                    val staged = url?.copyToTemporary()
                    dispatch_async(dispatch_get_main_queue()) {
                        val active = scope
                        if (staged == null || active == null) {
                            staged?.delete()
                            finishOne()
                            return@dispatch_async
                        }
                        active.launch {
                            // Poster kadrini dekodlash arzon emas — UI oqimida qilinmaydi.
                            videos[index] = withContext(Dispatchers.Default) { describeVideo(staged) }
                            finishOne()
                        }
                    }
                }
            } else {
                provider.loadDataRepresentationForTypeIdentifier(UTI_IMAGE) { data, _ ->
                    val image = data?.toByteArray()
                        ?.let { PickedImage(it, "image_$index." + provider.pickedExtension()) }
                    // Callback fon oqimida keladi — holatga faqat asosiy oqimdan tegamiz.
                    dispatch_async(dispatch_get_main_queue()) {
                        images[index] = image
                        finishOne()
                    }
                }
            }
        }
    }
}
