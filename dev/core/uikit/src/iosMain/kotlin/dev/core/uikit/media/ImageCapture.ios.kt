package dev.core.uikit.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationController
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject

/** Kamera oladigan format — `PHPicker` dagi bilan bir xil identifikator. */
private const val UTI_IMAGE_CAPTURE = "public.image"

/**
 * Suratning JPEG sifati.
 *
 * `1.0` emas: kamera bergan `UIImage` siqilmagan holda 10 MB dan oshadi va u yuklashda
 * sezilarli kutish bo'lardi. `0.9` da farq ko'z bilan ilg'anmaydi.
 */
private const val CAPTURE_JPEG_QUALITY = 0.9

@Composable
actual fun rememberImageCapture(onResult: (PickedImage?) -> Unit): ImagePicker {
    // ⚠️ Delegate Compose qayta chizilishlari orasida saqlanishi kerak — aks holda kamera
    // yopilguncha u yig'ib yuboriladi va callback hech qachon kelmaydi.
    val captureDelegate = remember { PhotoCaptureDelegate() }
    captureDelegate.onResult = onResult

    return remember(captureDelegate) {
        ImagePicker {
            val source = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            // Simulyatorda va kamerasi yopiq qurilmalarda kamera yo'q — usiz
            // `presentViewController` bo'sh qora ekran ko'rsatib qotib qolardi.
            if (!UIImagePickerController.isSourceTypeAvailable(source)) {
                onResult(null)
                return@ImagePicker
            }

            val picker = UIImagePickerController().apply {
                sourceType = source
                // Faqat surat: usiz kamera video rejimiga ham o'ta olardi va natijada
                // rasm o'rniga `mediaURL` kelardi.
                mediaTypes = listOf(UTI_IMAGE_CAPTURE)
                delegate = captureDelegate
            }

            UIApplication.sharedApplication.keyWindow?.rootViewController
                ?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

/**
 * `UIImagePickerController` ikkala protokolni ham talab qiladi: `UINavigationControllerDelegate`
 * ishlatilmasa ham, usiz `delegate` ni belgilab bo'lmaydi.
 */
private class PhotoCaptureDelegate :
    NSObject(),
    UIImagePickerControllerDelegateProtocol,
    UINavigationControllerDelegateProtocol {

    var onResult: (PickedImage?) -> Unit = {}

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        picker.dismissViewControllerAnimated(true, completion = null)

        // Kamera faylni emas, `UIImage` ni beradi — uni o'zimiz JPEG'ga o'giramiz. Callback
        // asosiy oqimda keladi, ya'ni qo'shimcha almashtirish kerak emas.
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        val data = image?.let { UIImageJPEGRepresentation(it, CAPTURE_JPEG_QUALITY) }
        onResult(data?.toByteArray()?.let { PickedImage(it, "image.jpg") })
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
        onResult(null)
    }

    /** Protokol talabi — kamera oynasining navigatsiyasiga aralashmaymiz. */
    override fun navigationController(
        navigationController: UINavigationController,
        willShowViewController: UIViewController,
        animated: Boolean,
    ) = Unit
}
