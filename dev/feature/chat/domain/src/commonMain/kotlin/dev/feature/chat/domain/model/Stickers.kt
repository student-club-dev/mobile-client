package dev.feature.chat.domain.model

/**
 * Stiker.
 *
 * [url] `null` bo'lsa — stiker **emoji sifatida** chiziladi (katta o'lchamda, pufaksiz).
 * Backend stiker paketlarini ochganda ([url] to'lganda) o'sha rasm ko'rsatiladi va
 * qolgan kod o'zgarmaydi.
 */
data class Sticker(
    val id: String,
    val emoji: String,
    val url: String? = null,
)

/** Bitta paket — stiker panelidagi bo'lim. */
data class StickerPack(
    val id: String,
    val name: String,
    /** Panel yorlig'ida ko'rinadigan belgi. */
    val cover: String,
    val stickers: List<Sticker>,
)

/**
 * **Ilovaga kiritilgan** stiker katalogi.
 *
 * Nega backenddan emas: `GET /v1/stickers/packs` hali yo'q (`CHAT_MEDIA_AND_CALLS_BACKEND.md`
 * §4.2). Nega emoji: tayyor stiker rasmlari yo'q, Telegram'nikini olish esa mualliflik
 * huquqini buzadi va ilovani do'kondan uchirishi mumkin (§4.4). Emoji hech qanday resurs
 * talab qilmaydi, oflayn ishlaydi va **eski klientlarda ham to'g'ri ko'rinadi** — chunki
 * xabar tanasi sifatida aynan emojining o'zi yuboriladi.
 *
 * Backend paketlarni ochganda bu obyekt zaxira ro'yxatga aylanadi, `Sticker.url` esa
 * serverdan keladi.
 */
object StickerCatalog {

    val packs: List<StickerPack> = listOf(
        StickerPack(
            id = "reactions",
            name = "Kayfiyat",
            cover = "😄",
            stickers = stickersOf(
                "😀", "😄", "😁", "😂", "🥹", "😊", "😇", "🙂",
                "😉", "😍", "🥰", "😘", "😜", "🤪", "🤗", "🤭",
                "🤔", "🫡", "🤨", "😐", "😴", "😪", "😮‍💨", "🥱",
                "😎", "🥳", "😭", "😱", "😤", "🥺", "😢", "😅",
            ),
        ),
        StickerPack(
            id = "student",
            name = "Talaba",
            cover = "🎓",
            stickers = stickersOf(
                "🎓", "📚", "📖", "✏️", "📝", "🖊️", "📐", "📎",
                "💻", "🖥️", "🔬", "🧪", "🧮", "📊", "🗂️", "📅",
                "☕", "🍕", "🍔", "🥤", "🍎", "🥪", "🌙", "⏰",
                "🚌", "🏫", "🎒", "🛏️", "💡", "🔥", "💯", "🏆",
            ),
        ),
        StickerPack(
            id = "gestures",
            name = "Ishoralar",
            cover = "👍",
            stickers = stickersOf(
                "👍", "👎", "👌", "🤝", "🙏", "👏", "🙌", "✌️",
                "🤞", "🫶", "❤️", "🧡", "💛", "💚", "💙", "💜",
                "🖤", "🤍", "💔", "✨", "⭐", "🎉", "🎊", "🎁",
                "⚽", "🏀", "🎮", "🎧", "🎵", "🌹", "🌸", "☀️",
            ),
        ),
    )

    /** Panelda ko'rsatish uchun barcha stikerlar (qidiruvsiz — ro'yxat kichik). */
    val all: List<Sticker> = packs.flatMap { it.stickers }

    /** Yuborilgan matn shu katalogdagi stikermi — kiruvchi xabarni tanib olish uchun. */
    fun findByEmoji(emoji: String): Sticker? = all.firstOrNull { it.emoji == emoji }

    private fun stickersOf(vararg emojis: String): List<Sticker> =
        emojis.map { Sticker(id = it, emoji = it) }
}
