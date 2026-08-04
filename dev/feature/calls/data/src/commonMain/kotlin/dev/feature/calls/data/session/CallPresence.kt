package dev.feature.calls.data.session

/**
 * Tizimga «hozir qo'ng'iroq ketyapti» deb bildiradigan platforma ilmog'i.
 *
 * Android'da bu old plan xizmati ([CallForegroundService]): usiz Android 14+ ilova fonga
 * o'tganda mikrofonni jimgina o'chirib qo'yadi. iOS'da bu CallKit bo'ladi (2-bosqich).
 *
 * Interfeys `commonMain` da, chunki uni chaqiradigan joy — holat mashinasi, u esa ikkala
 * platformada bir xil.
 */
interface CallPresence {

    /**
     * Kiruvchi qo'ng'iroq keldi — telefon jiringlay boshladi.
     *
     * Android'da bu **to'liq ekranli** bildirishnoma: ilova fonda bo'lsa ham (yoki ekran
     * o'chiq bo'lsa ham) tizim qo'ng'irog'idek "Javob berish / Rad etish" oynasi chiqadi.
     * Usiz kiruvchi qo'ng'iroq faqat ilova ochiq turganda ko'rinardi — ya'ni amalda
     * ko'rinmasdi.
     */
    fun onIncomingCall(peerName: String, video: Boolean)

    /**
     * Jonli qo'ng'iroq boshlandi. [peerName] bildirishnomada ko'rinadi.
     *
     * [video] — kamera ishlatiladimi. Android'da bu **muhim**: old plan xizmatining
     * `camera` turi berilgan `CAMERA` ruxsatini talab qiladi va ovozli qo'ng'iroqda u
     * yo'q — turni bekorga e'lon qilish `SecurityException` keltiradi.
     */
    fun onCallStarted(peerName: String, video: Boolean)

    /** Qo'ng'iroq tugadi — barcha ilmoqlar bo'shatiladi. */
    fun onCallEnded()

    /** Platforma ilmog'i kerak bo'lmagan joylar uchun (testlar, iOS 1-bosqichi). */
    companion object Noop : CallPresence {
        override fun onIncomingCall(peerName: String, video: Boolean) = Unit
        override fun onCallStarted(peerName: String, video: Boolean) = Unit
        override fun onCallEnded() = Unit
    }
}
