package dev.feature.chat.domain.model

import dev.feature.calls.domain.model.CallEndReason
import dev.feature.calls.domain.model.CallMedia
import dev.feature.calls.domain.model.CallStatus
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
 * `SYSTEM` va `CALL` ni **faqat server yozadi** — klient yuborsa WS'da ham, REST'da ham
 * `422 VALIDATION_ERROR` (`handoff/09-CALLS-REST.md` §4). `CALL` qatori qo'ng'iroq
 * tugagach avtomatik paydo bo'ladi va tafsilotlari [Message.call] da keladi.
 *
 * ⚠️ Sim formatida bu **enum emas, `String`** (`lenientEnums`): server ro'yxatni
 * kengaytirsa noma'lum qiymat butun javobni yiqitmasin. Bu yerga o'girish har doim
 * `parseEnum(raw, TEXT)` orqali — noma'lum tur oddiy matn pufakchasi bo'lib qoladi.
 */
enum class MessageType { TEXT, IMAGE, GIF, VIDEO, VIDEO_NOTE, VOICE, FILE, STICKER, CALL, SYSTEM }

/**
 * Xabar bilan nima ketayotgani — `handoff/03-WEBSOCKET.md` dagi jadval.
 *
 * Bu qoidalar **klientda** tekshiriladi: `422` ni kutib o'tirish foydalanuvchining
 * xabarini "yuborilmadi" holatiga tushirardi, holbuki xatoni yuborishdan oldin ham
 * bilsa bo'lardi.
 */
val MessageType.requiresBody: Boolean get() = this == MessageType.TEXT

/**
 * `GIF`/`VOICE`/`STICKER`/`VIDEO_NOTE` da izoh ataylab rad etiladi — uni chizadigan joy yo'q.
 *
 * `VIDEO_NOTE` (dumaloq video xabar) shu ro'yxatda: pufakcha aylana shaklida chiziladi va
 * matn qo'yiladigan maydon umuman yo'q (server ham izohni qabul qilmaydi).
 */
val MessageType.forbidsBody: Boolean
    get() = this == MessageType.GIF || this == MessageType.VOICE ||
        this == MessageType.STICKER || this == MessageType.VIDEO_NOTE

/** `mediaId` majburiy bo'lgan turlar (`GIF` — yuklangani; qidiruvdan olingani `gif` bilan ketadi). */
val MessageType.requiresMedia: Boolean
    get() = this == MessageType.IMAGE || this == MessageType.VIDEO ||
        this == MessageType.VIDEO_NOTE || this == MessageType.FILE || this == MessageType.VOICE

/** Xabarning **local** yuborilish holati (serverda bunday maydon yo'q). */
enum class MessageStatus {
    /** Ekranda ko'rsatildi, lekin server hali tasdiqlamadi. */
    SENDING,

    /** Server qabul qildi — `seq` va `id` haqiqiy. */
    SENT,

    /** Yuborib bo'lmadi — foydalanuvchi qayta urinishi mumkin (o'sha `clientMsgId` bilan). */
    FAILED,
}

/**
 * Biriktirmaning turi — `POST /v1/media/chat-upload` dagi `kind`.
 *
 * [VIDEO_NOTE] — dumaloq video xabar: **kvadrat** bo'lishi shart (aks holda
 * `422 MEDIA_NOT_SQUARE`), ≤ 60 s va ≤ 12 MB. Butun spec bo'yicha hajm chegarasi qolgan
 * yagona tur shu (`handoff/09-CALLS-REST.md` emas — `chat-upload` tavsifida).
 *
 * `IMAGE_ORIGINAL` bu yerda **yo'q**: u yuklash rejimi, biriktirma turi emas — server
 * baribir `kind: IMAGE` qaytaradi, farq faqat sifatda ([dev.core.network.media.ChatMediaKind]).
 */
enum class MediaKind { IMAGE, GIF, VIDEO, VIDEO_NOTE, VOICE, FILE }

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
    /**
     * [MessageType.CALL] xabarining tafsiloti (`MessageDto.call`), qolganlarida `null`.
     *
     * ⚠️ Bu **surat**, `Call` jadvaliga havola emas: qiymatlar xabar yozilgan paytda
     * muzlatiladi va **hech qachon o'zgarmaydi** (`handoff/09-CALLS-DEVIATIONS.md` §14),
     * ya'ni pufakchani yangilash uchun hech narsa qayta so'ralmaydi.
     */
    val call: MessageCall? = null,
) {
    /** O'chirilgan xabar — ekranda "Xabar o'chirildi" tombstone'i chiziladi. */
    val deleted: Boolean get() = deletedAt != null
}

/**
 * Chat lentasidagi qo'ng'iroq yozuvi (`MessageDto.call`).
 *
 * Xabarning `senderId` si **doimo chaquvchi** — javobsiz qo'ng'iroqda ham. Ya'ni pufakcha
 * qaysi tomonda chizilishi «kim qo'ng'iroq qildi» degan savolga javob beradi, «kim bilan
 * gaplashdi» ga emas.
 */
data class MessageCall(
    /** **uuid v4** (36 belgi) — talaba id'laridan (cuid) farq qiladi. */
    val callId: String,
    val media: CallMedia = CallMedia.AUDIO,
    /** Amalda faqat terminal qiymatlar: `ENDED` `MISSED` `DECLINED` `CANCELED` `FAILED`. */
    val status: CallStatus = CallStatus.ENDED,
    /** **Hech qachon `null` emas** — javob berilmagan qo'ng'iroqda `0`. */
    val durationMs: Int = 0,
    val endReason: CallEndReason? = null,
) {
    /** Faqat javobsiz qo'ng'iroq o'qilmagan hisoblanadi (`handoff/09-CALLS-REST.md` §4). */
    val missed: Boolean get() = status == CallStatus.MISSED
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
    /**
     * Ovozli xabarning to'lqin shakli (`0..100`) — serverda hisoblanadi.
     *
     * ⚠️ Nuqtalar soni **serverga bog'liq** va 2026-08-03 da 48 dan **100** ga oshdi.
     * Shuning uchun uni hech qayerda qotirib qo'ymang: chizuvchi `size` ni ro'yxatning
     * o'zidan oladi (`ChatMediaUi`), aks holda eski keshdagi 48 nuqtali qatorlar
     * yangi kod bilan noto'g'ri chizilardi.
     */
    val waveform: List<Int> = emptyList(),
    /** `FILE` uchun asl nom. */
    val fileName: String? = null,
    /** Rasm yuklanguncha ko'rsatiladigan xira o'rinbosar. */
    val blurHash: String? = null,
    /** `true` — ovozsiz va takrorlanuvchi qilib o'ynatiladi (GIF va GIF'dan o'girilgan MP4). */
    val isAnimated: Boolean = false,
    /**
     * Ovozli xabarning matnga o'girilgani. **Bugun doim `null`** — maydon spec'da
     * transkripsiyani keyin yoqish klient o'zgarishisiz bo'lsin deb zaxiralangan.
     */
    val transcript: String? = null,
    /**
     * Videoning muqobil sifatlari (tarmoq kengligiga qarab tanlash uchun). **Bugun doim
     * bo'sh** — to'lguncha [url] o'ynatiladi.
     */
    val variants: List<MediaVariant> = emptyList(),
) {
    /** Ko'rsatish uchun eng arzon havola — ro'yxatda kichigi, ochilganda to'lig'i. */
    val previewUrl: String get() = thumbUrl ?: url

    /** Nisbat ma'lum bo'lmasa `null` — chaqiruvchi o'zining odatiy nisbatini qo'yadi. */
    val aspectRatio: Float? get() = if (width > 0 && height > 0) width.toFloat() / height else null

    /** Video hali transkodlanmoqda — poster kadr bor, o'zi hali yo'q. */
    val processing: Boolean get() = status == MediaStatus.PROCESSING

    /** Dumaloq chiziladigan video xabar — nisbat 1:1 va boshqaruv paneli yo'q. */
    val isVideoNote: Boolean get() = kind == MediaKind.VIDEO_NOTE
}

/**
 * Videoning bitta muqobil sifati (`AttachmentDto.variants` elementi).
 *
 * Server hozircha ularni to'ldirmaydi; ro'yxat bo'sh bo'lsa [Attachment.url] o'ynatiladi.
 */
data class MediaVariant(
    val url: String,
    /** Kadr balandligi (`720`, `1080`…). */
    val height: Int = 0,
    /** Bit tezligi (bit/sek). */
    val bitrate: Int = 0,
)

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
    /**
     * Bu **dumaloq video xabar** (`VIDEO_NOTE`) mi.
     *
     * Faqat bitta bayroq, chunki oqim bir xil: yozib olish → tayyorlash → yuklash →
     * `message:send`. Farq qiladigan uchta narsa ham shu bayroqdan chiqadi:
     * `kind = VIDEO_NOTE`, `type = VIDEO_NOTE` va **izoh yo'q** (server matnni qabul
     * qilmaydi). Kvadratlik esa tayyorlashda ta'minlanadi — aks holda
     * `422 MEDIA_NOT_SQUARE`.
     */
    val videoNote: Boolean = false,
)
