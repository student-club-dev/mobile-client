package dev.feature.calls.domain.model

import dev.feature.connections.domain.model.StudentSummary
import kotlinx.datetime.Instant

/**
 * **Jonli** qo'ng'iroqning to'liq holati — qo'ng'iroq ekrani shuni chizadi.
 *
 * Bir vaqtda faqat bittasi bo'ladi: server ham ikkinchi taklifni `CALL_BUSY` bilan rad
 * etadi, klient ham kiruvchi taklifni `call:decline { BUSY }` bilan yopadi.
 *
 * ⚠️ [status] serverning holat mashinasining ko'zgusi, [failure] esa — **klient tomonidagi**
 * xato (mikrofon ruxsati yo'q, TURN olinmadi). Ikkalasi mustaqil: server hech narsa
 * bilmagan holda ham qo'ng'iroq klientda yiqilishi mumkin.
 */
data class CallSession(
    /** uuid v4. Chiquvchi qo'ng'iroqda `call:invite` ack'i kelgunicha bo'sh bo'ladi. */
    val callId: String,
    val peer: StudentSummary,
    val direction: CallDirection,
    val media: CallMedia,
    val status: CallStatus,
    /**
     * Butun media TURN orqali ketishi shartmi.
     *
     * `true` bo'lsa klient `iceTransportPolicy = "relay"` bilan ishlaydi va `host`/`srflx`
     * nomzodlarni **umuman yig'maydi**. Bu maxfiylik nazorati: offer taklif bilan birga
     * ketadi, ya'ni TURN majburlanmasa chaqirilgan **javob bermasa ham** chaquvchining uy
     * IP manzilini olardi (`handoff/09-CALLS-PROTOCOL.md` §11).
     *
     * Serverning qoidasi: juftlik orasida avval javob berilgan qo'ng'iroq bo'lmagan bo'lsa
     * `true`, bir marta haqiqatan gaplashgandan keyin `false`.
     */
    val relayOnly: Boolean = true,
    /** Suhbat id'si — `call:incoming` da keladi; chiquvchida `call:invite` ack'ida. */
    val conversationId: String? = null,
    /**
     * Jiringlash **qachon tugashi** — `startedAt + 45s`, serverdan keladi.
     *
     * ⚠️ Ekrandagi taymer aynan shundan hisoblanadi, o'z soatimizdan emas: qurilma soati
     * bir necha soniyaga qochsa taymer serverning haqiqiy muddatidan ajralib ketardi.
     */
    val ringingExpiresAt: Instant? = null,
    /** `ACTIVE` ga o'tgan payt — davomiylik taymeri shundan sanaydi. */
    val connectedAt: Instant? = null,

    // --- Media holati --------------------------------------------------------------------
    val micEnabled: Boolean = true,
    val cameraEnabled: Boolean = false,
    val frontCamera: Boolean = true,
    val speakerOn: Boolean = false,
    /** Suhbatdoshning mikrofoni ochiqmi (`call:media-state`). */
    val remoteAudio: Boolean = true,
    /** Suhbatdoshning kamerasi ochiqmi (`call:media-state`). */
    val remoteVideo: Boolean = false,

    /** Qo'ng'iroq yopilgan sabab — terminal holatda to'ladi. */
    val endReason: CallEndReason? = null,
    /** Klient tomonidagi xato matni (ruxsat yo'q, TURN olinmadi va h.k.). */
    val failure: String? = null,
) {
    /** Ekranda «Jiringlamoqda…» / «Ulanmoqda…» / davomiylik — qaysi biri ko'rsatiladi. */
    val isRinging: Boolean get() = status == CallStatus.RINGING
    val isConnecting: Boolean get() = status == CallStatus.CONNECTING
    val isActive: Boolean get() = status == CallStatus.ACTIVE

    /** Video oqimi kerakmi — kamera yoqilgan yoki suhbatdoshniki yoqilgan bo'lsa. */
    val showsVideo: Boolean get() = cameraEnabled || remoteVideo
}
