package dev.feature.notifications.data.mapper

import dev.core.database.sql.NotificationEntity
import dev.core.network.generated.model.NotificationDto
import dev.feature.notifications.domain.model.AppNotification
import dev.feature.notifications.domain.model.NotificationTarget
import dev.feature.notifications.domain.model.NotificationType
import kotlinx.datetime.Instant

/** DB qatori → domen. */
internal fun NotificationEntity.toDomain(): AppNotification {
    val kind = parseEnum(type, NotificationType.SYSTEM)
    return AppNotification(
        id = id,
        title = title,
        body = body,
        type = kind,
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        // `targetType` bo'lmasa bildirishnomaning O'Z turi ishlatiladi: server xabar
        // bildirishnomasini ba'zan `target` siz yuboradi va u holda qator bosilganda
        // hech nima bo'lmasdi. Tur esa har doim keladi va u ham xuddi shu ma'noni beradi.
        target = NotificationTarget.of(targetType ?: kind.name, targetId),
        read = read != 0L,
    )
}

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
    // Tanasi bo'sh qator sarlavhaning o'zi bilan chiziladi (§1, `body` nullable).
    body = body.orEmpty(),
    type = parseEnum(type, NotificationType.SYSTEM).name,
    createdAt = createdAt.toEpochMilliseconds(),
    targetType = target?.type?.takeIf { it.isNotBlank() },
    targetId = target?.id?.takeIf { it.isNotBlank() },
    // Server `readAt` yuboradi (`read: bool` EMAS — §1 dagi 4-talab): `null` — o'qilmagan.
    read = if (readAt != null) 1L else 0L,
)

/**
 * Sim/server qiymati → domen enum'i. Noma'lum qiymat [default] ga tushadi.
 *
 * DTO'dagi `type` ataylab `String`: kotlinx noma'lum enum qiymatida BUTUN javobni yiqitadi,
 * ya'ni serverda yangi bildirishnoma turi paydo bo'lishi eski klientlarda ro'yxatni
 * butunlay o'chirib qo'yardi.
 */
private inline fun <reified T : Enum<T>> parseEnum(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
