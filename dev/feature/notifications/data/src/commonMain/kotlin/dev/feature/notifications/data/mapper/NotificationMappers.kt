package dev.feature.notifications.data.mapper

import dev.core.database.sql.NotificationEntity
import dev.feature.notifications.data.dto.NotificationDto
import dev.feature.notifications.domain.model.AppNotification
import dev.feature.notifications.domain.model.NotificationTarget
import dev.feature.notifications.domain.model.NotificationType
import kotlinx.datetime.Instant

/** DB qatori → domen. */
internal fun NotificationEntity.toDomain(): AppNotification = AppNotification(
    id = id,
    title = title,
    body = body,
    type = parseEnum(type, NotificationType.SYSTEM),
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    target = targetOf(targetType, targetId),
    read = read != 0L,
)

/**
 * Server javobi → DB qatori.
 *
 * Oraliq turi ataylab SQLDelight'ning O'Z [NotificationEntity] si: `upsert` sakkizta
 * pozitsion argument kutadi va ularni chaqiruv joyida qo'lda terish — ikki maydon o'rni
 * almashganda kompilyator jim qolishi demakdi (hammasi `String`).
 */
internal fun NotificationDto.toEntity(): NotificationEntity = NotificationEntity(
    id = id,
    title = title,
    body = body,
    type = parseEnum(type, NotificationType.SYSTEM).name,
    createdAt = parseInstant(createdAt),
    targetType = target?.type?.takeIf { it.isNotBlank() },
    targetId = target?.id?.takeIf { it.isNotBlank() },
    // Server ikki shaklning birini yuborishi mumkin: `read` (bool) yoki `readAt` (vaqt).
    // Ikkalasi ham yo'q bo'lsa — o'qilmagan (yangi bildirishnoma odatiy holat).
    read = if (read ?: (readAt != null)) 1L else 0L,
)

/**
 * `targetType` + `targetId` → domen [NotificationTarget].
 *
 * Noma'lum tur ham, id kutgani holda id'siz kelgan tur ham [NotificationTarget.None] ga
 * tushadi: bosilganda hech qayerga o'tmaydi, faqat o'qilgan bo'ladi. Bu ataylab —
 * "id'siz suhbat" ni ochishga urinish bo'sh ekranga olib borardi.
 */
internal fun targetOf(type: String?, id: String?): NotificationTarget = when (type) {
    "CHAT" -> id?.let { NotificationTarget.Chat(it) } ?: NotificationTarget.None
    "LISTING" -> id?.let { NotificationTarget.Listing(it) } ?: NotificationTarget.None
    "CONNECTION_REQUESTS" -> NotificationTarget.ConnectionRequests
    "MY_LISTINGS" -> NotificationTarget.MyListings
    "PROFILE" -> NotificationTarget.Profile
    else -> NotificationTarget.None
}

/**
 * ISO-8601 → epoch ms. Parse xatosi butun ro'yxatni yiqitmasin — zaxira `0` (bildirishnoma
 * ko'rinadi, faqat eng oxirida va vaqtsiz).
 */
private fun parseInstant(value: String?): Long =
    value?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() } ?: 0L

/**
 * Sim/server qiymati → domen enum'i. Noma'lum qiymat [default] ga tushadi.
 *
 * DTO'dagi `type` ataylab `String`: kotlinx noma'lum enum qiymatida BUTUN javobni yiqitadi,
 * ya'ni serverda yangi bildirishnoma turi paydo bo'lishi eski klientlarda ro'yxatni
 * butunlay o'chirib qo'yardi.
 */
private inline fun <reified T : Enum<T>> parseEnum(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
