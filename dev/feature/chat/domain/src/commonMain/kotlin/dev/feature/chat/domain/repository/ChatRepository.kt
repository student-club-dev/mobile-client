package dev.feature.chat.domain.repository

import dev.core.common.Resource
import dev.feature.chat.domain.model.ConversationItem
import dev.feature.chat.domain.model.GifRef
import dev.feature.chat.domain.model.Message
import dev.feature.chat.domain.model.OutgoingImage
import dev.feature.chat.domain.model.Sticker
import dev.feature.chat.domain.model.StickerRef
import dev.feature.chat.domain.model.UnreadCount
import dev.feature.chat.domain.model.UploadState
import kotlinx.coroutines.flow.Flow

/**
 * **Chat** — 1:1 yozishma (handoff: `03-WEBSOCKET.md`).
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

    /**
     * Hozir yuklanayotgan biriktirmalar: **xabar id** → holat (foiz, fayl nomi, hajmi).
     *
     * Faqat xotirada va faqat yuklash davomida: qator serverga yetib borgach (yoki yiqilgach)
     * kalit olib tashlanadi, ya'ni bu yerda turgan har bir yozuv — ekranda halqa aylanib
     * turgan xabar.
     */
    fun observeUploads(): Flow<Map<String, UploadState>>

    /** Real-time kanalni ochadi/yopadi (ilova old planga chiqqanda / sessiya tugaganda). */
    fun connectRealtime()
    fun disconnectRealtime()

    /** `GET /v1/conversations` — ro'yxat va o'qilmaganlar sonini yangilaydi. */
    suspend fun refreshConversations(): Resource<Unit>

    /**
     * `GET /v1/conversations/unread-count` — tab badge'i uchun.
     *
     * Busiz butun suhbatlar ro'yxati faqat o'qilmaganlarni qo'shish uchun yuklanardi
     * (`handoff/02-API-CHANGES.md` §4b).
     */
    suspend fun unreadCount(): Resource<UnreadCount>

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
     * Matnli xabar yuborish. Avval ekranda `SENDING` holatida ko'rinadi, so'ng WS
     * `message:send` (ack bilan), u ishlamasa `POST /conversations/{id}/messages` orqali
     * ketadi. Ikki yo'l ham bir xil `clientMsgId` ni ishlatadi — server takror xabar
     * yaratmaydi (C6).
     */
    suspend fun send(conversationId: String, body: String): Resource<Unit>

    /**
     * Rasm(lar) yuborish — **bir martada bir nechta**.
     *
     * Har rasm alohida xabar bo'ladi (`seq` — chatning tartib o'qi, uni buzib bo'lmaydi),
     * lekin bitta `albumId` bilan bog'lanadi va ekranda **bitta to'r** bo'lib chiziladi.
     *
     * Oqim (`handoff/03-WEBSOCKET.md`): ekranda darhol ko'rinadi (local nusxa) →
     * `POST /v1/media/chat-upload` → qaytgan `mediaId` bilan `message:send { type: "IMAGE" }`.
     * Bittasi yiqilsa qolganlari yuborilaveradi; yiqilgani `FAILED` bo'lib qoladi va
     * [retry] bilan qayta urinish mumkin — fayl **qayta yuklanmaydi**, chunki biriktirma
     * bir martalik (`422 MEDIA_ALREADY_USED`).
     */
    suspend fun sendImages(conversationId: String, images: List<OutgoingImage>): Resource<Unit>

    /**
     * Hujjat yuboradi — `kind = FILE` (48 MB, oq ro'yxatdagi turlar).
     *
     * Rasm albomidan farqli o'laroq **yakka** ketadi: fayllar to'r bo'lib chizilmaydi va
     * ularni guruhlashning ma'nosi yo'q.
     */
    suspend fun sendFile(conversationId: String, bytes: ByteArray, fileName: String): Resource<Unit>

    /**
     * Video yuboradi — `kind = VIDEO` (64 MB, ≤ 3 daqiqa).
     *
     * ⚠️ Server H.264/AAC bo'lmagan videoni **transkod qiladi**: o'shanda biriktirma
     * `PROCESSING` holatida keladi (poster bor, o'zi yo'q) va tayyor bo'lgach `media:ready`
     * WS hodisasi uni almashtiradi. Klient hech narsa kutmaydi.
     */
    suspend fun sendVideo(conversationId: String, bytes: ByteArray, fileName: String): Resource<Unit>

    /**
     * Ovozli xabar yuboradi — `kind = VOICE` (16 MB, ≤ 5 daqiqa).
     *
     * To'lqin (48 nuqta) va davomiylikni **server** hisoblaydi, shuning uchun bu yerda
     * faqat baytlar uzatiladi.
     */
    suspend fun sendVoice(conversationId: String, bytes: ByteArray, fileName: String): Resource<Unit>

    /**
     * Stiker yuboradi.
     *
     * Ikki yo'l — stiker qayerdanligiga qarab (`Sticker.isRemote`):
     * - **server katalogidan** (`GET /v1/stickers/packs`) → `type = STICKER` + `stickerId`,
     *   tana **taqiqlangan**;
     * - **ilovaga kiritilgan zaxira** katalogdan → oddiy `TEXT`, tanasi emojining o'zi.
     *   Zaxira katalogning id'lari serverda yo'q va u `422 STICKER_NOT_FOUND` qaytarardi.
     */
    suspend fun sendSticker(conversationId: String, sticker: Sticker): Resource<Unit>

    /**
     * Qidiruvdan tanlangan **provayder stikerini** yuboradi (`type = STICKER` + `sticker`
     * obyekti) — `handoff/06-STICKER-SEARCH.md` §2.
     *
     * [sendSticker] dan farqi: bu stiker serverning katalogida YO'Q, ya'ni `stickerId`
     * yaramaydi (`422 STICKER_NOT_FOUND`). Uning o'rniga javobda kelgan obyekt
     * **o'zgartirilmasdan** qaytariladi — GIF'dagi aynan o'sha qoida
     * (`422 STICKER_URL_NOT_ALLOWED`).
     */
    suspend fun sendStickerRef(conversationId: String, sticker: StickerRef): Resource<Unit>

    /**
     * Qidiruvdan tanlangan GIF'ni yuboradi (`type = GIF` + `gif` obyekti).
     *
     * Fayl **yuklanmaydi** — provayderning havolasi serverga qaytariladi, shuning uchun
     * `mediaId` yo'q va tana taqiqlangan. [gif] javobda kelgan holida uzatiladi: server uni
     * domen oq ro'yxatidan o'tkazadi va har qanday o'zgarish `422 GIF_URL_NOT_ALLOWED`
     * bo'lib qaytadi (`handoff/04-GIF-INTEGRATION.md`).
     *
     * Foydalanuvchi o'zi yuklagan GIF esa boshqa yo'ldan — [sendImages] kabi `chat-upload`
     * orqali ketadi va server uni ovozsiz MP4 ga o'giradi.
     */
    suspend fun sendGif(conversationId: String, gif: GifRef): Resource<Unit>

    /**
     * Hali yuklanayotgan rasmlarning **local nusxasi**: xabar id → fayl baytlari.
     *
     * Yuklash tugaguncha serverda havola yo'q, shuning uchun ekranda tanlangan faylning
     * o'zi ko'rsatiladi. Xotirada turadi va yuborilishi bilan tozalanadi — ilova o'lsa
     * xabar `FAILED` bo'lib, nusxasiz qoladi.
     */
    fun observeLocalImages(): Flow<Map<String, ByteArray>>

    /** Yuborilmagan (`FAILED`) xabarni **o'sha** `clientMsgId` bilan qayta yuboradi. */
    suspend fun retry(messageId: String): Resource<Unit>

    /**
     * `DELETE /v1/messages/{id}` — **o'z** xabaringizni o'chirish (soft delete).
     *
     * Qator o'chirilmaydi: `seq` joyida qoladi, tana bo'shatiladi, `deletedAt` to'ldiriladi
     * va xabar o'qilmaganlar sanog'idan chiqadi. Tarixda tombstone bo'lib qoladi. Ikkala
     * a'zoga WS `message:deleted` ketadi. **Idempotent** (`handoff/02-API-CHANGES.md` §4b).
     */
    suspend fun deleteMessage(messageId: String): Resource<Unit>

    /**
     * Belgilangan xabarlarni o'chiradi — Telegram'dagi ikki qamrov bilan.
     *
     * - [forEveryone] = `true` — **ikkalangizda ham**: har biri uchun [deleteMessage]
     *   (soft delete, tarixda tombstone qoladi). Faqat **o'z** xabaringga qo'llanadi,
     *   suhbatdoshnikilari jimgina o'tkazib yuboriladi.
     * - [forEveryone] = `false` — **faqat menda**: xabar local keshda yashiriladi
     *   (`hiddenAt`), serverda va suhbatdoshda o'zgarishsiz qoladi. Istalgan xabarga —
     *   o'zingnikiga ham, suhbatdoshnikiga ham — qo'llanadi.
     *
     * ⚠️ «Faqat menda» hozircha **qurilmaga bog'liq**: backendda `scope = ME` yo'q
     * (`CHAT_SELECTION_AND_HISTORY_BACKEND.md` §A1), ya'ni ilova qayta o'rnatilsa
     * yashirilganlar qaytadi. Kontrakt tayyor bo'lgach shu usulning ichi o'zgaradi, imzosi
     * emas.
     *
     * Bir nechtasi yiqilsa ham qolganlari o'chiriladi; qaytadigan xato — **birinchisiniki**.
     */
    suspend fun deleteMessages(messageIds: List<String>, forEveryone: Boolean): Resource<Unit>

    /** O'qildi kursorini suradi (eng yuqori ko'rilgan `seq`). */
    suspend fun markRead(conversationId: String)

    /**
     * Yetkazildi kursori — suhbat ochilganda bir marta. WS ishlamasa
     * `POST /v1/conversations/{id}/delivered` zaxirasi ishlatiladi (§17.6): busiz uzilgan
     * ulanish jo'natuvchini abadiy bitta belgichada qoldirardi.
     */
    suspend fun markDelivered(conversationId: String)

    /** "Yozmoqda" holatini yuboradi (best-effort, xato bermaydi). */
    suspend fun setTyping(conversationId: String, typing: Boolean)

    /** Arxivlash — **faqat local**, serverga bormaydi. */
    suspend fun setArchived(conversationId: String, archived: Boolean)
}
