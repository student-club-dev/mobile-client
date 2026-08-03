package dev.feature.chat.domain.model

/**
 * "Yakka emoji" tekshiruvi — Telegram/WhatsApp qoidasi: faqat emojidan iborat qisqa xabar
 * **katta qilib, pufaksiz** chiziladi.
 *
 * Nega domenda: bu endi **tur aniqlash** emas. Backend 2026-07-29 dan tipli xabarni o'zi
 * qaytaradi, ya'ni qo'lda yozilgan emoji `TEXT` bo'lib ketadi va `STICKER` bo'lib qaytmaydi
 * (stiker uchun `stickerId` kerak, tana esa taqiqlangan — `handoff/03-WEBSOCKET.md`). Katta chizish
 * esa sof KO'RSATISH qarori bo'lib qoldi, shuning uchun u ekran qatlamiga kerak.
 *
 * Chegara tor: kamroq qamrasa xato arzon (emoji shunchaki kichikroq ko'rinadi), ortiqcha
 * qamrasa esa oddiy matn pufaksiz chiqib ketardi.
 */
object EmojiText {

    /** Tana faqat emoji(lar)dan iboratmi — 3 tagacha. */
    fun isLoneEmoji(body: String): Boolean {
        val text = body.trim()
        if (text.isEmpty()) return false
        // Stiker katalogidagi emoji — aniq javob, taxminga hojat yo'q. Bu shart: zaxira
        // stikerlari aynan matn bo'lib yuboriladi, ya'ni pastdagi diapazon tekshiruvi
        // ularning birortasini o'tkazib yuborsa, o'sha stiker qaytib kelganda kichkina
        // matn bo'lib chizilardi. Katalogda esa `#️⃣`, `‼️`, `🅰️` kabi diapazonga
        // tushmaydigan shakllar ham bor.
        if (StickerCatalog.findByEmoji(text) != null) return true
        if (text.length > MAX_EMOJI_CHARS) return false

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
     * Ro'yxat to'liq Unicode jadvali emas — chatda uchraydigan diapazonlar.
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
