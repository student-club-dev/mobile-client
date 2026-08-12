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
import dev.core.network.media.UploadProgress
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
import dev.feature.chat.domain.model.DeleteScope
import dev.feature.chat.domain.model.GifRef
import dev.feature.chat.domain.model.Message
import dev.feature.chat.domain.model.MessageStatus
import dev.feature.chat.domain.model.MessageType
import dev.feature.chat.domain.model.OutgoingImage
import dev.feature.chat.domain.model.OutgoingVideo
import dev.feature.chat.domain.model.Quote
import dev.feature.chat.domain.model.Sticker
import dev.feature.chat.domain.model.StickerRef
import dev.feature.chat.domain.model.UnreadCount
import dev.feature.chat.domain.model.UploadState
import dev.feature.chat.domain.repository.ChatRepository
import dev.feature.chat.domain.repository.OutgoingVideoStored
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.random.Random
import dev.feature.chat.domain.model.ChatDomainStrings

/**
 * Chat repository'si — REST + WebSocket ustidagi **offline-first** qatlam.
 *
 * Oqim: ikkala manba ham SQLDelight keshiga yozadi, UI esa faqat keshni kuzatadi. Shuning
 * uchun ekran tarmoqqa bog'liq emas va WS uzilib-ulanganda qayta chizilmaydi.
 *
 * Tartib o'qi — **`seq`** (`handoff/03-WEBSOCKET.md`): tarix `?before=`, qayta ulanish `?after=`,
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
    /**
     * API origin'i — biriktirma havolalarini to'liq holga keltirish uchun (`MediaUrl`).
     * Serverdan ular nisbiy keladi va video/ovoz pleyeri bunday havolani ocholmaydi.
     */
    private val apiOrigin: String,
) : ChatRepository {

    private val q: ChatQueries get() = db.chatQueries

    /**
     * Yuklanayotgan rasmlarning local nusxasi (xabar id → baytlar). Faqat xotirada:
     * yuklash bir necha soniya davom etadi, shu vaqt ichida ekranda tanlangan faylning
     * o'zi ko'rinadi, keyin serverdagi havolaga almashadi.
     */
    private val localImages = MutableStateFlow<Map<String, ByteArray>>(emptyMap())

    /**
     * Yuklanmay qolgan videolar (xabar id → keshdagi fayl).
     *
     * Rasmdan farqli o'laroq baytlar emas, **fayl** saqlanadi ([OutgoingVideo]) — 64 MB ni
     * xotirada ushlab turish mumkin emas. Yozuv yuklash muvaffaqiyatli tugaganda o'chadi;
     * qolgani — qayta urinish uchun kutayotgan fayl.
     */
    private val localVideos = MutableStateFlow<Map<String, OutgoingVideo>>(emptyMap())

    /**
     * Ketayotgan yuborishlar (xabar id → korutin) — bekor qilish uchun ([cancelSend]).
     *
     * Faqat video: rasm va fayl sekundlarda ketadi, video esa siqish bilan birga bir necha
     * daqiqa davom etishi mumkin va foydalanuvchi fikridan qaytishi tabiiy.
     */
    private val sendJobs = MutableStateFlow<Map<String, Job>>(emptyMap())

    /**
     * Hozir ketayotgan biriktirmalar (xabar id → foiz). Faqat xotirada: yarim ketgan
     * yuklashni ilova qayta ishga tushgach davom ettirib bo'lmaydi, ya'ni bu holatni
     * keshda saqlashning ma'nosi yo'q.
     */
    private val uploads = MutableStateFlow<Map<String, UploadState>>(emptyMap())

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
        q.selectMessages(conversationId).asFlow().mapToList(dispatchers.io)
            .map { rows -> rows.map { it.toDomain(apiOrigin) } }

    override fun observeTyping(conversationId: String): Flow<Boolean> =
        typingIds.map { conversationId in it }

    override fun observeTypingIds(): Flow<Set<String>> = typingIds

    override fun observeRealtimeConnected(): Flow<Boolean> = socket.connected

    override fun observeLocalImages(): Flow<Map<String, ByteArray>> = localImages

    override fun observeUploads(): Flow<Map<String, UploadState>> = uploads

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
            // `message:deleted` — belgilab o'chirilgan butun paket uchun **bitta** hodisa.
            //
            // Auditoriya qoidasi (§4.4): `EVERYONE` ikkala a'zoga, `ME` esa faqat MENING
            // qurilmalarimga keladi — ya'ni `ME` kelgan bo'lsa, bu boshqa telefonimda
            // yashirgan xabarlarim va ular bu yerda ham yashirilishi kerak.
            socket.deletedMessages.collect { event ->
                val ids = event.allIds
                if (ids.isEmpty()) return@collect
                if (event.onlyForMe) {
                    hideLocally(event.conversationId, ids)
                } else {
                    ids.zip(event.allSeqs.ifEmpty { List(ids.size) { 0 } })
                        .forEach { (id, seq) -> applyDeleted(event.conversationId, id, seq) }
                }
            }
        }
        launch {
            // `history:cleared` — tarix tozalandi (o'zim boshqa qurilmada yoki suhbatdosh
            // `EVERYONE` bilan). Kesh serverning suv belgisiga tenglashadi.
            socket.historyCleared.collect { event ->
                applyHistoryCleared(event.conversationId, event.clearedBeforeSeq)
            }
        }
        launch {
            // `conversation:deleted` — suhbat ro'yxatdan olib tashlandi. Qator O'CHIRILMAYDI:
            // yangi xabar kelsa u aynan o'sha id bilan qaytadi.
            socket.conversationsDeleted.collect { event ->
                withContext(dispatchers.io) {
                    q.transaction {
                        q.clearLastMessage(event.conversationId)
                        q.setConversationHidden(1L, event.conversationId)
                    }
                }
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
     * taymerni qaytadan boshlaydi (`handoff/03-WEBSOCKET.md`: ~5 soniya).
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
            type = item.conversation.type,
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
            lastMessageType = last?.type,
            lastMessageDeleted = lastDeleted,
            unreadCount = item.unreadCount.toLong(),
            lastReadSeq = item.myReadSeq.toLong(),
            otherReadSeq = item.peerReadSeq.toLong(),
            otherDeliveredSeq = item.peerDeliveredSeq.toLong(),
        )
        q.updateConversation(
            type = item.conversation.type,
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
            lastMessageType = last?.type,
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

    override suspend fun cachedDirectId(studentId: String): String? = withContext(dispatchers.io) {
        q.selectConversationIdByOther(studentId).executeAsOneOrNull()
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

    override suspend fun send(
        conversationId: String,
        body: String,
        replyToMessageId: String?,
        quote: Quote?,
    ): Resource<Unit> = sendPayload(
        conversationId,
        SendPayload(
            type = MessageType.TEXT,
            body = body,
            replyToMessageId = replyToMessageId,
            quote = quote,
        ),
    )

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

    override suspend fun sendStickerRef(conversationId: String, sticker: StickerRef): Resource<Unit> =
        sendPayload(
            conversationId,
            SendPayload(
                type = MessageType.STICKER,
                // Javobda kelgan obyekt AYNAN shu holda qaytariladi — server uni domen oq
                // ro'yxatidan o'tkazadi (`422 STICKER_URL_NOT_ALLOWED`).
                sticker = sticker.toJson(),
                // Optimistik qatorga — server javobini kutmasdan ko'rsatish uchun. `emoji`
                // yo'q (bu personaj stikeri, emoji o'rnini bosuvchi belgi emas), shuning
                // uchun faqat tasvir havolasi yoziladi.
                stickerUrl = sticker.url,
            ),
        )

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

        // Sitata surati optimistik qatorga ham yoziladi: serverning javobi kelguncha bir
        // necha soniya o'tadi va usiz javob xabari o'sha vaqt ichida sitatasiz ko'rinardi.
        // Nishonni keshdan olamiz — u ekranda turgani uchun albatta bor.
        val replySnapshot = payload.replyToMessageId?.let { targetId ->
            withContext(dispatchers.io) { q.selectMessageById(targetId).executeAsOneOrNull() }
        }

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
                        // ⚠️ `senderName` bo'sh qoladi — keshda ismlar yo'q (faqat id).
                        // Server javobida u to'ladi va qator o'sha bilan almashadi; shu
                        // qisqa oynada pufakda faqat matn ko'rinadi.
                        replyToId = payload.replyToMessageId,
                        replyToSeq = replySnapshot?.seq,
                        replyToSenderId = replySnapshot?.senderId,
                        replyToType = replySnapshot?.type,
                        replyToPreview = replySnapshot?.body?.take(REPLY_PREVIEW_LENGTH),
                        replyToQuoteText = payload.quote?.text,
                        replyToQuoteOffset = payload.quote?.offset?.toLong(),
                        replyToOriginalDeleted = 0L,
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
        caption: String?,
    ): Resource<Unit> {
        if (images.isEmpty()) return Resource.Success(Unit)
        if (images.size > MAX_ALBUM_SIZE) {
            return errorOf(AppException.Validation(ChatDomainStrings.albumTooLarge(MAX_ALBUM_SIZE)))
        }
        val text = caption?.trim()?.takeIf { it.isNotEmpty() }
        if (text != null && text.length > SendPayload.MAX_CAPTION) {
            return errorOf(AppException.Validation(ChatDomainStrings.captionTooLong(SendPayload.MAX_CAPTION)))
        }
        val me = currentUserId ?: return errorOf(AppException.Unauthorized())

        // Bitta albom — ekranda bitta to'r bo'lib chiziladi. Kalitni KLIENT generatsiya
        // qiladi va endi server ham uni qaytaradi, ya'ni qabul qiluvchi ham to'r ko'radi.
        //
        // ⚠️ GIF albomga KIRMAYDI: u alohida tur (`type = GIF`) va server uni ovozsiz MP4
        // ga o'giradi, ya'ni rasmlar to'ri bilan bir katakda tursa maket buzilardi.
        val albumImages = images.count { !it.isGif }
        val albumId = if (albumImages > 1) randomClientMsgId() else null

        // 1-qadam: HAMMASI darhol ekranga chiqadi. Yuklash sekundlab davom etadi, foydalanuvchi
        // esa tanlagan rasmlarini shu zahoti ko'rishi kerak.
        val pending = images.mapIndexed { index, image ->
            val clientMsgId = randomClientMsgId()
            val localId = LOCAL_ID_PREFIX + clientMsgId
            val now = Clock.System.now().toEpochMilliseconds()
            // Izoh faqat BIRINCHISIDA: albom ekranda bitta to'r bo'lib chiziladi va matn
            // har katak ostida takrorlansa bitta izoh o'n marta ko'rinardi.
            val body = text.takeIf { index == 0 }.orEmpty()
            withContext(dispatchers.io) {
                q.transaction {
                    q.insert(
                        MessageRow(
                            id = localId,
                            conversationId = conversationId,
                            senderId = me,
                            seq = 0L,
                            type = image.messageType.name,
                            // Media xabarda tana — faqat izoh. Havola TANAGA yozilmaydi.
                            body = body,
                            createdAt = now,
                            clientMsgId = clientMsgId,
                            status = MessageStatus.SENDING.name,
                            albumId = if (image.isGif) null else albumId,
                        ),
                    )
                    q.touchConversation(body, me, image.messageType.name, now, 0L, conversationId)
                }
            }
            localImages.update { it + (localId to image.bytes) }
            PendingImage(image, clientMsgId, localId, caption = body.takeIf { it.isNotEmpty() })
        }

        // 2-qadam: KETMA-KET yuklaymiz. Parallel qilinsa mobil tarmoqda hammasi birdek
        // sekinlashadi va serverning yuklash kvotasi (daqiqasiga 20 fayl) tezroq tugaydi.
        var failure: Resource.Error? = null
        // Albomning birinchi rasmi — `albumSize` faqat unga qo'shiladi (pastga qarang).
        val firstOfAlbum = pending.firstOrNull { !it.image.isGif }
        for (item in pending) {
            // Videodagi bilan bir xil sabab ([withSendJob]): chatdan chiqib ketish
            // yarim ketgan albomni to'xtatmasin.
            // `albumSize` FAQAT albomning birinchi rasmida ketadi — push aynan o'shanda
            // yuboriladi va serverda sanaydigan narsa yo'q (qolgan rasmlar hali yo'lda).
            // Server chegarasi — `2..10`; tanlagich ham 10 tadan oshirmaydi
            // (`DEFAULT_MAX_IMAGES`), lekin chegara ikki joyda turgani uchun kesib qo'yamiz:
            // oshib ketgan son butun albomni `422` qilardi.
            val result = withSendJob(item.localId) {
                uploadAndDeliver(
                    conversationId = conversationId,
                    item = item,
                    albumId = albumId,
                    albumSize = albumImages.coerceAtMost(MAX_ALBUM_SIZE)
                        .takeIf { albumId != null && item === firstOfAlbum },
                )
            }
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
        /** Albom izohi — faqat birinchi rasmda to'ldiriladi. */
        val caption: String? = null,
    )

    /** Bitta rasmni yuklaydi va xabar sifatida yuboradi. */
    private suspend fun uploadAndDeliver(
        conversationId: String,
        item: PendingImage,
        albumId: String?,
        albumSize: Int? = null,
    ): Resource<Unit> {
        val upload = tracked(item.localId, item.image.fileName, item.image.bytes.size.toLong()) { onProgress ->
            remote.uploadAttachment(
                conversationId = conversationId,
                bytes = item.image.bytes,
                fileName = item.image.fileName,
                // Foydalanuvchining O'Z GIF'i — server uni ovozsiz MP4 ga o'giradi (~20 barobar
                // yengil). Qidiruvdan tanlangan GIF esa umuman yuklanmaydi, u `gif` obyekti
                // bilan havola sifatida ketadi (`sendGif`).
                kind = if (item.image.isGif) ChatMediaKind.GIF else ChatMediaKind.IMAGE,
                onProgress = onProgress,
            )
        }
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
                type = item.image.messageType,
                body = item.caption,
                mediaId = attachment.id,
                albumId = if (item.image.isGif) null else albumId,
                albumSize = albumSize,
            ),
            clientMsgId = item.clientMsgId,
            localId = item.localId,
        )
        // Server havolasi bor — local nusxa endi kerak emas.
        localImages.update { it - item.localId }
        return result
    }

    override suspend fun sendFile(conversationId: String, bytes: ByteArray, fileName: String) =
        sendSingleAttachment(conversationId, bytes, fileName, ChatMediaKind.FILE, MessageType.FILE)

    /**
     * Video — yakka biriktirmalarning ([sendSingleAttachment]) ichida **eng og'iri**, shuning
     * uchun alohida yo'l:
     *
     * - fayl xotiraga o'qilmaydi, oqim bilan yuklanadi (`uploadAttachmentFile`);
     * - poster kadr darhol ekranga chiqadi, ya'ni yuklanayotgan video bo'sh to'rtburchak
     *   emas — Telegramdagidek o'z kadri bilan turadi;
     * - izoh (caption) qo'llab-quvvatlanadi.
     */
    override suspend fun sendVideo(
        conversationId: String,
        video: OutgoingVideo,
        onStored: OutgoingVideoStored?,
    ): Resource<Unit> {
        // ⚠️ Dumaloq video xabarda izoh **umuman yo'q** — server matnni qabul qilmaydi
        // (`handoff/09-CALLS-REST.md` emas, `chat-upload` tavsifi: «carries no caption»).
        // Shuning uchun u bu yerda jimgina tashlanadi, xato sifatida emas: UI'da izoh
        // maydoni ham chizilmaydi, ya'ni bu yerga faqat dasturiy xato bilan kelinadi.
        val caption = video.caption?.trim()
            ?.takeIf { it.isNotEmpty() && !video.videoNote }
        // Chegara klientda tekshiriladi: `422` ni kutsak xabar ekranda "yuborilmadi" bo'lib
        // qolardi va foydalanuvchi sababini bilmasdi.
        if (caption != null && caption.length > SendPayload.MAX_CAPTION) {
            deleteLocalFile(video.path)
            return errorOf(AppException.Validation(ChatDomainStrings.captionTooLong(SendPayload.MAX_CAPTION)))
        }
        val messageType = if (video.videoNote) MessageType.VIDEO_NOTE else MessageType.VIDEO

        val me = currentUserId ?: return errorOf(AppException.Unauthorized())
        val clientMsgId = randomClientMsgId()
        val localId = LOCAL_ID_PREFIX + clientMsgId
        val now = Clock.System.now().toEpochMilliseconds()

        // Poster DARHOL ko'rinsin — yuklash o'nlab soniya davom etadi va usiz o'sha vaqt
        // ichida ekranda bo'sh pufak turardi.
        video.posterBytes?.let { poster -> localImages.update { it + (localId to poster) } }
        // Yiqilsa qayta urinish uchun fayl yo'li kerak — baytlar saqlanmaydi.
        localVideos.update { it + (localId to video) }

        withContext(dispatchers.io) {
            q.transaction {
                q.insert(
                    MessageRow(
                        id = localId,
                        conversationId = conversationId,
                        senderId = me,
                        seq = 0L,
                        type = messageType.name,
                        body = caption.orEmpty(),
                        createdAt = now,
                        clientMsgId = clientMsgId,
                        status = MessageStatus.SENDING.name,
                        attachmentFileName = video.fileName,
                        attachmentSizeBytes = video.sizeBytes,
                    ),
                )
                q.touchConversation(caption.orEmpty(), me, messageType.name, now, 0L, conversationId)
            }
        }

        // Bekor qilish uchun shu yuborishning korutini eslab qolinadi ([cancelSend]).
        // ⚠️ Aynan chaqiruvchining `Job` i — uni bekor qilish faqat shu videoni to'xtatadi,
        // ViewModel'ning qolgan ishlariga tegmaydi.
        return withSendJob(localId) {
            uploadAndDeliverVideo(conversationId, video, caption, clientMsgId, localId, onStored)
        }
    }

    /**
     * Ketayotgan yuborishni to'xtatadi: siqish ham, yuklash ham uziladi, optimistik qator
     * va keshdagi fayl o'chadi.
     *
     * Telegramdagi kabi bekor qilish **pufakning o'zida** — yuklash halqasi ichidagi `×`.
     * Jarayon allaqachon tugagan bo'lsa hech narsa qilinmaydi.
     */
    override suspend fun cancelSend(messageId: String) {
        sendJobs.value[messageId]?.cancel()
        sendJobs.update { it - messageId }
        // Qator faqat hali yuborilmagan bo'lsa o'chiriladi: bekor qilish serverga yetib
        // ulgurgan xabarni qaytarib ololmaydi.
        if (messageId.startsWith(LOCAL_ID_PREFIX)) {
            withContext(dispatchers.io) { q.deleteMessage(messageId) }
        }
        forgetLocalMedia(setOf(messageId))
        uploads.update { it - messageId }
    }

    /**
     * Blokni **repozitoriy qamrovida** bajaradi va [sendJobs] da ro'yxatga oladi.
     *
     * ⚠️ Ataylab chaqiruvchining korutini emas. Ilgari yuborish `viewModelScope` da
     * ketardi: chatdan chiqib ketish yoki ekranning aylanishi yarim yo'ldagi videoni
     * o'ldirar, foydalanuvchi esa buni faqat qaytib kelganda ko'rardi. Repozitoriy Koin'da
     * `single`, ya'ni uning qamrovi ilova bilan tengdosh.
     *
     * Chaqiruvchi baribir natijani kutadi ([await]) — lekin uning bekor bo'lishi faqat
     * **kutishni** to'xtatadi, ishning o'zini emas. Yuborishni atayin to'xtatish uchun
     * [cancelSend] bor va u aynan shu yerda ro'yxatga olingan korutinni bekor qiladi.
     */
    private suspend fun <T> withSendJob(localId: String, block: suspend () -> T): T {
        val work = scope.async { block() }
        sendJobs.update { it + (localId to work) }
        // Ro'yxat ishning o'zi tugaganda tozalanadi: `finally` da tozalash kutish bekor
        // bo'lganda hali ketayotgan yuborishni ro'yxatdan o'chirib, uni bekor qilib
        // bo'lmaydigan holga keltirardi.
        work.invokeOnCompletion { sendJobs.update { jobs -> jobs - localId } }
        return work.await()
    }

    /**
     * Halqaning siqishga ajratilgan qismi — **davomiylikka qarab**.
     *
     * Ilgari bu qotirilgan `0.5f` edi va uzun lavhada halqa yarmida daqiqalab qotib
     * turardi: uch daqiqalik videoni qayta kodlash bir necha daqiqa, uni yuklash esa
     * o'n soniya — ya'ni ish 90% siqishda, halqa esa 50% deb ko'rsatardi.
     *
     * Bu ham **baho**, aniq o'lchov emas (kodek ham, tarmoq ham oldindan noma'lum), lekin
     * u ishning haqiqiy nisbatiga qarab o'zgaradi: qisqa lavhada yuklash, uzunida siqish
     * ustun bo'ladi.
     */
    private fun prepareShare(video: OutgoingVideo): Float {
        val seconds = video.durationMs?.takeIf { it > 0 }?.let { it / MS_IN_SECOND } ?: return DEFAULT_PREPARE_SHARE
        val encodeSeconds = seconds * ENCODE_SECONDS_PER_SECOND
        // Siqilgan fayl manbadan katta bo'lmaydi va maqsadli hajmdan ham oshmaydi.
        val uploadBytes = minOf(video.sizeBytes, COMPRESSED_TARGET_BYTES)
        val uploadSeconds = uploadBytes / UPLOAD_BYTES_PER_SECOND
        val total = encodeSeconds + uploadSeconds
        if (total <= 0f) return DEFAULT_PREPARE_SHARE
        return (encodeSeconds / total).coerceIn(MIN_PREPARE_SHARE, MAX_PREPARE_SHARE)
    }

    /**
     * Videoni **siqadi**, yuklaydi va xabar sifatida yuboradi — [sendVideo] va [retry] uchun
     * umumiy.
     *
     * Siqish shu yerda, yuklashning oldida: foydalanuvchi uchun bu bitta jarayon va u bitta
     * halqada ko'rinadi. Telegram ham shunday — tanlangandan keyin hech narsa kutilmaydi,
     * xabar darrov ro'yxatga tushadi va halqa siqish bilan yuklashni birga hisoblaydi.
     */
    private suspend fun uploadAndDeliverVideo(
        conversationId: String,
        video: OutgoingVideo,
        caption: String?,
        clientMsgId: String,
        localId: String,
        onStored: OutgoingVideoStored? = null,
    ): Resource<Unit> {
        // Siqishga ajratilgan ulush: siqilmaydigan videoda `0f`, ya'ni halqa darrov
        // yuklashdan boshlanadi va yarmidan sakrab ketmaydi.
        val prepareShare = if (video.needsPreparing && video.prepare != null) prepareShare(video) else 0f
        var ready: OutgoingVideo? = null

        val upload = tracked(localId, video.fileName, video.sizeBytes) { onProgress ->
            val prepare = video.prepare
            val prepared = if (prepare == null) {
                // Video allaqachon siqilgan (qayta urinish) — uni yana siqish shunchaki
                // yana o'nlab soniya va batareya degani.
                video
            } else {
                prepare { fraction -> onProgress(fraction * prepareShare) } ?: return@tracked null
            }
            ready = prepared
            // Siqilgani saqlanadi: yuklash yiqilsa qayta urinish uni QAYTA SIQMASLIGI kerak —
            // bu yana o'nlab soniya va batareya degani.
            localVideos.update { it + (localId to prepared) }

            remote.uploadAttachmentFile(
                conversationId = conversationId,
                path = prepared.path,
                sizeBytes = prepared.sizeBytes,
                fileName = prepared.fileName,
                kind = if (video.videoNote) ChatMediaKind.VIDEO_NOTE else ChatMediaKind.VIDEO,
                onProgress = { fraction -> onProgress(prepareShare + fraction * (1f - prepareShare)) },
            )
        } ?: return fail(
            localId,
            ChatDomainStrings.videoUnsupported,
        )

        val attachment = when (upload) {
            is Resource.Success -> upload.data.toColumns()
            // Fayl **saqlanadi** — qayta urinishda u qaytadan yuklanishi kerak.
            is Resource.Error -> return fail(localId, upload.message, upload.error)
            Resource.Loading -> return Resource.Success(Unit)
        }

        // Fayl HALI o'chirilmagan, `mediaId` esa endi ma'lum — telefon xotirasiga ko'chirib
        // qolish uchun yagona lahza shu. Usiz o'z videongizni qayta ko'rish uni serverdan
        // qaytadan yuklab olishni talab qilardi, holbuki u shu daqiqada qurilmada yotibdi.
        //
        // Xatosi yutiladi: saqlanmasa ham video serverda bor va yuborish muvaffaqiyatli.
        runCatching { onStored?.invoke(attachment.id, (ready ?: video).path) }

        // Serverda baytlar bor — keshdagi nusxa endi keraksiz. `mediaId` bir martalik, ya'ni
        // qayta urinish ham faylni qaytadan yuklamaydi.
        deleteLocalFile((ready ?: video).path)
        localVideos.update { it - localId }

        // Biriktirma DARHOL keshga yoziladi: yuborish yiqilsa ham qayta urinishda fayl
        // qaytadan yuklanmasligi kerak (`422 MEDIA_ALREADY_USED`).
        withContext(dispatchers.io) { q.setAttachment(attachment, localId) }

        val result = deliver(
            conversationId = conversationId,
            payload = SendPayload(
                type = if (video.videoNote) MessageType.VIDEO_NOTE else MessageType.VIDEO,
                body = caption,
                mediaId = attachment.id,
            ),
            clientMsgId = clientMsgId,
            localId = localId,
        )
        // Server posteri keldi — local nusxa endi kerak emas.
        localImages.update { it - localId }
        return result
    }

    override suspend fun sendVoice(conversationId: String, bytes: ByteArray, fileName: String) =
        sendSingleAttachment(conversationId, bytes, fileName, ChatMediaKind.VOICE, MessageType.VOICE)

    /**
     * Yakka biriktirmali xabar: yuklash → `message:send { mediaId }`.
     *
     * Rasm oqimidan ([sendImages]) farqi — albom yo'q va **local nusxa saqlanmaydi**:
     * fayl/video/ovozni yuklanguncha ekranda ko'rsatadigan joy yo'q (rasmda esa tanlangan
     * kadr darhol chiziladi). Shuning uchun optimistik qator biriktirmasiz turadi va
     * yuklash tugagach to'ladi.
     */
    private suspend fun sendSingleAttachment(
        conversationId: String,
        bytes: ByteArray,
        fileName: String,
        kind: ChatMediaKind,
        type: MessageType,
    ): Resource<Unit> {
        val me = currentUserId ?: return errorOf(AppException.Unauthorized())
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
                        type = type.name,
                        body = "",
                        createdAt = now,
                        clientMsgId = clientMsgId,
                        status = MessageStatus.SENDING.name,
                        // Nom va hajm DARHOL yoziladi: yuklash tugagunicha biriktirma
                        // qatorda yo'q, pufak esa ularsiz "Fayl" deb turardi. Qayta
                        // urinishda ham asl nom shu yerdan olinadi.
                        attachmentFileName = fileName,
                        attachmentSizeBytes = bytes.size.toLong(),
                    ),
                )
                q.touchConversation("", me, type.name, now, 0L, conversationId)
            }
        }

        val upload = tracked(localId, fileName, bytes.size.toLong()) { onProgress ->
            remote.uploadAttachment(
                conversationId = conversationId,
                bytes = bytes,
                fileName = fileName,
                kind = kind,
                onProgress = onProgress,
            )
        }
        val attachment = when (upload) {
            is Resource.Success -> upload.data.toColumns()
            is Resource.Error -> return fail(localId, upload.message, upload.error)
            Resource.Loading -> return Resource.Success(Unit)
        }

        // Biriktirma DARHOL keshga yoziladi: `mediaId` **bir martalik**, ya'ni yuborish
        // yiqilsa ham qayta urinishda fayl qaytadan yuklanmasligi kerak.
        withContext(dispatchers.io) { q.setAttachment(attachment, localId) }

        return deliver(
            conversationId = conversationId,
            payload = SendPayload(type = type, mediaId = attachment.id),
            clientMsgId = clientMsgId,
            localId = localId,
        )
    }

    override suspend fun retry(messageId: String): Resource<Unit> {
        val message = withContext(dispatchers.io) {
            q.selectMessageById(messageId).executeAsOneOrNull()
        } ?: return errorOf(AppException.NotFound())
        // Qayta urinish AYNAN o'sha kalit bilan ketadi — server takror xabar yaratmaydi.
        val clientMsgId = message.clientMsgId
            ?: return errorOf(AppException.Unknown(ChatDomainStrings.cantResend))

        withContext(dispatchers.io) { q.setMessageStatus(MessageStatus.SENDING.name, message.id) }

        val type = parseEnum(message.type, MessageType.TEXT)
        // Fayl yuklanmay qolgan bo'lsa `attachmentId` bo'sh — avval yuklashni takrorlaymiz.
        if (type == MessageType.IMAGE && message.attachmentId == null) {
            val bytes = localImages.value[message.id]
                ?: return fail(message.id, ChatDomainStrings.imageMissing)
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
        // Video ham xuddi shunday: fayl hali yuklanmagan bo'lsa avval uni yuklaymiz. Fayl
        // keshdan topilmasa (ilova qayta ishga tushgan, tizim keshni tozalagan) qayta
        // urinishning ma'nosi yo'q — foydalanuvchi videoni qaytadan tanlashi kerak.
        if (type == MessageType.VIDEO && message.attachmentId == null) {
            val video = localVideos.value[message.id]
                ?: return fail(message.id, ChatDomainStrings.videoMissing)
            return uploadAndDeliverVideo(
                conversationId = message.conversationId,
                video = video,
                caption = message.body.takeIf { it.isNotEmpty() },
                clientMsgId = clientMsgId,
                localId = message.id,
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
            albumSize = payload.albumSize,
            gif = payload.gif,
            sticker = payload.sticker,
            replyToMessageId = payload.replyToMessageId,
            quote = payload.quote,
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
            return fail(localId, ack.error?.text ?: ChatDomainStrings.sendFailed)
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
            forgetLocalMedia(setOf(messageId))
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
     * `POST /v1/messages/delete` — **bitta so'rov**, 1–100 ta id.
     *
     * Ilgari har bir xabar uchun alohida `DELETE` ketardi (50 ta belgilansa 50 ta so'rov);
     * backend endi paketni bitta tranzaksiyada bajaradi va qayta hisoblangan `unreadCount`
     * bilan `lastMessage` ni ham qaytaradi.
     *
     * Optimistik qatorlar so'rovga **kirmaydi**: ularning server id'si yo'q, ya'ni ular
     * uchun `skipped(NOT_FOUND)` kelardi. Ular keshdan to'g'ridan-to'g'ri o'chiriladi
     * (yashirilsa, `FAILED` holatida keshda abadiy qolib ketardi).
     */
    override suspend fun deleteMessages(messageIds: List<String>, forEveryone: Boolean): Resource<Unit> {
        if (messageIds.isEmpty()) return Resource.Success(Unit)
        val (local, remoteIds) = messageIds.partition { it.startsWith(LOCAL_ID_PREFIX) }

        if (local.isNotEmpty()) {
            withContext(dispatchers.io) { q.transaction { q.deleteMessages(local) } }
            forgetLocalMedia(local.toSet())
        }
        if (remoteIds.isEmpty()) return Resource.Success(Unit)

        val scope = if (forEveryone) DeleteScope.EVERYONE else DeleteScope.ME
        return when (val res = remote.deleteMessages(remoteIds.take(MAX_DELETE_IDS), scope)) {
            is Resource.Success -> {
                applyBulkDelete(res.data.conversationId, res.data.deleted, forEveryone, res.data.unreadCount)
                Resource.Success(Unit)
            }
            is Resource.Error -> res
            Resource.Loading -> Resource.Success(Unit)
        }
    }

    /**
     * Server tasdiqlagan o'chirishni keshga qo'llaydi.
     *
     * `EVERYONE` — tana bo'shatiladi va tarixda tombstone qoladi; `ME` — qator o'z holicha
     * qoladi, faqat **yashiriladi** (server ham xuddi shunday saqlaydi va o'sha xabarlarni
     * boshqa qaytarmaydi, ya'ni qurilma almashsa ham yashiringan qoladi).
     *
     * `unreadCount` **serverdan** olinadi: uni klientda qayta sanash ikki manbadan bir xil
     * natija kutish demakdir va ular albatta bir kun ajralib ketardi.
     */
    private suspend fun applyBulkDelete(
        conversationId: String,
        ids: List<String>,
        forEveryone: Boolean,
        unreadCount: Int,
    ) {
        if (ids.isEmpty()) return
        val now = Clock.System.now().toEpochMilliseconds()
        withContext(dispatchers.io) {
            q.transaction {
                if (forEveryone) {
                    ids.forEach { id ->
                        val row = q.selectMessageById(id).executeAsOneOrNull() ?: return@forEach
                        q.setMessageDeleted(now, row.id)
                    }
                } else {
                    q.hideMessages(now, ids)
                }
                q.setUnreadCount(unreadCount.toLong(), conversationId)
                refreshPreview(conversationId)
            }
        }
        forgetLocalMedia(ids.toSet())
    }

    override suspend fun clearHistory(conversationId: String, forEveryone: Boolean): Resource<Unit> {
        val scope = if (forEveryone) DeleteScope.EVERYONE else DeleteScope.ME
        return when (val res = remote.clearHistory(conversationId, scope)) {
            is Resource.Success -> {
                applyHistoryCleared(res.data.conversationId, res.data.clearedBeforeSeq)
                Resource.Success(Unit)
            }
            is Resource.Error -> res
            Resource.Loading -> Resource.Success(Unit)
        }
    }

    override suspend fun deleteConversation(conversationId: String, forEveryone: Boolean): Resource<Unit> {
        val scope = if (forEveryone) DeleteScope.EVERYONE else DeleteScope.ME
        return when (val res = remote.deleteConversation(conversationId, scope)) {
            is Resource.Success -> {
                applyConversationDeleted(res.data.conversationId, res.data.clearedBeforeSeq)
                Resource.Success(Unit)
            }
            is Resource.Error -> res
            Resource.Loading -> Resource.Success(Unit)
        }
    }

    /**
     * Tarix tozalandi: `seq <= clearedBeforeSeq` bo'lgan qatorlar keshdan chiqadi.
     *
     * Ularni saqlashning ma'nosi yo'q — server ham boshqa qaytarmaydi (`before`, `after`,
     * `around` — hammasiga taalluqli), ya'ni kesh bilan server abadiy ajralib qolardi.
     */
    private suspend fun applyHistoryCleared(conversationId: String, clearedBeforeSeq: Int) {
        withContext(dispatchers.io) {
            q.transaction {
                q.deleteMessagesUpToSeq(conversationId, clearedBeforeSeq.toLong())
                q.setClearedBeforeSeq(clearedBeforeSeq.toLong(), conversationId)
                refreshPreview(conversationId)
            }
        }
    }

    /**
     * Suhbat ro'yxatdan olib tashlandi.
     *
     * ⚠️ Qator **o'chirilmaydi**, yashiriladi: yangi xabar kelsa suhbat aynan o'sha
     * `conversationId` bilan qaytadi va `touchConversation` bayroqni o'zi nolga tushiradi.
     * O'chirsak, kelgan xabar egasiz qolib, ro'yxatda umuman ko'rinmasdi.
     */
    private suspend fun applyConversationDeleted(conversationId: String, clearedBeforeSeq: Int) {
        withContext(dispatchers.io) {
            q.transaction {
                if (clearedBeforeSeq > 0) {
                    q.deleteMessagesUpToSeq(conversationId, clearedBeforeSeq.toLong())
                    q.setClearedBeforeSeq(clearedBeforeSeq.toLong(), conversationId)
                }
                q.clearLastMessage(conversationId)
                q.setConversationHidden(1L, conversationId)
            }
        }
    }

    /**
     * WS'dan kelgan «faqat menda o'chirildi» — boshqa qurilmamdagi amalning ko'zgusi.
     *
     * Serverga qayta so'rov yubormaymiz: hodisaning o'zi tasdiq va u idempotent. Sanoq
     * ham shu yerda tuzatiladi — yashiringan xabarni o'qib bo'lmaydi, ya'ni usiz badge
     * yonib qolardi.
     */
    private suspend fun hideLocally(conversationId: String, ids: List<String>) {
        val now = Clock.System.now().toEpochMilliseconds()
        withContext(dispatchers.io) {
            q.transaction {
                val rows = ids.mapNotNull { q.selectMessageById(it).executeAsOneOrNull() }
                if (rows.isEmpty()) return@transaction
                q.hideMessages(now, rows.map { it.id })
                rows.filter { it.senderId != currentUserId && it.deletedAt == null }
                    .forEach { q.decrementUnreadForDeleted(it.conversationId, it.seq) }
                refreshPreview(conversationId)
            }
        }
        forgetLocalMedia(ids.toSet())
    }

    /**
     * `?around=` — sitataga sakrash uchun tarixning o'rtasidan oyna.
     *
     * Qaytadi: nishon keshga tushdimi. `false` bo'lsa (tozalangan yoki fizik o'chirilgan)
     * chaqiruvchi sakramaydi — bo'sh ekranga olib borishdan ko'ra shunisi to'g'ri.
     */
    override suspend fun loadAround(conversationId: String, seq: Int): Resource<Boolean> =
        when (val res = remote.messages(conversationId, around = seq)) {
            is Resource.Success -> {
                storeMessages(res.data.items.map { it.toRow() })
                Resource.Success(res.data.items.any { it.seq == seq })
            }
            is Resource.Error -> res
            Resource.Loading -> Resource.Success(false)
        }


    /**
     * Suhbatlar ro'yxatidagi ko'rinishni keshdan qayta hisoblaydi.
     *
     * Oxirgi xabar yashirilganda `ConversationEntity` dagi nusxa eskirib qoladi va ro'yxatda
     * chatda **ko'rinmaydigan** matn turib qolardi. Hech nima qolmasa qator "Xabar yozing…"
     * holatiga tushadi.
     *
     * Tranzaksiya ichida chaqirilishi kutiladi.
     */
    private fun refreshPreview(conversationId: String) {
        val last = q.selectLastVisibleMessage(conversationId).executeAsOneOrNull()
        q.setLastMessagePreview(
            lastMessageBody = last?.body,
            lastMessageSenderId = last?.senderId,
            lastMessageType = last?.type,
            lastMessageDeleted = if (last?.deletedAt != null) 1L else 0L,
            lastMessageAt = last?.createdAt,
            id = conversationId,
        )
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
        forgetLocalMedia(setOf(messageId))
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
     * uchun qaytaradi (`handoff/03-WEBSOCKET.md`).
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
        attachmentTranscript = row.attachmentTranscript,
        stickerId = row.stickerId,
        stickerEmoji = row.stickerEmoji,
        stickerUrl = row.stickerUrl,
        albumId = row.albumId,
        replyToId = row.replyToId,
        replyToSeq = row.replyToSeq,
        replyToSenderId = row.replyToSenderId,
        replyToSenderName = row.replyToSenderName,
        replyToType = row.replyToType,
        replyToPreview = row.replyToPreview,
        replyToQuoteText = row.replyToQuoteText,
        replyToQuoteOffset = row.replyToQuoteOffset,
        replyToOriginalDeleted = row.replyToOriginalDeleted,
        callId = row.callId,
        callMedia = row.callMedia,
        callStatus = row.callStatus,
        callDurationMs = row.callDurationMs,
        callEndReason = row.callEndReason,
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
        attachmentTranscript = columns.transcript,
        id = messageId,
    )

    /**
     * Xabar(lar)ning local nusxalarini unutadi.
     *
     * Rasm baytlari xotiradan chiqadi, videoning keshdagi fayli esa **diskdan ham**
     * o'chiriladi: aks holda o'chirilgan yoki yuborilmay qolgan har bir video keshda
     * o'nlab MB bo'lib qolib ketardi va uni tozalaydigan hech kim yo'q edi.
     */
    private fun forgetLocalMedia(ids: Set<String>) {
        localImages.update { it - ids }
        // Fayllar `update` dan TASHQARIDA o'chiriladi: blok raqobat holatida bir necha marta
        // qayta ishga tushishi mumkin va yon ta'sir shuncha marta takrorlanardi.
        val dropped = localVideos.value.filterKeys { it in ids }.values
        localVideos.update { it - ids }
        dropped.forEach { deleteLocalFile(it.path) }
    }

    /** Keshdagi faylni o'chiradi; yo'q bo'lsa jim qaytadi. */
    private fun deleteLocalFile(path: String) {
        runCatching { SystemFileSystem.delete(Path(path), mustExist = false) }
    }

    /**
     * Yuklashni [uploads] da ko'rsatib turadi: boshida `0%`, keyin foiz, oxirida — **har
     * qanday holatda** o'chiriladi.
     *
     * `finally` shart: yuklash yiqilsa (tarmoq uzildi, `413`, bekor qilindi) yozuv qolib
     * ketsa, xabar `FAILED` bo'lib turgani holda ekranda halqa abadiy aylanardi.
     *
     * Foiz **butun qadam bilan** yangilanadi ([PROGRESS_STEP]): Ktor har bufer bo'shaganda
     * xabar beradi va 60 MB video uchun bu minglab qayta chizish degani.
     */
    private suspend fun <T> tracked(
        localId: String,
        fileName: String,
        sizeBytes: Long,
        block: suspend (UploadProgress) -> T,
    ): T {
        uploads.update { it + (localId to UploadState(0f, fileName, sizeBytes)) }
        return try {
            block { fraction ->
                uploads.update { current ->
                    val state = current[localId] ?: return@update current
                    val shown = state.progress ?: 0f
                    if (fraction - shown < PROGRESS_STEP) {
                        current
                    } else {
                        current + (localId to state.copy(progress = fraction))
                    }
                }
            }
        } finally {
            uploads.update { it - localId }
        }
    }

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

        /** Foiz shundan kam o'zgargan bo'lsa qayta chizmaymiz — 1% yetarlicha silliq. */
        const val PROGRESS_STEP = 0.01f

        /**
         * Siqish tezligi: bir soniyalik video shuncha soniyada qayta kodlanadi.
         *
         * O'rta qurilmada 720p apparat kodegi taxminan shu atrofda. Aniq son yo'q — u
         * kodekka ham, manba o'lchamiga ham bog'liq; bu yerda kerak bo'lgani **nisbat**,
         * mutlaq vaqt emas.
         */
        const val ENCODE_SECONDS_PER_SECOND = 0.5f

        /** Mobil internetning ehtiyotkor bahosi — sekundiga 1 MB. */
        const val UPLOAD_BYTES_PER_SECOND = 1024f * 1024f

        /** Siqilgan videoning kutilayotgan hajmi (`VideoCompressor.TARGET_BYTES` bilan bir xil). */
        const val COMPRESSED_TARGET_BYTES = 12L * 1024 * 1024

        /**
         * Siqish ulushining chegaralari.
         *
         * Ikkala chetda ham halqa "o'lik" ko'rinmasin: 0.8 dan yuqorisi yuklashni bir
         * lahzaga siqib qo'yadi, 0.2 dan pasti esa siqish paytida halqani qotib turgandek
         * ko'rsatadi.
         */
        const val MIN_PREPARE_SHARE = 0.2f
        const val MAX_PREPARE_SHARE = 0.8f

        /** Davomiylik noma'lum bo'lsa — eski xulq (teng ulush). */
        const val DEFAULT_PREPARE_SHARE = 0.5f

        const val MS_IN_SECOND = 1000f

        /** Server chegarasi: bitta so'rovda 100 tagacha id (`422 TOO_MANY_IDS`). */
        const val MAX_DELETE_IDS = 100

        /** Optimistik sitatadagi qisqartma — server ham ≤120 belgi beradi (§5.2). */
        const val REPLY_PREVIEW_LENGTH = 120
    }
}
