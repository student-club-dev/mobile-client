package dev.feature.calls.data.engine

import dev.feature.calls.domain.model.CallMedia
import dev.feature.calls.domain.model.CallStats
import dev.feature.calls.domain.model.IceServer
import kotlinx.coroutines.flow.Flow

/**
 * WebRTC media qatlami — signalizatsiyadan **butunlay ajratilgan**.
 *
 * Vazifasi taqsimoti aniq: [dev.feature.calls.data.realtime.CallsSocket] SDP va nomzodlarni
 * **tashiydi**, bu interfeys ularni **yaratadi va qo'llaydi**. Ikkalasini bog'lovchi
 * yagona joy — `CallSessionManager`.
 *
 * Nega interfeys: media qatlami platformaga xos (Android'da `org.webrtc`, iOS'da
 * `WebRTC.framework`), holat mashinasi esa ikkalasida bir xil bo'lishi shart — aks holda
 * taymerlar, glare va qayta ulanish mantig'i ikki marta yozilib, ikki xil xatoga ega
 * bo'lardi.
 */
interface CallEngine {

    /** Media qatlamidan chiqadigan hodisalar — [CallEngineEvent]. */
    val events: Flow<CallEngineEvent>

    /**
     * Chiquvchi qo'ng'iroq uchun **offer** quradi va uni local tavsif sifatida qo'yadi.
     *
     * [relayOnly] `true` bo'lsa `iceTransportPolicy = RELAY` qo'yiladi **va** `host`/`srflx`
     * nomzodlar umuman chiqarilmaydi. Bu kosmetika emas: offer taklif bilan birga ketadi va
     * `call:incoming` uni chaqirilganning barcha qurilmalariga yuboradi, ya'ni TURN
     * majburlanmasa chaqirilgan **javob bermasa ham** chaquvchining uy IP manzilini olardi
     * (`handoff/09-CALLS-PROTOCOL.md` §11).
     *
     * `null` — media qatlamini ko'tarib bo'lmadi (ruxsat yo'q, qurilma band).
     */
    suspend fun createOffer(media: CallMedia, relayOnly: Boolean, iceServers: List<IceServer>): String?

    /** Kiruvchi qo'ng'iroqning offer'ini qo'llab **answer** quradi. */
    suspend fun createAnswer(
        remoteOfferSdp: String,
        media: CallMedia,
        relayOnly: Boolean,
        iceServers: List<IceServer>,
    ): String?

    /** Chaquvchi tomonda: `call:accepted` dagi answer'ni qo'llaydi. */
    suspend fun acceptAnswer(remoteAnswerSdp: String): Boolean

    /**
     * Peer'dan kelgan nomzodni qo'shadi.
     *
     * Nomzod hech qachon o'zgartirilmaydi — server ham shunday qiladi (yagona chetlashish:
     * `relayOnly` qo'ng'iroqda `typ relay` bo'lmagan nomzodni **tashlab yuborish**, qayta
     * yozish emas).
     */
    suspend fun addRemoteCandidate(candidate: String, sdpMid: String, sdpMLineIndex: Int)

    /** Qayta muzokara: yangi offer quradi (kamera yoqilganda, ICE restart'da). */
    suspend fun createRenegotiationOffer(iceRestart: Boolean = false): String?

    /** Peer'dan kelgan `call:renegotiate` offer'iga answer quradi. */
    suspend fun answerRenegotiation(remoteOfferSdp: String): String?

    fun setMicEnabled(enabled: Boolean)
    fun setCameraEnabled(enabled: Boolean)
    fun switchCamera()
    fun setSpeakerEnabled(enabled: Boolean)

    /**
     * Qo'ng'iroq o'lchovlari — `POST /v1/calls/{id}/stats` uchun.
     *
     * ⚠️ Qiymatlar **tanlangan juftlikdan** olinadi (`state == succeeded && nominated`),
     * yig'ilgan nomzodlardan emas: relay nomzodlar deyarli har qo'ng'iroqda yig'iladi,
     * lekin ko'pincha ishlatilmaydi va «relay yig'ildimi?» degan o'lchov butunlay yaroqsiz
     * raqam berardi (`handoff/09-CALLS-REST.md` §3).
     *
     * `null` — o'lchash uchun narsa yo'q (media umuman oqmagan).
     */
    suspend fun collectStats(): CallStats?

    /** Hamma narsani bo'shatadi. Qayta ishlatib bo'lmaydi — har qo'ng'iroqqa yangi nusxa. */
    fun close()
}

/** Media qatlamidan chiqadigan hodisalar. */
sealed interface CallEngineEvent {

    /**
     * Local ICE nomzodi topildi — **darhol** (trickle) `call:ice` bilan yuboriladi.
     *
     * Yig'ib, keyin bir yo'la yuborish socket bucket'ini (30 token, sekundiga 15 ta)
     * ~2 soniyada bo'shatadi va `RATE_LIMITED` keltiradi.
     */
    data class LocalCandidate(
        val candidate: String,
        val sdpMid: String,
        val sdpMLineIndex: Int,
    ) : CallEngineEvent

    /** ICE `connected`/`completed` — endi `call:connected` yuboriladi. */
    data object Connected : CallEngineEvent

    /** ICE `disconnected` — vaqtinchalik bo'lishi mumkin, qo'ng'iroq yopilmaydi. */
    data object Disconnected : CallEngineEvent

    /** ICE `failed`/`closed` — qo'ng'iroq tugadi. */
    data object Failed : CallEngineEvent

    /** Suhbatdoshning video treki keldi yoki ketdi. */
    data class RemoteVideo(val enabled: Boolean) : CallEngineEvent

    /** Media qatlami qayta muzokara so'radi (trek qo'shildi/olib tashlandi). */
    data object RenegotiationNeeded : CallEngineEvent
}

/**
 * Har qo'ng'iroq uchun yangi [CallEngine] beradi.
 *
 * Platformaga xos (Android'da `Context` va `EglBase` kerak), shuning uchun DI orqali
 * uzatiladi — `expect fun` emas: fabrika holatga ega va uni Koin boshqargani ma'qul.
 */
interface CallEngineFactory {
    fun create(): CallEngine
}
