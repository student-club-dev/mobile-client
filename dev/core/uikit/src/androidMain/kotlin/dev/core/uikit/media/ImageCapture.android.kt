package dev.core.uikit.media

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
actual fun rememberImageCapture(onResult: (PickedImage?) -> Unit): ImagePicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    /**
     * Kamera yozadigan fayl. ⚠️ Kompozitsiyalar orasida saqlanishi shart: `launch` bilan
     * natija orasida ekran qayta chizilishi mumkin va yangi qiymat eskisini yo'qotsa,
     * olingan suratni topa olmay qolardik ([rememberVideoCapture] dagi kabi).
     */
    val target = remember { PhotoTarget() }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = target.file
        target.file = null
        // `false` — foydalanuvchi suratni bekor qildi yoki kamera ilovasi yiqildi.
        if (!saved || file == null) {
            file?.delete()
            onResult(null)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            // Videodan farqli o'laroq surat baytlari xotiraga o'qiladi: u kichik va
            // shunda natija galereyadan tanlangani bilan bir xil ([PickedImage]) bo'ladi.
            val picked = withContext(Dispatchers.IO) {
                runCatching { PickedImage(file.readBytes(), "image.jpg") }.getOrNull()
            }
            // Baytlar qo'limizda — keshda nusxa qoldirishning ma'nosi yo'q.
            file.delete()
            onResult(picked)
        }
    }

    /**
     * ⚠️ **Ruxsat kerak bo'lib qoldi** — sabab `VideoCapture.android.kt` dagi bilan bir xil:
     * ilova `CAMERA` ruxsatini e'lon qilgani uchun Android tizim kamerasini ochadigan
     * `TakePicture` ni ham himoyalangan deb hisoblaydi.
     */
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) target.pendingLaunch?.invoke() else onResult(null)
        target.pendingLaunch = null
    }

    return remember(launcher, permission, target) {
        ImagePicker {
            val open = open@{
                val file = context.newCaptureFile("jpg")
                val uri = context.captureUriOrNull(file)
                if (uri == null) {
                    // FileProvider sozlanmagan — kamera yozadigan joy topa olmasdi.
                    file.delete()
                    onResult(null)
                    return@open
                }
                target.file = file
                launcher.launch(uri)
            }
            if (context.hasCameraPermission()) {
                open()
            } else {
                target.pendingLaunch = open
                permission.launch(Manifest.permission.CAMERA)
            }
        }
    }
}

/** Kamera yozayotgan surat fayli va ruxsat berilgach bajariladigan ish. */
private class PhotoTarget {
    var file: File? = null

    /** Ruxsat berilgach ochiladigan kamera — foydalanuvchi tugmani ikki marta bosmasin. */
    var pendingLaunch: (() -> Unit)? = null
}
