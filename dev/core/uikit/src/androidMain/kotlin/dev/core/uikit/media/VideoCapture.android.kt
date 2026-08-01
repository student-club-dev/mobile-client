package dev.core.uikit.media

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

@Composable
actual fun rememberVideoCapture(onResult: (PickedVideo?) -> Unit): VideoPicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    /**
     * Kamera yozadigan fayl. ⚠️ Kompozitsiyalar orasida saqlanishi shart: `launch` bilan
     * natija orasida ekran qayta chizilishi mumkin va yangi qiymat eskisini yo'qotsa,
     * yozilgan videoni topa olmay qolardik.
     */
    val target = remember { CaptureTarget() }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { saved ->
        val uri = target.uri
        target.uri = null
        // `false` — foydalanuvchi yozishni bekor qildi yoki kamera ilovasi yiqildi.
        if (!saved || uri == null) {
            target.file?.delete()
            target.file = null
            onResult(null)
            return@rememberLauncherForActivityResult
        }
        // ⚠️ Fayl `ownedFile` bo'lib uzatiladi: u allaqachon bizning keshimizda, ya'ni uni
        // ko'chirish shart emas — `launchStaging` faqat nomini o'zgartiradi. Ilgari bu yerda
        // to'liq nusxa olinardi va 180 MB lik lavha keshda IKKI marta yozilardi.
        scope.launchStaging(context, uri, ownedFile = target.file) { picked ->
            // Nomlash muvaffaqiyatsiz bo'lsa fayl o'z joyida ishlatiladi — o'shanda uni
            // o'chirmaymiz, aks holda yuboriladigan videoni yo'q qilgan bo'lardik.
            target.file?.takeIf { it.absolutePath != picked?.path }?.delete()
            target.file = null
            onResult(picked)
        }
    }

    return remember(launcher, target) {
        VideoPicker {
            val file = context.newCaptureFile()
            val uri = context.captureUriOrNull(file)
            if (uri == null) {
                // FileProvider sozlanmagan — kamerani ochishning ma'nosi yo'q, u baribir
                // yozadigan joy topa olmasdi.
                file.delete()
                onResult(null)
            } else {
                target.file = file
                target.uri = uri
                launcher.launch(uri)
            }
        }
    }
}

/** Kamera yozayotgan fayl va uning `content://` havolasi. */
private class CaptureTarget {
    var file: File? = null
    var uri: Uri? = null
}

/**
 * Kamera yozadigan fayl — ilovaning **o'z** keshida.
 *
 * `MediaStore` emas: u videoni foydalanuvchining galereyasiga qo'shardi va tasodifan
 * bosilgan tugma o'sha yerda keraksiz lavha qoldirardi.
 */
private fun Context.newCaptureFile(): File =
    File(cacheDir, CAPTURE_DIR).apply { mkdirs() }
        .resolve("capture_${System.currentTimeMillis()}.mp4")

/**
 * Faylning kamera ilovasi yoza oladigan havolasi.
 *
 * `Uri.fromFile` ishlamaydi: Android 7+ da boshqa ilovaga `file://` uzatish
 * `FileUriExposedException` bilan tugaydi. Shuning uchun `FileProvider` — uning e'loni
 * `uikit` modulining manifestida, ya'ni ilova tomonda sozlash talab qilinmaydi.
 */
private fun Context.captureUriOrNull(file: File): Uri? =
    runCatching {
        FileProvider.getUriForFile(this, "$packageName.$FILE_PROVIDER_SUFFIX", file)
    }.getOrNull()

/**
 * Papka nomi `sc_media_paths.xml` dagi `cache-path` bilan **bir xil** bo'lishi shart —
 * aks holda `FileProvider` faylni "ruxsat etilmagan" deb rad etadi.
 */
private const val CAPTURE_DIR = "sc_capture"

/** Manifestdagi `android:authorities` bilan bir xil. */
private const val FILE_PROVIDER_SUFFIX = "scfileprovider"
