package dev.core.uikit.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerMediaURL
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationController
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject

/** Kamera yozadigan format — `PHPicker` dagi bilan bir xil identifikator. */
private const val UTI_MOVIE_CAPTURE = "public.movie"

@Composable
actual fun rememberVideoCapture(onResult: (PickedVideo?) -> Unit): VideoPicker {
    val scope = rememberCoroutineScope()

    // ⚠️ Delegate Compose qayta chizilishlari orasida saqlanishi kerak — aks holda kamera
    // yopilguncha u yig'ib yuboriladi va callback hech qachon kelmaydi.
    val captureDelegate = remember { VideoCaptureDelegate() }
    captureDelegate.onStaged = { staged -> scope.launchStaging(staged, onResult) }

    return remember(captureDelegate) {
        VideoPicker {
            val source = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            // Simulyatorda va kamerasi yopiq qurilmalarda kamera yo'q — usiz
            // `presentViewController` bo'sh qora ekran ko'rsatib qotib qolardi.
            if (!UIImagePickerController.isSourceTypeAvailable(source)) {
                onResult(null)
                return@VideoPicker
            }

            val picker = UIImagePickerController().apply {
                sourceType = source
                // Faqat video: usiz kamera surat rejimida ochilardi va natijada
                // `mediaURL` bo'sh kelardi.
                mediaTypes = listOf(UTI_MOVIE_CAPTURE)
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
private class VideoCaptureDelegate :
    NSObject(),
    UIImagePickerControllerDelegateProtocol,
    UINavigationControllerDelegateProtocol {

    /** Ilova keshiga ko'chirilgan fayl; bekor qilinsa yoki ko'chirib bo'lmasa `null`. */
    var onStaged: (NSURL?) -> Unit = {}

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        picker.dismissViewControllerAnimated(true, completion = null)

        // ⚠️ Kamera bergan URL tizimning vaqtinchalik papkasida va uni tizim istagan
        // paytda o'chiradi. Yuborish esa bir necha daqiqa davom etishi mumkin, shuning
        // uchun fayl darrov o'zimizning nusxamizga ko'chiriladi.
        val source = didFinishPickingMediaWithInfo[UIImagePickerControllerMediaURL] as? NSURL
        val staged = source?.copyToTemporary()
        // Asl vaqtinchalik faylni o'zimiz tozalaymiz — tizimga tayanmaymiz.
        source?.delete()
        onStaged(staged)
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
        onStaged(null)
    }

    /** Protokol talabi — kamera oynasining navigatsiyasiga aralashmaymiz. */
    override fun navigationController(
        navigationController: UINavigationController,
        willShowViewController: UIViewController,
        animated: Boolean,
    ) = Unit
}
