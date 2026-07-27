package dev.feature.chat.domain.repository

import dev.core.common.Resource
import dev.feature.chat.domain.model.ConversationItem
import dev.feature.chat.domain.model.Message
import kotlinx.coroutines.flow.Flow

/**
 * **Chat** — 1:1 yozishma (handoff: `chat.md`).
 *
 * Real vaqt — WebSocket (`/chat`, Socket.IO); REST esa tarix, ro'yxat va WS ishlamay
 * qolganda zaxira yo'l. Ikkalasi ham bir xil local keshga (SQLDelight) yozadi, UI faqat
 * keshni kuzatadi — shuning uchun ekran offline'da ham to'la ishlaydi.
 *
 * ⚠️ **Eshik — `Connections`.** Bog'lanmagan talaba bilan suhbat ochib ham, yozib ham
 * bo'lmaydi → `403 NOT_CONNECTED`.
 */
interface ChatRepository {

    /** Suhbatlar ro'yxati (local keshdan, jonli). */
    fun observeConversations(): Flow<List<ConversationItem>>

    /** Arxivlanganlar — **faqat local** bayroq bo'yicha (backendda arxiv yo'q). */
    fun observeArchivedConversations(): Flow<List<ConversationItem>>

    /** Bitta suhbat (sarlavha uchun: nom, onlayn holati). */
    fun observeConversation(conversationId: String): Flow<ConversationItem?>

    /** Suhbat xabarlari — eskidan yangiga. */
    fun observeMessages(conversationId: String): Flow<List<Message>>

    /** Suhbatdosh "yozmoqda" holati (WS `typing`; ~5 soniyadan keyin o'zi so'nadi). */
    fun observeTyping(conversationId: String): Flow<Boolean>

    /** WebSocket ulangannmi — sarlavhadagi holat va zaxira yo'lni tanlash uchun. */
    fun observeRealtimeConnected(): Flow<Boolean>

    /** Real-time kanalni ochadi/yopadi (ilova old planga chiqqanda / sessiya tugaganda). */
    fun connectRealtime()
    fun disconnectRealtime()

    /** `GET /v1/conversations` — ro'yxat va o'qilmaganlar sonini yangilaydi. */
    suspend fun refreshConversations(): Resource<Unit>

    /**
     * `POST /v1/conversations` — suhbat ochadi yoki mavjudini qaytaradi (**idempotent**,
     * "bormi?" deb tekshirish shart emas). Qaytadi — `conversationId`.
     *
     * `403 NOT_CONNECTED` — avval bog'lanish kerak.
     */
    suspend fun openDirect(studentId: String): Resource<String>

    /** Suhbat ochilganda: oxirgi xabarlarni yuklaydi va uzilib qolganlarini yetishib oladi. */
    suspend fun loadLatest(conversationId: String): Resource<Unit>

    /**
     * Yuqoriga aylantirish — keshdagi eng eski `seq` dan oldingi sahifa.
     * Qaytadi: **yana eski xabar bormi**.
     */
    suspend fun loadOlder(conversationId: String): Resource<Boolean>

    /**
     * Xabar yuborish. Avval ekranda `SENDING` holatida ko'rinadi, so'ng WS `message:send`
     * (ack bilan), u ishlamasa `POST /conversations/{id}/messages` orqali ketadi. Ikki yo'l
     * ham bir xil `clientMsgId` ni ishlatadi — server takror xabar yaratmaydi (C6).
     */
    suspend fun send(conversationId: String, body: String): Resource<Unit>

    /** Yuborilmagan (`FAILED`) xabarni **o'sha** `clientMsgId` bilan qayta yuboradi. */
    suspend fun retry(messageId: String): Resource<Unit>

    /** O'qildi kursorini suradi (eng yuqori ko'rilgan `seq`). */
    suspend fun markRead(conversationId: String)

    /** Yetkazildi kursori — suhbat ochilganda bir marta (faqat WS, javob qaytmaydi). */
    suspend fun markDelivered(conversationId: String)

    /** "Yozmoqda" holatini yuboradi (best-effort, xato bermaydi). */
    suspend fun setTyping(conversationId: String, typing: Boolean)

    /** Arxivlash — **faqat local**, serverga bormaydi. */
    suspend fun setArchived(conversationId: String, archived: Boolean)
}
