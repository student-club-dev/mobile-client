package dev.core.network.ws

import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Serverdan kelgan bitta hodisa: `["message:new", {...}]`. */
data class SocketIoEvent(val name: String, val data: JsonElement?)

/**
 * **Minimal Socket.IO klienti** (Engine.IO v4 + Socket.IO v5) — Ktor WebSocket transporti ustida.
 *
 * Nega qo'lda yozilgan: Socket.IO'ning rasmiy Kotlin/Multiplatform klienti yo'q (JVM kutubxonasi
 * iOS'da ishlamaydi), backend gateway'i esa aynan Socket.IO (`03-WEBSOCKET.md` §7–§9). Protokolning
 * bizga kerakli qismi kichik, shuning uchun shu yerda to'liq amalga oshirilgan:
 *
 * ```
 * Engine.IO ramka:  0=open  1=close  2=ping  3=pong  4=message
 * Socket.IO paket:  0=CONNECT  1=DISCONNECT  2=EVENT  3=ACK  4=CONNECT_ERROR
 * Shakl:            <tur>[<namespace>,][<ackId>]<json>
 * Misol:            42/chat,7["message:send",{...}]   →  43/chat,7[{"status":"sent",...}]
 * ```
 *
 * **Namespace** — `/chat` (URL yo'li emas!): handshake doim `{HOST}/socket.io/` ga boradi,
 * namespace esa CONNECT paketida yuboriladi. Token ikki joyda beriladi — handshake
 * so'rovining `Authorization` sarlavhasida **va** CONNECT paketining `auth` obyektida
 * (`03-WEBSOCKET.md` ikkalasini ham qo'llab-quvvatlaydi; qaysi biri o'qilishiga bog'liq bo'lmaslik uchun).
 *
 * Ulanish uzilsa [start] ichidagi sikl **avtomatik qayta ulanadi** (eksponensial kechikish).
 * Sessiya darhol uzilgan bo'lsa (token yaroqsiz — server sababni aytmaydi, faqat `disconnect`)
 * keyingi urinishdan oldin [tokenProvider] `refresh = true` bilan chaqiriladi.
 */
class SocketIoClient(
    private val client: HttpClient,
    /** Server manzili — faqat origin, yo'lsiz: `https://api.studentclub.uz`. */
    private val endpoint: String,
    /** Socket.IO namespace: `/chat`. */
    private val namespace: String,
    /**
     * Joriy access token. [refresh] `true` bo'lsa chaqiruvchi tokenni **yangilab** berishi
     * kutiladi (masalan arzon REST so'rovi orqali — Ktor `Auth` plagini uni o'zi yangilaydi).
     */
    private val tokenProvider: suspend (refresh: Boolean) -> String?,
    private val scope: CoroutineScope,
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _events = MutableSharedFlow<SocketIoEvent>(extraBufferCapacity = 64)

    /** Serverdan kelgan hodisalar oqimi (`message:new`, `typing`, `presence:update`...). */
    val events: SharedFlow<SocketIoEvent> = _events.asSharedFlow()

    private val _connected = MutableStateFlow(false)

    /** Namespace'ga CONNECT tasdiqlangandan keyin `true`. */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /**
     * Chiquvchi ramkalar navbati — bitta yozuvchi korutina uni bo'shatadi, shunda WS'ga
     * bir vaqtda bir nechta korutina yozmaydi. To'lib ketsa eng eskisi tashlanadi: chat
     * hodisalari (typing, read) eskirsa qadrsiz, muhim yo'l esa `sendWithAck` — u ulanish
     * yo'qligini o'zi tekshiradi va REST zaxirasiga tushadi.
     */
    private val outgoing = Channel<String>(capacity = 64, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)

    private val ackMutex = Mutex()
    private val acks = mutableMapOf<Int, CompletableDeferred<JsonElement?>>()
    private var ackCounter = 0

    private var loopJob: Job? = null

    /**
     * Ketma-ket nechta sessiya polling bilan o'tdi. `0` — keyingi urinish WebSocket'dan
     * boshlanadi. Ya'ni WS to'silgan tarmoqda har sessiyada bekorga urinib o'tirmaymiz,
     * lekin [WS_RETRY_EVERY] da bir marta qayta sinaymiz.
     */
    private var pollingRounds = 0

    /**
     * Server namespace'ni uzdi (`41/chat` yoki CONNECT_ERROR) — sessiyani tugatish kerak.
     * WebSocket'da buni soketning yopilishi bildirardi, polling'da esa transport tirik
     * qolaveradi: shu bayroqsiz ilova "ulanmoqda…" da abadiy aylanib, tokenni ham
     * yangilamasdi.
     */
    private var namespaceClosed = false

    /** Qayta ulanish siklini boshlaydi (allaqachon ishlayotgan bo'lsa — hech nima). */
    fun start() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch { reconnectLoop() }
    }

    /** Siklni to'xtatadi va soketni yopadi. */
    fun stop() {
        loopJob?.cancel()
        loopJob = null
        _connected.value = false
    }

    /**
     * Hodisa yuboradi (javob kutmasdan). Ulanish yo'q bo'lsa `false` — chaqiruvchi zaxira
     * yo'lni tanlashi mumkin.
     */
    suspend fun emit(event: String, payload: JsonObject): Boolean {
        if (!_connected.value) return false
        outgoing.send(eventPacket(event, payload, ackId = null))
        return true
    }

    /**
     * Hodisa yuboradi va **ack** (javob callback) kutadi — `message:send` shu yo'l bilan
     * ketadi. Ulanish yo'q, ack kelmadi yoki muddat o'tdi → `null` (REST zaxirasiga tushing).
     */
    suspend fun emitWithAck(
        event: String,
        payload: JsonObject,
        timeoutMs: Long = ACK_TIMEOUT_MS,
    ): JsonElement? {
        if (!_connected.value) return null
        val id = ackMutex.withLock {
            val next = ackCounter++
            acks[next] = CompletableDeferred()
            next
        }
        val waiter = ackMutex.withLock { acks[id] } ?: return null
        return try {
            outgoing.send(eventPacket(event, payload, ackId = id))
            withTimeoutOrNull(timeoutMs) { waiter.await() }
        } finally {
            ackMutex.withLock { acks.remove(id) }
        }
    }

    // ------------------------------------------------------------------------------------
    // Ulanish sikli
    // ------------------------------------------------------------------------------------

    private suspend fun reconnectLoop() {
        var attempt = 0
        // Birinchi urinishda tokenni yangilash shart emas — saqlangani odatda yaroqli.
        var refreshToken = false
        while (currentCoroutineContext().isActive) {
            val startedAt = Clock.System.now().toEpochMilliseconds()
            val outcome = runSession(refreshToken)
            val lasted = Clock.System.now().toEpochMilliseconds() - startedAt

            refreshToken = when (outcome) {
                // Sessiya YO'Q (kirmagan foydalanuvchi) — yangilashning ma'nosi yo'q:
                // refresh token ham yo'q, ya'ni `tokenProvider(refresh = true)` ichidagi
                // "arzon avtorizatsiyali so'rov" faqat 401 olib qaytadi. Aynan shu yo'l
                // login ekranida turgan ilovadan har urinishda `/v1/calls/ice-servers` ga
                // (chatda — `/v1/conversations` ga) so'rov yuborardi.
                SessionOutcome.NO_TOKEN -> false
                // Sessiya deyarli darhol tugadi → ehtimol token yaroqsiz. `03-WEBSOCKET.md`:
                // server sabab bildirmaydi, faqat `disconnect`. Shuning uchun keyingi
                // urinishdan oldin yangilaymiz.
                SessionOutcome.CONNECTED -> lasted < SHORT_SESSION_MS
                SessionOutcome.FAILED -> true
            }
            attempt = if (lasted > STABLE_SESSION_MS) 0 else attempt + 1

            if (!currentCoroutineContext().isActive) return
            // Kirilmagan holatda eksponensial kechikish noto'g'ri bo'lardi: bu xato emas,
            // shunchaki kutish. Tekshiruv local (tokenlar bazadan o'qiladi, tarmoq yo'q),
            // shuning uchun qisqa va o'zgarmas oraliq — kirilgan zahoti kanal ko'tariladi.
            if (outcome == SessionOutcome.NO_TOKEN) {
                attempt = 0
                delay(NO_SESSION_POLL_MS)
            } else {
                delay(backoffMs(attempt))
            }
        }
    }

    /** Bitta sessiya urinishining natijasi — [reconnectLoop] shunga qarab qaror qiladi. */
    private enum class SessionOutcome {
        /** Namespace'ga CONNECT tasdiqlandi (keyin uzilgan bo'lishi mumkin). */
        CONNECTED,

        /** Token bor edi, lekin ulanib bo'lmadi (tarmoq, proxy, yaroqsiz token). */
        FAILED,

        /** Saqlangan sessiya yo'q — ulanishga urinilmadi ham. */
        NO_TOKEN,
    }

    /**
     * Bitta sessiya — avval WebSocket, u ulanmasa **polling**.
     *
     * Nega ikki transport: Engine.IO ning o'zi ham shunday ishlaydi. WebSocket upgrade'ni
     * reverse-proxy (nginx'da `proxy_set_header Upgrade` unutilgan bo'lsa), korporativ
     * proxy yoki ba'zi mobil operatorlar to'sib qo'yishi mumkin — o'shanda polling ishlaydi
     * va chat jonli qolaveradi, faqat kechikish biroz kattaroq.
     *
     * Polling'ga tushib qolsak ham [WS_RETRY_EVERY] sessiyada bir marta WebSocket qayta
     * sinaladi: server tuzatilgan kuni ilova o'zi yaxshiroq transportga qaytadi.
     */
    private suspend fun runSession(refreshToken: Boolean): SessionOutcome {
        val token = runCatching { tokenProvider(refreshToken) }.getOrNull()
        // Sessiya yo'q — ulanishga urinishning ma'nosi yo'q (kirmagan foydalanuvchi).
        if (token.isNullOrBlank()) {
            Napier.d("WS: token yo'q — ulanmaymiz", tag = LOG_TAG)
            return SessionOutcome.NO_TOKEN
        }

        if (pollingRounds == 0) {
            namespaceClosed = false
            if (runWebSocketSession(token)) return SessionOutcome.CONNECTED
            Napier.w("WS transporti ulanmadi — polling'ga o'tamiz", tag = LOG_TAG)
        }

        namespaceClosed = false
        val connected = runPollingSession(token)
        // Polling ham ishlamasa hisoblagichni nolga qaytaramiz: muammo transportda emas,
        // tarmoqda — keyingi urinish yana WebSocket'dan boshlansin.
        pollingRounds = if (connected) (pollingRounds + 1) % WS_RETRY_EVERY else 0
        return if (connected) SessionOutcome.CONNECTED else SessionOutcome.FAILED
    }

    // --- WebSocket transporti -------------------------------------------------------------

    private suspend fun runWebSocketSession(token: String): Boolean {
        Napier.d("WS: ulanmoqda → ${handshakeUrl()}", tag = LOG_TAG)

        var session: DefaultClientWebSocketSession? = null
        var didConnect = false
        try {
            session = client.webSocketSession(handshakeUrl()) {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            val writer = scope.launch {
                for (frame in outgoing) session.send(Frame.Text(frame))
            }
            try {
                for (frame in session.incoming) {
                    if (frame !is Frame.Text) continue
                    if (handleFrame(session, frame.readText(), token)) didConnect = true
                    // Server namespace'ni uzdi — soketni ushlab turishning ma'nosi yo'q.
                    if (namespaceClosed) break
                }
            } finally {
                writer.cancel()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Tarmoq/handshake xatosi — sikl qayta uriniladi. Jimgina yutib yubormaymiz:
            // busiz "ulanmoqda…" da qotib qolgan chatning sababini topib bo'lmaydi.
            Napier.w("WS sessiyasi uzildi: ${e::class.simpleName}: ${e.message}", e, tag = LOG_TAG)
        } finally {
            endSession()
            runCatching { session?.close() }
        }
        return didConnect
    }

    /** Bitta matnli ramkani qayta ishlaydi. CONNECT tasdiqlangan bo'lsa `true`. */
    private suspend fun handleFrame(
        session: DefaultClientWebSocketSession,
        text: String,
        token: String,
    ): Boolean {
        when (text.firstOrNull()) {
            // Engine.IO OPEN — endi namespace'ga CONNECT yuboramiz (token `auth` ichida).
            SocketIoProtocol.ENGINE_OPEN -> session.send(Frame.Text(connectPacket(token)))
            SocketIoProtocol.ENGINE_PING -> session.send(Frame.Text(SocketIoProtocol.ENGINE_PONG.toString()))
            SocketIoProtocol.ENGINE_MESSAGE -> return handlePacket(text.substring(1))
            SocketIoProtocol.ENGINE_CLOSE -> session.close()
        }
        return false
    }

    // --- Polling transporti ---------------------------------------------------------------

    /**
     * Engine.IO **long-polling** sessiyasi — WebSocket'siz ham jonli kanal.
     *
     * ```
     * GET  /socket.io/?EIO=4&transport=polling            → 0{"sid":…,"pingInterval":25000,…}
     * POST /socket.io/?EIO=4&transport=polling&sid=<sid>  ← chiquvchi paketlar, javob: "ok"
     * GET  /socket.io/?EIO=4&transport=polling&sid=<sid>  → server paket yubormaguncha ushlab turadi
     * ```
     *
     * Bitta javobda bir nechta paket kelishi mumkin — ular `RECORD_SEPARATOR` (U+001E) bilan
     * ajratiladi. Server har `pingInterval` da `2` (ping) yuboradi, biz `3` (pong) bilan
     * javob beramiz; javob bermasak server `pingTimeout` dan keyin sessiyani yopadi.
     */
    private suspend fun runPollingSession(token: String): Boolean {
        Napier.d("WS: polling → ${pollingUrl(sid = null)}", tag = LOG_TAG)

        var didConnect = false
        var writer: Job? = null
        var sid: String? = null
        try {
            val handshake = httpPoll(pollingUrl(sid = null), token)
            val packets = SocketIoProtocol.splitPayload(handshake)
            sid = packets.firstNotNullOfOrNull { SocketIoProtocol.openSid(it) }
            if (sid == null) {
                Napier.w("Polling: handshake javobida `sid` yo'q — ${handshake.take(120)}", tag = LOG_TAG)
                return false
            }

            // OPEN kelgach namespace'ga CONNECT (token `auth` obyektida — WS'dagidek).
            postPackets(sid, token, listOf(connectPacket(token)))
            // Handshake javobidagi qolgan paketlar ham ishlansin (odatda bo'sh).
            packets.forEach { if (handleEnginePacket(it, sid, token)) didConnect = true }

            writer = scope.launch { pumpOutgoing(sid, token) }

            while (currentCoroutineContext().isActive && !namespaceClosed) {
                val body = httpPoll(pollingUrl(sid), token)
                // Bo'sh javob — server so'rovni shunchaki yopdi; qayta so'raymiz.
                if (body.isEmpty()) continue
                for (packet in SocketIoProtocol.splitPayload(body)) {
                    if (packet.firstOrNull() == SocketIoProtocol.ENGINE_CLOSE) {
                        Napier.d("Polling: server sessiyani yopdi", tag = LOG_TAG)
                        return didConnect
                    }
                    if (handleEnginePacket(packet, sid, token)) didConnect = true
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Napier.w("Polling sessiyasi uzildi: ${e::class.simpleName}: ${e.message}", e, tag = LOG_TAG)
        } finally {
            writer?.cancel()
            endSession()
        }
        return didConnect
    }

    /** Chiquvchi navbatni POST'lar bilan bo'shatadi. Navbatdagi paketlar bitta so'rovga birlashadi. */
    private suspend fun pumpOutgoing(sid: String, token: String) {
        for (first in outgoing) {
            val batch = mutableListOf(first)
            // Navbatda kutayotganlarni ham qo'shamiz — har `typing` uchun alohida so'rov
            // yubormaymiz (polling'da har so'rov qimmat).
            while (batch.size < MAX_BATCH) {
                batch += outgoing.tryReceive().getOrNull() ?: break
            }
            runCatching { postPackets(sid, token, batch) }
                .onFailure { Napier.w("Polling: yuborib bo'lmadi — ${it.message}", tag = LOG_TAG) }
        }
    }

    /** Engine.IO ramkasi (polling varianti — javob bir xil, faqat yo'llash usuli boshqa). */
    private suspend fun handleEnginePacket(packet: String, sid: String, token: String): Boolean {
        when (packet.firstOrNull()) {
            SocketIoProtocol.ENGINE_PING ->
                postPackets(sid, token, listOf(SocketIoProtocol.ENGINE_PONG.toString()))
            SocketIoProtocol.ENGINE_MESSAGE -> return handlePacket(packet.substring(1))
        }
        return false
    }

    /**
     * Long-poll so'rovi. Odatdagi 15 soniyalik chegara bu yerda YARAMAYDI: server ping
     * intervali 25 s, ya'ni javob shuncha kutishi normal — chegara qisqa bo'lsa sessiya
     * har safar uzilib turardi.
     */
    private suspend fun httpPoll(url: String, token: String): String {
        val response = client.get(url) {
            header(HttpHeaders.Authorization, "Bearer $token")
            timeout {
                requestTimeoutMillis = POLL_TIMEOUT_MS
                socketTimeoutMillis = POLL_TIMEOUT_MS
            }
        }
        if (!response.status.isSuccess()) error("polling GET ${response.status}")
        return response.bodyAsText()
    }

    private suspend fun postPackets(sid: String, token: String, packets: List<String>) {
        val response = client.post(pollingUrl(sid)) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Text.Plain)
            setBody(packets.joinToString(SocketIoProtocol.RECORD_SEPARATOR))
        }
        if (!response.status.isSuccess()) error("polling POST ${response.status}")
    }

    private fun pollingUrl(sid: String?): String {
        val base = "${endpoint.trimEnd('/')}/socket.io/?EIO=4&transport=polling"
        return if (sid == null) base else "$base&sid=$sid"
    }

    /**
     * Sessiya tugadi: holatni tozalaymiz (ikkala transport uchun ham bir xil).
     *
     * [NonCancellable] — bu `finally` da chaqiriladi va sessiya bekor qilingan bo'lishi
     * mumkin; usiz `withLock` darhol qayta uzilib, kutayotgan ack'lar bo'shatilmasdan
     * qolardi (chat esa timeout'gacha "yuborilmoqda" da turardi).
     */
    private suspend fun endSession() = withContext(NonCancellable) {
        _connected.value = false
        // Kutilayotgan ack'larni bo'shatamiz, aks holda `emitWithAck` timeout'gacha osilib
        // qolardi va xabar zaxira (REST) yo'liga kech tushardi.
        ackMutex.withLock {
            acks.values.forEach { it.complete(null) }
            acks.clear()
        }
    }

    /** Socket.IO paketi (Engine.IO `4` dan keyingi qism). CONNECT bo'lsa `true`. */
    private suspend fun handlePacket(raw: String): Boolean {
        // Boshqa namespace'ga tegishli paket — `null`, e'tiborsiz qoldiramiz.
        val packet = SocketIoProtocol.decode(namespace, raw) ?: return false
        when (packet.type) {
            SocketIoProtocol.CONNECT -> {
                Napier.d("WS: $namespace ga ulandi", tag = LOG_TAG)
                _connected.value = true
                return true
            }
            // CONNECT_ERROR — odatda token yaroqsiz; sikl qayta ulanib, tokenni yangilaydi.
            SocketIoProtocol.DISCONNECT, SocketIoProtocol.CONNECT_ERROR -> {
                Napier.w("WS: server uzdi (tur=${packet.type}) — ${packet.body}", tag = LOG_TAG)
                _connected.value = false
                namespaceClosed = true
            }
            SocketIoProtocol.EVENT -> {
                val array = packet.body.asJsonArray() ?: return false
                val name = array.getOrNull(0)?.jsonPrimitive?.content ?: return false
                _events.emit(SocketIoEvent(name, array.getOrNull(1)))
            }
            SocketIoProtocol.ACK -> {
                val id = packet.ackId ?: return false
                ackMutex.withLock { acks[id] }?.complete(packet.body.asJsonArray()?.getOrNull(0))
            }
        }
        return false
    }

    private fun String.asJsonArray(): JsonArray? =
        runCatching { json.parseToJsonElement(this) as? JsonArray }.getOrNull()

    /** `42/chat,[<ackId>]["<hodisa>", {…}]`. */
    private fun eventPacket(event: String, payload: JsonObject, ackId: Int?): String =
        SocketIoProtocol.encode(
            namespace,
            SocketIoProtocol.EVENT,
            ackId = ackId,
            body = buildJsonArray {
                add(JsonPrimitive(event))
                add(payload)
            },
        )

    /** `40/chat,{"token":…}` — `auth` obyekti CONNECT paketida ketadi (Socket.IO v3+). */
    private fun connectPacket(token: String): String = SocketIoProtocol.encode(
        namespace,
        SocketIoProtocol.CONNECT,
        ackId = null,
        body = buildJsonObject { put("token", JsonPrimitive(token)) },
    )

    /**
     * Socket.IO handshake manzili — **doim `/socket.io/`**, namespace bu yerga qo'shilmaydi
     * (u CONNECT paketida ketadi). Sxema `ws`/`wss` ga o'giriladi: Ktor'ning WebSockets
     * plagini `http(s)` manzilni oddiy so'rov deb qabul qiladi.
     */
    private fun handshakeUrl(): String {
        val origin = endpoint.trimEnd('/')
        val wsOrigin = when {
            origin.startsWith("https://") -> "wss://" + origin.removePrefix("https://")
            origin.startsWith("http://") -> "ws://" + origin.removePrefix("http://")
            else -> origin
        }
        return "$wsOrigin/socket.io/?EIO=4&transport=websocket"
    }

    private companion object {
        const val LOG_TAG = "ChatWS"

        const val ACK_TIMEOUT_MS = 10_000L

        /**
         * Long-poll so'rovining chegarasi. Server ping intervali 25 s, shuning uchun bundan
         * kattaroq bo'lishi shart — aks holda har poll o'z chegarasida uzilib, sessiya
         * behuda qayta qurilardi.
         */
        const val POLL_TIMEOUT_MS = 45_000L

        /** Bitta POST'ga birlashtiriladigan paketlarning maksimumi. */
        const val MAX_BATCH = 16

        /** Polling'da shuncha sessiyadan keyin WebSocket qayta sinaladi. */
        const val WS_RETRY_EVERY = 5

        /** Shundan qisqa sessiya — deyarli aniq auth muammosi. */
        const val SHORT_SESSION_MS = 3_000L

        /** Shundan uzun sessiya "barqaror" — kechikish hisoblagichi nolga qaytadi. */
        const val STABLE_SESSION_MS = 30_000L

        /**
         * Kirilmagan holatda tokenni qayta tekshirish oralig'i. Tarmoqqa chiqmaydi —
         * faqat local `TokenStore` o'qiladi, shuning uchun qisqa bo'lishi mumkin va
         * foydalanuvchi kirgach kanal deyarli darhol ko'tariladi.
         */
        const val NO_SESSION_POLL_MS = 2_000L

        fun backoffMs(attempt: Int): Long = when {
            attempt <= 0 -> 1_000L
            attempt >= 5 -> 30_000L
            else -> 1_000L * (1L shl attempt) // 2s, 4s, 8s, 16s
        }
    }
}
