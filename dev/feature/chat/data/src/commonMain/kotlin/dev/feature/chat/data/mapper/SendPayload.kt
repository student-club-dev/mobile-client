package dev.feature.chat.data.mapper

import dev.feature.chat.domain.model.MessageType
import dev.feature.chat.domain.model.Quote
import kotlinx.serialization.json.JsonObject

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
                text.isEmpty() -> "Xabar bo'sh."
                text.length > MAX_BODY -> "Xabar $MAX_BODY belgidan uzun bo'lmasin."
                mediaId != null || stickerId != null || gif != null || sticker != null ->
                    "Matnli xabarga biriktirma qo'shib bo'lmaydi."
                else -> null
            }

            MessageType.IMAGE, MessageType.VIDEO, MessageType.FILE -> when {
                mediaId.isNullOrBlank() -> "Fayl yuklanmadi."
                text.length > MAX_CAPTION -> "Izoh $MAX_CAPTION belgidan uzun bo'lmasin."
                else -> null
            }

            // Ikkalasi ham bo'lishi ham, ikkalasi ham bo'lmasligi ham xato.
            MessageType.GIF -> when {
                mediaId.isNullOrBlank() == (gif == null) ->
                    "GIF uchun yo yuklangan fayl, yo qidiruv natijasi kerak."
                text.isNotEmpty() -> CAPTION_FORBIDDEN
                else -> null
            }

            MessageType.VOICE -> when {
                mediaId.isNullOrBlank() -> "Ovozli xabar yuklanmadi."
                text.isNotEmpty() -> CAPTION_FORBIDDEN
                else -> null
            }

            // GIF'dagi o'sha qoida: manba ikkitadan biri bo'lsin — server katalogidagi
            // `stickerId` yoki qidiruvdan kelgan `sticker` obyekti. Ikkalasi birga
            // `422 STICKER_SOURCE_AMBIGUOUS`, ikkalasi ham yo'q — `422 VALIDATION_ERROR`.
            MessageType.STICKER -> when {
                stickerId.isNullOrBlank() == (sticker == null) ->
                    if (sticker != null) STICKER_SOURCE_AMBIGUOUS else "Stiker topilmadi."
                text.isNotEmpty() -> CAPTION_FORBIDDEN
                else -> null
            }

            // `SYSTEM` ni faqat server yozadi, `CALL` esa hali yo'q — klient yuborsa 422.
            MessageType.SYSTEM, MessageType.CALL -> "Bu turdagi xabarni yuborib bo'lmaydi."
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
            replyToMessageId == null -> "Sitata javob xabarisiz yuborilmaydi."
            fragment.text.isBlank() -> "Sitata bo'sh."
            fragment.text.length > Quote.MAX_LENGTH ->
                "Sitata ${Quote.MAX_LENGTH} belgidan uzun bo'lmasin."
            fragment.offset < 0 -> "Sitataning o'rni noto'g'ri."
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
        const val CAPTION_FORBIDDEN = "Bu turdagi xabarga izoh qo'shib bo'lmaydi."

        /**
         * Ikkala stiker manbasi birga berilgan — bu **klient xatosi**, foydalanuvchi
         * tuzatadigan narsa emas. Matn baribir ko'rsatiladi (jimgina yutilsa xabar
         * sababsiz yo'qolardi), lekin uni ko'rish — koddagi nuqson belgisi.
         */
        const val STICKER_SOURCE_AMBIGUOUS = "Stiker manbasi noaniq."
    }
}
