package dev.core.uikit.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberFilePicker(onResult: (PickedFile?) -> Unit): FilePicker {
    // ⚠️ Delegate Compose qayta chizilishlari orasida saqlanishi kerak — aks holda
    // tanlagich javob qaytarguncha u yig'ib yuboriladi va callback hech qachon kelmaydi.
    val delegate = remember { DocumentPickerDelegate() }
    delegate.onResult = onResult

    return remember(delegate) {
        FilePicker {
            // `UTTypeItem` — turi cheklanmagan har qanday fayl (papkalardan tashqari).
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeItem))
            picker.allowsMultipleSelection = false
            picker.delegate = delegate

            UIApplication.sharedApplication.keyWindow?.rootViewController
                ?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

private class DocumentPickerDelegate : NSObject(), UIDocumentPickerDelegateProtocol {

    var onResult: (PickedFile?) -> Unit = {}

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        // Callback allaqachon asosiy oqimda keladi — `dispatch_async` shart emas.
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        onResult(url?.readPickedFile())
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onResult(null) // bekor qilindi
    }
}

/**
 * Tanlangan hujjatni baytlarga o'giradi.
 *
 * ⚠️ URL "security scoped": ilova qumdonidan tashqaridagi faylga ruxsat faqat
 * `startAccessingSecurityScopedResource()` va `stopAccessingSecurityScopedResource()`
 * orasida ochiq bo'ladi, aks holda o'qish jimgina `null` qaytaradi.
 */
@OptIn(ExperimentalForeignApi::class)
private fun NSURL.readPickedFile(): PickedFile? {
    val accessGranted = startAccessingSecurityScopedResource()
    try {
        val filePath = path ?: return null
        val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(filePath, error = null)
        val size = (attributes?.get(NSFileSize) as? NSNumber)?.longLongValue ?: return null

        // ⚠️ Chegara tekshiruvi `dataWithContentsOfURL` dan OLDIN: u faylni to'liq xotiraga
        // o'qiydi, ya'ni katta faylda ilova xotira yetishmasligidan yiqiladi.
        if (size <= 0L || size > MAX_FILE_BYTES) return null

        val data = NSData.dataWithContentsOfURL(this) ?: return null
        return PickedFile(
            bytes = data.toKotlinBytes(),
            fileName = lastPathComponent?.takeIf { it.isNotBlank() } ?: "file",
            mimeType = mimeTypeFromExtension(),
            sizeBytes = size,
        )
    } finally {
        if (accessGranted) stopAccessingSecurityScopedResource()
    }
}

/**
 * MIME turi kengaytmadan aniqlanadi: hujjat tanlagichi `PHPicker` dan farqli o'laroq
 * turni to'g'ridan-to'g'ri bermaydi. Noma'lum kengaytmada `null` — bunda serverning o'zi
 * turni aniqlaydi.
 */
private fun NSURL.mimeTypeFromExtension(): String? {
    val extension = pathExtension?.takeIf { it.isNotBlank() } ?: return null
    return UTType.typeWithFilenameExtension(extension)?.preferredMIMEType
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toKotlinBytes(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }
}
