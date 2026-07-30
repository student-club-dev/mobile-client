package dev.feature.chat.domain.model

/**
 * **Fluent Emoji 3D** — ilovaning zaxira stiker katalogi.
 *
 * Nega bu bor: server katalogi (`GET /v1/stickers/packs`) hali bo'sh, va oldingi zaxira
 * atigi 96 ta **tizim emojisi** edi — ular stikerga o'xshamasdi, har bir qurilmada boshqacha
 * chizilardi. Fluent Emoji esa hajmli (3D), shaffof fonli tasvirlar: ko'rinishi stiker,
 * litsenziyasi MIT, ya'ni Telegram stikerlaridagi huquqiy xavf yo'q
 * (`CHAT_MEDIA_AND_CALLS_BACKEND.md` §4.4).
 *
 * Rasmlar **ilovaga kiritilmagan** — 1585 tasi APK'ni o'nlab megabaytga shishirardi. Ular
 * CDN'dan yuklanadi va Coil ularni keshlaydi. Rasm kelmasa (oflayn, CDN bloklangan)
 * chizuvchi joylar tizim emojisiga qaytadi — ya'ni eng yomon holat **hozirgi** ko'rinish.
 *
 * Yuborishga ta'sir qilmaydi: bu stikerlar server katalogida yo'q, shuning uchun ular
 * [Sticker.isRemote] `false` bilan qoladi va xabar tanasi sifatida **emojining o'zi**
 * ketadi. Shu sababli ularni eski klientlar ham, boshqa ilovalar ham to'g'ri ko'radi.
 */
object FluentEmoji {

    /**
     * [emoji] uchun 3D tasvir URL'i, Fluent'da bunday emoji bo'lmasa `null`.
     *
     * Variatsiya selektori (`U+FE0F`) bilan ham, usiz ham topadi: `emoji-test.txt` to'liq
     * shaklni beradi (`❤️`), klaviaturadan esa qisqasi (`❤`) kelishi mumkin.
     */
    fun urlFor(emoji: String): String? {
        val key = emoji.trim()
        val entry = index[key]
            ?: index[key.filterNot { it == VARIATION_SELECTOR }]
            ?: index[key + VARIATION_SELECTOR]
            ?: return null

        val variant = entry.startsWith(VARIANT_MARKER)
        val folder = if (variant) entry.substring(1) else entry
        val file = folder.lowercase().replace(' ', '_')
        // Teri rangi variantlari bor emojilarda yo'lda qo'shimcha bo'g'in bo'ladi; katalogda
        // neytral ("Default") shakli ishlatiladi.
        return if (variant) {
            "${FluentEmojiAssets.CDN}/${folder.urlEncoded()}/Default/3D/${file.urlEncoded()}_3d_default.png"
        } else {
            "${FluentEmojiAssets.CDN}/${folder.urlEncoded()}/3D/${file.urlEncoded()}_3d.png"
        }
    }

    /** Katalogda shu emoji bormi. */
    fun contains(emoji: String): Boolean = urlFor(emoji) != null

    /** Unicode guruhlari bo'yicha paketlar — panel bo'limlari shu tartibda chiziladi. */
    val packs: List<StickerPack> by lazy {
        GROUPS.map { group ->
            val stickers = group.raw.lineSequence()
                .mapNotNull { line ->
                    val tab = line.indexOf('\t')
                    if (tab <= 0) return@mapNotNull null
                    val emoji = line.substring(0, tab)
                    Sticker(
                        id = "fluent:$emoji",
                        emoji = emoji,
                        url = urlFor(emoji),
                        packId = group.id,
                    )
                }
                .toList()
            StickerPack(
                id = group.id,
                name = group.title,
                cover = group.cover,
                stickers = stickers,
                coverUrl = urlFor(group.cover),
            )
        }
    }

    /** emoji → `"<papka>"` yoki `"*<papka>"` (yulduzcha — teri rangi variantlari bor). */
    private val index: Map<String, String> by lazy {
        buildMap {
            GROUPS.forEach { group ->
                group.raw.lineSequence().forEach { line ->
                    val tab = line.indexOf('\t')
                    if (tab > 0) put(line.substring(0, tab), line.substring(tab + 1))
                }
            }
        }
    }

    /**
     * URL yo'lidagi bo'g'inni kodlaydi.
     *
     * Papka nomlarida bo'sh joy, `!` va hatto `ñ` uchraydi (`Piñata`) — ular fayl nomiga ham
     * o'zgarishsiz o'tadi, ya'ni kodlash **majburiy**. `ktor-utils` ni domen moduliga tortib
     * kelmaslik uchun shu yerda: qoida oddiy va o'zgarmaydi.
     */
    private fun String.urlEncoded(): String = buildString {
        this@urlEncoded.encodeToByteArray().forEach { byte ->
            val code = byte.toInt() and 0xFF
            val char = code.toChar()
            if (char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char in UNRESERVED) {
                append(char)
            } else {
                append('%').append(HEX[code shr 4]).append(HEX[code and 0x0F])
            }
        }
    }

    private class Group(
        val id: String,
        val title: String,
        val cover: String,
        val raw: String,
    )

    private val GROUPS = listOf(
        Group("fluent_smileys", "Kayfiyat", "😄", FluentEmojiAssets.SMILEYS),
        Group("fluent_people", "Odamlar", "🙌", FluentEmojiAssets.PEOPLE),
        Group("fluent_nature", "Tabiat", "🐱", FluentEmojiAssets.NATURE),
        Group("fluent_food", "Ovqat", "🍕", FluentEmojiAssets.FOOD),
        Group("fluent_activities", "Faoliyat", "⚽", FluentEmojiAssets.ACTIVITIES),
        Group("fluent_travel", "Sayohat", "✈️", FluentEmojiAssets.TRAVEL),
        Group("fluent_objects", "Buyumlar", "💡", FluentEmojiAssets.OBJECTS),
        Group("fluent_symbols", "Belgilar", "❤️", FluentEmojiAssets.SYMBOLS),
    )

    /** `U+FE0F` — ko'rinmas belgi, shuning uchun ataylab kod bilan yozilgan. */
    private const val VARIATION_SELECTOR = '\uFE0F'
    private const val VARIANT_MARKER = "*"
    private const val UNRESERVED = "-_.~"
    private const val HEX = "0123456789ABCDEF"
}
