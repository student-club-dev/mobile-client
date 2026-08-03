package dev.feature.calls.data.engine

import dev.feature.calls.domain.model.CallMedia
import dev.feature.calls.domain.model.CallStats
import dev.feature.calls.domain.model.IceServer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * iOS media qatlami — **hali ulanmagan**.
 *
 * Signalizatsiya, holat mashinasi, taymerlar, REST va butun UI `commonMain` da va iOS'da
 * ham ishlaydi; yetishmayotgani faqat shu — `WebRTC.framework`. Uni loyihaga qo'shish
 * Xcode tomonida ish talab qiladi (SPM/CocoaPods paketi + `Info.plist` dagi mikrofon va
 * kamera izohlari + CallKit), shuning uchun u alohida bosqichga qoldirilgan.
 *
 * Bu nusxa **jimgina yiqilmaydi**: [createOffer] va [createAnswer] `null` qaytaradi, ya'ni
 * qo'ng'iroq boshlanmaydi va foydalanuvchi aniq xato ko'radi. Server esa hech narsa
 * bilmaydi — `call:invite` gacha yetib bormaydi, chegaralar sarflanmaydi.
 */
private class UnsupportedCallEngine : CallEngine {

    override val events: Flow<CallEngineEvent> = emptyFlow()

    override suspend fun createOffer(
        media: CallMedia,
        relayOnly: Boolean,
        iceServers: List<IceServer>,
    ): String? = null

    override suspend fun createAnswer(
        remoteOfferSdp: String,
        media: CallMedia,
        relayOnly: Boolean,
        iceServers: List<IceServer>,
    ): String? = null

    override suspend fun acceptAnswer(remoteAnswerSdp: String): Boolean = false

    override suspend fun addRemoteCandidate(candidate: String, sdpMid: String, sdpMLineIndex: Int) = Unit

    override suspend fun createRenegotiationOffer(iceRestart: Boolean): String? = null

    override suspend fun answerRenegotiation(remoteOfferSdp: String): String? = null

    override fun setMicEnabled(enabled: Boolean) = Unit
    override fun setCameraEnabled(enabled: Boolean) = Unit
    override fun switchCamera() = Unit
    override fun setSpeakerEnabled(enabled: Boolean) = Unit

    override suspend fun collectStats(): CallStats? = null

    override fun close() = Unit
}

/** iOS uchun fabrika — `WebRTC.framework` qo'shilgach shu klass almashtiriladi. */
class IosCallEngineFactory : CallEngineFactory {
    override fun create(): CallEngine = UnsupportedCallEngine()
}
