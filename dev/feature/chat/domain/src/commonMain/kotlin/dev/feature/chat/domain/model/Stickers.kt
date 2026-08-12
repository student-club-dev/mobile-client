package dev.feature.chat.domain.model

import dev.core.common.error.AppException

/**
 * Stiker.
 *
 * [url] — chizish uchun tasvir: server katalogidan yoki [FluentEmoji] CDN'idan. `null`
 * bo'lsa (yoki rasm yuklanmasa) stiker **emoji sifatida** chiziladi — katta o'lchamda,
 * pufaksiz. Ikkala holatda ham qolgan kod bir xil ishlaydi.
 *
 * [isRemote] — **serverdagi** katalogdan olinganmi. Buni [url] dan chiqarib bo'lmaydi:
 * Fluent stikerining ham tasviri bor, lekin server uni bilmaydi, ya'ni `stickerId` bilan
 * yuborilsa `422 STICKER_NOT_FOUND` bo'lardi. Shuning uchun bu — alohida maydon.
 *
 * [id] — serverdagi stiker id'si (`stickerId` bo'lib `message:send` ga ketadi). Zaxira
 * katalogda id `"fluent:<emoji>"` ko'rinishida bo'ladi va **hech qachon** serverga ketmaydi.
 */
data class Sticker(
    val id: String,
    val emoji: String,
    val url: String? = null,
    /** Qaysi paketdan — server katalogida to'ladi, zaxira katalogda paket id'si. */
    val packId: String? = null,
    val isRemote: Boolean = false,
)

/** Bitta paket — stiker panelidagi bo'lim. */
data class StickerPack(
    val id: String,
    val name: String,
    /** Panel yorlig'ida ko'rinadigan belgi (emoji) — [coverUrl] bo'lmaganda ishlatiladi. */
    val cover: String,
    val stickers: List<Sticker>,
    /** Muqova rasmi; server katalogidan yoki Fluent CDN'idan. */
    val coverUrl: String? = null,
)

/**
 * Katalogning bir marta o'qilgan holati.
 *
 * [fromServer] `false` bo'lsa — pastdagi zaxira ([StickerCatalog]) ko'rsatilyapti. Panel buni
 * bilishi kerak: zaxira stikerlari **emoji matni** sifatida yuboriladi, serverdagilar esa
 * `stickerId` bilan.
 *
 * [version] — serverdagi katalog versiyasi (`ETag` bilan bir xil qiymat), keshni yangilash uchun.
 * [error] — server bilan gaplashishda xato bo'lgan bo'lsa (panel baribir ishlaydi, faqat log uchun).
 */
data class StickerCatalogState(
    val packs: List<StickerPack>,
    val fromServer: Boolean,
    val version: Int? = null,
    val error: AppException? = null,
)

/**
 * **Ilovaga kiritilgan zaxira** stiker katalogi — 1600 dan ortiq stiker.
 *
 * Asosiy manba — `GET /v1/stickers/packs` (butun katalog bitta javobda, `ETag` bilan). Ammo
 * backendda hozircha **stiker tasvirlarining o'zi yo'q** (sxema, endpoint va seed skripti
 * tayyor — `PENDING_ACTIONS.md` §6). Server bo'sh ro'yxat qaytarsa yoki umuman javob
 * bermasa panel shu katalogga qaytadi.
 *
 * Tasvirlar — **Microsoft Fluent Emoji, MIT** ([FluentEmoji]): hajmli, shaffof fonli va
 * tarqatishga ruxsat etilgan. Telegram stikerlarini olib ishlatish mumkin emas — mualliflik
 * huquqi buziladi va ilova do'kondan olib tashlanishi mumkin
 * (`CHAT_MEDIA_AND_CALLS_BACKEND.md` §4.4).
 *
 * Bu stikerlar xabar tanasi sifatida **emojining o'zini** yuboradi, ya'ni ular eski
 * klientlarda ham, boshqa ilovalarda ham to'g'ri ko'rinadi.
 */
object StickerCatalog {

    /**
     * Talaba mavzusidagi tanlangan paket — panelda **birinchi** turadi.
     *
     * Nega qo'lda: qolgan paketlar Unicode guruhlari bo'yicha avtomatik yig'iladi va u yerda
     * "imtihon, kutubxona, kofe, deadline" degan bo'lim yo'q. Ilova talabalar uchun, shuning
     * uchun eng kerakli stikerlar birinchi ekranda turishi kerak.
     */
    private val STUDENT_EMOJIS = listOf(
        "🎓", "📚", "📖", "✏️", "📝", "🖊️", "📐", "📎",
        "💻", "🖥️", "🔬", "🧪", "🧮", "📊", "🗂️", "📅",
        "☕", "🍕", "🍔", "🥤", "🍎", "🥪", "🌙", "⏰",
        "🚌", "🏫", "🎒", "🛏️", "💡", "🔥", "💯", "🏆",
        "😴", "🥱", "😭", "🥳", "🤓", "🫡", "👍", "🙏",
    )

    val packs: List<StickerPack> by lazy {
        val student = StickerPack(
            id = "student",
            name = ChatDomainStrings.student,
            cover = "🎓",
            coverUrl = FluentEmoji.urlFor("🎓"),
            stickers = STUDENT_EMOJIS.map { emoji ->
                Sticker(
                    id = "fluent:$emoji",
                    emoji = emoji,
                    url = FluentEmoji.urlFor(emoji),
                    packId = "student",
                )
            },
        )
        listOf(student) + FluentEmoji.packs
    }

    /** Panelda ko'rsatish uchun barcha stikerlar. */
    val all: List<Sticker> by lazy { packs.flatMap { it.stickers } }

    /**
     * Yuborilgan matn shu katalogdagi stikermi — kiruvchi xabarni tanib olish uchun.
     *
     * Indeks bo'yicha: katalogda 1600 dan ortiq stiker bor, ro'yxat bo'ylab yurish har bir
     * xabar uchun takrorlanardi.
     */
    fun findByEmoji(emoji: String): Sticker? = byEmoji[emoji.trim()]

    private val byEmoji: Map<String, Sticker> by lazy {
        // Bir xil emoji ChatDomainStrings.student paketida ham, guruh paketida ham bor — birinchisi qoladi
        // (`associateBy` teskarisini qilardi: oxirgisi g'olib chiqardi).
        buildMap {
            all.forEach { sticker -> if (!containsKey(sticker.emoji)) put(sticker.emoji, sticker) }
        }
    }
}
