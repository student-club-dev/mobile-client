package dev.feature.calls.data.session

/**
 * iOS'da bu CallKit bo'ladi (`CXProvider` + `CXCallController`) — 2-bosqich, VoIP push
 * bilan birga. Hozircha hech narsa qilmaydi: ilova ochiq turganda qo'ng'iroq baribir
 * ishlaydi, faqat tizim qo'ng'iroq ekrani ko'rinmaydi.
 */
class IosCallPresence : CallPresence {
    override fun onCallStarted(peerName: String, video: Boolean) = Unit
    override fun onCallEnded() = Unit
}
