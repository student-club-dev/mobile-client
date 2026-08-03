package dev.shared

import coil3.PlatformContext
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/**
 * `Library/Caches/StudentClub/images`.
 *
 * iOS'da **`Documents` emas, `Caches`**: `Documents` iCloud'ga zaxiralanadi va Apple qayta
 * yuklab olinadigan fayllarni u yerda saqlashni taqiqlaydi (App Store tekshiruvida rad
 * javob sababi). `Caches` esa faqat qurilma xotirasi tugaganda tozalanadi.
 */
internal actual fun imageCacheDirectory(context: PlatformContext): Path {
    val caches = NSSearchPathForDirectoriesInDomains(
        directory = NSCachesDirectory,
        domainMask = NSUserDomainMask,
        expandTilde = true,
    ).firstOrNull() as? String
    // Papka topilmasa (amalda bo'lmaydi) — nisbiy yo'l ham ishlaydi, kesh sandbox ichida
    // yaraladi; muhimi, kesh butunlay o'chib qolmasin.
    return ((caches ?: "") + "/StudentClub/images").toPath()
}
