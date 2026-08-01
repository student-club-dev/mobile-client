package dev.feature.chat.domain.model

import dev.feature.connections.domain.model.StudentSummary
import kotlinx.datetime.Instant

/** v1 da faqat `DIRECT` yaratiladi; `GROUP` — keyingi bosqich. */
enum class ConversationType { DIRECT, GROUP }

/**
 * Xabar turi — **serverdan keladi** (`MessageDto.type`, `handoff/03-WEBSOCKET.md`).
 *
 * 2026-07-29 gacha backend faqat `TEXT` yozardi va rasm/stiker klient tomonida tanadan
 * taxmin qilinardi. Endi tipli xabar to'liq ishlaydi va evristika faqat **eski keshdagi**
 * qatorlar uchun zaxira bo'lib qoldi.
 *
 * `SYSTEM` ni faqat server yozadi — klient yuborsa `422`. `CALL` hali yo'q (qo'ng'iroq
 * bosqichida qo'shiladi), lekin enum'da turibdi: eski kesh qatorlari uni saqlashi mumkin.
 */
enum class MessageType { TEXT, IMAGE, GIF, VIDEO, VOICE, FILE, STICKER, CALL, SYSTEM }

/**
 * Xabar bilan nima ketayotgani — `handoff/03-WEBSOCKET.md` dagi jadval.
 *
 * Bu qoidalar **klientda** tekshiriladi: `422` ni kutib o'tirish foydalanuvchining
 * xabarini "yuborilmadi" holatiga tushirardi, holbuki xatoni yuborishdan oldin ham
 * bilsa bo'lardi.
 */
val MessageType.requiresBody: Boolean get() = this == MessageType.TEXT

/** `GIF`/`VOICE`/`STICKER` da izoh ataylab rad etiladi — uni chizadigan joy yo'q. */
val MessageType.forbidsBody: Boolean
    get() = this == MessageType.GIF || this == MessageType.VOICE || this == MessageType.STICKER

/** `mediaId` majburiy bo'lgan turlar (`GIF` — yuklangani; qidiruvdan olingani `gif` bilan ketadi). */
val MessageType.requiresMedia: Boolean
    get() = this == MessageType.IMAGE || this == MessageType.VIDEO ||
        this == MessageType.FILE || this == MessageType.VOICE

/** Xabarning **local** yuborilish holati (serverda bunday maydon yo'q). */
enum class MessageStatus {
    /** Ekranda ko'rsatildi, lekin server hali tasdiqlamadi. */
    SENDING,

    /** Server qabul qildi — `seq` va `id` haqiqiy. */
    SENT,

    /** Yuborib bo'lmadi — foydalanuvchi qayta urinishi mumkin (o'sha `clientMsgId` bilan). */
    FAILED,
}

/** Biriktirmaning turi — `POST /v1/media/chat-upload` dagi `kind`. */
enum class MediaKind { IMAGE, GIF, VIDEO, VOICE, FILE }

/**
 * Biriktirmaning holati.
 *
 * [PROCESSING] faqat hali transkodlanayotgan videoda uchraydi: poster kadr allaqachon bor,
 * shuning uchun xabar darhol yuboriladi va WS `media:ready` kelganda biriktirma
 * almashtiriladi. Xatolik bo'lsa [FAILED] keladi — hech qachon jim [READY] emas.
 */
enum class MediaStatus { PROCESSING, READY, FAILED }

/** Suhbatning o'zi. */
data class Conversation(
    val id: String,
    val type: ConversationType = ConversationType.DIRECT,
    /** Oxirgi xabar vaqti. Yangi (bo'sh) suhbatda `null`. */
    val lastMessageAt: Instant? = null,
)

/**
 * Suhbatlar ro'yxatidagi bitta qator.
 *
 * [other].`online` / `lastSeenAt` — Redis'dan jonli o'qiladi va WS `presence:update` bilan
 * yangilanadi. Suhbatdosh `lastSeenVisibility = NOBODY` qo'ygan bo'lsa server ikkalasini
 * ham yashiradi (`false` / `null`).
 */
data class ConversationItem(
    val conversation: Conversation,
    val other: StudentSummary,
    val lastMessage: Message? = null,
    val unreadCount: Int = 0,
    /**
     * Suhbatdosh **o'qigan** eng yuqori `seq` — chiquvchi xabar shundan past yoki teng
     * bo'lsa "o'qildi" (✓✓ yorqin). Ikki manba: WS `message:read` va ro'yxat javobidagi
     * `peerReadSeq` — ya'ni ilova qayta ochilganda holat tiklanadi.
     */
    val otherReadSeq: Int = 0,
    /**
     * Suhbatdoshning **qurilmasi olgan** eng yuqori `seq` (WS `message:delivered` /
     * `peerDeliveredSeq`). Shundan past `seq` — "yetkazildi" (✓✓ xira).
     */
    val otherDeliveredSeq: Int = 0,
    /** Faqat local bayroq — backendda arxivlash endpointi yo'q. */
    val archived: Boolean = false,
) {
    val id: String get() = conversation.id
}

/**
 * Suhbat ichidagi bitta xabar.
 *
 * [seq] — suhbat ichidagi tartib o'qi: 1 dan boshlanadi, bo'shliq qoldirmaydi. **Tartiblash,
 * tarix kursori (`before`), qayta ulanishda yetishib olish (`after`) va o'qildi belgisi —
 * hammasi shu bo'yicha**, `createdAt` bo'yicha emas. Hali yuborilmagan xabarda `0`.
 */
data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val seq: Int,
    val body: String,
    val createdAt: Instant,
    val type: MessageType = MessageType.TEXT,
    val status: MessageStatus = MessageStatus.SENT,
    val clientMsgId: String? = null,
    /**
     * Xabar o'chirilgan vaqti (`DELETE /v1/messages/{id}`). `null` emas bo'lsa — bu
     * **tombstone**: qator `seq` i bilan tarixda qoladi, lekin tanasi ham, biriktirmasi
     * ham yo'q va u o'qilmaganlar sanog'iga kirmaydi (`handoff/02-API-CHANGES.md` §4b).
     */
    val deletedAt: Instant? = null,
    /** `IMAGE`/`GIF`/`VIDEO`/`VOICE`/`FILE` da to'ladi; qolganlarida doim `null`. */
    val attachment: Attachment? = null,
    /** `STICKER` da to'ladi. Tana taqiqlangani uchun ko'rsatiladigan hamma narsa shu yerda. */
    val sticker: MessageSticker? = null,
    /**
     * Bir martada yuborilgan rasmlarni bog'laydi — ketma-ket kelgan bir xil qiymatli
     * xabarlar bitta to'r bo'lib chiziladi. Klient generatsiya qiladi, **server esa uni
     * qaytaradi** (`MessageDto.albumId`), ya'ni qabul qiluvchi tomonda ham to'g'ri ishlaydi.
     */
    val albumId: String? = null,
    /**
     * Javob berilgan xabarning surati — pufak ustidagi sitata bloki. Oddiy xabarda `null`.
     */
    val replyTo: ReplyTo? = null,
) {
    /** O'chirilgan xabar — ekranda "Xabar o'chirildi" tombstone'i chiziladi. */
    val deleted: Boolean get() = deletedAt != null
}

/**
 * Media biriktirmasi — `MessageDto.attachment` ning ko'zgusi.
 *
 * [url] **himoyalangan**: `GET /v1/media/{id}/raw` suhbat a'zoligini tekshiradi, ya'ni
 * havolani ochish uchun ham `Bearer` token kerak (`handoff/02-API-CHANGES.md` §4c). Rasm
 * yuklovchi (Coil) shuning uchun tokenli klientdan foydalanadi.
 */
data class Attachment(
    /** Server `mediaId` si. Eski keshdagi qatorlarda `null` bo'lishi mumkin. */
    val id: String? = null,
    val url: String,
    val thumbUrl: String? = null,
    val kind: MediaKind = MediaKind.IMAGE,
    val status: MediaStatus = MediaStatus.READY,
    val mimeType: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val sizeBytes: Long = 0,
    val durationMs: Int = 0,
    /** Ovozli xabarning 48 nuqtali to'lqin shakli (`0..100`). Boshqa turlarda bo'sh. */
    val waveform: List<Int> = emptyList(),
    /** `FILE` uchun asl nom. */
    val fileName: String? = null,
    /** Rasm yuklanguncha ko'rsatiladigan xira o'rinbosar. */
    val blurHash: String? = null,
    /** `true` — ovozsiz va takrorlanuvchi qilib o'ynatiladi (GIF va GIF'dan o'girilgan MP4). */
    val isAnimated: Boolean = false,
) {
    /** Ko'rsatish uchun eng arzon havola — ro'yxatda kichigi, ochilganda to'lig'i. */
    val previewUrl: String get() = thumbUrl ?: url

    /** Nisbat ma'lum bo'lmasa `null` — chaqiruvchi o'zining odatiy nisbatini qo'yadi. */
    val aspectRatio: Float? get() = if (width > 0 && height > 0) width.toFloat() / height else null

    /** Video hali transkodlanmoqda — poster kadr bor, o'zi hali yo'q. */
    val processing: Boolean get() = status == MediaStatus.PROCESSING
}

/** Xabarga biriktirilgan stiker (`MessageDto.sticker`). */
data class MessageSticker(
    val id: String,
    val packId: String = "",
    val emoji: String = "",
    /** Serverdagi tasviri. Bo'sh bo'lsa [emoji] katta qilib chiziladi. */
    val url: String? = null,
    val width: Int = 0,
    val height: Int = 0,
)

/**
 * Xabar tanasining **belgilangan bo'lagi** — javob berishda sitata qilingan matn.
 *
 * [offset] — **UTF-16 kod birligida**: Kotlin `String` ham aynan shunday sanaydi, ya'ni
 * `body.substring(offset, offset + text.length)` to'g'ridan-to'g'ri ishlaydi va server
 * ham shu tekshiruvni bajaradi (mos kelmasa `422 QUOTE_NOT_FOUND`).
 */
data class Quote(
    val text: String,
    val offset: Int,
) {
    companion object {
        /** Server chegarasi (`422 QUOTE_TOO_LONG`). */
        const val MAX_LENGTH = 300
    }
}

/**
 * Javob berilgan xabarning **surati** (`MessageDto.replyTo`).
 *
 * ⚠️ Bu nishonning nusxasi, havolasi emas: nishon keyin o'chirilsa ham [preview] va
 * [quote] o'z joyida qoladi. Shuning uchun pufakdagi sitata keshdan qidirilmaydi —
 * qidirsak, tarixi yuklanmagan yoki tozalangan xabarda u bo'sh bo'lib qolardi.
 */
data class ReplyTo(
    /**
     * Nishonning id'si — **sakrash** uchun (`?around=`). `null` bo'lsa xabar bazadan
     * butunlay tozalangan va sakrab bo'lmaydi.
     */
    val id: String? = null,
    val seq: Int = 0,
    val senderId: String = "",
    /** Server tayyor holda beradi — id bo'yicha ism qidirish shart emas. */
    val senderName: String = "",
    val type: MessageType = MessageType.TEXT,
    /** ≤120 belgi. Media xabarda `null` — o'shanda [type] bo'yicha «📷 Rasm» chiziladi. */
    val preview: String? = null,
    val quote: Quote? = null,
    /** Nishon o'chirilgan — sitata ko'rinadi, lekin sakrash tugmasi bo'lmaydi. */
    val originalDeleted: Boolean = false,
) {
    /** Sitataga bosilganda tarixning o'sha joyiga sakrash mumkinmi. */
    val canJump: Boolean get() = id != null && !originalDeleted && seq > 0
}

/**
 * O'chirish qamrovi (`scope`) — `handoff` §A1.
 *
 * ⚠️ Sukut qiymati endpointga qarab **boshqacha** bo'lgani uchun klient uni doim ochiq
 * yuboradi: xabar o'chirishda server sukut bo'yicha [EVERYONE], tarix/suhbatda esa [ME].
 */
enum class DeleteScope { ME, EVERYONE }

/**
 * Ketayotgan biriktirmaning holati — xabar ekranda ko'rinib turibdi, fayl esa hali yo'lda.
 *
 * Serverda bunday tushuncha yo'q va keshga ham yozilmaydi: yuklash bir necha soniya davom
 * etadi, ilova qayta ishga tushsa esa yarim ketgan faylni davom ettirib bo'lmaydi (server
 * `Range` bilan yuklashni qo'llamaydi) — shuning uchun holat faqat xotirada yashaydi.
 *
 * [fileName] va [sizeBytes] **yuklovchidan** olinadi, keshdan emas: yakka biriktirmali
 * xabarning qatori yuklash tugagunicha bo'sh turadi (`attachment` faqat javob bilan
 * keladi), pufakda esa nom va hajm shu vaqtning o'zida kerak.
 */
data class UploadState(
    /** `0f..1f`. `null` — hajm noma'lum, UI aylanma halqa ko'rsatadi. */
    val progress: Float? = null,
    val fileName: String? = null,
    val sizeBytes: Long = 0,
)

/** `GET /v1/conversations/unread-count` — tab badge'i uchun ikkala hisoblagich. */
data class UnreadCount(
    /** O'qilmagan xabarlar soni (o'chirilganlar hisobga olinmaydi). */
    val total: Int,
    /** Kamida bitta o'qilmagani bor suhbatlar soni. */
    val conversations: Int,
)

/** Yuborish uchun tayyorlangan rasm — domen qatlami platformadagi tanlagichga bog'lanmasin. */
class OutgoingImage(
    val bytes: ByteArray,
    val fileName: String,
) {
    /**
     * Galereyadan tanlangan fayl **GIF**mi.
     *
     * Tizim tanlagichi GIF'ni ham "rasm" deb qaytaradi, lekin server uchun bu boshqa tur:
     * `kind = GIF` bilan yuklangan fayl ovozsiz MP4 ga o'giriladi (~20 barobar yengil) va
     * xabar `type = GIF` bo'ladi. Kengaytma bo'yicha aniqlanadi — bu faqat **ko'rsatma**,
     * server turni baribir faylning baytlaridan aniqlaydi.
     */
    val isGif: Boolean
        get() = fileName.substringAfterLast('.', "").equals("gif", ignoreCase = true)

    /** Shu fayl uchun xabar turi. */
    val messageType: MessageType
        get() = if (isGif) MessageType.GIF else MessageType.IMAGE
}

/**
 * Yuborish uchun tayyorlangan video.
 *
 * Rasmdan ([OutgoingImage]) farqli o'laroq baytlar emas, **fayl yo'li** saqlanadi: video
 * 64 MB gacha bo'lishi mumkin va uni xotiraga o'qish arzon telefonda ilovani quladi.
 * Fayl ilova keshida turadi va yuborish tugagach o'chiriladi.
 */
class OutgoingVideo(
    /** Keshdagi fayl yo'li — tanlangan (yoki siqilgan) videoning nusxasi. */
    val path: String,
    val fileName: String,
    val sizeBytes: Long,
    /** Aniqlab bo'lmasa `null` — o'shanda chegarani server tekshiradi. */
    val durationMs: Int?,
    /**
     * Birinchi kadr. Yuklash tugagunicha serverda hech narsa yo'q, ya'ni pufakda
     * ko'rsatadigan yagona tasvir shu — usiz ekranda bo'sh to'rtburchak turardi.
     */
    val posterBytes: ByteArray?,
    /** Foydalanuvchi yozgan izoh (`≤ 1024` belgi); bo'sh bo'lsa `null`. */
    val caption: String?,
    /**
     * Siqish kerakmi — halqaning qancha qismi shunga ajratilishini hal qiladi.
     *
     * Siqilmaydigan videoda halqa darrov yuklashdan boshlanadi; usiz u yarmigacha
     * sakrab, keyin yuklashni boshlagandek ko'rinardi.
     */
    val needsPreparing: Boolean,
    /**
     * Yuborishdan oldingi **siqish** — Telegramdagi kabi, tanlash paytida emas,
     * yuborilgandan keyin.
     *
     * Nega lambda: siqish platformaga xos (`media3-transformer` / `AVAssetExportSession`)
     * va domen qatlami uni bilmaydi. Ayni paytda uni **repozitoriy** boshqarishi kerak:
     * faqat o'sha yerda siqish bilan yuklash bitta jarayon bo'lib, bitta halqada
     * ko'rsatilishi mumkin.
     *
     * Qaytadi: yuboriladigan video (siqilgani); `null` — siqib ham chegaraga sig'madi.
     *
     * ⚠️ Maydonning o'zi `null` bo'lsa video **allaqachon tayyor** va qayta siqilmaydi.
     * Bu yuklash yiqilgandan keyingi qayta urinish uchun muhim: siqilgan faylni yana bir
     * marta siqish yana o'nlab soniya va batareya degani.
     */
    val prepare: (suspend (onProgress: (Float) -> Unit) -> OutgoingVideo?)? = null,
)
