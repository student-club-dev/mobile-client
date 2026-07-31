package dev.feature.stories.domain.model

import dev.feature.connections.domain.model.StudentSummary
import kotlinx.datetime.Instant

/** Lavha turi. Video hozircha faqat **kiruvchi** tomonda (klientda video tanlagich yo'q). */
enum class StoryKind { IMAGE, VIDEO }

/**
 * Bitta lavha (`handoff/07-STORIES.md` §3).
 *
 * ⚠️ [url] va [thumbUrl] **token bilan** so'raladi (`Authorization: Bearer`) — oddiy
 * `Image.load` ishlamaydi. Chat biriktirmalaridan farqli, story medialarini faqat muallif
 * va unga **bog'langan** odam o'qiy oladi, ya'ni havolani begonaga yuborish foydasiz.
 *
 * [viewsCount] **faqat o'z story'ingizda** son bo'ladi; boshqalarnikida doim `null` —
 * ko'rishlar soni orqali odamning tanishlar doirasi o'lchanib qolmasligi uchun.
 */
data class Story(
    val id: String,
    val authorId: String,
    val kind: StoryKind,
    val url: String,
    val thumbUrl: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    /** Video davomiyligi. Rasmda `null` — o'shanda [DEFAULT_IMAGE_MS] ishlatiladi. */
    val durationMs: Int? = null,
    val caption: String? = null,
    val createdAt: Instant,
    val expiresAt: Instant,
    /** **Siz** ko'rganmisiz. O'z story'ingizda doim `true`. */
    val seen: Boolean = false,
    val viewsCount: Int? = null,
) {
    /** Ekranda necha millisekund turishi. */
    val displayMs: Int get() = durationMs?.takeIf { it > 0 } ?: DEFAULT_IMAGE_MS

    companion object {
        /** Rasm uchun standart davomiylik — hujjat aynan shuni tavsiya qiladi (§3). */
        const val DEFAULT_IMAGE_MS = 5_000

        /** Izoh chegarasi (§8). */
        const val MAX_CAPTION = 200
    }
}

/**
 * Muallif bo'yicha guruh — lenta aynan shu ro'yxatdan chiziladi.
 *
 * ⚠️ **Qayta saralamang** (§2): server avval [hasUnseen] bo'lganlarni, ular ichida
 * [lastCreatedAt] bo'yicha yangidan eskiga beradi. Guruh ichidagi [stories] esa
 * **eskidan yangiga** — ko'rish tartibi.
 */
data class StoryGroup(
    val author: StudentSummary,
    val stories: List<Story>,
    /** Avatar atrofidagi halqa aynan shunga qarab yonadi. */
    val hasUnseen: Boolean,
    val lastCreatedAt: Instant,
) {
    /** Ochilganda qaysi lavhadan boshlash — birinchi ko'rilmagani, bo'lmasa boshidan. */
    val startIndex: Int get() = stories.indexOfFirst { !it.seen }.takeIf { it >= 0 } ?: 0
}

/** `GET /v1/stories/{id}/views` sahifasi — faqat muallifga. */
data class StoryViewerPage(
    val items: List<StudentSummary>,
    val page: Int,
    val size: Int,
    val total: Int,
    val hasNext: Boolean,
)

/**
 * Story cheklovlari (§8) — klientda ham ko'rsatiladi, server ham tekshiradi.
 *
 * Kunlik hisob **o'chirilganlarni ham** sanaydi, ya'ni "qo'y-o'chir" bilan chetlab o'tib
 * bo'lmaydi.
 */
object StoryLimits {
    /** Bir vaqtda faol. */
    const val MAX_ACTIVE = 10

    /** Kuniga (o'chirilganlar ham hisobda). */
    const val MAX_PER_DAY = 20

    /** Rasm hajmi — `kind=STORY_IMAGE`. */
    const val MAX_IMAGE_BYTES = 12 * 1024 * 1024

    /** Video hajmi — `kind=STORY_VIDEO`, ustiga **≤ 30 s**. */
    const val MAX_VIDEO_BYTES = 48 * 1024 * 1024
}
