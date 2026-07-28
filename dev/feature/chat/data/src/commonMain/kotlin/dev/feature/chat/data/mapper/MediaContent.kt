package dev.feature.chat.data.mapper

import dev.feature.chat.domain.model.MessageType

/**
 * Xabar **tanasidan** turini aniqlaydi.
 *
 * ⚠️ Bu vaqtinchalik evristika. Backend `SendMessageDto.type` va `MessageDto.attachment` ni
 * qo'shguncha media xabar oddiy matn bo'lib yuriydi (`CHAT_MEDIA_AND_CALLS_BACKEND.md` §0):
 *
 * - rasm → tanasi `POST /v1/media/upload` qaytargan **havola**;
 * - stiker → tanasi **yakka emoji**.
 *
 * Ikkalasi ham ataylab shunday tanlangan: yangilanmagan klient rasm o'rniga bosiladigan
 * havolani, stiker o'rniga o'sha emojining o'zini ko'radi — ya'ni suhbat buzilmaydi.
 *
 * Server tipli xabarni qaytara boshlagach [detect] chaqirilmaydi (mapper serverning
 * `type` ini afzal ko'radi), shuning uchun bu fayl o'z-o'zidan ishlamay qoladi va
 * xavfsiz o'chiriladi.
 */
internal object MediaContent {

    /** Havola shu kengaytmalar bilan tugasa — rasm. */
    private val IMAGE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".webp", ".heic")

    /**
     * Tanadan turni aniqlaydi. Aniqlanmasa — [MessageType.TEXT].
     *
     * Server allaqachon `TEXT` dan boshqa tur bergan bo'lsa bu funksiya **chaqirilmasin**.
     */
    fun detect(body: String): MessageType = when {
        imageUrlOrNull(body) != null -> MessageType.IMAGE
        isLoneEmoji(body) -> MessageType.STICKER
        else -> MessageType.TEXT
    }

    /**
     * Tana yakka rasm havolasi bo'lsa — o'sha havola, aks holda `null`.
     *
     * Matn ichidagi havola **hisobga olinmaydi**: "manabu rasm https://…" oddiy matn bo'lib
     * qoladi, chunki foydalanuvchi u yerda izoh yozgan. Faqat butun tana havola bo'lsa
     * rasm sifatida chiziladi.
     */
    fun imageUrlOrNull(body: String): String? {
        val text = body.trim()
        if (!text.startsWith("https://") && !text.startsWith("http://")) return null
        // Bo'shliq bo'lsa — bu havola emas, havolali gap.
        if (text.any { it.isWhitespace() }) return null
        val path = text.substringBefore('?').substringBefore('#').lowercase()
        return if (IMAGE_EXTENSIONS.any { path.endsWith(it) }) text else null
    }

    /**
     * Tana faqat emoji(lar)dan iboratmi — 3 tagacha.
     *
     * Telegram/WhatsApp'dagi qoida: yakka emoji katta qilib, pufaksiz chiziladi. Uch
     * tadan ko'p bo'lsa oddiy matn — aks holda uzun emoji qatori butun ekranni egallardi.
     */
    fun isLoneEmoji(body: String): Boolean {
        val text = body.trim()
        if (text.isEmpty() || text.length > MAX_EMOJI_CHARS) return false

        var index = 0
        var emojiCount = 0
        while (index < text.length) {
            val code = text.codePointAt(index)
            when {
                isEmoji(code) -> emojiCount++
                // Ulagichlar o'zi emoji emas, lekin tarkibida bo'lishi mumkin
                // (👨‍👩‍👧 kabi birikmalar, teri rangi, variatsiya selektori).
                code == ZWJ || code == VARIATION_SELECTOR || isSkinTone(code) -> Unit
                else -> return false
            }
            index += if (code > 0xFFFF) 2 else 1
        }
        return emojiCount in 1..MAX_EMOJI_COUNT
    }

    // --- Kod nuqtalari bilan ishlash -------------------------------------------------------

    /**
     * `String.codePointAt` — JVM'ga xos, `commonMain` da yo'q. Surrogat juftlikni qo'lda
     * yig'amiz: emojilarning aksariyati BMP dan tashqarida (0x1F300+) va UTF-16 da ikki
     * belgidan iborat.
     */
    private fun String.codePointAt(index: Int): Int {
        val high = this[index]
        if (!high.isHighSurrogate() || index + 1 >= length) return high.code
        val low = this[index + 1]
        if (!low.isLowSurrogate()) return high.code
        return 0x10000 + ((high.code - 0xD800) shl 10) + (low.code - 0xDC00)
    }

    /**
     * Kod nuqtasi emojimi.
     *
     * Ro'yxat to'liq Unicode jadvali emas — chatda uchraydigan diapazonlar. Kamroq
     * qamrasa xato arzon (stiker oddiy matn bo'lib, kichikroq ko'rinadi), ortiqcha
     * qamrasa esa **oddiy matn** stiker bo'lib qolardi, shuning uchun chegaralar tor.
     */
    private fun isEmoji(code: Int): Boolean = code in 0x1F300..0x1FAFF || // pictographs, emoticons, transport
        code in 0x1F000..0x1F0FF || // o'yin toshlari
        code in 0x2600..0x27BF || // turli belgilar va dingbatlar
        code in 0x2B00..0x2BFF || // qo'shimcha strelka/yulduzlar
        code in 0x1F1E6..0x1F1FF || // bayroqlar (mintaqa indikatorlari)
        // Soat, qum soati, media boshqaruvi va budilnik (⏰ — U+23F0).
        code in 0x231A..0x23FF ||
        code in 0x25A0..0x25FF || // kichik kvadrat/doiralar
        code in 0x2934..0x2935 || // egilgan strelkalar
        code == 0x24C2 || code == 0x3030 || code == 0x303D ||
        code == 0x3297 || code == 0x3299 ||
        code == 0x203C || code == 0x2049 || code in 0x2122..0x2139

    private fun isSkinTone(code: Int): Boolean = code in 0x1F3FB..0x1F3FF

    private const val ZWJ = 0x200D
    private const val VARIATION_SELECTOR = 0xFE0F

    /** Uchta emoji + ulagichlar/modifikatorlar uchun zaxira bilan. */
    private const val MAX_EMOJI_CHARS = 24
    private const val MAX_EMOJI_COUNT = 3
}
