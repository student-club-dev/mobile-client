package dev.feature.chat.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.core.common.AppDispatchers
import dev.core.common.Resource
import dev.core.common.auth.TokenStore
import dev.core.common.error.AppException
import dev.core.common.errorOf
import dev.core.database.sql.ChatQueries
import dev.core.database.sql.StudentClubDatabase
import dev.core.network.generated.model.ConversationListItemDto
import dev.core.network.media.ChatMediaKind
import dev.feature.chat.data.mapper.AttachmentColumns
import dev.feature.chat.data.mapper.MessageRow
import dev.feature.chat.data.mapper.SendPayload
import dev.feature.chat.data.mapper.parseEnum
import dev.feature.chat.data.mapper.parseInstant
import dev.feature.chat.data.mapper.toColumns
import dev.feature.chat.data.mapper.toDomain
import dev.feature.chat.data.mapper.toJson
import dev.feature.chat.data.mapper.toRow
import dev.feature.chat.data.realtime.ChatSocket
import dev.feature.chat.data.remote.ChatRemoteDataSource
import dev.feature.chat.domain.model.ConversationItem
import dev.feature.chat.domain.model.GifRef
import dev.feature.chat.domain.model.Message
import dev.feature.chat.domain.model.MessageStatus
import dev.feature.chat.domain.model.MessageType
import dev.feature.chat.domain.model.OutgoingImage
import dev.feature.chat.domain.model.Sticker
import dev.feature.chat.domain.model.UnreadCount
import dev.feature.chat.domain.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlin.random.Random

/**
 * Chat repository'si — REST + WebSocket ustidagi **offline-first** qatlam.
 *
 * Oqim: ikkala manba ham SQLDelight keshiga yozadi, UI esa faqat keshni kuzatadi. Shuning
 * uchun ekran tarmoqqa bog'liq emas va WS uzilib-ulanganda qayta chizilmaydi.
 *
 * Tartib o'qi — **`seq`** (`handoff/chat.md`): tarix `?before=`, qayta ulanish `?after=`,
 * o'qildi kursori ham shu.
 *
 * Media oqimi (2026-07-29 kontrakti): `POST /v1/media/chat-upload` → `mediaId` →
 * `message:send { type, mediaId }`. Ilgari fayl havolasi xabar **tanasi** sifatida
 * yuborilardi — bu vaqtinchalik hiyla edi va endi olib tashlangan.
 */
class ChatRepositoryImpl(
    private val db: StudentClubDatabase,
    private val dispatchers: AppDispatchers,
    private val remote: ChatRemoteDataSource,
    private val socket: ChatSocket,
    private val tokenStore: TokenStore,
) : ChatRepository {

    private val q: ChatQueries get() = db.chatQueries

    /**
     * Yuklanayotgan rasmlarning local nusxasi (xabar id → baytlar). Faqat xotirada:
     * yuklash bir necha soniya davom etadi, shu vaqt ichida ekranda tanlangan faylning
     * o'zi ko'rinadi, keyin serverdagi havolaga almashadi.
     */
    private val localImages = MutableStateFlow<Map<String, ByteArray>>(emptyMap())

    /** WS hodisalarini keshga yozuvchi kolektorlar shu qamrovda yashaydi. */
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private var collectors: Job? = null

    /** "Yozmoqda" — suhbat id'lari. `typing:stop` yo'qolsa taymer o'zi tozalaydi. */
    private val typingIds = MutableStateFlow<Set<String>>(emptySet())
    private val typingTimers = mutableMapOf<String, Job>()

    /** Joriy talaba — JWT `sub`. Kim yozganini shunga qarab aniqlaymiz. */
    private val currentUserId: String? get() = tokenStore.userId()

    // --- Kuzatish ------------------------------------------------------------------------

    override fun observeConversations(): Flow<List<ConversationItem>> =
        q.selectConversations().asFlow().mapToList(dispatchers.io).map { rows -> rows.map { it.toDomain() } }

    override fun observeArchivedConversations(): Flow<List<ConversationItem>> =
        q.selectArchivedConversations().asFlow().mapToList(dispatchers.io).map { rows -> rows.map { it.toDomain() } }

    override fun observeConversation(conversationId: String): Flow<ConversationItem?> =
        q.selectConversation(conversationId).asFlow().mapToOneOrNull(dispatchers.io).map { it?.toDomain() }

    override fun observeMessages(conversationId: String): Flow<List<Message>> =
        q.selectMessages(conversationId).asFlow().mapToList(dispatchers.io).map { rows -> rows.map { it.toDomain() } }

    override fun observeTyping(conversationId: String): Flow<Boolean> =
        typingIds.map { conversationId in it }

    override fun observeRealtimeConnected(): Flow<Boolean> = socket.connected

    override fun observeLocalImages(): Flow<Map<String, ByteArray>> = localImages

    // --- Real-time -----------------------------------------------------------------------

    override fun connectRealtime() {
        if (collectors?.isActive != true) collectors = scope.launch { collectEvents() }
        socket.start()
    }

    override fun disconnectRealtime() {
        socket.stop()
        collectors?.cancel()
        collectors = null
        typingTimers.values.forEach { it.cancel() }
        typingTimers.clear()
        typingIds.value = emptySet()
    }

    /** WS hodisalari → kesh. Har biri alohida korutinada: biri yiqilsa qolganlari ishlayveradi. */
    private fun CoroutineScope.collectEvents() {
        launch {
            socket.newMessages.collect { event ->
                val row = event.message.toRow()
                withContext(dispatchers.io) {
                    q.transaction {
                        // Server xabarni IKKALA tomonga yuboradi — o'zimizniki bo'lib qaytsa
                        // optimistik nusxa [insertOwn] ichida olib tashlanadi, aks holda
                        // ekranda ikki marta ko'rinardi.
                        q.insertOwn(row, currentUserId)
                        // O'chirilgan xabar o'qilmaganlar sanog'iga kirmaydi (server ham
                        // shunday hisoblaydi), shuning uchun tombstone hech nimani surmaydi.
                        val incoming = if (row.senderId != currentUserId && row.deletedAt == null) 1L else 0L
                        q.touchConversation(row.body, row.senderId, row.type, row.createdAt, incoming, row.conversationId)
                        if (row.deletedAt != null) q.setLastMessageDeleted(row.conversationId)
                    }
                }
                // Suhbat hali keshda yo'q (yangi suhbatdosh yozdi) — butun ro'yxat o'rniga
                // bitta qatorni olamiz (`GET /v1/conversations/{id}`, §4b).
                if (!conversationCached(row.conversationId)) refreshConversation(row.conversationId)
            }
        }
        launch {
            // `message:deleted` IKKALA a'zoga keladi — o'zimiz o'chirgan bo'lsak ham,
            // suhbatdosh o'chirgan bo'lsa ham bir xil yo'ldan yuriladi.
            socket.deletedMessages.collect { event ->
                applyDeleted(event.conversationId, event.messageId, event.seq)
            }
        }
        launch {
            // `media:ready` — video transkodlash tugadi (yoki yiqildi). Xabar allaqachon
            // ro'yxatda turibdi, faqat biriktirmasi almashadi.
            socket.mediaReady.collect { event ->
                val columns = event.attachment?.toColumns() ?: return@collect
                withContext(dispatchers.io) { q.setAttachment(columns, event.messageId) }
            }
        }
        launch {
            socket.readCursors.collect { cursor ->
                // Faqat SUHBATDOSHNING kursori qiziq — o'zimizniki allaqachon localda.
                if (cursor.byStudentId == null || cursor.byStudentId == currentUserId) return@collect
                withContext(dispatchers.io) { q.setOtherReadSeq(cursor.seq.toLong(), cursor.conversationId) }
            }
        }
        launch {
            socket.deliveredCursors.collect { cursor ->
                // O'z qurilmamiz yuborgan "yetkazildi" qaytib kelsa — e'tiborsiz.
                if (cursor.byStudentId == null || cursor.byStudentId == currentUserId) return@collect
                withContext(dispatchers.io) {
                    q.setOtherDeliveredSeq(cursor.seq.toLong(), cursor.conversationId)
                }
            }
        }
        launch {
            socket.presence.collect { presence ->
                withContext(dispatchers.io) {
                    q.setPresence(
                        if (presence.online) 1L else 0L,
                        // `online = true` bo'lganda server `lastSeenAt` ni yubormaydi.
                        presence.lastSeenAt?.let { parseInstant(it) },
                        presence.studentId,
                    )
                }
            }
        }
        launch {
            socket.typing.collect { event ->
                if (event.studentId == currentUserId) return@collect
                if (event.isTyping) startTyping(event.conversationId) else stopTyping(event.conversationId)
            }
        }
    }

    /**
     * `typing:stop` yo'qolib qolsa indikator abadiy osilib qolmasin — har `typing` hodisasi
     * taymerni qaytadan boshlaydi (`handoff/chat.md`: ~5 soniya).
     */
    private fun startTyping(conversationId: String) {
        typingIds.value = typingIds.value + conversationId
        typingTimers.remove(conversationId)?.cancel()
        typingTimers[conversationId] = scope.launch {
            delay(TYPING_TIMEOUT_MS)
            stopTyping(conversationId)
        }
    }

    private fun stopTyping(conversationId: String) {
        typingTimers.remove(conversationId)?.cancel()
        typingIds.value = typingIds.value - conversationId
    }

    // --- Sinxronlash ---------------------------------------------------------------------

    override suspend fun refreshConversations(): Resource<Unit> =
        when (val res = remote.conversations()) {
            is Resource.Success -> {
                withContext(dispatchers.io) {
                    q.transaction { res.data.items.forEach { storeConversation(it) } }
                }
                Resource.Success(Unit)
            }
            is Resource.Error -> res
            Resource.Loading -> Resource.Success(Unit)
        }

    /** Bitta suhbat qatorini yangilaydi — butun ro'yxatni yuklamaslik uchun (§4b). */
    private suspend fun refreshConversation(conversationId: String): Resource<Unit> =
        when (val res = remote.conversation(conversationId)) {
            is Resource.Success -> {
                withContext(dispatchers.io) { q.transaction { storeConversation(res.data) } }
                Resource.Success(Unit)
            }
            // Endpoint hali yo'q/xato bo'lsa eski yo'l ishlaydi: butun ro'yxat.
            is Resource.Error -> refreshConversations()
            Resource.Loading -> Resource.Success(Unit)
        }

    /**
     * Serverdan kelgan suhbat qatorini keshga yozadi. Tranzaksiya ichida chaqiriladi.
     *
     * Local ustunlarga (`archived`) va kursorlarga tegilmaydi — ular WS orqali allaqachon
     * oldinga ketgan bo'lishi mumkin.
     */
    private fun storeConversation(item: ConversationListItemDto) {
        val me = currentUserId
        val last = item.lastMessage
        val lastDeleted = if (last?.deletedAt != null) 1L else 0L
        // Uchala kursor ham serverdan keladi (spec 2026-07-28) — avvalgi "unread == 0 bo'lsa
        // hammasi o'qilgan" taxmini kerak emas, va ✓/✓✓ holati ilova qayta ochilganda ham
        // tiklanadi.
        q.insertConversationIfNew(
            id = item.conversation.id,
            type = item.conversation.type.name,
            lastMessageAt = item.conversation.lastMessageAt?.toEpochMilliseconds(),
            otherId = item.other.id,
            otherUsername = item.other.username,
            otherFullName = item.other.fullName,
            otherAvatarUrl = item.other.avatarUrl,
            otherOnline = if (item.other.online) 1L else 0L,
            otherLastSeenAt = item.other.lastSeenAt?.toEpochMilliseconds(),
            otherUniversityId = item.other.universityId,
            otherGender = item.other.gender?.name,
            otherCourseYear = item.other.courseYear?.value,
            lastMessageBody = last?.body,
            lastMessageSenderId = last?.senderId,
            lastMessageType = last?.type?.name,
            lastMessageDeleted = lastDeleted,
            unreadCount = item.unreadCount.toLong(),
            lastReadSeq = item.myReadSeq.toLong(),
            otherReadSeq = item.peerReadSeq.toLong(),
            otherDeliveredSeq = item.peerDeliveredSeq.toLong(),
        )
        q.updateConversation(
            type = item.conversation.type.name,
            lastMessageAt = item.conversation.lastMessageAt?.toEpochMilliseconds(),
            otherId = item.other.id,
            otherUsername = item.other.username,
            otherFullName = item.other.fullName,
            otherAvatarUrl = item.other.avatarUrl,
            otherOnline = if (item.other.online) 1L else 0L,
            otherLastSeenAt = item.other.lastSeenAt?.toEpochMilliseconds(),
            otherUniversityId = item.other.universityId,
            otherGender = item.other.gender?.name,
            otherCourseYear = item.other.courseYear?.value,
            lastMessageBody = last?.body,
            lastMessageSenderId = last?.senderId,
            lastMessageType = last?.type?.name,
            lastMessageDeleted = lastDeleted,
            unreadCount = item.unreadCount.toLong(),
            lastReadSeq = item.myReadSeq.toLong(),
            otherReadSeq = item.peerReadSeq.toLong(),
            otherDeliveredSeq = item.peerDeliveredSeq.toLong(),
            id = item.conversation.id,
        )
        last?.let { q.insertOwn(it.toRow(), me) }
    }

    override suspend fun unreadCount(): Resource<UnreadCount> = when (val res = remote.unreadCount()) {
        is Resource.Success -> Resource.Success(
            UnreadCount(total = res.data.total, conversations = res.data.conversations),
        )
        is Resource.Error -> res
        Resource.Loading -> Resource.Loading
    }

    override suspend fun openDirect(studentId: String): Resource<String> {
        val res = remote.openDirect(studentId)
        // Yangi suhbat keshda yo'q — ro'yxatni yangilab, ikkinchi tomon ma'lumotini olamiz.
        if (res is Resource.Success) refreshConversation(res.data)
        return res
    }

    override suspend fun loadLatest(conversationId: String): Resource<Unit> {
        val lastSeq = maxSeq(conversationId)
        // Kesh bo'sh → oxirgi sahifa; kesh bor → faqat uzilib qolganlarini yetishib olamiz.
        val res = if (lastSeq > 0) {
            remote.messages(conversationId, after = lastSeq)
        } else {
            remote.messages(conversationId)
        }
        return when (res) {
            is Resource.Success -> {
                storeMessages(res.data.items.map { it.toRow() })
                Resource.Success(Unit)
            }
            is Resource.Error -> res
            Resource.Loading -> Resource.Success(Unit)
        }
    }

    override suspend fun loadOlder(conversationId: String): Resource<Boolean> {
        // Keshda xabar bo'lmasa `before = null` — ya'ni oxirgi sahifa.
        val oldest = withContext(dispatchers.io) {
            q.minSeq(conversationId).executeAsOneOrNull()?.toInt()
        }
        return when (val res = remote.messages(conversationId, before = oldest)) {
            is Resource.Success -> {
                storeMessages(res.data.items.map { it.toRow() })
                // `hasMore` endi aniq: server `size + 1` ta o'qib, ortiqchasini tashlaydi
                // (§17.5). Bo'sh javob esa baribir tugaganini bildiradi.
                Resource.Success(res.data.items.isNotEmpty() && res.data.hasMore)
            }
            is Resource.Error -> res
            Resource.Loading -> Resource.Success(false)
        }
    }

    private suspend fun storeMessages(rows: List<MessageRow>) {
        if (rows.isEmpty()) return
        val me = currentUserId
        withContext(dispatchers.io) { q.transaction { rows.forEach { q.insertOwn(it, me) } } }
    }

    // --- Yuborish ------------------------------------------------------------------------

    override suspend fun send(conversationId: String, body: String): Resource<Unit> =
        sendPayload(conversationId, SendPayload(type = MessageType.TEXT, body = body))

    override suspend fun sendSticker(conversationId: String, sticker: Sticker): Resource<Unit> =
        // Serverdagi stiker `stickerId` bilan ketadi (tana TAQIQLANGAN). Ilovaga kiritilgan
        // ZAXIRA katalogning id'lari esa emojining o'zi — server ularni topmay
        // `422 STICKER_NOT_FOUND` qaytarardi, shuning uchun ular oddiy `TEXT` bo'lib ketadi
        // va ekranda baribir katta chiziladi (`EmojiText`).
        if (sticker.isRemote) {
            sendPayload(
                conversationId,
                SendPayload(
                    type = MessageType.STICKER,
                    stickerId = sticker.id,
                    stickerEmoji = sticker.emoji,
                    stickerUrl = sticker.url,
                ),
            )
        } else {
            sendPayload(conversationId, SendPayload(type = MessageType.TEXT, body = sticker.emoji))
        }

    override suspend fun sendGif(conversationId: String, gif: GifRef): Resource<Unit> =
        sendPayload(
            conversationId,
            SendPayload(
                type = MessageType.GIF,
                // Javobda kelgan obyekt AYNAN shu holda qaytariladi — `toJson()` uni
                // `GifRefDto` serializatsiyasidan quradi, ya'ni maydon nomlari spec bilan
                // bir xil qoladi (qo'lda yig'ilsa bitta xato nom 422 bo'lardi).
                gif = gif.toJson(),
                previewUrl = gif.url,
                previewThumbUrl = gif.thumbUrl,
                previewWidth = gif.width.toLong(),
                previewHeight = gif.height.toLong(),
            ),
        )

    /**
     * Bitta xabarni optimistik qator bilan yuboradi.
     *
     * Qoidalar avval **klientda** tekshiriladi ([SendPayload.validate]) — `422` ni kutib
     * o'tirsak xabar ekranda "yuborilmadi" bo'lib qolardi.
     */
    private suspend fun sendPayload(conversationId: String, payload: SendPayload): Resource<Unit> {
        payload.validate()?.let { return errorOf(AppException.Validation(it)) }
        val me = currentUserId ?: return errorOf(AppException.Unauthorized())

        // Har xabar uchun YANGI kalit: noyoblik `(jo'natuvchi, clientMsgId)` bo'yicha
        // tekshiriladi va suhbat hisobga OLINMAYDI — takroriy kalit boshqa suhbatdagi
        // eski xabarni qaytarib yuborardi.
        val clientMsgId = randomClientMsgId()
        val now = Clock.System.now().toEpochMilliseconds()
        val localId = LOCAL_ID_PREFIX + clientMsgId
        val text = payload.wireBody.orEmpty()

        // Optimistik ko'rinish — foydalanuvchi xabarni darhol ko'radi.
        withContext(dispatchers.io) {
            q.transaction {
                q.insert(
                    MessageRow(
                        id = localId,
                        conversationId = conversationId,
                        senderId = me,
                        seq = 0L,
                        type = payload.type.name,
                        body = text,
                        createdAt = now,
                        clientMsgId = clientMsgId,
                        status = MessageStatus.SENDING.name,
                        albumId = payload.albumId,
                        stickerId = payload.stickerId,
                        stickerEmoji = payload.stickerEmoji,
                        stickerUrl = payload.stickerUrl,
                        // Havolasi oldindan ma'lum biriktirma (GIF) — server javobini
                        // kutmasdan ko'rsatiladi. `status` yozilmaydi: mapper uni `READY`
                        // deb oladi, `PROCESSING` esa faqat serverdan kelishi mumkin.
                        attachmentUrl = payload.previewUrl,
                        attachmentThumbUrl = payload.previewThumbUrl,
                        attachmentWidth = payload.previewWidth,
                        attachmentHeight = payload.previewHeight,
                        attachmentKind = payload.previewUrl?.let { payload.type.name },
                        attachmentIsAnimated = if (payload.type == MessageType.GIF) 1L else null,
                    ),
                )
                q.touchConversation(text, me, payload.type.name, now, 0L, conversationId)
            }
        }
        return deliver(conversationId, payload, clientMsgId, localId)
    }

    override suspend fun sendImages(
        conversationId: String,
        images: List<OutgoingImage>,
    ): Resource<Unit> {
        if (images.isEmpty()) return Resource.Success(Unit)
        if (images.size > MAX_ALBUM_SIZE) {
            return errorOf(AppException.Validation("Bir martada $MAX_ALBUM_SIZE tagacha rasm yuboriladi."))
        }
        val me = currentUserId ?: return errorOf(AppException.Unauthorized())

        // Bitta albom — ekranda bitta to'r bo'lib chiziladi. Kalitni KLIENT generatsiya
        // qiladi va endi server ham uni qaytaradi, ya'ni qabul qiluvchi ham to'r ko'radi.
        val albumId = if (images.size > 1) randomClientMsgId() else null

        // 1-qadam: HAMMASI darhol ekranga chiqadi. Yuklash sekundlab davom etadi, foydalanuvchi
        // esa tanlagan rasmlarini shu zahoti ko'rishi kerak.
        val pending = images.map { image ->
            val clientMsgId = randomClientMsgId()
            val localId = LOCAL_ID_PREFIX + clientMsgId
            val now = Clock.System.now().toEpochMilliseconds()
            withContext(dispatchers.io) {
                q.transaction {
                    q.insert(
                        MessageRow(
                            id = localId,
                            conversationId = conversationId,
                            senderId = me,
                            seq = 0L,
                            type = MessageType.IMAGE.name,
                            // Media xabarda tana bo'sh — havola endi TANAGA yozilmaydi.
                            body = "",
                            createdAt = now,
                            clientMsgId = clientMsgId,
                            status = MessageStatus.SENDING.name,
                            albumId = albumId,
                        ),
                    )
                    q.touchConversation("", me, MessageType.IMAGE.name, now, 0L, conversationId)
                }
            }
            localImages.update { it + (localId to image.bytes) }
            PendingImage(image, clientMsgId, localId)
        }

        // 2-qadam: KETMA-KET yuklaymiz. Parallel qilinsa mobil tarmoqda hammasi birdek
        // sekinlashadi va serverning yuklash kvotasi (daqiqasiga 20 fayl) tezroq tugaydi.
        var failure: Resource.Error? = null
        for (item in pending) {
            val result = uploadAndDeliver(conversationId, item, albumId)
            if (result is Resource.Error) failure = result
        }
        // Bittasi yiqilsa ham qolganlari yuborilgan — xatoni qaytaramiz, lekin xabarlar
        // ro'yxatda `FAILED` bo'lib turadi va qayta urinish mumkin.
        return failure ?: Resource.Success(Unit)
    }

    private class PendingImage(
        val image: OutgoingImage,
        val clientMsgId: String,
        val localId: String,
    )

    /** Bitta rasmni yuklaydi va xabar sifatida yuboradi. */
    private suspend fun uploadAndDeliver(
        conversationId: String,
        item: PendingImage,
        albumId: String?,
    ): Resource<Unit> {
        val upload = remote.uploadAttachment(
            conversationId = conversationId,
            bytes = item.image.bytes,
            fileName = item.image.fileName,
            kind = ChatMediaKind.IMAGE,
        )
        val attachment = when (upload) {
            is Resource.Success -> upload.data.toColumns()
            is Resource.Error -> return fail(item.localId, upload.message, upload.error)
            Resource.Loading -> return Resource.Success(Unit)
        }

        // Biriktirma DARHOL keshga yoziladi: u **bir martalik**, ya'ni yuborish yiqilsa ham
        // qayta urinishda fayl qaytadan yuklanmasligi kerak (`422 MEDIA_ALREADY_USED`).
        withContext(dispatchers.io) { q.setAttachment(attachment, item.localId) }

        val result = deliver(
            conversationId = conversationId,
            payload = SendPayload(
                type = MessageType.IMAGE,
                mediaId = attachment.id,
                albumId = albumId,
            ),
            clientMsgId = item.clientMsgId,
            localId = item.localId,
        )
        // Server havolasi bor — local nusxa endi kerak emas.
        localImages.update { it - item.localId }
        return result
    }

    override suspend fun retry(messageId: String): Resource<Unit> {
        val message = withContext(dispatchers.io) {
            q.selectMessageById(messageId).executeAsOneOrNull()
        } ?: return errorOf(AppException.NotFound())
        // Qayta urinish AYNAN o'sha kalit bilan ketadi — server takror xabar yaratmaydi.
        val clientMsgId = message.clientMsgId
            ?: return errorOf(AppException.Unknown("Bu xabarni qayta yuborib bo'lmaydi."))

        withContext(dispatchers.io) { q.setMessageStatus(MessageStatus.SENDING.name, message.id) }

        val type = parseEnum(message.type, MessageType.TEXT)
        // Fayl yuklanmay qolgan bo'lsa `attachmentId` bo'sh — avval yuklashni takrorlaymiz.
        if (type == MessageType.IMAGE && message.attachmentId == null) {
            val bytes = localImages.value[message.id]
                ?: return fail(message.id, "Rasm topilmadi — uni qaytadan tanlang")
            return uploadAndDeliver(
                conversationId = message.conversationId,
                item = PendingImage(
                    OutgoingImage(bytes, message.attachmentFileName ?: RETRY_FILE_NAME),
                    clientMsgId,
                    message.id,
                ),
                albumId = message.albumId,
            )
        }
        return deliver(
            conversationId = message.conversationId,
            payload = SendPayload(
                type = type,
                // Tana faqat matnli xabarda va rasm izohida bo'lishi mumkin.
                body = message.body.takeIf { it.isNotEmpty() },
                mediaId = message.attachmentId,
                stickerId = message.stickerId,
                albumId = message.albumId,
            ),
            clientMsgId = clientMsgId,
            localId = message.id,
        )
    }

    /**
     * Yuborishning ikki yo'li: avval WS (ack bilan), u ishlamasa REST. Ikkalasi ham bir xil
     * [clientMsgId] ni ishlatadi — ikki marta ketib qolsa ham server bitta xabar yaratadi (C6).
     */
    private suspend fun deliver(
        conversationId: String,
        payload: SendPayload,
        clientMsgId: String,
        localId: String,
    ): Resource<Unit> {
        val ack = socket.send(
            conversationId = conversationId,
            clientMsgId = clientMsgId,
            body = payload.wireBody,
            // `type` doim yuboriladi: berilmasa server `TEXT` deb oladi va media xabar
            // oddiy matnga aylanib qolardi.
            type = payload.type.name,
            mediaId = payload.mediaId,
            stickerId = payload.stickerId,
            albumId = payload.albumId,
            gif = payload.gif,
        )
        if (ack != null) {
            if (ack.isSent) {
                promoteLocal(
                    localId = localId,
                    serverId = ack.id!!,
                    seq = ack.seq!!.toLong(),
                    createdAt = parseInstant(ack.createdAt).takeIf { it > 0L },
                )
                return Resource.Success(Unit)
            }
            // WS xatosi konvertsiz keladi (`{ code, message }`) — matnni to'g'ridan-to'g'ri
            // ko'rsatamiz; `NOT_CONNECTED` uchun server tushunarli matn beradi.
            return fail(localId, ack.error?.text ?: "Xabar yuborilmadi")
        }

        // WS ulanmagan / ack kelmadi → zaxira yo'l.
        return when (val res = remote.send(conversationId, payload, clientMsgId)) {
            is Resource.Success -> {
                replaceLocal(localId, res.data.toRow(clientMsgId))
                Resource.Success(Unit)
            }
            is Resource.Error -> fail(localId, res.message, res.error)
            Resource.Loading -> Resource.Success(Unit)
        }
    }

    /**
     * Optimistik qatorni serverning haqiqiy `id`/`seq` iga ko'chiradi.
     *
     * Qator **qaytadan qurilmaydi**, o'sha qatorning o'zi ko'chiriladi: biriktirmaning
     * metama'lumotlari (o'lchamlar, waveform, blurHash) WS ack'ida YO'Q, ya'ni yangi qator
     * yasasak ular yo'qolardi va rasm ekranda kvadratga tushib qolardi.
     *
     * `message:new` biroz oldinroq kelgan bo'lsa optimistik qator allaqachon o'chirilgan —
     * o'shanda qiladigan ish yo'q.
     */
    private suspend fun promoteLocal(localId: String, serverId: String, seq: Long, createdAt: Long?) {
        withContext(dispatchers.io) {
            q.transaction {
                val local = q.selectMessageById(localId).executeAsOneOrNull() ?: return@transaction
                q.deleteMessage(localId)
                q.insert(
                    local.toRow().copy(
                        id = serverId,
                        seq = seq,
                        createdAt = createdAt ?: local.createdAt,
                        status = MessageStatus.SENT.name,
                    ),
                )
            }
        }
    }

    /** Optimistik qatorni serverdan kelgan to'liq qator bilan almashtiradi (REST yo'li). */
    private suspend fun replaceLocal(localId: String, row: MessageRow) {
        withContext(dispatchers.io) {
            q.transaction {
                q.deleteMessage(localId)
                q.insert(row)
            }
        }
    }

    private suspend fun fail(localId: String, message: String, error: AppException? = null): Resource<Unit> {
        withContext(dispatchers.io) { q.setMessageStatus(MessageStatus.FAILED.name, localId) }
        return Resource.Error(message, error?.cause, error)
    }

    // --- O'chirish -----------------------------------------------------------------------

    override suspend fun deleteMessage(messageId: String): Resource<Unit> {
        // Hali yuborilmagan (optimistik) xabar serverda yo'q — uni oddiy o'chirsa bo'ladi.
        if (messageId.startsWith(LOCAL_ID_PREFIX)) {
            withContext(dispatchers.io) { q.deleteMessage(messageId) }
            localImages.update { it - messageId }
            return Resource.Success(Unit)
        }
        return when (val res = remote.deleteMessage(messageId)) {
            is Resource.Success -> {
                // WS `message:deleted` ham keladi, lekin uni kutib turmaymiz: ekran darhol
                // yangilanishi kerak, hodisa esa aynan shu natijani takrorlaydi (idempotent).
                applyDeleted(res.data.conversationId, res.data.id, res.data.seq)
                Resource.Success(Unit)
            }
            is Resource.Error -> res
            Resource.Loading -> Resource.Success(Unit)
        }
    }

    /**
     * Soft delete'ni keshga qo'llaydi — REST javobi va WS hodisasi uchun **bitta** yo'l.
     *
     * Qator o'chirilmaydi (`seq` — tarix va kursorlarning o'qi), faqat tanasi bo'shatiladi.
     * Xabar o'qilmaganlar sanog'idan chiqadi: ko'rinmaydigan xabarni o'qib bo'lmaydi, aks
     * holda badge abadiy yoqiq qolardi.
     */
    private suspend fun applyDeleted(conversationId: String, messageId: String, seq: Int) {
        val now = Clock.System.now().toEpochMilliseconds()
        withContext(dispatchers.io) {
            q.transaction {
                val row = q.selectMessageById(messageId).executeAsOneOrNull()
                // Xabar keshda bo'lmasligi mumkin (tarix hali yuklanmagan) — sanoq baribir
                // to'g'rilanadi, aks holda badge yolg'on ko'rsatardi.
                if (row != null && row.deletedAt != null) return@transaction
                q.setMessageDeleted(now, messageId)
                if (row == null || row.senderId != currentUserId) {
                    q.decrementUnreadForDeleted(conversationId, seq.toLong())
                }
                // Ro'yxatdagi ko'rinish ham tombstone'ga aylanadi — faqat oxirgi xabar bo'lsa.
                val newest = q.maxSeq(conversationId).executeAsOneOrNull() ?: 0L
                if (row == null || row.seq >= newest) q.setLastMessageDeleted(conversationId)
            }
        }
        localImages.update { it - messageId }
    }

    // --- Kursorlar -----------------------------------------------------------------------

    override suspend fun markRead(conversationId: String) {
        val seq = maxSeq(conversationId)
        // Local holat darhol yangilanadi — badge tarmoqni kutmasin.
        withContext(dispatchers.io) { q.markRead(seq.toLong(), conversationId) }
        if (seq <= 0) return
        // WS endi ack qaytaradi (§17.8) — ack kelmasa kursor yo'lda yo'qolgan, REST bilan
        // qayta yuboramiz.
        if (!socket.markRead(conversationId, seq)) remote.markRead(conversationId, seq)
    }

    override suspend fun markDelivered(conversationId: String) {
        val seq = maxSeq(conversationId)
        if (seq <= 0) return
        // REST zaxirasi 2026-07-29 dan bor (§17.6): busiz uzilgan ulanish jo'natuvchini
        // abadiy bitta belgichada qoldirardi.
        if (!socket.markDelivered(conversationId, seq)) remote.markDelivered(conversationId, seq)
    }

    override suspend fun setTyping(conversationId: String, typing: Boolean) {
        socket.setTyping(conversationId, typing)
    }

    override suspend fun setArchived(conversationId: String, archived: Boolean) {
        withContext(dispatchers.io) { q.setArchived(if (archived) 1L else 0L, conversationId) }
    }

    // --- Yordamchilar --------------------------------------------------------------------

    private suspend fun maxSeq(conversationId: String): Int = withContext(dispatchers.io) {
        q.maxSeq(conversationId).executeAsOneOrNull()?.toInt() ?: 0
    }

    private suspend fun conversationCached(conversationId: String): Boolean =
        withContext(dispatchers.io) { q.selectConversation(conversationId).executeAsOneOrNull() != null }

    /**
     * Serverdan kelgan xabarni yozadi va **o'zimizniki bo'lsa** unga mos optimistik qatorni
     * olib tashlaydi.
     *
     * `message:new` ni o'tkazib yuborgan bo'lsak (uzilish, reconnect, ilova yopiq edi)
     * optimistik `SENDING` qator aks holda abadiy dublikat bo'lib qolardi: serverning qatori
     * boshqa `id` bilan keladi. Backend `clientMsgId` ni REST tarixida ham aynan shuning
     * uchun qaytaradi (`handoff/chat.md`).
     *
     * Tranzaksiya ichida chaqirilishi kutiladi.
     */
    private fun ChatQueries.insertOwn(row: MessageRow, me: String?) {
        row.clientMsgId?.takeIf { row.senderId == me }?.let { key ->
            deleteSendingByClientMsgId(row.conversationId, key)
        }
        insert(row)
    }

    /** SQLDelight generatsiya qilgan uzun imzoni bitta joyda o'raymiz. */
    private fun ChatQueries.insert(row: MessageRow) = upsertMessage(
        id = row.id,
        conversationId = row.conversationId,
        senderId = row.senderId,
        seq = row.seq,
        type = row.type,
        body = row.body,
        createdAt = row.createdAt,
        clientMsgId = row.clientMsgId,
        status = row.status,
        deletedAt = row.deletedAt,
        attachmentId = row.attachmentId,
        attachmentUrl = row.attachmentUrl,
        attachmentThumbUrl = row.attachmentThumbUrl,
        attachmentWidth = row.attachmentWidth,
        attachmentHeight = row.attachmentHeight,
        attachmentKind = row.attachmentKind,
        attachmentStatus = row.attachmentStatus,
        attachmentMime = row.attachmentMime,
        attachmentSizeBytes = row.attachmentSizeBytes,
        attachmentDurationMs = row.attachmentDurationMs,
        attachmentWaveform = row.attachmentWaveform,
        attachmentFileName = row.attachmentFileName,
        attachmentBlurHash = row.attachmentBlurHash,
        attachmentIsAnimated = row.attachmentIsAnimated,
        stickerId = row.stickerId,
        stickerEmoji = row.stickerEmoji,
        stickerUrl = row.stickerUrl,
        albumId = row.albumId,
    )

    private fun ChatQueries.setAttachment(columns: AttachmentColumns, messageId: String) = setMessageAttachment(
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
        id = messageId,
    )

    /**
     * `clientMsgId` **global noyob** bo'lishi shart. `kotlin.uuid` hali eksperimental,
     * shuning uchun vaqt + ikkita tasodifiy 64-bitli son — amalda takrorlanmaydi.
     */
    private fun randomClientMsgId(): String {
        val time = Clock.System.now().toEpochMilliseconds().toString(16)
        val a = Random.nextLong().toULong().toString(16)
        val b = Random.nextLong().toULong().toString(16)
        return "$time-$a-$b"
    }

    private companion object {
        const val TYPING_TIMEOUT_MS = 5_000L
        const val LOCAL_ID_PREFIX = "local:"

        /** Bir albomdagi rasmlar chegarasi — backend ham shu sonni kutadi (`422 ALBUM_TOO_LARGE`). */
        const val MAX_ALBUM_SIZE = 10

        /** Qayta yuborishda asl fayl nomi saqlanmagan — kengaytma MIME uchun yetarli. */
        const val RETRY_FILE_NAME = "image.jpg"
    }
}
