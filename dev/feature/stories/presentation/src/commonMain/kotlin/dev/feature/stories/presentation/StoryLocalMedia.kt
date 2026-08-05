package dev.feature.stories.presentation

import dev.core.uikit.media.storyMediaFileName
import dev.core.uikit.media.studentClubMediaUrls
import dev.feature.stories.domain.model.Story
import dev.feature.stories.domain.model.StoryKind

/**
 * Telefondagi «StudentClub» papkasi — `fayl nomi → havola`.
 *
 * Papka **bitta marta** o'qiladi va butun ro'yxatga yetadi: har bir hikoya uchun alohida
 * qidirish 30 ta MediaStore so'rovi degani bo'lardi. Papkani o'qib bo'lmasa (ruxsat,
 * SD-karta) bo'sh xarita qaytadi va hammasi eskicha — tarmoqdan — ishlaydi.
 */
internal suspend fun localMediaUrls(): Map<String, String> =
    runCatching { studentClubMediaUrls() }.getOrDefault(emptyMap())

/**
 * Hikoyaga telefondagi nusxasini bog'laydi (`story_<id>.mp4|jpg`).
 *
 * Nusxa bo'lmasa (boshqa telefondan qo'yilgan, foydalanuvchi o'chirgan, ilova qayta
 * o'rnatilgan) hikoya o'zgarishsiz qaytadi va media serverdan o'qiladi.
 */
internal fun Story.withLocalMedia(local: Map<String, String>): Story {
    if (local.isEmpty()) return this
    val uri = local[storyMediaFileName(id, isVideo = kind == StoryKind.VIDEO)] ?: return this
    return copy(localUri = uri)
}
