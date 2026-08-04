package dev.feature.calls.data.engine

import android.content.Context
import dev.feature.calls.domain.model.CallMedia
import dev.feature.calls.domain.model.CallStats
import dev.feature.calls.domain.model.CandidateType
import dev.feature.calls.domain.model.IceServer
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RTCStats
import org.webrtc.RTCStatsReport
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * [CallEngine] ning Android amalga oshirilishi — `org.webrtc` ustida.
 *
 * Sof P2P: SFU yo'q, media to'g'ridan-to'g'ri (yoki TURN orqali) oqadi. Server SDP va ICE
 * nomzodlarining bir baytiga ham tegmaydi, ya'ni bu yerdagi kodek sozlamalari o'zgarishsiz
 * peer'ga yetadi (`handoff/09-CALLS-PROTOCOL.md` §13).
 *
 * ⚠️ Nusxa **bir martalik**: har qo'ng'iroqqa yangisi quriladi va [close] dan keyin qayta
 * ishlatilmaydi. `PeerConnection` ni qayta ishlatish ICE holatini toza qoldirmaydi va
 * ikkinchi qo'ng'iroq «ulanmoqda» da qotib qolardi.
 */
internal class WebRtcCallEngine(
    private val context: Context,
    private val factory: PeerConnectionFactory,
    private val eglBase: EglBase,
) : CallEngine {

    private val _events = MutableSharedFlow<CallEngineEvent>(extraBufferCapacity = 64)
    override val events: Flow<CallEngineEvent> = _events.asSharedFlow()

    private var peerConnection: PeerConnection? = null

    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null

    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var capturer: CameraVideoCapturer? = null
    private var surfaceHelper: SurfaceTextureHelper? = null

    private var frontCamera = true
    private var closed = false

    /**
     * Remote tavsif qo'yilgunicha kelgan nomzodlar.
     *
     * `addIceCandidate` remote tavsifsiz **jimgina yiqiladi**, nomzod esa qaytib kelmaydi.
     * `call:accepted` va `call:ice` alohida oqimlarda yig'ilgani uchun nomzodning oldinroq
     * kelishi kamdan-kam emas — aksincha, odatiy hol. Aynan shu qo'ng'iroqni "Ulanmoqda"
     * da qotirib qo'yardi.
     */
    private val pendingCandidates = mutableListOf<IceCandidate>()
    private var remoteDescriptionSet = false
    private val candidateLock = Any()

    /** Joriy konfiguratsiya — [relaxIceTransportPolicy] uni o'zgartirib qayta qo'yadi. */
    private var configuration: PeerConnection.RTCConfiguration? = null

    // --- Qurish ---------------------------------------------------------------------------

    override suspend fun createOffer(
        media: CallMedia,
        relayOnly: Boolean,
        iceServers: List<IceServer>,
    ): String? {
        if (!openPeerConnection(media, relayOnly, iceServers)) return null
        val offer = createLocalDescription(offer = true) ?: return null
        return offer.description
    }

    override suspend fun createAnswer(
        remoteOfferSdp: String,
        media: CallMedia,
        relayOnly: Boolean,
        iceServers: List<IceServer>,
    ): String? {
        if (!openPeerConnection(media, relayOnly, iceServers)) return null
        if (!setRemote(SessionDescription(SessionDescription.Type.OFFER, remoteOfferSdp))) return null
        return createLocalDescription(offer = false)?.description
    }

    override suspend fun acceptAnswer(remoteAnswerSdp: String): Boolean =
        setRemote(SessionDescription(SessionDescription.Type.ANSWER, remoteAnswerSdp))

    override suspend fun addRemoteCandidate(candidate: String, sdpMid: String, sdpMLineIndex: Int) {
        val ice = IceCandidate(sdpMid, sdpMLineIndex, candidate)
        val connection = synchronized(candidateLock) {
            if (!remoteDescriptionSet) {
                pendingCandidates += ice
                null
            } else {
                peerConnection
            }
        }
        connection?.addIceCandidate(ice)
    }

    /** Navbatdagi nomzodlarni quyadi — remote tavsif qo'yilgan zahoti chaqiriladi. */
    private fun flushPendingCandidates() {
        val connection = peerConnection ?: return
        val queued = synchronized(candidateLock) {
            remoteDescriptionSet = true
            val copy = pendingCandidates.toList()
            pendingCandidates.clear()
            copy
        }
        if (queued.isEmpty()) return
        Napier.d("WebRTC: navbatdagi ${queued.size} ta nomzod quyildi", tag = LOG_TAG)
        queued.forEach { runCatching { connection.addIceCandidate(it) } }
    }

    override suspend fun createRenegotiationOffer(iceRestart: Boolean): String? =
        createLocalDescription(offer = true, iceRestart = iceRestart)?.description

    override suspend fun answerRenegotiation(remoteOfferSdp: String): String? {
        if (!setRemote(SessionDescription(SessionDescription.Type.OFFER, remoteOfferSdp))) return null
        return createLocalDescription(offer = false)?.description
    }

    /**
     * `PeerConnection` ni quradi va local treklarni qo'shadi.
     *
     * [relayOnly] → `IceTransportsType.RELAY`: bu **faqat siyosat emas**, u host va srflx
     * nomzodlarni umuman yig'dirmaydi ham, ya'ni uy IP manzili tarmoqqa chiqmaydi.
     */
    private fun openPeerConnection(
        media: CallMedia,
        relayOnly: Boolean,
        iceServers: List<IceServer>,
    ): Boolean = runCatching {
        val config = PeerConnection.RTCConfiguration(iceServers.map(::toNativeIceServer)).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            // Nomzodlar qo'ng'iroq davomida ham yig'ilaveradi (tarmoq almashganda —
            // Wi-Fi → mobil — yangi nomzod kerak, aks holda qo'ng'iroq uziladi).
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            iceTransportsType = if (relayOnly) {
                PeerConnection.IceTransportsType.RELAY
            } else {
                PeerConnection.IceTransportsType.ALL
            }
        }
        val connection = factory.createPeerConnection(config, PeerObserver()) ?: return false
        peerConnection = connection
        configuration = config

        // Ovoz doim bor — `VIDEO` qo'ng'iroqda ham suhbat ovozdan boshlanadi.
        val source = factory.createAudioSource(audioConstraints())
        audioSource = source
        val track = factory.createAudioTrack(AUDIO_TRACK_ID, source)
        audioTrack = track
        connection.addTrack(track, listOf(STREAM_ID))

        if (media == CallMedia.VIDEO) startCamera(connection)
        true
    }.getOrElse {
        Napier.e("WebRTC: PeerConnection qurilmadi", it, tag = LOG_TAG)
        false
    }

    /**
     * Kamerani ishga tushiradi.
     *
     * `Camera2Enumerator` ba'zi eski qurilmalarda bo'sh ro'yxat qaytaradi — o'shanda video
     * treki qo'shilmaydi va qo'ng'iroq **ovozli bo'lib davom etadi**. Bu yiqilishdan
     * ko'ra yaxshiroq: foydalanuvchi gaplasha oladi.
     */
    private fun startCamera(connection: PeerConnection) = runCatching {
        val enumerator = Camera2Enumerator(context)
        val deviceName = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            ?: enumerator.deviceNames.firstOrNull()
            ?: return@runCatching
        val videoCapturer = enumerator.createCapturer(deviceName, null) ?: return@runCatching

        val helper = SurfaceTextureHelper.create(CAPTURE_THREAD, eglBase.eglBaseContext)
        surfaceHelper = helper
        val source = factory.createVideoSource(false)
        videoSource = source
        videoCapturer.initialize(helper, context, source.capturerObserver)
        videoCapturer.startCapture(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS)
        capturer = videoCapturer

        val track = factory.createVideoTrack(VIDEO_TRACK_ID, source)
        videoTrack = track
        connection.addTrack(track, listOf(STREAM_ID))
        WebRtcVideoBus.setLocal(track)
    }.onFailure {
        Napier.w("WebRTC: kamera ochilmadi — ovozli qo'ng'iroq sifatida davom etamiz", it, tag = LOG_TAG)
    }.let { }

    // --- Media boshqaruvi -----------------------------------------------------------------

    override fun setMicEnabled(enabled: Boolean) {
        audioTrack?.setEnabled(enabled)
    }

    override fun setCameraEnabled(enabled: Boolean) {
        val connection = peerConnection ?: return
        if (enabled && videoTrack == null) {
            // Ovozli qo'ng'iroq davomida kamera yoqildi — trek endi qo'shiladi, ya'ni
            // qayta muzokara kerak (`call:renegotiate`).
            startCamera(connection)
            _events.tryEmit(CallEngineEvent.RenegotiationNeeded)
            return
        }
        videoTrack?.setEnabled(enabled)
        runCatching {
            if (enabled) {
                capturer?.startCapture(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS)
            } else {
                capturer?.stopCapture()
            }
        }
    }

    override fun switchCamera() {
        capturer?.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                frontCamera = isFrontCamera
            }

            override fun onCameraSwitchError(errorDescription: String?) {
                Napier.w("WebRTC: kamera almashmadi — $errorDescription", tag = LOG_TAG)
            }
        })
    }

    /**
     * ICE siyosatini `ALL` ga bo'shatadi — `setConfiguration` ni qayta qo'yish orqali.
     *
     * `PeerConnection` qayta qurilmaydi: mavjud nomzodlar va oqimlar joyida qoladi,
     * libwebrtc esa endi host/srflx nomzodlarni ham yig'a boshlaydi
     * (`continualGatheringPolicy = GATHER_CONTINUALLY` shuning uchun ham kerak).
     */
    override fun relaxIceTransportPolicy() {
        val connection = peerConnection ?: return
        val config = configuration ?: return
        if (config.iceTransportsType == PeerConnection.IceTransportsType.ALL) return
        runCatching {
            config.iceTransportsType = PeerConnection.IceTransportsType.ALL
            connection.setConfiguration(config)
            Napier.d("WebRTC: ICE siyosati ALL ga bo'shatildi", tag = LOG_TAG)
        }.onFailure {
            Napier.w("WebRTC: ICE siyosati bo'shatilmadi", it, tag = LOG_TAG)
        }
    }

    // --- O'lchov --------------------------------------------------------------------------

    /**
     * `getStats()` dan **tanlangan juftlik** bo'yicha o'lchov yig'adi.
     *
     * Tartib aynan `handoff/09-CALLS-REST.md` §3 dagidek:
     * 1. `candidate-pair` lardan `state == "succeeded" && nominated == true` bo'lganini topamiz;
     * 2. uning `localCandidateId`/`remoteCandidateId` si bo'yicha nomzod yozuvlarini olamiz;
     * 3. **ikkalasidan bittasi** `relay` bo'lsa → `RELAY`, aks holda `srflx` bo'lsa → `SRFLX`,
     *    yo'qsa → `HOST`.
     *
     * ⚠️ «Relay nomzod yig'ildimi?» deb qarash mumkin emas: relay nomzodlar deyarli har
     * qo'ng'iroqda yig'iladi, lekin ko'pincha ishlatilmaydi — u holda deyarli har qo'ng'iroq
     * `RELAY` chiqib, TURN byudjeti butunlay noto'g'ri hisoblanardi.
     */
    override suspend fun collectStats(): CallStats? {
        val connection = peerConnection ?: return null
        val report = CompletableDeferred<RTCStatsReport?>()
        runCatching { connection.getStats { report.complete(it) } }
            .onFailure { return null }
        val stats = report.await()?.statsMap ?: return null

        val pair = selectedPair(stats) ?: return null
        val local = stats[pair.members["localCandidateId"] as? String]
        val remote = stats[pair.members["remoteCandidateId"] as? String]
        val candidateType = candidateTypeOf(
            local?.members?.get("candidateType") as? String,
            remote?.members?.get("candidateType") as? String,
        )

        val inbound = stats.values.firstOrNull {
            it.type == "inbound-rtp" && it.members["kind"] == "audio"
        }

        return CallStats(
            candidateType = candidateType,
            // `currentRoundTripTime` — **sekundlarda**, `Double`.
            rttMs = (pair.members["currentRoundTripTime"] as? Number)
                ?.let { (it.toDouble() * MILLIS_IN_SECOND).toInt() }
                ?.coerceIn(0, MAX_RTT_MS),
            jitterMs = (inbound?.members?.get("jitter") as? Number)
                ?.let { (it.toDouble() * MILLIS_IN_SECOND).toInt() }
                ?.coerceIn(0, MAX_JITTER_MS),
            packetsLost = (inbound?.members?.get("packetsLost") as? Number)?.toInt()?.coerceAtLeast(0),
            packetsReceived = (inbound?.members?.get("packetsReceived") as? Number)?.toInt()
                ?.coerceAtLeast(0),
            // ⚠️ `Long`: uzun video qo'ng'iroq Int32 ning ~2 GB chegarasidan oshadi.
            bytesSent = (pair.members["bytesSent"] as? Number)?.toLong()?.coerceAtLeast(0),
            bytesReceived = (pair.members["bytesReceived"] as? Number)?.toLong()?.coerceAtLeast(0),
        )
    }

    /**
     * Tanlangan juftlik. Avval `transport.selectedCandidatePairId` bo'yicha (aniq yo'l),
     * bo'lmasa `nominated && succeeded` bo'yicha izlanadi.
     */
    private fun selectedPair(stats: Map<String, RTCStats>): RTCStats? {
        val selectedId = stats.values
            .firstOrNull { it.type == "transport" }
            ?.members?.get("selectedCandidatePairId") as? String
        stats[selectedId]?.let { return it }
        return stats.values.firstOrNull {
            it.type == "candidate-pair" &&
                it.members["state"] == "succeeded" &&
                it.members["nominated"] == true
        }
    }

    private fun candidateTypeOf(local: String?, remote: String?): CandidateType = when {
        local == "relay" || remote == "relay" -> CandidateType.RELAY
        local == "srflx" || remote == "srflx" || local == "prflx" || remote == "prflx" ->
            CandidateType.SRFLX
        else -> CandidateType.HOST
    }

    // --- Yopish ---------------------------------------------------------------------------

    override fun close() {
        if (closed) return
        closed = true
        WebRtcVideoBus.clear()
        runCatching { capturer?.stopCapture() }
        runCatching { capturer?.dispose() }
        runCatching { surfaceHelper?.dispose() }
        runCatching { videoSource?.dispose() }
        runCatching { audioSource?.dispose() }
        runCatching { peerConnection?.dispose() }
        synchronized(candidateLock) {
            pendingCandidates.clear()
            remoteDescriptionSet = false
        }
        configuration = null
        capturer = null
        surfaceHelper = null
        videoSource = null
        videoTrack = null
        audioSource = null
        audioTrack = null
        peerConnection = null
    }

    // --- SDP yordamchilari ------------------------------------------------------------------

    private suspend fun createLocalDescription(
        offer: Boolean,
        iceRestart: Boolean = false,
    ): SessionDescription? {
        val connection = peerConnection ?: return null
        val constraints = MediaConstraints().apply {
            if (iceRestart) mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
        }
        val created = CompletableDeferred<SessionDescription?>()
        val observer = object : SimpleSdpObserver() {
            override fun onCreateSuccess(description: SessionDescription) {
                created.complete(description)
            }

            override fun onCreateFailure(error: String?) {
                Napier.w("WebRTC: SDP yaratilmadi — $error", tag = LOG_TAG)
                created.complete(null)
            }
        }
        if (offer) connection.createOffer(observer, constraints) else connection.createAnswer(observer, constraints)

        val description = created.await() ?: return null
        // Opus sozlamalari: yo'qolgan paketni qoplash (FEC) va jimlikda uzatmaslik (DTX).
        // Server SDP'ga tegmaydi, ya'ni bu qator o'zgarishsiz peer'ga yetadi.
        val tuned = SessionDescription(description.type, tuneOpus(description.description))

        val applied = CompletableDeferred<Boolean>()
        connection.setLocalDescription(
            object : SimpleSdpObserver() {
                override fun onSetSuccess() = applied.complete(true).let { }
                override fun onSetFailure(error: String?) {
                    Napier.w("WebRTC: local SDP qo'llanmadi — $error", tag = LOG_TAG)
                    applied.complete(false)
                }
            },
            tuned,
        )
        return if (applied.await()) tuned else null
    }

    private suspend fun setRemote(description: SessionDescription): Boolean {
        val connection = peerConnection ?: return false
        val applied = CompletableDeferred<Boolean>()
        connection.setRemoteDescription(
            object : SimpleSdpObserver() {
                override fun onSetSuccess() = applied.complete(true).let { }
                override fun onSetFailure(error: String?) {
                    Napier.w("WebRTC: remote SDP qo'llanmadi — $error", tag = LOG_TAG)
                    applied.complete(false)
                }
            },
            description,
        )
        val ok = applied.await()
        // Endi nomzodlarni qabul qilsa bo'ladi — kutib turganlari shu yerda quyiladi.
        if (ok) flushPendingCandidates()
        return ok
    }

    // --- PeerConnection kuzatuvchisi ---------------------------------------------------------

    private inner class PeerObserver : PeerConnection.Observer {

        /**
         * Local nomzod — **darhol** yuboriladi (trickle).
         *
         * Yig'ib, keyin bir yo'la yuborish socket bucket'ini (30 token, sekundiga 15 ta)
         * ~2 soniyada bo'shatadi va `RATE_LIMITED` keltiradi.
         */
        override fun onIceCandidate(candidate: IceCandidate) {
            val mid = candidate.sdpMid ?: return
            _events.tryEmit(
                CallEngineEvent.LocalCandidate(
                    candidate = candidate.sdp,
                    sdpMid = mid,
                    sdpMLineIndex = candidate.sdpMLineIndex,
                ),
            )
        }

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            when (state) {
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED,
                -> _events.tryEmit(CallEngineEvent.Connected)

                PeerConnection.IceConnectionState.DISCONNECTED ->
                    _events.tryEmit(CallEngineEvent.Disconnected)

                PeerConnection.IceConnectionState.FAILED,
                PeerConnection.IceConnectionState.CLOSED,
                -> _events.tryEmit(CallEngineEvent.Failed)

                else -> Unit
            }
        }

        override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
            val track = receiver.track() ?: return
            if (track.kind() == MediaStreamTrack.VIDEO_TRACK_KIND && track is VideoTrack) {
                WebRtcVideoBus.setRemote(track)
                _events.tryEmit(CallEngineEvent.RemoteVideo(enabled = true))
            }
        }

        override fun onRemoveTrack(receiver: RtpReceiver) {
            WebRtcVideoBus.setRemote(null)
            _events.tryEmit(CallEngineEvent.RemoteVideo(enabled = false))
        }

        override fun onRenegotiationNeeded() {
            _events.tryEmit(CallEngineEvent.RenegotiationNeeded)
        }

        override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
        override fun onAddStream(stream: MediaStream) = Unit
        override fun onRemoveStream(stream: MediaStream) = Unit
        override fun onDataChannel(channel: DataChannel) = Unit
        override fun onTrack(transceiver: RtpTransceiver) = Unit
    }

    private companion object {
        const val LOG_TAG = "CallEngine"
        const val STREAM_ID = "sc-call"
        const val AUDIO_TRACK_ID = "sc-audio"
        const val VIDEO_TRACK_ID = "sc-video"
        const val CAPTURE_THREAD = "ScCaptureThread"

        const val VIDEO_WIDTH = 1280
        const val VIDEO_HEIGHT = 720
        const val VIDEO_FPS = 30

        const val MILLIS_IN_SECOND = 1000.0

        /** Server validatsiyasidagi chegaralar (`RecordCallStatsDto`). */
        const val MAX_RTT_MS = 60_000
        const val MAX_JITTER_MS = 10_000
    }
}

/**
 * Mikrofon manbasining sozlamalari.
 *
 * Aks-sado bekor qilish va shovqin bostirish **dasturiy** yoqiladi ham: qurilmaning
 * apparat moduli (`JavaAudioDeviceModule`) ba'zi telefonlarda umuman yo'q va o'shanda
 * suhbatdosh o'z ovozini eshitib turardi.
 */
private fun audioConstraints(): MediaConstraints = MediaConstraints().apply {
    mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
    mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
    mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
    mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
}

private fun toNativeIceServer(server: IceServer): PeerConnection.IceServer =
    PeerConnection.IceServer.builder(server.urls)
        .setUsername(server.username.orEmpty())
        .setPassword(server.credential.orEmpty())
        .createIceServer()

/**
 * Opus `fmtp` qatoriga FEC va DTX qo'shadi.
 *
 * `useinbandfec=1` — yo'qolgan paketni qo'shni paketdan tiklaydi (mobil tarmoqda eshitiladigan
 * farq beradi); `usedtx=1` — jimlikda uzatmaydi, ya'ni TURN trafigi kamayadi.
 *
 * ⚠️ Bu SDP'ni **qayta yozish**, shuning uchun qat'iy: faqat `a=fmtp:<opusPayload>` qatoriga
 * tegadi va parametr allaqachon bo'lsa ikkinchi marta qo'shmaydi. Opus topilmasa SDP
 * o'zgarishsiz qaytadi.
 */
internal fun tuneOpus(sdp: String): String {
    val payload = Regex("""a=rtpmap:(\d+) opus/48000""").find(sdp)?.groupValues?.get(1) ?: return sdp
    val fmtpPrefix = "a=fmtp:$payload "
    val lines = sdp.split("\r\n").toMutableList()
    val index = lines.indexOfFirst { it.startsWith(fmtpPrefix) }
    if (index < 0) return sdp
    var line = lines[index]
    if (!line.contains("useinbandfec")) line += ";useinbandfec=1"
    if (!line.contains("usedtx")) line += ";usedtx=1"
    lines[index] = line
    return lines.joinToString("\r\n")
}

/** `SdpObserver` ning to'rtta metodidan odatda ikkitasi kerak — qolgani shu yerda yopiladi. */
private abstract class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(description: SessionDescription) = Unit
    override fun onSetSuccess() = Unit
    override fun onCreateFailure(error: String?) = Unit
    override fun onSetFailure(error: String?) = Unit
}

/**
 * [CallEngineFactory] ning Android tomoni — `PeerConnectionFactory` **bir marta** quriladi.
 *
 * Uni har qo'ng'iroqda qayta qurish qimmat (native kutubxona yuklanadi, audio moduli
 * ishga tushadi) va ba'zi qurilmalarda ikkinchi marta umuman ishlamaydi.
 */
class AndroidCallEngineFactory(private val context: Context) : CallEngineFactory {

    private val eglBase: EglBase by lazy { EglBase.create().also { WebRtcVideoBus.eglBase = it } }

    private val factory: PeerConnectionFactory by lazy {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .createInitializationOptions(),
        )
        val audioModule = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
        PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioModule)
            .setVideoEncoderFactory(
                // Apparat kodeklari yoqilgan: H.264 telefonda dasturiy kodlashdan bir necha
                // barobar arzon va batareyani sezilarli tejaydi.
                DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true),
            )
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
    }

    override fun create(): CallEngine = WebRtcCallEngine(context, factory, eglBase)
}
