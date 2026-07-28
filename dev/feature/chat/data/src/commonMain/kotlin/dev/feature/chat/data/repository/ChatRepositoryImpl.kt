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
import dev.core.network.media.MediaPurpose
import dev.core.network.media.MediaUploader
import dev.feature.chat.data.mapper.MediaContent
import dev.feature.chat.data.mapper.MessageRow
import dev.feature.chat.data.mapper.parseEnum
import dev.feature.chat.data.mapper.parseInstant
import dev.feature.chat.data.mapper.toDomain
import dev.feature.chat.data.mapper.toRow
import dev.feature.chat.data.realtime.ChatSocket
import dev.feature.chat.data.remote.ChatRemoteDataSource
import dev.feature.chat.domain.model.ConversationItem
import dev.feature.chat.domain.model.Message
import dev.feature.chat.domain.model.MessageStatus
import dev.feature.chat.domain.model.MessageType
import dev.feature.chat.domain.model.OutgoingImage
import dev.feature.chat.domain.model.Sticker
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
 * Tartib o'qi — **`seq`** (`chat.md` §1): tarix `?before=`, qayta ulanish `?after=`,
 * o'qildi kursori ham shu.
 */
class ChatRepositoryImpl(
    private val db: StudentClubDatabase,
    private val dispatchers: AppDispatchers,
    private val remote: ChatRemoteDataSource,
    private val socket: ChatSocket,
    private val tokenStore: TokenStore,
    private val media: MediaUploader,
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
                        // O'z xabarimiz bo'lib qaytdi (server ikkala tomonga yuboradi) —
                        // optimistik nusxani olib tashlaymiz, aks holda ikki marta ko'rinardi.
                        if (row.senderId == currentUserId) {
                            q.deleteSendingByBody(row.conversationId, row.body)
                        }
                        q.insert(row)
                        val incoming = if (row.senderId != currentUserId) 1L else 0L
                        q.touchConversation(row.body, row.senderId, row.createdAt, incoming, row.conversationId)
                    }
                }
                // Suhbat hali keshda yo'q (yangi suhbatdosh yozdi) — ro'yxatni yangilaymiz.
                if (!conversationCached(row.conversationId)) refreshConversations()
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
     * taymerni qaytadan boshlaydi (`chat.md` §11: ~5 soniya).
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
                    q.transaction {
                        res.data.items.forEach { item ->
                            // Uchala kursor ham serverdan keladi (spec 2026-07-28) — avvalgi
                            // "unread == 0 bo'lsa hammasi o'qilgan" taxmini kerak emas, va
                            // ✓/✓✓ holati ilova qayta ochilganda ham tiklanadi.
                            val readSeq = item.myReadSeq.toLong()
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
                                lastMessageBody = item.lastMessage?.body,
                                lastMessageSenderId = item.lastMessage?.senderId,
                                unreadCount = item.unreadCount.toLong(),
                                lastReadSeq = readSeq,
                                otherReadSeq = item.peerReadSeq.toLong(),
                                otherDeliveredSeq = item.peerDeliveredSeq.toLong(),
                            )
                            // Local ustunlarga (`archived`) tegmaydi.
                            q.updateConversation(
                                type = item.conversation.type.name,
                                lastMessageAt = item.conversation.lastMessageAt?.toEpochMilliseconds(),
                                otherId = item.other.id,
                                otherUsername = item.other.username,
                                otherFullName = item.other.fullName,
                                otherAvatarUrl = item.other.avatarUrl,
                                otherOnline = if (item.other.online) 1L else 0L,
                                otherLastSeenAt = item.other.lastSeenAt?.toEpochMilliseconds(),
                                lastMessageBody = item.lastMessage?.body,
                                lastMessageSenderId = item.lastMessage?.senderId,
                                unreadCount = item.unreadCount.toLong(),
                                lastReadSeq = readSeq,
                                otherReadSeq = item.peerReadSeq.toLong(),
                                otherDeliveredSeq = item.peerDeliveredSeq.toLong(),
                                id = item.conversation.id,
                            )
                            item.lastMessage?.let { q.insert(it.toRow()) }
                        }
                    }
                }
                Resource.Success(Unit)
            }
            is Resource.Error -> res
            Resource.Loading -> Resource.Success(Unit)
        }

    override suspend fun openDirect(studentId: String): Resource<String> {
        val res = remote.openDirect(studentId)
        // Yangi suhbat keshda yo'q — ro'yxatni yangilab, ikkinchi tomon ma'lumotini olamiz.
        if (res is Resource.Success) refreshConversations()
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
                // `hasMore` — "taxminan": oxirgi sahifa aynan `size` ta bo'lsa ham `true`
                // bo'lib qoladi. Bo'sh javob esa aniq tugaganini bildiradi.
                Resource.Success(res.data.items.isNotEmpty() && res.data.hasMore)
            }
            is Resource.Error -> res
            Resource.Loading -> Resource.Success(false)
        }
    }

    private suspend fun storeMessages(rows: List<MessageRow>) {
        if (rows.isEmpty()) return
        withContext(dispatchers.io) { q.transaction { rows.forEach { q.insert(it) } } }
    }

    // --- Yuborish ------------------------------------------------------------------------

    override suspend fun send(conversationId: String, body: String): Resource<Unit> {
        val text = body.trim()
        if (text.isEmpty()) return errorOf(AppException.Validation("Xabar bo'sh."))
        if (text.length > MAX_BODY_LENGTH) {
            return errorOf(AppException.Validation("Xabar $MAX_BODY_LENGTH belgidan uzun bo'lmasin."))
        }
        val me = currentUserId ?: return errorOf(AppException.Unauthorized())

        // Har xabar uchun YANGI kalit: noyoblik `(jo'natuvchi, clientMsgId)` bo'yicha
        // tekshiriladi va suhbat hisobga OLINMAYDI — takroriy kalit boshqa suhbatdagi
        // eski xabarni qaytarib yuborardi.
        val clientMsgId = randomClientMsgId()
        val now = Clock.System.now().toEpochMilliseconds()
        val localId = LOCAL_ID_PREFIX + clientMsgId

        // Yakka emoji — stiker: foydalanuvchi qo'lda yozgani ham panelda tanlagani kabi
        // katta qilib chiziladi (Telegram/WhatsApp qoidasi).
        val type = MediaContent.detect(text)

        // Optimistik ko'rinish — foydalanuvchi xabarni darhol ko'radi.
        withContext(dispatchers.io) {
            q.transaction {
                q.insert(
                    MessageRow(
                        id = localId,
                        conversationId = conversationId,
                        senderId = me,
                        seq = 0L,
                        type = type.name,
                        body = text,
                        createdAt = now,
                        clientMsgId = clientMsgId,
                        status = MessageStatus.SENDING.name,
                    ),
                )
                q.touchConversation(text, me, now, 0L, conversationId)
            }
        }
        return deliver(conversationId, text, clientMsgId, localId, type)
    }

    override suspend fun sendSticker(conversationId: String, sticker: Sticker): Resource<Unit> =
        // Tanasi — emojining o'zi: turini [MediaContent] aniqlaydi, ya'ni alohida yo'l kerak emas.
        send(conversationId, sticker.emoji)

    override suspend fun sendImages(
        conversationId: String,
        images: List<OutgoingImage>,
    ): Resource<Unit> {
        if (images.isEmpty()) return Resource.Success(Unit)
        if (images.size > MAX_ALBUM_SIZE) {
            return errorOf(AppException.Validation("Bir martada $MAX_ALBUM_SIZE tagacha rasm yuboriladi."))
        }
        val me = currentUserId ?: return errorOf(AppException.Unauthorized())

        // Bitta albom — ekranda bitta to'r bo'lib chiziladi.
        val albumId = randomClientMsgId()

        // 1-qadam: HAMMASI darhol ekranga chiqadi. Yuklash sekundlab davom etadi, foydalanuvchi
        // esa tanlagan rasmlarini shu zahoti ko'rishi kerak.
        val pending = images.map { image ->
            val clientMsgId = randomClientMsgId()
            val localId = LOCAL_ID_PREFIX + clientMsgId
            withContext(dispatchers.io) {
                q.insert(
                    MessageRow(
                        id = localId,
                        conversationId = conversationId,
                        senderId = me,
                        seq = 0L,
                        type = MessageType.IMAGE.name,
                        // Havola hali yo'q — yuklangach [ChatQueries.setMessageMedia] to'ldiradi.
                        body = "",
                        createdAt = Clock.System.now().toEpochMilliseconds(),
                        clientMsgId = clientMsgId,
                        status = MessageStatus.SENDING.name,
                        albumId = albumId,
                    ),
                )
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
        val url = uploadOrNull(item.image)
            ?: return fail(item.localId, "Rasm yuklanmadi — qayta urinib ko'ring")

        // Tana endi havola: `message:new` o'z xabarimiz bo'lib qaytganda optimistik nusxa
        // aynan shu matn bo'yicha topib o'chiriladi (`deleteSendingByBody`).
        withContext(dispatchers.io) {
            q.transaction {
                q.setMessageMedia(url, url, null, item.localId)
                q.touchConversation(
                    url,
                    currentUserId.orEmpty(),
                    Clock.System.now().toEpochMilliseconds(),
                    0L,
                    conversationId,
                )
            }
        }
        val result = deliver(
            conversationId = conversationId,
            text = url,
            clientMsgId = item.clientMsgId,
            localId = item.localId,
            type = MessageType.IMAGE,
            albumId = albumId,
        )
        // Server havolasi bor — local nusxa endi kerak emas.
        localImages.update { it - item.localId }
        return result
    }

    /**
     * `POST /v1/media/upload` — rasm uchun **bugun ishlaydigan yagona** yo'l.
     *
     * ⚠️ `purpose` sifatida `LISTING` yuboriladi: backendda chat uchun alohida tur yo'q
     * (`CHAT_MEDIA_AND_CALLS_BACKEND.md` §1.1 uni qo'shishni so'raydi). Bu faqat serverdagi
     * papka nomiga ta'sir qiladi.
     */
    private suspend fun uploadOrNull(image: OutgoingImage): String? = runCatching {
        media.upload(image.bytes, image.fileName, MediaPurpose.LISTING).url
    }.getOrNull()?.takeIf { it.isNotBlank() }

    override suspend fun retry(messageId: String): Resource<Unit> {
        val message = withContext(dispatchers.io) {
            q.selectMessageById(messageId).executeAsOneOrNull()
        } ?: return errorOf(AppException.NotFound())
        // Qayta urinish AYNAN o'sha kalit bilan ketadi — server takror xabar yaratmaydi.
        val clientMsgId = message.clientMsgId
            ?: return errorOf(AppException.Unknown("Bu xabarni qayta yuborib bo'lmaydi."))

        withContext(dispatchers.io) { q.setMessageStatus(MessageStatus.SENDING.name, message.id) }

        val type = parseEnum(message.type, MessageType.TEXT)
        // Rasm yuklanmay qolgan bo'lsa tana bo'sh — avval yuklashni takrorlaymiz.
        if (type == MessageType.IMAGE && message.body.isBlank()) {
            val bytes = localImages.value[message.id]
                ?: return fail(message.id, "Rasm topilmadi — uni qaytadan tanlang")
            return uploadAndDeliver(
                conversationId = message.conversationId,
                item = PendingImage(OutgoingImage(bytes, RETRY_FILE_NAME), clientMsgId, message.id),
                albumId = message.albumId,
            )
        }
        return deliver(
            conversationId = message.conversationId,
            text = message.body,
            clientMsgId = clientMsgId,
            localId = message.id,
            type = type,
            albumId = message.albumId,
        )
    }

    /**
     * Yuborishning ikki yo'li: avval WS (ack bilan), u ishlamasa REST. Ikkalasi ham bir xil
     * [clientMsgId] ni ishlatadi — ikki marta ketib qolsa ham server bitta xabar yaratadi (C6).
     */
    private suspend fun deliver(
        conversationId: String,
        text: String,
        clientMsgId: String,
        localId: String,
        type: MessageType,
        albumId: String? = null,
    ): Resource<Unit> {
        val ack = socket.send(conversationId, clientMsgId, text)
        if (ack != null) {
            if (ack.isSent) {
                replaceLocal(
                    localId,
                    MessageRow(
                        id = ack.id!!,
                        conversationId = conversationId,
                        senderId = currentUserId.orEmpty(),
                        seq = ack.seq!!.toLong(),
                        type = type.name,
                        body = text,
                        createdAt = parseInstant(ack.createdAt).takeIf { it > 0L }
                            ?: Clock.System.now().toEpochMilliseconds(),
                        clientMsgId = clientMsgId,
                        attachmentUrl = text.takeIf { type == MessageType.IMAGE },
                        albumId = albumId,
                    ),
                )
                return Resource.Success(Unit)
            }
            // WS xatosi konvertsiz keladi (`{ code, message }`) — matnni to'g'ridan-to'g'ri
            // ko'rsatamiz; `NOT_CONNECTED` uchun server tushunarli matn beradi.
            return fail(localId, ack.error?.text ?: "Xabar yuborilmadi")
        }

        // WS ulanmagan / ack kelmadi → zaxira yo'l.
        return when (val res = remote.send(conversationId, text, clientMsgId)) {
            is Resource.Success -> {
                // `albumId` serverdan kelmaydi — local guruhlash yo'qolmasin uchun qo'shamiz.
                replaceLocal(localId, res.data.toRow(clientMsgId).copy(albumId = albumId))
                Resource.Success(Unit)
            }
            is Resource.Error -> fail(localId, res.message, res.error)
            Resource.Loading -> Resource.Success(Unit)
        }
    }

    /** Optimistik qatorni serverning haqiqiy (id + seq) qatoriga almashtiradi. */
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

    // --- Kursorlar -----------------------------------------------------------------------

    override suspend fun markRead(conversationId: String) {
        val seq = maxSeq(conversationId)
        // Local holat darhol yangilanadi — badge tarmoqni kutmasin.
        withContext(dispatchers.io) { q.markRead(seq.toLong(), conversationId) }
        if (seq <= 0) return
        // WS "eng yaxshi harakat" — javob qaytarmaydi; ulanmagan bo'lsa REST orqali.
        if (!socket.markReadOrFalse(conversationId, seq)) remote.markRead(conversationId, seq)
    }

    override suspend fun markDelivered(conversationId: String) {
        val seq = maxSeq(conversationId)
        // Yetkazildi kursorining REST varianti yo'q — faqat WS (chat.md §14).
        if (seq > 0) socket.markDelivered(conversationId, seq)
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

    /** WS ulangan bo'lsa yuboradi va `true` qaytaradi; aks holda `false` (REST zaxirasi). */
    private suspend fun ChatSocket.markReadOrFalse(conversationId: String, seq: Int): Boolean {
        if (!connected.value) return false
        markRead(conversationId, seq)
        return true
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
        attachmentUrl = row.attachmentUrl,
        attachmentThumbUrl = row.attachmentThumbUrl,
        attachmentWidth = row.attachmentWidth,
        attachmentHeight = row.attachmentHeight,
        albumId = row.albumId,
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
        const val MAX_BODY_LENGTH = 4000
        const val LOCAL_ID_PREFIX = "local:"

        /** Bir albomdagi rasmlar chegarasi — backend ham shu sonni kutadi (spec §3). */
        const val MAX_ALBUM_SIZE = 10

        /** Qayta yuborishda asl fayl nomi saqlanmagan — kengaytma MIME uchun yetarli. */
        const val RETRY_FILE_NAME = "image.jpg"
    }
}
