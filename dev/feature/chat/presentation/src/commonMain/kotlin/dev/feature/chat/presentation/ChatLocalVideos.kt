package dev.feature.chat.presentation

import dev.core.uikit.media.chatVideoFileName
import dev.core.uikit.media.studentClubMediaUrls
import dev.feature.chat.domain.model.Attachment
import dev.feature.chat.domain.model.MediaKind

/**
 * Telefondagi «StudentClub/Video» papkasi — `fayl nomi → havola`.
 *
 * Papka **bitta marta** o'qiladi va butun suhbatga yetadi: har bir video uchun alohida
 * qidirish yuzlab MediaStore so'rovi degani bo'lardi. Papkani o'qib bo'lmasa (SD-karta
 * chiqarilgan, ruxsat yo'q) bo'sh xarita qaytadi va hammasi eskicha — tarmoqdan — ishlaydi.
 */
internal suspend fun localVideoUrls(): Map<String, String> =
    runCatching { studentClubMediaUrls() }.getOrDefault(emptyMap())

/**
 * Biriktirmaning telefondagi nusxasi (`chat_<mediaId>.mp4`), yoki `null` — hali yuklab
 * olinmagan.
 *
 * `null` **xato emas**: video serverda bor, ekran uni tarmoqdan o'qiydi va birinchi
 * ochilishda papkaga tushadi.
 */
internal fun Attachment.localVideoUrl(local: Map<String, String>): String? {
    if (local.isEmpty()) return null
    if (kind != MediaKind.VIDEO && kind != MediaKind.VIDEO_NOTE) return null
    val mediaId = id ?: return null
    return local[chatVideoFileName(mediaId)]
}
