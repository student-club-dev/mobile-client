package dev.feature.chat.data.mapper

import dev.feature.chat.domain.model.EmojiText
import dev.feature.chat.domain.model.MessageType

/**
 * Xabar **tanasidan** turini aniqlaydi — **faqat eski kesh uchun zaxira**.
 *
 * 2026-07-29 gacha backend tipli xabarni bilmasdi va media shunday uzatilardi:
 * rasm — tanasi `POST /v1/media/upload` qaytargan **havola**, stiker — tanasi **yakka
 * emoji**. Endi server `type` va `attachment` ni o'zi qaytaradi (`handoff/chat.md`), ya'ni
 * yangi xabarlar hech qachon bu yerdan o'tmaydi.
 *
 * Nima uchun fayl saqlab qolindi: qurilmadagi **eski kesh qatorlari** hali ham o'sha eski
 * shaklda yotibdi. Ular uchun suhbatlar ro'yxatida `lastMessageType` ustuni bo'sh
 * (22.sqm dan oldin yozilgan), shuning uchun tur tanadan taxmin qilinadi — busiz ro'yxatda
 * rasm o'rniga uzun havola ko'rinardi. Kesh yangilanib bo'lgach bu fayl o'zi ishlamay qoladi.
 */
internal object MediaContent {

    /** Havola shu kengaytmalar bilan tugasa — rasm. */
    private val IMAGE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".webp", ".heic")

    /**
     * Tanadan turni aniqlaydi. Aniqlanmasa — [MessageType.TEXT].
     *
     * Server bergan tur bor bo'lsa bu funksiya **chaqirilmasin** — unga ishoniladi.
     */
    fun detect(body: String): MessageType = when {
        imageUrlOrNull(body) != null -> MessageType.IMAGE
        EmojiText.isLoneEmoji(body) -> MessageType.STICKER
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
}
