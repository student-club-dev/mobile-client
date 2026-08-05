package dev.core.uikit.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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

    /**
     * ⚠️ **Ruxsat kerak bo'lib qoldi.** `ActivityResultContracts.CaptureVideo` tizim
     * kamerasini ochadi va odatda hech qanday ruxsat talab qilmaydi — **lekin faqat ilova
     * `CAMERA` ruxsatini e'lon qilmagan bo'lsa**. Biz uni video qo'ng'iroq uchun e'lon
     * qildik, ya'ni Android endi shu chaqiruvni ham himoyalangan deb hisoblaydi va ruxsat
     * berilmagan bo'lsa `SecurityException` bilan yiqiladi.
     *
     * Shuning uchun ruxsat **tugma bosilganda** so'raladi; rad etilsa `null` qaytadi va
     * chaqiruvchi uchun bu «bekor qilindi» bilan bir xil.
     */
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) target.pendingLaunch?.invoke() else onResult(null)
        target.pendingLaunch = null
    }

    return remember(launcher, permission, target) {
        VideoPicker {
            val open = open@{
                val file = context.newCaptureFile("mp4")
                val uri = context.captureUriOrNull(file)
                if (uri == null) {
                    // FileProvider sozlanmagan — kamerani ochishning ma'nosi yo'q, u baribir
                    // yozadigan joy topa olmasdi.
                    file.delete()
                    onResult(null)
                    return@open
                }
                target.file = file
                target.uri = uri
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

/** Kamera ruxsati berilganmi — surat olish ([rememberImageCapture]) ham shunga tayanadi. */
internal fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

/** Kamera yozayotgan fayl va uning `content://` havolasi. */
private class CaptureTarget {
    var file: File? = null
    var uri: Uri? = null

    /** Ruxsat berilgach bajariladigan ish — foydalanuvchi tugmani ikki marta bosmasin. */
    var pendingLaunch: (() -> Unit)? = null
}

/**
 * Kamera yozadigan fayl — ilovaning **o'z** keshida. [extension] `mp4` (video) yoki `jpg`
 * (surat).
 *
 * `MediaStore` emas: u videoni foydalanuvchining galereyasiga qo'shardi va tasodifan
 * bosilgan tugma o'sha yerda keraksiz lavha qoldirardi.
 */
internal fun Context.newCaptureFile(extension: String): File =
    File(cacheDir, CAPTURE_DIR).apply { mkdirs() }
        .resolve("capture_${System.currentTimeMillis()}.$extension")

/**
 * Faylning kamera ilovasi yoza oladigan havolasi.
 *
 * `Uri.fromFile` ishlamaydi: Android 7+ da boshqa ilovaga `file://` uzatish
 * `FileUriExposedException` bilan tugaydi. Shuning uchun `FileProvider` — uning e'loni
 * `uikit` modulining manifestida, ya'ni ilova tomonda sozlash talab qilinmaydi.
 */
internal fun Context.captureUriOrNull(file: File): Uri? =
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
