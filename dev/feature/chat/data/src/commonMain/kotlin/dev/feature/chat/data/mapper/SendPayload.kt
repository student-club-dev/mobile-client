package dev.feature.chat.data.mapper

import dev.feature.chat.domain.model.MessageType
import dev.feature.chat.domain.model.Quote
import kotlinx.serialization.json.JsonObject
import dev.feature.chat.domain.model.ChatDomainStrings

/**
 * Yuboriladigan xabarning tanasi — WS `message:send` va REST `SendMessageDto` uchun **bitta**
 * ko'rinish.
 *
 * Nega alohida klass: qoidalar ikkala yo'lda ham bir xil, lekin ikkita joyda takrorlansa
 * ular albatta bir-biridan ajralib ketardi. Ustiga [validate] ni shu yerda sinab ko'rish
 * mumkin — WS ham, REST ham kerak emas.
 */
internal data class SendPayload(
    val type: MessageType,
    val body: String? = null,
    /** `POST /v1/media/chat-upload` qaytargan id. **Bir martalik**. */
    val mediaId: String? = null,
    val stickerId: String? = null,
    val albumId: String? = null,
    /**
     * Albomda nechta rasm bo'lishi — **faqat birinchi xabarda** (`2..10`).
     *
     * Server buni push matni uchun ishlatadi: «📷 10 ta rasm». Usiz u faqat birinchi
     * rasmni tasvirlay olardi, chunki push aynan birinchi rasm kelganda ketadi va o'sha
     * paytda albomdan boshqa hech narsa yetib bormagan bo'ladi — ya'ni sanab bo'lmaydi,
     * faqat **e'lon qilish** mumkin (`01-QOLGAN_ISHLAR_RESPONSE.md` §1).
     *
     * Ixtiyoriy: yuborilmasa eski xatti-harakat qoladi.
     */
    val albumSize: Int? = null,
    /** Qidiruvdan olingan GIF (`GifRefDto` shakli) — xom JSON, GIF moduliga bog'lanmaslik uchun. */
    val gif: JsonObject? = null,
    /**
     * Qidiruvdan olingan **provayder stikeri** (`handoff/06-STICKER-SEARCH.md` §2) — xom JSON.
     *
     * [stickerId] bilan **birga yuborilmaydi**: biri server katalogidagi qatorga, ikkinchisi
     * begona CDN'dagi tasvirga ishora qiladi va server ikkalasini birga ko'rsa
     * `422 STICKER_SOURCE_AMBIGUOUS` qaytaradi.
     */
    val sticker: JsonObject? = null,
    /**
     * Optimistik qatorga yoziladigan stiker ko'rinishi — **tarmoqqa ketmaydi**.
     *
     * Server javobida stiker to'liq qaytadi, lekin unga qadar bir necha soniya o'tadi:
     * usiz foydalanuvchi bosgan stiker o'rnida bo'sh pufak turardi.
     */
    val stickerEmoji: String? = null,
    val stickerUrl: String? = null,
    /**
     * Optimistik qatorga yoziladigan biriktirma ko'rinishi — **tarmoqqa ketmaydi**.
     *
     * Qidiruvdan tanlangan GIF serverga umuman yuklanmaydi (havola bilan ketadi), ya'ni
     * yuklanayotgan faylning local nusxasi ham yo'q: usiz javob kelguncha ekranda bo'sh
     * pufak turardi. Maydonlar GIF'ga xos EMAS — havolasi oldindan ma'lum har qanday
     * biriktirma shu yo'ldan o'tadi.
     */
    val previewUrl: String? = null,
    val previewThumbUrl: String? = null,
    val previewWidth: Long = 0,
    val previewHeight: Long = 0,
    /**
     * Javob berilayotgan xabar (`§C1`) — REST va WS'da bir xil maydon.
     *
     * Nishon **o'sha suhbatdan** va o'chirilmagan bo'lishi shart; aks holda server
     * `422 REPLY_TARGET_NOT_FOUND` / `REPLY_TARGET_DELETED` qaytaradi.
     */
    val replyToMessageId: String? = null,
    /**
     * Nishon tanasining belgilangan bo'lagi. Server `body.slice(offset, offset + text.length)`
     * ni nishonning **haqiqiy** tanasi bilan solishtiradi (`422 QUOTE_NOT_FOUND`).
     */
    val quote: Quote? = null,
) {

    /**
     * Turga xos qoidalarni tekshiradi (`handoff/03-WEBSOCKET.md` dagi jadval). Xato bo'lsa —
     * foydalanuvchiga ko'rsatiladigan matn, aks holda `null`.
     *
     * Nega klientda: `422` ni kutib o'tirish xabarni "yuborilmadi" holatiga tushirardi va
     * foydalanuvchi sababini tushunmasdan qayta urinaverardi. Server baribir o'zi ham
     * tekshiradi — bu uni almashtirmaydi, faqat tarmoqqa chiqmasdan javob beradi.
     */
    fun validate(): String? {
        val text = body?.trim().orEmpty()
        // Sitata qoidalari turdan mustaqil — shuning uchun turlar jadvalidan OLDIN.
        quoteError()?.let { return it }
        return when (type) {
            MessageType.TEXT -> when {
                text.isEmpty() -> ChatDomainStrings.emptyMessage
                text.length > MAX_BODY -> ChatDomainStrings.bodyTooLong(MAX_BODY)
                mediaId != null || stickerId != null || gif != null || sticker != null ->
                    ChatDomainStrings.textCantHaveAttachment
                else -> null
            }

            MessageType.IMAGE, MessageType.VIDEO, MessageType.FILE -> when {
                mediaId.isNullOrBlank() -> ChatDomainStrings.fileNotUploaded
                text.length > MAX_CAPTION -> ChatDomainStrings.captionTooLong(MAX_CAPTION)
                else -> null
            }

            // Ikkalasi ham bo'lishi ham, ikkalasi ham bo'lmasligi ham xato.
            MessageType.GIF -> when {
                mediaId.isNullOrBlank() == (gif == null) ->
                    ChatDomainStrings.gifNeedsSource
                text.isNotEmpty() -> CAPTION_FORBIDDEN
                else -> null
            }

            MessageType.VOICE -> when {
                mediaId.isNullOrBlank() -> ChatDomainStrings.voiceNotUploaded
                text.isNotEmpty() -> CAPTION_FORBIDDEN
                else -> null
            }

            // GIF'dagi o'sha qoida: manba ikkitadan biri bo'lsin — server katalogidagi
            // `stickerId` yoki qidiruvdan kelgan `sticker` obyekti. Ikkalasi birga
            // `422 STICKER_SOURCE_AMBIGUOUS`, ikkalasi ham yo'q — `422 VALIDATION_ERROR`.
            MessageType.STICKER -> when {
                stickerId.isNullOrBlank() == (sticker == null) ->
                    if (sticker != null) STICKER_SOURCE_AMBIGUOUS else ChatDomainStrings.stickerNotFound
                text.isNotEmpty() -> CAPTION_FORBIDDEN
                else -> null
            }

            // Dumaloq video xabar: izoh **umuman yo'q** (uni chizadigan joy ham yo'q) va
            // fayl kvadrat bo'lishi shart — kvadratlikni yuklashdan oldin kesuvchi
            // ta'minlaydi, aks holda server `422 MEDIA_NOT_SQUARE` beradi.
            MessageType.VIDEO_NOTE -> when {
                mediaId.isNullOrBlank() -> ChatDomainStrings.videoNoteNotUploaded
                text.isNotEmpty() -> CAPTION_FORBIDDEN
                else -> null
            }

            // `SYSTEM` va `CALL` qatorini FAQAT server yozadi — klient yuborsa WS'da ham,
            // REST'da ham `422 VALIDATION_ERROR` (`handoff/09-CALLS-REST.md` §4). Aks holda
            // har kim soxta "javobsiz qo'ng'iroq" push'i yuborardi.
            MessageType.SYSTEM, MessageType.CALL -> ChatDomainStrings.unsupportedKind
        }
    }

    /**
     * Sitata qoidalari (`§C1`) — serverdagi `QUOTE_*` kodlarining klientdagi ko'zgusi.
     *
     * Nega oldindan: sitata bilan yuborilgan xabar `422` olsa, u ekranda `FAILED` bo'lib
     * qolardi va foydalanuvchi sababini bilmasdi.
     */
    private fun quoteError(): String? {
        val fragment = quote ?: return null
        return when {
            replyToMessageId == null -> ChatDomainStrings.quoteNeedsReply
            fragment.text.isBlank() -> ChatDomainStrings.quoteEmpty
            fragment.text.length > Quote.MAX_LENGTH ->
                ChatDomainStrings.quoteTooLong(Quote.MAX_LENGTH)
            fragment.offset < 0 -> ChatDomainStrings.quoteBadRange
            else -> null
        }
    }

    /**
     * Tarmoqqa ketadigan tana. Bo'sh matn `null` ga aylanadi: `GIF`/`VOICE`/`STICKER` da
     * bo'sh satr ham izoh sifatida rad etilishi mumkin.
     */
    val wireBody: String? get() = body?.trim()?.takeIf { it.isNotEmpty() }

    internal companion object {
        const val MAX_BODY = 4000
        const val MAX_CAPTION = 1024

        /**
         * `GIF`/`VOICE`/`STICKER` da izoh **ataylab** rad etiladi: uni chizadigan joy yo'q,
         * ya'ni qabul qilsak foydalanuvchining matni jimgina yo'qolardi.
         */
        val CAPTION_FORBIDDEN: String get() = ChatDomainStrings.captionNotAllowed

        /**
         * Ikkala stiker manbasi birga berilgan — bu **klient xatosi**, foydalanuvchi
         * tuzatadigan narsa emas. Matn baribir ko'rsatiladi (jimgina yutilsa xabar
         * sababsiz yo'qolardi), lekin uni ko'rish — koddagi nuqson belgisi.
         */
        val STICKER_SOURCE_AMBIGUOUS: String get() = ChatDomainStrings.stickerSourceUnclear
    }
}
