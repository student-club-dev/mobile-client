package dev.feature.calls.data.session

import dev.core.common.Resource
import dev.core.common.error.AppException
import dev.feature.calls.data.engine.CallEngine
import dev.feature.calls.data.engine.CallEngineEvent
import dev.feature.calls.data.engine.CallEngineFactory
import dev.feature.calls.data.realtime.CallAck
import dev.feature.calls.data.realtime.CallsSocket
import dev.feature.calls.data.realtime.WsCallIncoming
import dev.feature.calls.domain.model.CallDeclineReason
import dev.feature.calls.domain.model.CallDirection
import dev.feature.calls.domain.model.CallEndReason
import dev.feature.calls.domain.model.CallErrorCode
import dev.feature.calls.domain.model.CallMedia
import dev.feature.calls.domain.model.CallSession
import dev.feature.calls.domain.model.CallStatus
import dev.feature.calls.domain.model.IceServers
import dev.feature.calls.domain.repository.CallController
import dev.feature.calls.domain.repository.CallRepository
import dev.feature.calls.domain.repository.callsUnavailable
import dev.feature.connections.domain.model.StudentSummary
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Qo'ng'iroqning **holat mashinasi** — signalizatsiya (`/calls`) va media qatlamini
 * (`CallEngine`) bog'laydigan yagona joy.
 *
 * Serverning holat mashinasi (`handoff/09-CALLS-PROTOCOL.md` §5) shu yerda aks etadi:
 *
 * ```
 * call:invite ──► RINGING ── call:accept ──► CONNECTING ── call:connected ──► ACTIVE
 * ```
 *
 * ⚠️ **`ok` ack yopilish degani emas.** Tugatuvchi hodisa joriy holatga mos kelmasa server
 * uni jim qabul qiladi va hech narsa qilmaydi. Shuning uchun UI hech qachon ack'ga qarab
 * yopilmaydi — faqat `call:ended` / `call:declined` / `call:canceled` yoki o'zimizning
 * local teardown'imiz bilan.
 *
 * Ilova bo'ylab **bitta** nusxa: bir vaqtda bitta jonli qo'ng'iroq bo'ladi.
 */
class CallSessionManager(
    private val socket: CallsSocket,
    private val engineFactory: CallEngineFactory,
    private val repository: CallRepository,
    /**
     * Joriy access token — `call:auth` uchun.
     *
     * Ktor'ning `Auth` plagini tokenni fonda o'zi yangilaydi va buni hech qayerga
     * e'lon qilmaydi, shuning uchun bu yerda qiymat **kuzatiladi** ([watchToken]).
     */
    private val accessToken: () -> String?,
    /** Tizim ilmog'i — Android'da old plan xizmati, iOS'da (keyinroq) CallKit. */
    private val presence: CallPresence,
    /** Eshitiladigan tomoni: jiringlash, gudok, tugash signali va ovoz marshruti. */
    private val audio: CallAudio,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
) : CallController {

    private val _session = MutableStateFlow<CallSession?>(null)
    override val session: StateFlow<CallSession?> = _session.asStateFlow()

    /** Bir vaqtda ikkita qo'ng'iroq qurilmasin (ikkala tugma bosilishi, glare). */
    private val mutex = Mutex()

    private var engine: CallEngine? = null
    private var engineJob: Job? = null
    private var ringingTimer: Job? = null
    private var connectingTimer: Job? = null
    private var maxDurationTimer: Job? = null

    /** Ulanish hodisasi allaqachon yuborilganmi — `call:connected` bir marta yetadi. */
    private var connectedSent = false

    /** ICE tiklash urinishlari soni — cheksiz sikl bo'lmasin. */
    private var iceRestartAttempts = 0
    private var iceRecoveryTimer: Job? = null

    private var socketJobs: List<Job> = emptyList()

    // -------------------------------------------------------------------------------------
    // Hayot sikli
    // -------------------------------------------------------------------------------------

    override fun start() {
        if (socketJobs.isNotEmpty()) return
        socket.start()
        socketJobs = listOf(
            scope.launch { socket.incoming.collect(::onIncoming) },
            scope.launch { socket.accepted.collect { onAccepted(it.callId, it.sdp, it.relayOnly) } },
            scope.launch { socket.ringing.collect { onPeerRinging(it.callId) } },
            scope.launch { socket.taken.collect { onTaken(it.callId) } },
            scope.launch { socket.declined.collect { onRemoteClose(it.callId, CallStatus.DECLINED, reasonOf(it.reason)) } },
            scope.launch { socket.canceled.collect { onRemoteClose(it.callId, CallStatus.CANCELED, CallEndReason.CANCELED) } },
            scope.launch { socket.ended.collect { onEnded(it.callId, it.reason) } },
            scope.launch { socket.ice.collect { onRemoteIce(it.callId, it) } },
            scope.launch { socket.renegotiate.collect { onRemoteRenegotiate(it.callId, it.sdp) } },
            scope.launch { socket.mediaState.collect { onRemoteMediaState(it.callId, it.audioEnabled, it.videoEnabled) } },
            scope.launch { watchReconnect() },
            scope.launch { watchToken() },
        )

        // Sovuq start: jarayon o'lgan, tizimdagi jiringlash oynasi esa qolib ketgan
        // bo'lishi mumkin (u ilova jarayonidan uzoq yashaydi). Sessiya yo'q ekan, u
        // oyna baribir foydasiz: javob berish uchun kerak bo'lgan SDP taklifi jarayon
        // bilan birga yo'qolgan, ya'ni "Javob berish" ishlamasdi.
        //
        // ⚠️ Bu yerda serverdan SO'RALMAYDI. Bu metod ilova har ochilganda ishlaydi
        // (`CallHost` ildizda, hatto kirish ekranida ham), ya'ni `GET /calls/active`
        // qo'yilsa u har ishga tushishda bitta keraksiz so'rov bo'lardi — kirmagan
        // foydalanuvchida esa `401`. Javob esa hech narsani o'zgartirmasdi: qo'ng'iroq
        // serverda tirik bo'lsa ham biz unga qo'shila olmaymiz.
        if (_session.value == null) presence.onCallEnded()
    }

    override fun stop() {
        scope.launch { hangUp() }
        socketJobs.forEach(Job::cancel)
        socketJobs = emptyList()
        socket.stop()
    }

    /**
     * Socket qayta ulanganda jonli qo'ng'iroq uchun **darhol** `call:connected` yuboradi.
     *
     * Usiz: ishtirokchining socket'i uzilganda server 20 soniyalik oyna quradi va uni faqat
     * o'sha talabadan, o'sha qo'ng'iroq uchun kelgan `call:*` freym bekor qiladi. Qayta
     * ulanishning o'zi yetarli emas — yangi socket hech qanday `callId` olib kelmaydi.
     * Natijada media **peer-to-peer oqib turgan sog'lom** qo'ng'iroq 20 soniyadan keyin
     * `call:ended { FAILED }` bilan yopilardi. Amalda bu — lift, tunnel, metro, Wi-Fi →
     * mobil o'tish, ya'ni **har kuni** (`handoff/09-CALLS-PREREQUISITES.md` §2).
     */
    private suspend fun watchReconnect() {
        // `drop(1)` — joriy qiymat emas, faqat KEYINGI o'zgarishlar: birinchi ulanishda
        // hali hech qanday qo'ng'iroq yo'q.
        socket.connected.drop(1).collect { online ->
            if (!online) return@collect
            val live = _session.value?.takeIf { it.status.isLive && it.callId.isNotEmpty() } ?: return@collect
            val ack = socket.connected(live.callId)
            // `CALL_NOT_FOUND` — «qo'ng'iroq tugadi», xato holati emas: oyna yopilib
            // ulgurgan. UI shunda jimgina yopiladi.
            if (ack?.error?.code == CallErrorCode.CALL_NOT_FOUND) {
                closeLocally(CallStatus.FAILED, CallEndReason.FAILED)
            } else if (ack == null) {
                // Ack umuman kelmadi — socket yana uzilgan yoki server jim. Bunda
                // qo'ng'iroq tirikmi degan savolga REST javob beradi.
                verifyActiveCall()
            }
        }
    }

    /**
     * `GET /v1/calls/active` bo'yicha **jonli sessiyani** serverga solishtiradi.
     *
     * ⚠️ Faqat [watchReconnect] dan, faqat sessiya BOR bo'lganda va faqat `call:connected`
     * ack'i umuman kelmaganda chaqiriladi. Ya'ni bu so'rov ilova ochilganda emas, aynan
     * "socket qaytdi, lekin qo'ng'iroq tirikmi — bilmayapmiz" holatida ketadi. Uni
     * `start()` ga qo'yish har ishga tushishda bitta keraksiz so'rov degani bo'lardi
     * (`CallHost` ildizda turadi, hatto kirish ekranida ham).
     *
     * Bu tekshiruv faqat **yopadi**, hech qachon ochmaydi: javob berish uchun SDP taklifi
     * kerak, u esa faqat `call:incoming` bilan keladi.
     *
     * Poyga himoyasi: REST so'rovi ketayotganda haqiqiy `call:incoming` kelib qolishi
     * mumkin. Shuning uchun javob qaytganda sessiya **o'zgarmaganligi** tekshiriladi —
     * o'zgargan bo'lsa tegilmaydi.
     */
    private suspend fun verifyActiveCall() {
        val before = _session.value ?: return
        val result = repository.activeCall()
        // Tarmoq xatosi — hech narsa qilinmaydi. "Bilmayman" hech qachon "tugadi" degani
        // emas: aks holda bitta uzilgan so'rov jonli qo'ng'iroqni yopib qo'yardi.
        val active = when (result) {
            is Resource.Success -> result.data
            else -> return
        }
        if (active != null && active.status.isLive && active.callId == before.callId) return

        // Serverda bu qo'ng'iroq jonli emas.
        val current = _session.value
        if (current !== before) return
        if (current.status.isLive) {
            Napier.i("calls: server jonli qo'ng'iroqni bilmaydi — local sessiya yopiladi")
            // Statistika YUBORILMAYDI: bu qo'ng'iroq bizning tomonimizda o'lchanmagan
            // (jarayon o'lgan yoki media umuman oqmagan).
            closeLocally(CallStatus.ENDED, CallEndReason.CANCELED, sendStats = false)
        }
    }

    /**
     * Access token yangilanganini **kuzatadi** va ochiq socket'ga `call:auth` yuboradi.
     *
     * Nega kuzatuv, hodisa emas: tokenni Ktor'ning `Auth` plagini `TOKEN_EXPIRED` kelganda
     * fonda o'zi yangilaydi va bu haqda hech kimga xabar bermaydi. Socket esa handshake'da
     * ko'rgan `exp` ni **eslab qoladi** — ya'ni yangilanish uning uchun ko'rinmas.
     *
     * Buzilish stsenariysi aniq: odam ilovani ochib qo'yib, 20 daqiqa boshqa ish bilan
     * band bo'ladi; unga qo'ng'iroq keladi, «Javob berish» ni bosadi va `TOKEN_EXPIRED`
     * oladi. 45 soniyadan keyin qo'ng'iroq `MISSED` bo'ladi, chaquvchi esa «javob bermadi»
     * deb o'ylaydi (`handoff/09-CALLS-PREREQUISITES.md` §3).
     */
    private suspend fun watchToken() {
        var lastSent = accessToken()
        while (true) {
            delay(TOKEN_POLL_MS)
            val current = accessToken() ?: continue
            if (current == lastSent) continue
            if (!socket.connected.value) continue
            socket.auth(current)
            lastSent = current
        }
    }

    override fun onTokenRefreshed(accessToken: String) {
        scope.launch { socket.auth(accessToken) }
    }

    // -------------------------------------------------------------------------------------
    // Chiquvchi qo'ng'iroq
    // -------------------------------------------------------------------------------------

    override suspend fun call(peer: StudentSummary, media: CallMedia): String? = mutex.withLock {
        if (_session.value != null) return "Siz allaqachon qo'ng'iroqdasiz."

        // 1. TURN hisobi. 503 — «qo'ng'iroq hozircha mavjud emas», server yiqilgani emas.
        val ice = when (val result = repository.iceServers()) {
            is Resource.Success -> result.data
            is Resource.Error -> return result.error?.takeIf(AppException::callsUnavailable)
                ?.let { "Qo'ng'iroq hozircha mavjud emas." }
                ?: result.message
            Resource.Loading -> return "Qo'ng'iroqni boshlab bo'lmadi."
        }

        // 2. Media qatlami. `relayOnly` hali noma'lum (u juftlikka bog'liq va `invite`
        //    ack'ida keladi), shuning uchun ENG QAT'IY variantdan boshlaymiz: relay.
        //    Aks holda offer'ga host/srflx nomzod tushib, IP manzil taklif bilan birga
        //    ketardi — aynan `relayOnly` to'sadigan narsa (`…PROTOCOL.md` §11).
        //
        // ⚠️ Yagona istisno — TURN umuman berilmagan holat. O'shanda `relay` siyosati
        //    himoya emas, kafolatlangan muvaffaqiyatsizlik bo'lardi: yig'iladigan nomzod
        //    qolmaydi va qo'ng'iroq 30 soniya "Ulanmoqda" da turib `FAILED` bo'ladi.
        val relayOnly = ice.hasTurn
        if (!relayOnly) {
            Napier.w("Qo'ng'iroq: TURN yo'q — relay majburlanmaydi", tag = LOG_TAG)
        }
        val newEngine = engineFactory.create()
        val offer = newEngine.createOffer(media, relayOnly = relayOnly, iceServers = ice.servers)
        if (offer == null) {
            newEngine.close()
            return "Mikrofonga ruxsat berilmagan yoki qurilma band."
        }

        // ⚠️ Sessiya `attach` dan OLDIN quriladi: `attach` tizim ilmog'ini chaqiradi
        // (Android'da old plan xizmati) va u kimga qo'ng'iroq qilinayotganini hamda
        // kamera yoqilganini sessiyadan o'qiydi. Teskari tartibda bildirishnoma nomsiz
        // chiqar, video qo'ng'iroqda esa `camera` turi umuman e'lon qilinmasdi.
        _session.value = CallSession(
            callId = "",
            peer = peer,
            direction = CallDirection.OUTGOING,
            media = media,
            status = CallStatus.RINGING,
            relayOnly = relayOnly,
            cameraEnabled = media == CallMedia.VIDEO,
            speakerOn = media == CallMedia.VIDEO,
        )
        attach(newEngine)

        // 3. Taklif. Shu paytgacha server hech narsa bilmaydi — yuqoridagi bosqichlarning
        //    birortasi yiqilsa hech qanday chegara sarflanmaydi.
        val ack = socket.invite(calleeId = peer.id, media = media.name, sdp = offer)
        if (ack == null || !ack.isOk || ack.callId == null) {
            teardown()
            return ackError(ack)
        }

        // Server «bu juftlik avval gaplashgan» desa (`relayOnly = false`) siyosat
        // bo'shatiladi: endi host/srflx nomzodlar ham yig'iladi va bir tarmoqdagi ikki
        // telefon TURN'siz, sezilarli tez ulanadi.
        val serverRelayOnly = ack.relayOnly ?: true
        if (!serverRelayOnly) engine?.relaxIceTransportPolicy()

        _session.update {
            it.copy(
                callId = ack.callId,
                relayOnly = serverRelayOnly && it.relayOnly,
                ringingExpiresAt = parseInstant(ack.expiresAt),
            )
        }
        // Endi qo'ng'iroq haqiqatan mavjud — quloqqa gudok beriladi.
        audio.startOutgoingRingback()
        startRingingTimer()
        return null
    }

    // -------------------------------------------------------------------------------------
    // Kiruvchi qo'ng'iroq
    // -------------------------------------------------------------------------------------

    /**
     * `call:incoming` — chaqirilganning **barcha** qurilmalariga keladi.
     *
     * Band bo'lsak taklif darhol `call:decline { BUSY }` bilan yopiladi: server bu holatni
     * o'zi bilmaydi (uning `CALL_BUSY` tekshiruvi faqat chaquvchi tomonda ishlaydi).
     */
    private suspend fun onIncoming(event: WsCallIncoming) = mutex.withLock {
        if (_session.value != null) {
            socket.decline(event.callId, CallDeclineReason.BUSY.name)
            return
        }
        pendingOffer = event.sdp
        _session.value = CallSession(
            callId = event.callId,
            peer = event.caller.toSummary(),
            direction = CallDirection.INCOMING,
            media = parseEnum(event.media, CallMedia.AUDIO),
            status = CallStatus.RINGING,
            relayOnly = event.relayOnly,
            conversationId = event.conversationId,
            ringingExpiresAt = parseInstant(event.expiresAt),
            cameraEnabled = false,
            speakerOn = parseEnum(event.media, CallMedia.AUDIO) == CallMedia.VIDEO,
        )
        // Telefon jiringlaydi va tebranadi — tizim qo'ng'irog'idagidek.
        audio.startIncomingRinging()
        presence.onIncomingCall(
            peerName = _session.value?.peer?.fullName.orEmpty(),
            video = parseEnum(event.media, CallMedia.AUDIO) == CallMedia.VIDEO,
        )
        // Chaquvchiga «telefon jiringlayapti» deymiz. Bu freym bir vaqtda bizning
        // mavjudligimizni ham qayd etadi va uzilish oynasini to'g'ri ishlatadi.
        socket.sendRinging(event.callId)
        startRingingTimer()
    }

    /** `call:incoming` dagi offer — javob berilgandagina qo'llanadi. */
    private var pendingOffer: String? = null

    override suspend fun accept(): String? = mutex.withLock {
        val current = _session.value ?: return "Qo'ng'iroq tugadi."
        if (current.direction != CallDirection.INCOMING || current.status != CallStatus.RINGING) {
            return "Qo'ng'iroqqa javob berib bo'lmaydi."
        }
        val offer = pendingOffer ?: return "Qo'ng'iroq tugadi."

        val ice = when (val result = repository.iceServers()) {
            is Resource.Success -> result.data
            is Resource.Error -> IceServers() // TURN olinmasa ham urinib ko'ramiz: STUN'siz
            Resource.Loading -> IceServers() // ham local tarmoqda ulanish bo'lishi mumkin.
        }

        // Chaquvchining talabi (`relayOnly`) hurmat qilinadi, lekin TURN olinmagan bo'lsa
        // uni majburlash qo'ng'iroqni o'ldirardi — qarang [call] dagi izoh.
        val relayOnly = current.relayOnly && ice.hasTurn
        val newEngine = engineFactory.create()
        val answer = newEngine.createAnswer(
            remoteOfferSdp = offer,
            media = current.media,
            relayOnly = relayOnly,
            iceServers = ice.servers,
        )
        if (answer == null) {
            newEngine.close()
            return "Mikrofonga ruxsat berilmagan yoki qurilma band."
        }
        attach(newEngine)

        val ack = socket.accept(current.callId, answer)
        if (ack == null || !ack.isOk) {
            // `INVALID_CALL_STATE` — boshqa qurilmam avvalroq javob berdi. Bu xato emas,
            // shu qurilmada qo'ng'iroq shunchaki yopiladi (`call:taken` ham keladi).
            teardown()
            return if (ack?.error?.code == CallErrorCode.INVALID_CALL_STATE) null else ackError(ack)
        }
        cancelRingingTimer()
        // Jiringlash to'xtaydi va ovoz suhbat rejimiga o'tadi — javob berilgan zahoti,
        // media oqishini kutmasdan: aks holda birinchi soniyalarda ovoz eshitilmasdi.
        _session.update { it.copy(status = CallStatus.CONNECTING, relayOnly = relayOnly && (ack.relayOnly ?: true)) }
        audio.onCallActive(_session.value?.speakerOn == true)
        startConnectingTimer()
        return null
    }

    override suspend fun decline() {
        val current = _session.value ?: return
        if (current.direction == CallDirection.INCOMING && current.status == CallStatus.RINGING) {
            socket.decline(current.callId, CallDeclineReason.DECLINED.name)
        }
        closeLocally(CallStatus.DECLINED, CallEndReason.DECLINED)
    }

    /**
     * Tugatish — holat va rolga qarab **to'g'ri** hodisani tanlaydi.
     *
     * ⚠️ `RINGING` da `call:end` qo'ng'iroqni yopmaydi: ack `ok` keladi, telefon esa
     * jiringlashda davom etadi va 45 soniyadan keyin `MISSED` bo'ladi. Hali javob
     * berilmagan qo'ng'iroqni chaquvchi `call:cancel`, chaqirilgan `call:decline` bilan
     * yopadi (`handoff/09-CALLS-PROTOCOL.md` §5).
     */
    override suspend fun hangUp() {
        val current = _session.value ?: return
        val callId = current.callId
        if (callId.isNotEmpty()) {
            when {
                current.status == CallStatus.RINGING && current.direction == CallDirection.OUTGOING ->
                    socket.cancel(callId)

                current.status == CallStatus.RINGING ->
                    socket.decline(callId, CallDeclineReason.DECLINED.name)

                else -> socket.end(callId)
            }
        }
        closeLocally(CallStatus.ENDED, CallEndReason.HANGUP)
    }

    // -------------------------------------------------------------------------------------
    // Media boshqaruvi
    // -------------------------------------------------------------------------------------

    override fun toggleMic() {
        val current = _session.value ?: return
        val enabled = !current.micEnabled
        engine?.setMicEnabled(enabled)
        _session.update { it.copy(micEnabled = enabled) }
        publishMediaState()
    }

    override fun toggleCamera() {
        val current = _session.value ?: return
        val enabled = !current.cameraEnabled
        engine?.setCameraEnabled(enabled)
        _session.update { it.copy(cameraEnabled = enabled) }
        // Tizim ilmog'i qayta e'lon qilinadi: Android 14+ da old plan xizmatining turlari
        // ruxsatlarga bog'liq va kamera suhbat o'rtasida yoqilganda `camera` turi
        // qo'shilishi kerak — aks holda ilova fonga o'tganda kamera o'chib qolardi.
        presence.onCallStarted(current.peer.fullName.orEmpty(), video = enabled)
        publishMediaState()
    }

    override fun switchCamera() {
        engine?.switchCamera()
        _session.update { it.copy(frontCamera = !it.frontCamera) }
    }

    override fun toggleSpeaker() {
        val current = _session.value ?: return
        val enabled = !current.speakerOn
        audio.setSpeaker(enabled)
        _session.update { it.copy(speakerOn = enabled) }
    }

    private fun publishMediaState() {
        val current = _session.value ?: return
        if (current.callId.isEmpty()) return
        scope.launch {
            socket.sendMediaState(current.callId, current.micEnabled, current.cameraEnabled)
        }
    }

    // -------------------------------------------------------------------------------------
    // Server hodisalari
    // -------------------------------------------------------------------------------------

    private suspend fun onAccepted(callId: String, sdp: String, relayOnly: Boolean) {
        val current = _session.value ?: return
        if (current.callId != callId) return
        cancelRingingTimer()
        if (!relayOnly) engine?.relaxIceTransportPolicy()
        _session.update { it.copy(status = CallStatus.CONNECTING, relayOnly = relayOnly && it.relayOnly) }
        // Gudok to'xtaydi: suhbatdosh javob berdi.
        audio.onCallActive(current.speakerOn)
        startConnectingTimer()
        engine?.acceptAnswer(sdp)
    }

    private fun onPeerRinging(callId: String) {
        // Chaquvchi tomonda: chaqirilganning telefoni haqiqatan jiringlay boshladi.
        // Holat o'zgarmaydi (allaqachon `RINGING`), lekin UI matnini aniqlashtirsa bo'ladi.
        if (_session.value?.callId == callId) {
            Napier.d("Qo'ng'iroq: peer jiringlayapti ($callId)", tag = LOG_TAG)
        }
    }

    /**
     * `call:taken` — **o'zimning boshqa** qurilmam javob berdi yoki rad etdi.
     *
     * Bu qurilmada hech narsa ko'rsatilmaydi: qo'ng'iroq boshqa joyda davom etyapti.
     */
    private suspend fun onTaken(callId: String) {
        if (_session.value?.callId != callId) return
        closeLocally(CallStatus.ENDED, endReason = null, sendStats = false)
    }

    private suspend fun onRemoteClose(callId: String, status: CallStatus, reason: CallEndReason?) {
        if (_session.value?.callId != callId) return
        closeLocally(status, reason)
    }

    private suspend fun onEnded(callId: String, rawReason: String) {
        if (_session.value?.callId != callId) return
        val reason = parseEnum(rawReason, CallEndReason.HANGUP)
        val status = when (reason) {
            CallEndReason.TIMEOUT -> if (_session.value?.status == CallStatus.RINGING) {
                CallStatus.MISSED
            } else {
                CallStatus.ENDED
            }
            CallEndReason.FAILED -> CallStatus.FAILED
            CallEndReason.BUSY -> CallStatus.DECLINED
            CallEndReason.CANCELED -> CallStatus.CANCELED
            CallEndReason.DECLINED -> CallStatus.DECLINED
            else -> CallStatus.ENDED
        }
        closeLocally(status, reason)
    }

    private suspend fun onRemoteIce(callId: String, event: dev.feature.calls.data.realtime.WsCallIce) {
        if (_session.value?.callId != callId) return
        engine?.addRemoteCandidate(
            candidate = event.candidate.candidate,
            sdpMid = event.candidate.sdpMid,
            sdpMLineIndex = event.candidate.sdpMLineIndex,
        )
    }

    private suspend fun onRemoteRenegotiate(callId: String, sdp: String) {
        if (_session.value?.callId != callId) return
        val answer = engine?.answerRenegotiation(sdp) ?: return
        socket.sendRenegotiate(callId, answer)
    }

    private fun onRemoteMediaState(callId: String, audio: Boolean, video: Boolean) {
        if (_session.value?.callId != callId) return
        _session.update { it.copy(remoteAudio = audio, remoteVideo = video) }
    }

    // -------------------------------------------------------------------------------------
    // Media qatlami hodisalari
    // -------------------------------------------------------------------------------------

    private fun attach(newEngine: CallEngine) {
        engine = newEngine
        connectedSent = false
        // Media qatlami ko'tarildi — endi mikrofon ochiq va tizimga shu haqda aytish
        // kerak. Sessiya obyekti hali qurilmagan bo'lishi mumkin, shuning uchun ism
        // bo'lmasa bo'sh satr ketadi (bildirishnomada zaxira sarlavha ko'rinadi).
        val current = _session.value
        presence.onCallStarted(
            peerName = current?.peer?.fullName.orEmpty(),
            // ⚠️ Kamera **haqiqatan yoqilganmi** — qo'ng'iroq turi emas. Video qo'ng'iroqda
            // ham kameraga ruxsat berilmagan bo'lishi mumkin (u ixtiyoriy), va o'shanda
            // old plan xizmatiga `camera` turini e'lon qilish `SecurityException` beradi.
            video = current?.cameraEnabled == true,
        )
        engineJob = scope.launch {
            newEngine.events.collect { event ->
                when (event) {
                    is CallEngineEvent.LocalCandidate -> sendCandidate(event)
                    CallEngineEvent.Connected -> onIceConnected()
                    CallEngineEvent.Disconnected -> onIceDisconnected()
                    CallEngineEvent.Failed -> onIceFailed()
                    is CallEngineEvent.RemoteVideo -> _session.update { it.copy(remoteVideo = event.enabled) }
                    CallEngineEvent.RenegotiationNeeded -> renegotiate()
                }
            }
        }
    }

    /**
     * Nomzodni yuboradi; `RATE_LIMITED` bo'lsa **eksponensial pauza bilan** qayta urinadi.
     *
     * Amalda birinchi bo'lib socket bucket'i uriladi (30 token, sekundiga 15 ta to'ladi),
     * ICE chegarasi (500) emas. Nomzod yo'qolishi halokat emas — ulanish boshqalari bilan
     * davom etadi — lekin bir necha marta urinib ko'rish arzon (`…PROTOCOL.md` §10).
     */
    private fun sendCandidate(event: CallEngineEvent.LocalCandidate) = scope.launch {
        val callId = _session.value?.callId?.takeIf { it.isNotEmpty() } ?: return@launch
        var delayMs = ICE_RETRY_BASE_MS
        repeat(ICE_RETRIES) { attempt ->
            val ack = socket.sendIce(callId, event.candidate, event.sdpMid, event.sdpMLineIndex)
            if (ack == null || ack.isOk) return@launch
            if (ack.error?.code != CallErrorCode.RATE_LIMITED) return@launch
            if (attempt < ICE_RETRIES - 1) {
                delay(delayMs)
                delayMs *= 2
            }
        }
    }.let { }

    /**
     * ICE ulandi → `call:connected` va `ACTIVE`.
     *
     * Serverda `call:accept` faqat `CONNECTING` beradi: «javob berildi» ≠ «media oqyapti».
     * `ACTIVE` ga o'tkazadigan narsa — aynan shu hodisa.
     */
    private fun onIceConnected() = scope.launch {
        val current = _session.value ?: return@launch
        if (current.callId.isEmpty()) return@launch
        cancelConnectingTimer()
        // Ulanish tiklandi — tiklash urinishlari hisobi nolga qaytadi.
        iceRecoveryTimer?.cancel()
        iceRecoveryTimer = null
        iceRestartAttempts = 0
        if (!connectedSent) {
            connectedSent = true
            socket.connected(current.callId)
        }
        if (current.status != CallStatus.ACTIVE) {
            _session.update { it.copy(status = CallStatus.ACTIVE, connectedAt = clock.now()) }
            // Ovoz marshruti aynan shu yerda ham qo'yiladi: media oqishidan oldin
            // qo'yilgani ba'zi qurilmalarda WebRTC audio moduli ishga tushganda tushib
            // qolardi va suhbat quloqchinda qolib ketardi.
            audio.onCallActive(current.speakerOn)
            startMaxDurationTimer()
            publishMediaState()
        }
    }.let { }

    /**
     * ICE `disconnected` — odatda **vaqtinchalik**: tarmoq almashdi, lift, tunnel.
     *
     * Qo'ng'iroq darhol yopilmaydi; libwebrtc ko'pincha o'zi tiklaydi. Lekin cheksiz
     * kutib ham bo'lmaydi — muhlat ichida tiklanmasa ICE qayta ishga tushiriladi.
     */
    private fun onIceDisconnected() {
        Napier.d("Qo'ng'iroq: ICE uzildi — tiklanishi kutilmoqda", tag = LOG_TAG)
        scheduleIceRecovery(ICE_RECOVERY_GRACE_MS)
    }

    /** ICE `failed` — o'zi tiklanmaydi, faqat qayta ishga tushirish yordam beradi. */
    private fun onIceFailed() {
        Napier.w("Qo'ng'iroq: ICE yiqildi", tag = LOG_TAG)
        scheduleIceRecovery(0)
    }

    /**
     * ICE ni qayta ishga tushiradi (`iceRestart`) — yangi nomzodlar bilan yangi urinish.
     *
     * ⚠️ Ikkala tomon ham bir vaqtda tiklashga urinsa qayta muzokara to'qnashadi (glare).
     * Shuning uchun **chaqirilgan tomon kutib turadi**: chaquvchi odatda birinchi bo'ladi
     * va uning taklifi yetib kelsa, bu yerdagi urinish keraksiz bo'lib qoladi.
     */
    private fun scheduleIceRecovery(delayMs: Long) {
        if (iceRecoveryTimer?.isActive == true) return
        iceRecoveryTimer = scope.launch {
            val current = _session.value ?: return@launch
            val roleDelay = if (current.direction == CallDirection.INCOMING) ICE_RESTART_CALLEE_DELAY_MS else 0
            delay(delayMs + roleDelay)

            // Kutish paytida o'zi tiklangan bo'lsa `onIceConnected` bu ishni allaqachon
            // bekor qilgan bo'lardi — bu yergacha yetib kelish "hali ham uzuq" degani.
            val live = _session.value?.takeIf { it.status.isLive && it.callId.isNotEmpty() } ?: return@launch
            if (iceRestartAttempts >= MAX_ICE_RESTARTS) {
                Napier.w("Qo'ng'iroq: ICE tiklanmadi — yopiladi", tag = LOG_TAG)
                closeLocally(CallStatus.FAILED, CallEndReason.FAILED)
                return@launch
            }
            iceRestartAttempts++
            Napier.d("Qo'ng'iroq: ICE qayta ishga tushirilmoqda ($iceRestartAttempts)", tag = LOG_TAG)
            val offer = engine?.createRenegotiationOffer(iceRestart = true) ?: return@launch
            socket.sendRenegotiate(live.callId, offer)
            // Bu urinish ham natija bermasa keyingisi (yoki yopilish) shu yerdan boshlanadi.
            iceRecoveryTimer = null
            scheduleIceRecovery(ICE_RECOVERY_GRACE_MS)
        }
    }

    private fun renegotiate() = scope.launch {
        val current = _session.value ?: return@launch
        if (current.callId.isEmpty() || !current.status.isLive) return@launch
        val offer = engine?.createRenegotiationOffer() ?: return@launch
        socket.sendRenegotiate(current.callId, offer)
    }.let { }

    // -------------------------------------------------------------------------------------
    // Taymerlar — qiymatlar serverdagilar bilan AYNAN bir xil (`…PROTOCOL.md` §6)
    // -------------------------------------------------------------------------------------

    /**
     * Jiringlash — 45 s. Server ham shu paytda `call:ended { TIMEOUT }` yuboradi; local
     * taymer faqat **zaxira**: hodisa yo'lda yo'qolsa ekran abadiy jiringlab qolmasin.
     * Shu sabab u serverdan bir oz kechroq ishlaydi.
     */
    private fun startRingingTimer() {
        cancelRingingTimer()
        ringingTimer = scope.launch {
            delay(RINGING_TIMEOUT_MS + TIMER_GRACE_MS)
            if (_session.value?.status == CallStatus.RINGING) {
                closeLocally(CallStatus.MISSED, CallEndReason.TIMEOUT, sendStats = false)
            }
        }
    }

    private fun cancelRingingTimer() {
        ringingTimer?.cancel()
        ringingTimer = null
    }

    /** Accept'dan keyin 30 s ichida ulanmasa — `FAILED`. */
    private fun startConnectingTimer() {
        cancelConnectingTimer()
        connectingTimer = scope.launch {
            delay(CONNECTING_TIMEOUT_MS + TIMER_GRACE_MS)
            if (_session.value?.status == CallStatus.CONNECTING) {
                closeLocally(CallStatus.FAILED, CallEndReason.FAILED)
            }
        }
    }

    private fun cancelConnectingTimer() {
        connectingTimer?.cancel()
        connectingTimer = null
    }

    /** 4 soatlik chegara — server ham shu paytda yopadi. */
    private fun startMaxDurationTimer() {
        maxDurationTimer?.cancel()
        maxDurationTimer = scope.launch {
            delay(MAX_DURATION_MS + TIMER_GRACE_MS)
            if (_session.value?.status == CallStatus.ACTIVE) hangUp()
        }
    }

    // -------------------------------------------------------------------------------------
    // Yopish
    // -------------------------------------------------------------------------------------

    /**
     * Local teardown: o'lchovni yuboradi, media qatlamini yopadi va holatni tozalaydi.
     *
     * [sendStats] — javobsiz qo'ng'iroqda `false`: media umuman oqmagan, o'lchash uchun
     * narsa yo'q va server `409 INVALID_CALL_STATE` qaytaradi (bu xato emas, kutilgan
     * javob — lekin bekorga so'rov yuborishning ma'nosi ham yo'q).
     */
    private suspend fun closeLocally(
        status: CallStatus,
        endReason: CallEndReason?,
        sendStats: Boolean = true,
    ) {
        val current = _session.value ?: return
        val answered = current.connectedAt != null || current.status == CallStatus.ACTIVE
        val callId = current.callId

        if (sendStats && answered && callId.isNotEmpty()) {
            // ⚠️ O'lchov engine YOPILISHIDAN OLDIN olinadi — yopilgandan keyin
            // `getStats()` bo'sh qaytadi.
            engine?.collectStats()?.let { stats ->
                scope.launch { repository.reportStats(callId, stats) }
            }
        }
        _session.value = current.copy(status = status, endReason = endReason)
        teardown(audibleReason(endReason, answered))
    }

    /**
     * Qaysi tugash sababi **eshitilishi** kerak — tizim telefonidagi odat bo'yicha.
     *
     * «Band» va «aloqa yo'q» har doim chalinadi: ular foydalanuvchiga nima bo'lganini
     * aytadi. «Tugatildi» signali esa faqat haqiqatan gaplashilgan bo'lsa — javob
     * berilmagan qo'ng'iroqni bekor qilish yoki kiruvchini rad etish jim bo'ladi.
     */
    private fun audibleReason(endReason: CallEndReason?, answered: Boolean): CallEndReason? =
        when (endReason) {
            CallEndReason.BUSY, CallEndReason.FAILED -> endReason
            CallEndReason.HANGUP, CallEndReason.DECLINED -> endReason.takeIf { answered }
            else -> null
        }

    /**
     * [endReason] — tugash signali uchun: "band", "aloqa yo'q" va oddiy tugatish har xil
     * eshitiladi, tizim telefonidagi kabi. `null` bo'lsa jim yopiladi.
     */
    private fun teardown(endReason: CallEndReason? = null) {
        presence.onCallEnded()
        audio.stop(endReason)
        cancelRingingTimer()
        cancelConnectingTimer()
        iceRecoveryTimer?.cancel()
        iceRecoveryTimer = null
        iceRestartAttempts = 0
        maxDurationTimer?.cancel()
        maxDurationTimer = null
        engineJob?.cancel()
        engineJob = null
        engine?.close()
        engine = null
        pendingOffer = null
        connectedSent = false
        // Terminal holat ekranda qisqa ko'rinib turadi («Rad etildi», «Javobsiz»), so'ng
        // tozalanadi — aks holda ekran hech qanday sababsiz birdan yopilardi.
        scope.launch {
            delay(TERMINAL_LINGER_MS)
            if (_session.value?.status?.isTerminal == true) _session.value = null
        }
    }

    private fun ackError(ack: CallAck?): String {
        val code = ack?.error?.code
        return ack?.error?.text ?: CallErrorCode.message(code)
    }

    private fun reasonOf(raw: String): CallEndReason =
        if (raw == "BUSY") CallEndReason.BUSY else CallEndReason.DECLINED

    private inline fun MutableStateFlow<CallSession?>.update(block: (CallSession) -> CallSession) {
        value = value?.let(block)
    }

    private companion object {
        const val LOG_TAG = "Calls"

        /** Server taymerlari (`…PROTOCOL.md` §6) — local nusxalari zaxira sifatida. */
        const val RINGING_TIMEOUT_MS = 45_000L
        const val CONNECTING_TIMEOUT_MS = 30_000L
        const val MAX_DURATION_MS = 4 * 60 * 60 * 1000L

        /**
         * Local taymer serverdan shuncha kechikadi — server hodisasi yo'lda bo'lganda
         * ikkalasi bir vaqtda ishlab, ekranda ikki xil sabab ko'rsatilmasin.
         */
        const val TIMER_GRACE_MS = 2_000L

        /** Terminal holat ekranda shuncha ko'rinib turadi. */
        const val TERMINAL_LINGER_MS = 1_500L

        const val ICE_RETRIES = 3

        /** Socket bucket'i sekundiga 15 ta to'ladi — ~200 ms odatda yetadi. */
        const val ICE_RETRY_BASE_MS = 250L

        /**
         * ICE uzilgandan keyin shuncha kutiladi — libwebrtc ko'pincha o'zi tiklaydi.
         * Wi-Fi → mobil o'tish odatda 2–4 soniya oladi, shuning uchun 6 s.
         */
        const val ICE_RECOVERY_GRACE_MS = 6_000L

        /**
         * Chaqirilgan tomon tiklashni shuncha kechiktiradi — glare'ni kamaytiradi:
         * chaquvchi birinchi bo'ladi va uning taklifi kelsa bu urinish keraksiz bo'ladi.
         */
        const val ICE_RESTART_CALLEE_DELAY_MS = 1_500L

        /** Shuncha urinishdan keyin qo'ng'iroq `FAILED` bo'lib yopiladi. */
        const val MAX_ICE_RESTARTS = 2

        /**
         * Token o'zgarganini tekshirish oralig'i.
         *
         * 30 soniya token umridan (15 daqiqa) ancha qisqa, ya'ni yangilangan token
         * socket'ga deyarli darhol yetadi; ayni paytda tekshiruv arzon (local o'qish).
         */
        const val TOKEN_POLL_MS = 30_000L
    }
}

private fun dev.feature.calls.data.realtime.WsCaller.toSummary(): StudentSummary = StudentSummary(
    id = id,
    // `fullName` null emas, lekin ism ham familiya ham bo'sh bo'lsa bo'sh satr bo'lishi
    // mumkin — o'shanda `username` ga tushamiz, aks holda ekranda bo'sh joy qolardi.
    fullName = fullName.ifBlank { username.orEmpty() },
    username = username,
    avatarUrl = avatarUrl,
)

private fun parseInstant(value: String?): Instant? =
    value?.let { runCatching { Instant.parse(it) }.getOrNull() }

private inline fun <reified T : Enum<T>> parseEnum(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
