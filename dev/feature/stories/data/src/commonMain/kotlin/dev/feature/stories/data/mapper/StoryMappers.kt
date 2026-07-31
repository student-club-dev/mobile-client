package dev.feature.stories.data.mapper

import dev.core.network.generated.model.StoryDto
import dev.core.network.generated.model.StoryGroupDto
import dev.core.network.generated.model.StoryKindDto
import dev.core.network.generated.model.StoryViewerPageDto
import dev.core.network.media.MediaUrl
import dev.feature.connections.data.mapper.toDomain as summaryToDomain
import dev.feature.stories.domain.model.Story
import dev.feature.stories.domain.model.StoryGroup
import dev.feature.stories.domain.model.StoryKind
import dev.feature.stories.domain.model.StoryViewerPage

// Generatsiya qilingan DTO'lar → domen. Bitta yo'nalish: domen backendga bog'lanmaydi.

/**
 * [origin] — API manzilining origin qismi (`https://api.studentclub.uz`).
 *
 * ⚠️ Havolalar shu yerda **to'liq** holga keltiriladi: backend ularni nisbiy
 * (`/v1/media/{id}/raw`) qaytaradi, video pleyer esa nisbiy yo'lni **local fayl** deb
 * o'qiydi va `FileNotFoundException` bilan yiqiladi — ekranda hech qanday xabarsiz qora
 * ekran qolardi. Rasmda bu sezilmasdi: Coil havolani o'zi tuzatadi.
 */
fun StoryDto.toDomain(origin: String): Story = Story(
    id = id,
    authorId = authorId,
    kind = kind.toDomain(),
    url = MediaUrl.normalize(url, origin) ?: url,
    thumbUrl = thumbUrl?.takeIf { it.isNotBlank() }?.let { MediaUrl.normalize(it, origin) },
    width = width ?: 0,
    height = height ?: 0,
    durationMs = durationMs,
    caption = caption?.takeIf { it.isNotBlank() },
    createdAt = createdAt,
    expiresAt = expiresAt,
    seen = seen,
    // ⚠️ Boshqa odamning story'sida server buni ATAYLAB `null` qiladi — ko'rishlar soni
    // orqali tanishlar doirasini o'lchab bo'lmasin (`handoff/07-STORIES.md` §3).
    viewsCount = viewsCount,
)

fun StoryKindDto.toDomain(): StoryKind = when (this) {
    StoryKindDto.IMAGE -> StoryKind.IMAGE
    StoryKindDto.VIDEO -> StoryKind.VIDEO
}

/**
 * ⚠️ Tartib **saqlanadi**: server avval `hasUnseen` bo'lganlarni, ular ichida
 * `lastCreatedAt` bo'yicha yangidan eskiga beradi (`handoff/07-STORIES.md` §2). Bu yerda
 * hech narsa qayta saralanmaydi.
 */
fun StoryGroupDto.toDomain(origin: String): StoryGroup = StoryGroup(
    author = author.summaryToDomain(),
    stories = stories.map { it.toDomain(origin) },
    hasUnseen = hasUnseen,
    lastCreatedAt = lastCreatedAt,
)

fun StoryViewerPageDto.toDomain(): StoryViewerPage = StoryViewerPage(
    items = items.map { it.summaryToDomain() },
    page = page,
    size = propertySize,
    total = total,
    hasNext = hasNext,
)
