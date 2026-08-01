package dev.shared

import coil3.PlatformContext
import okio.Path
import okio.Path.Companion.toOkioPath

/**
 * `filesDir/StudentClub/images` — ilovaning **o'z** papkasi.
 *
 * `cacheDir` emas: Android xotira tugaganda `cacheDir` ni ogohlantirmasdan tozalaydi va
 * bir marta ko'rilgan rasm qaytadan yuklanardi. `filesDir` esa faqat biz (LRU chegarasi
 * orqali) yoki foydalanuvchi «ma'lumotni tozalash» bilan bo'shatadi.
 *
 * Papka **ichki xotirada**: `getExternalFilesDir` SD-karta chiqarib olinganda `null`
 * qaytaradi va kesh butunlay ishlamay qolardi.
 */
internal actual fun imageCacheDirectory(context: PlatformContext): Path =
    context.filesDir.resolve("StudentClub/images").toOkioPath()
