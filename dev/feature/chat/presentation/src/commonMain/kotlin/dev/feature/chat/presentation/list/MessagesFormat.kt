package dev.feature.chat.presentation.list

import dev.feature.chat.domain.model.ConversationItem
import dev.feature.chat.domain.model.Message
import dev.feature.chat.domain.model.MessageType
import dev.feature.chat.presentation.callPreview
import dev.feature.clubs.domain.model.Club

/**
 * Ro'yxatdagi qisqa ko'rinish.
 *
 * Media xabarda tana **bo'sh** (server `body` ni faqat matn va izoh uchun to'ldiradi),
 * shuning uchun ko'rinish turdan quriladi. Turi keshda saqlanadi (`lastMessageType`).
 */
internal fun Message?.preview(): String = when {
    this == null -> "Xabar yozing…"
    deleted -> "Xabar o'chirildi"
    type == MessageType.IMAGE -> "📷 Rasm"
    type == MessageType.GIF -> "GIF"
    type == MessageType.VIDEO -> "🎬 Video"
    type == MessageType.VIDEO_NOTE -> "⭕️ Video xabar"
    type == MessageType.VOICE -> "🎤 Ovozli xabar"
    type == MessageType.FILE -> "📎 Fayl"
    type == MessageType.STICKER -> "${sticker?.emoji.orEmpty()} Stiker".trim()
    // Qo'ng'iroq — push matni bilan **bir xil** shakl (`handoff/09-CALLS-REST.md` §4).
    // `call` keshdan kelmasa (suhbatlar ro'yxatining qisqa qatorida u yo'q) turdan
    // umumiy matn quriladi.
    type == MessageType.CALL -> call?.let { "📞 ${callPreview(it)}" } ?: "📞 Qo'ng'iroq"
    body.isBlank() -> "Xabar yozing…"
    else -> body
}

/**
 * Ro'yxat qidiruvi: ism ham, oxirgi xabar matni ham qaraladi — Telegramdagidek "kim
 * bilan" va "nima haqida" degan ikkala savolga javob beradi.
 *
 * Bo'sh so'rovda ro'yxat O'ZI qaytadi (nusxa olinmaydi), ya'ni odatiy holatda qidiruv
 * hech narsa hisoblamaydi.
 */
internal fun List<ConversationItem>.matchingConversations(query: String): List<ConversationItem> {
    val needle = query.trim()
    if (needle.isBlank()) return this
    return filter { c ->
        c.other.displayName.contains(needle, ignoreCase = true) ||
            c.lastMessage?.body.orEmpty().contains(needle, ignoreCase = true)
    }
}

/** Klublar qidiruvi — nomi va tavsifi bo'yicha. */
internal fun List<Club>.matchingClubs(query: String): List<Club> {
    val needle = query.trim()
    if (needle.isBlank()) return this
    return filter {
        it.name.contains(needle, ignoreCase = true) ||
            it.description.contains(needle, ignoreCase = true)
    }
}
