package dev.feature.calls.data.session

import dev.feature.calls.domain.model.CallEndReason

/**
 * iOS'da qo'ng'iroq ovozini CallKit boshqaradi — jiringlash, gudok va marshrut tizimning
 * o'zidan keladi (`CXProvider`), ya'ni bu yerda qo'lda chalinadigan narsa yo'q.
 *
 * CallKit 2-bosqichda, `WebRTC.framework` bilan birga ulanadi ([IosCallPresence] ga
 * qarang). Shu paytgacha iOS'da media qatlami umuman ko'tarilmaydi, ya'ni bu nusxaning
 * bo'shligi hech narsani buzmaydi.
 */
class IosCallAudio : CallAudio {
    override fun startIncomingRinging() = Unit
    override fun startOutgoingRingback() = Unit
    override fun onCallActive(speaker: Boolean) = Unit
    override fun setSpeaker(enabled: Boolean) = Unit
    override fun stop(reason: CallEndReason?) = Unit
}
