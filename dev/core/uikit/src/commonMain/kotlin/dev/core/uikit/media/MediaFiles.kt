package dev.core.uikit.media

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * Yuborilishi kutilayotgan media fayllari bilan ishlash.
 *
 * Video ([PickedVideo]) baytlar emas, **fayl** bo'lib yuradi: 64 MB gacha bo'lgan faylni
 * xotiraga o'qish arzon telefonda ilovani quladi. Fayl ilova keshida turadi va yuborish
 * tugagach (yoki foydalanuvchi bekor qilganda) o'chiriladi — aks holda kesh tanlangan har
 * bir video bilan to'lib borardi.
 *
 * Faylni **yaratish** platformaga qoldirilgan (Android `cacheDir`, iOS `NSTemporaryDirectory`),
 * chunki tanlagichlar baribir o'sha platformaning API'sida ishlaydi. Bu yerda faqat ikkala
 * tomonga bir xil kerak bo'ladigan ish bor.
 */

/**
 * Faylni o'chiradi. Fayl allaqachon yo'q bo'lsa **jim qaytadi**: uni o'chirish yo'llari
 * bir nechta (yuborish tugadi, foydalanuvchi bekor qildi, tizim keshni tozaladi) va
 * ularning to'qnashuvi xato emas.
 */
fun deleteMediaFile(path: String) {
    runCatching { SystemFileSystem.delete(Path(path), mustExist = false) }
}

/** Fayl hajmi; fayl yo'q bo'lsa `null`. */
internal fun mediaFileSize(path: String): Long? =
    runCatching { SystemFileSystem.metadataOrNull(Path(path))?.size }.getOrNull()

/**
 * Keshdagi fayl yo'lini [ScVideoPlayer] tushunadigan havolaga o'giradi.
 *
 * `file://` sxemasi **shart**: iOS'da pleyer havolani `NSURL(string:)` bilan o'qiydi va
 * sxemasiz yo'l nisbiy URL bo'lib qoladi — video umuman ochilmasdi. Android'da esa
 * sxemasiz yo'l ishlardi, lekin ikkala tomonda bir xil bo'lgani tushunarliroq.
 */
fun localFileUrl(path: String): String = "file://$path"
