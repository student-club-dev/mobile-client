package dev.feature.chat.data.mapper

import dev.core.database.sql.ConversationEntity
import dev.core.database.sql.MessageEntity
import dev.core.network.generated.model.AttachmentDto
import dev.core.network.generated.model.MessageCallDto
import dev.core.network.generated.model.MessageDto
import dev.core.network.generated.model.MessageStickerDto
import dev.core.network.generated.model.ReplyToDto
import dev.core.network.media.MediaUrl
import dev.feature.chat.data.realtime.WsAttachment
import dev.feature.chat.data.realtime.WsCall
import dev.feature.chat.data.realtime.WsMessage
import dev.feature.chat.data.realtime.WsReplyTo
import dev.feature.chat.data.realtime.WsSticker
import dev.feature.chat.domain.model.Attachment
import dev.feature.calls.domain.model.CallEndReason
import dev.feature.calls.domain.model.CallMedia
import dev.feature.calls.domain.model.CallStatus
import dev.feature.chat.domain.model.Conversation
import dev.feature.chat.domain.model.ConversationItem
import dev.feature.chat.domain.model.ConversationType
import dev.feature.chat.domain.model.MediaKind
import dev.feature.chat.domain.model.MediaStatus
import dev.feature.chat.domain.model.Message
import dev.feature.chat.domain.model.MessageCall
import dev.feature.chat.domain.model.MessageStatus
import dev.feature.chat.domain.model.MessageSticker
import dev.feature.chat.domain.model.MessageType
import dev.feature.chat.domain.model.Quote
import dev.feature.chat.domain.model.ReplyTo
import dev.feature.connections.domain.model.Gender
import dev.feature.connections.domain.model.StudentSummary
import kotlinx.datetime.Instant

// --- DB → domen -------------------------------------------------------------------------

internal fun ConversationEntity.toDomain(): ConversationItem = ConversationItem(
    conversation = Conversation(
        id = id,
        type = parseEnum(type, ConversationType.DIRECT),
        lastMessageAt = lastMessageAt?.let(Instant::fromEpochMilliseconds),
    ),
    other = StudentSummary(
        id = otherId,
        username = otherUsername,
        fullName = otherFullName,
        avatarUrl = otherAvatarUrl,
        universityId = otherUniversityId,
        // Noma'lum qiymat MALE bo'lib qolmasin — mos kelmasa ko'rsatilmaydi.
        gender = Gender.entries.firstOrNull { it.name == otherGender },
        courseYear = otherCourseYear,
        online = otherOnline != 0L,
        lastSeenAt = otherLastSeenAt?.let(Instant::fromEpochMilliseconds),
    ),
    // Ro'yxat qatorida to'liq xabar kerak emas — matn, turi va kim yozgani yetarli.
    // O'chirilgan xabar ham qator sifatida qoladi (tanasi bo'sh, `deletedAt` to'ldirilgan),
    // aks holda ro'yxatda eski matn ko'rinib turardi.
    lastMessage = if (lastMessageDeleted != 0L) {
        lastMessageStub("", deleted = true)
    } else {
        lastMessageBody?.let { lastMessageStub(it, deleted = false) }
    },
    unreadCount = unreadCount.toInt(),
    otherReadSeq = otherReadSeq.toInt(),
    otherDeliveredSeq = otherDeliveredSeq.toInt(),
    archived = archived != 0L,
)

/**
 * Ro'yxatdagi qisqa ko'rinish uchun "xabar".
 *
 * Turi endi **serverdan** keladi (`lastMessageType`) — media xabarda tana bo'sh, ya'ni
 * usiz ro'yxatda "📷 Rasm" o'rniga bo'sh qator ko'rinardi. Eski keshdagi qatorlarda ustun
 * `NULL`; o'shanda tur tanadan taxmin qilinadi (qarang [MediaContent]).
 */
private fun ConversationEntity.lastMessageStub(text: String, deleted: Boolean): Message = Message(
    id = "$id-last",
    conversationId = id,
    senderId = lastMessageSenderId.orEmpty(),
    seq = 0,
    body = text,
    createdAt = Instant.fromEpochMilliseconds(lastMessageAt ?: 0L),
    type = lastMessageType?.let { parseEnum(it, MessageType.TEXT) } ?: MediaContent.detect(text),
    deletedAt = if (deleted) Instant.fromEpochMilliseconds(lastMessageAt ?: 0L) else null,
)

/**
 * [origin] — API origin'i (`https://api.studentclub.uz`); biriktirma havolalari shu bilan
 * **to'liq** holga keltiriladi.
 *
 * ⚠️ Backend havolani nisbiy (`/v1/media/{id}/raw`) qaytaradi. Rasmda bu bilinmasdi —
 * Coil o'z `Mapper` ida tuzatadi; **video va ovoz** esa Coil'dan o'tmaydi va pleyer nisbiy
 * yo'lni local fayl deb ochishga urinib, "fayl topilmadi" bilan yiqilardi (foydalanuvchi
 * esa faqat qora ekran ko'rardi). Shuning uchun tuzatish o'qish yo'lida: **keshda yotgan
 * eski qatorlar ham** to'g'ri ko'rinadi.
 */
internal fun MessageEntity.toDomain(origin: String): Message = Message(
    id = id,
    conversationId = conversationId,
    senderId = senderId,
    seq = seq.toInt(),
    body = body,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    type = parseEnum(type, MessageType.TEXT),
    status = parseEnum(status, MessageStatus.SENT),
    clientMsgId = clientMsgId,
    deletedAt = deletedAt?.let(Instant::fromEpochMilliseconds),
    attachment = attachmentUrl?.let { url ->
        Attachment(
            id = attachmentId,
            url = MediaUrl.normalize(url, origin) ?: url,
            thumbUrl = attachmentThumbUrl?.let { MediaUrl.normalize(it, origin) },
            kind = attachmentKind?.let { parseEnum(it, MediaKind.IMAGE) } ?: MediaKind.IMAGE,
            status = attachmentStatus?.let { parseEnum(it, MediaStatus.READY) } ?: MediaStatus.READY,
            mimeType = attachmentMime,
            width = attachmentWidth.toInt(),
            height = attachmentHeight.toInt(),
            sizeBytes = attachmentSizeBytes ?: 0L,
            durationMs = attachmentDurationMs?.toInt() ?: 0,
            waveform = decodeWaveform(attachmentWaveform),
            fileName = attachmentFileName,
            blurHash = attachmentBlurHash,
            isAnimated = attachmentIsAnimated == 1L,
            transcript = attachmentTranscript,
        )
    },
    // `CALL` xabarning tafsiloti — surat, ya'ni keshdagi ustunlar yagona manba.
    call = callId?.let { id ->
        MessageCall(
            callId = id,
            media = parseEnum(callMedia, CallMedia.AUDIO),
            status = parseEnum(callStatus, CallStatus.ENDED),
            durationMs = callDurationMs?.toInt() ?: 0,
            endReason = callEndReason?.let { reason -> parseEnumOrNull<CallEndReason>(reason) },
        )
    },
    // `stickerId` YOKI `stickerUrl` — ikkalasidan biri yetadi. Qidiruvdan yuborilgan
    // provayder stikerining optimistik qatorida `stickerId` YO'Q (u server katalogida yo'q,
    // `handoff/06-STICKER-SEARCH.md` §2), faqat havolasi bor — shartga uni ham qo'shmasak,
    // server javobi kelgunicha pufak bo'sh turardi.
    // Sitata — keshdagi **surat**, nishonning joriy holati emas. `replyToId` bo'lmasa ham
    // ko'rsatiladi: nishon tozalangan bo'lsa id yo'q, matn esa qoladi (§5.2).
    replyTo = if (replyToSenderId == null && replyToPreview == null && replyToQuoteText == null) {
        null
    } else {
        ReplyTo(
            id = replyToId,
            seq = replyToSeq?.toInt() ?: 0,
            senderId = replyToSenderId.orEmpty(),
            senderName = replyToSenderName.orEmpty(),
            type = replyToType?.let { parseEnum(it, MessageType.TEXT) } ?: MessageType.TEXT,
            preview = replyToPreview,
            quote = replyToQuoteText?.let { text ->
                Quote(text = text, offset = replyToQuoteOffset?.toInt() ?: 0)
            },
            originalDeleted = replyToOriginalDeleted == 1L,
        )
    },
    sticker = (stickerId ?: stickerUrl)?.let {
        MessageSticker(
            id = stickerId.orEmpty(),
            emoji = stickerEmoji.orEmpty(),
            // Stiker tasviri o'z serverimizdan ham, provayderdan ham kelishi mumkin —
            // `normalize` begona (to'liq) havolaga tegmaydi.
            url = stickerUrl?.let { url -> MediaUrl.normalize(url, origin) },
        )
    },
    albumId = albumId,
)

// --- Server javoblari → kesh ustunlari ---------------------------------------------------

/**
 * Serverdan kelgan xabarning keshga yoziladigan ko'rinishi.
 *
 * `MessageEntity` ning o'zi ishlatilmaydi: bu yerda **standart qiymatlar** kerak (optimistik
 * qator o'nlab bo'sh ustunni qo'lda sanab o'tirmasin), generatsiya qilingan klassda esa
 * ular yo'q.
 */
internal data class MessageRow(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val seq: Long,
    val type: String,
    val body: String,
    val createdAt: Long,
    val clientMsgId: String?,
    val status: String = MessageStatus.SENT.name,
    val deletedAt: Long? = null,
    val attachmentId: String? = null,
    val attachmentUrl: String? = null,
    val attachmentThumbUrl: String? = null,
    val attachmentWidth: Long = 0,
    val attachmentHeight: Long = 0,
    val attachmentKind: String? = null,
    val attachmentStatus: String? = null,
    val attachmentMime: String? = null,
    val attachmentSizeBytes: Long? = null,
    val attachmentDurationMs: Long? = null,
    val attachmentWaveform: String? = null,
    val attachmentFileName: String? = null,
    val attachmentBlurHash: String? = null,
    val attachmentIsAnimated: Long? = null,
    val attachmentTranscript: String? = null,
    val stickerId: String? = null,
    val stickerEmoji: String? = null,
    val stickerUrl: String? = null,
    val albumId: String? = null,
    val replyToId: String? = null,
    val replyToSeq: Long? = null,
    val replyToSenderId: String? = null,
    val replyToSenderName: String? = null,
    val replyToType: String? = null,
    val replyToPreview: String? = null,
    val replyToQuoteText: String? = null,
    val replyToQuoteOffset: Long? = null,
    val replyToOriginalDeleted: Long? = null,
    val callId: String? = null,
    val callMedia: String? = null,
    val callStatus: String? = null,
    val callDurationMs: Long? = null,
    val callEndReason: String? = null,
)

/** Keshdagi qator → yozish uchun qator (ack kelganda `id`/`seq` almashtiriladi). */
internal fun MessageEntity.toRow(): MessageRow = MessageRow(
    id = id,
    conversationId = conversationId,
    senderId = senderId,
    seq = seq,
    type = type,
    body = body,
    createdAt = createdAt,
    clientMsgId = clientMsgId,
    status = status,
    deletedAt = deletedAt,
    attachmentId = attachmentId,
    attachmentUrl = attachmentUrl,
    attachmentThumbUrl = attachmentThumbUrl,
    attachmentWidth = attachmentWidth,
    attachmentHeight = attachmentHeight,
    attachmentKind = attachmentKind,
    attachmentStatus = attachmentStatus,
    attachmentMime = attachmentMime,
    attachmentSizeBytes = attachmentSizeBytes,
    attachmentDurationMs = attachmentDurationMs,
    attachmentWaveform = attachmentWaveform,
    attachmentFileName = attachmentFileName,
    attachmentBlurHash = attachmentBlurHash,
    attachmentIsAnimated = attachmentIsAnimated,
    attachmentTranscript = attachmentTranscript,
    stickerId = stickerId,
    stickerEmoji = stickerEmoji,
    stickerUrl = stickerUrl,
    albumId = albumId,
    replyToId = replyToId,
    replyToSeq = replyToSeq,
    replyToSenderId = replyToSenderId,
    replyToSenderName = replyToSenderName,
    replyToType = replyToType,
    replyToPreview = replyToPreview,
    replyToQuoteText = replyToQuoteText,
    replyToQuoteOffset = replyToQuoteOffset,
    replyToOriginalDeleted = replyToOriginalDeleted,
    callId = callId,
    callMedia = callMedia,
    callStatus = callStatus,
    callDurationMs = callDurationMs,
    callEndReason = callEndReason,
)

/**
 * REST javobidagi xabar → kesh qatori.
 *
 * Tur endi **serverdan** keladi (`MessageDto.type`) — 2026-07-29 gacha u doim `TEXT` edi va
 * rasm/stiker tanadan taxmin qilinardi; endi evristika kerak emas (`handoff/03-WEBSOCKET.md`).
 *
 * [fallbackClientMsgId] — biz endi yuborgan, javobi kelgan xabar uchun zaxira: server
 * `clientMsgId` ni qaytarmasa ham optimistik nusxa topilishi kerak.
 */
internal fun MessageDto.toRow(fallbackClientMsgId: String? = null): MessageRow = MessageRow(
    id = id,
    conversationId = conversationId,
    senderId = senderId,
    seq = seq.toLong(),
    // `type` — DTO'da ataylab `String` (spec'ning `lenientEnums` ro'yxati): server enum'ni
    // kengaytirsa noma'lum qiymat butun javobni yiqitmasin. Domenga o'girish `parseEnum` da.
    type = type,
    body = body.orEmpty(),
    createdAt = createdAt.toEpochMilliseconds(),
    clientMsgId = clientMsgId ?: fallbackClientMsgId,
    deletedAt = deletedAt?.toEpochMilliseconds(),
    albumId = albumId,
).withAttachment(attachment).withSticker(sticker).withReplyTo(replyTo).withCall(call)

internal fun WsMessage.toRow(): MessageRow = MessageRow(
    id = id,
    conversationId = conversationId,
    senderId = senderId,
    seq = seq.toLong(),
    type = type,
    body = body.orEmpty(),
    createdAt = parseInstant(createdAt),
    clientMsgId = clientMsgId,
    deletedAt = parseInstant(deletedAt).takeIf { it > 0L },
    albumId = albumId,
).withAttachment(attachment).withSticker(sticker).withReplyTo(replyTo).withCall(call)

// --- Sitata (javob berilgan xabarning surati) --------------------------------------------

/**
 * `MessageDto.replyTo` → kesh ustunlari.
 *
 * ⚠️ Surat **o'zgarmas**: nishon keyin o'chirilsa ham matni shu qatorda qoladi. Shuning
 * uchun bu yerda nishonni id bo'yicha izlab, uning joriy holatini olib qo'yish YO'Q —
 * server bergan nusxa aynan shu holicha yoziladi.
 */
private fun MessageRow.withReplyTo(reply: ReplyToDto?): MessageRow {
    if (reply == null) return this
    return copy(
        replyToId = reply.id,
        replyToSeq = reply.seq.toLong(),
        replyToSenderId = reply.senderId,
        replyToSenderName = reply.senderName,
        replyToType = reply.type,
        replyToPreview = reply.preview,
        replyToQuoteText = reply.quote?.text,
        replyToQuoteOffset = reply.quote?.offset?.toLong(),
        replyToOriginalDeleted = if (reply.originalDeleted) 1L else 0L,
    )
}

/** WS `message:new` dagi o'sha surat — REST bilan bir xil shakl, faqat qo'lda yozilgan tip. */
private fun MessageRow.withReplyTo(reply: WsReplyTo?): MessageRow {
    if (reply == null) return this
    return copy(
        replyToId = reply.id,
        replyToSeq = reply.seq?.toLong() ?: 0L,
        replyToSenderId = reply.senderId,
        replyToSenderName = reply.senderName,
        replyToType = reply.type,
        replyToPreview = reply.preview,
        replyToQuoteText = reply.quote?.text,
        replyToQuoteOffset = reply.quote?.offset?.toLong(),
        replyToOriginalDeleted = if (reply.originalDeleted == true) 1L else 0L,
    )
}

// --- Qo'ng'iroq yozuvi (`MessageDto.call`) -----------------------------------------------

/**
 * `type = CALL` xabarining ustunlari.
 *
 * ⚠️ `durationMs` **hech qachon `null` emas** — javob berilmagan qo'ng'iroqda `0`
 * (`handoff/09-CALLS-DEVIATIONS.md` §10). Shuning uchun bu yerda `?: 0` yo'q: server
 * qiymatni doim yuboradi va uni "noma'lum" ga aylantirish pufakchada `0:00` o'rniga
 * bo'sh joy qoldirardi.
 */
private fun MessageRow.withCall(dto: MessageCallDto?): MessageRow =
    if (dto == null) this else copy(
        callId = dto.callId,
        callMedia = dto.media,
        callStatus = dto.status,
        callDurationMs = dto.durationMs.toLong(),
        callEndReason = dto.endReason,
    )

/** WS `message:new` dagi o'sha yozuv — bir xil shakl, qo'lda yozilgan tip. */
private fun MessageRow.withCall(ws: WsCall?): MessageRow =
    if (ws == null) this else copy(
        callId = ws.callId,
        callMedia = ws.media,
        callStatus = ws.status,
        callDurationMs = ws.durationMs.toLong(),
        callEndReason = ws.endReason,
    )

// --- Biriktirma --------------------------------------------------------------------------

/**
 * Biriktirmaning keshdagi ustunlari. REST va WS bir xil shaklni yuboradi, lekin
 * generatsiya qilingan DTO va qo'lda yozilgan WS modeli ikki xil tip — shuning uchun
 * ikkalasi ham shu oraliq ko'rinishga o'giriladi.
 */
internal data class AttachmentColumns(
    val id: String,
    val url: String,
    val thumbUrl: String?,
    val width: Long,
    val height: Long,
    val kind: String,
    val status: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val durationMs: Long?,
    val waveform: String?,
    val fileName: String?,
    val blurHash: String?,
    val isAnimated: Long,
    /** Ovozli xabarning matni — server maydonni zaxiralagan, bugun doim `null`. */
    val transcript: String? = null,
)

internal fun AttachmentDto.toColumns(): AttachmentColumns = AttachmentColumns(
    id = id,
    url = url,
    thumbUrl = thumbUrl,
    width = width?.toLong() ?: 0L,
    height = height?.toLong() ?: 0L,
    kind = kind,
    status = status,
    mimeType = mimeType,
    sizeBytes = sizeBytes.toLong(),
    durationMs = durationMs?.toLong(),
    waveform = encodeWaveform(waveform),
    fileName = fileName,
    blurHash = blurHash,
    isAnimated = if (isAnimated) 1L else 0L,
    transcript = transcript,
)

internal fun WsAttachment.toColumns(): AttachmentColumns = AttachmentColumns(
    id = id,
    url = url,
    thumbUrl = thumbUrl,
    width = width?.toLong() ?: 0L,
    height = height?.toLong() ?: 0L,
    kind = kind,
    status = status,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    durationMs = durationMs?.toLong(),
    waveform = encodeWaveform(waveform),
    fileName = fileName,
    blurHash = blurHash,
    isAnimated = if (isAnimated) 1L else 0L,
    transcript = transcript,
)

private fun MessageRow.withAttachment(columns: AttachmentColumns?): MessageRow =
    if (columns == null) this else copy(
        attachmentId = columns.id,
        attachmentUrl = columns.url,
        attachmentThumbUrl = columns.thumbUrl,
        attachmentWidth = columns.width,
        attachmentHeight = columns.height,
        attachmentKind = columns.kind,
        attachmentStatus = columns.status,
        attachmentMime = columns.mimeType,
        attachmentSizeBytes = columns.sizeBytes,
        attachmentDurationMs = columns.durationMs,
        attachmentWaveform = columns.waveform,
        attachmentFileName = columns.fileName,
        attachmentBlurHash = columns.blurHash,
        attachmentIsAnimated = columns.isAnimated,
        attachmentTranscript = columns.transcript,
    )

private fun MessageRow.withAttachment(dto: AttachmentDto?): MessageRow = withAttachment(dto?.toColumns())

private fun MessageRow.withAttachment(dto: WsAttachment?): MessageRow = withAttachment(dto?.toColumns())

private fun MessageRow.withSticker(dto: MessageStickerDto?): MessageRow =
    if (dto == null) this else copy(stickerId = dto.id, stickerEmoji = dto.emoji.orEmpty(), stickerUrl = dto.url)

private fun MessageRow.withSticker(dto: WsSticker?): MessageRow =
    if (dto == null) this else copy(stickerId = dto.id, stickerEmoji = dto.emoji.orEmpty(), stickerUrl = dto.url)

/**
 * To'lqin shakli — `"12,40,88"`. JSON emas: qiymatlar oddiy sonlar va SQLDelight'da massiv
 * ustuni yo'q, ya'ni serializator qo'shishning ma'nosi yo'q.
 */
internal fun encodeWaveform(values: List<Int>): String? =
    values.takeIf { it.isNotEmpty() }?.joinToString(",")

internal fun decodeWaveform(raw: String?): List<Int> =
    raw?.split(',')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()

/**
 * ISO-8601 → epoch ms. Server doim to'g'ri sana yuboradi, lekin parse xatosi butun suhbatni
 * yiqitmasin — zaxira `0` (xabar yo'qolmaydi, faqat vaqti noto'g'ri ko'rinadi).
 */
internal fun parseInstant(value: String?): Long =
    value?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() } ?: 0L

/**
 * Sim/kesh qiymati → domen enum'i. Noma'lum qiymat [default] ga tushadi.
 *
 * Bu butun chat qatlamining enum eshigi: DTO'lar ataylab `String` bo'lib generatsiya
 * qilinadi (`lenientEnums`, `dev/api-client-generator/build.gradle.kts` qadam 11), chunki
 * kotlinx.serialization noma'lum enum qiymatida **butun javobni** yiqitadi — `MessageType`
 * ga `CALL` qo'shilishi aynan shu tarzda eski klientlarda chat ekranini o'ldirdi.
 */
internal inline fun <reified T : Enum<T>> parseEnum(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default

/** [parseEnum] ning nullable varianti — maydonning o'zi ixtiyoriy bo'lganda. */
internal inline fun <reified T : Enum<T>> parseEnumOrNull(name: String?): T? =
    enumValues<T>().firstOrNull { it.name == name }
