package dev.feature.calls.data.session

import dev.feature.calls.domain.model.CallEndReason

/**
 * Qo'ng'iroqning **eshitiladigan** tomoni — tizim telefonidagi bilan bir xil bo'lishi kerak:
 *
 * - kimdir qo'ng'iroq qilsa telefon **jiringlaydi** va tebranadi (jimlik rejimi hurmat qilinadi);
 * - siz qo'ng'iroq qilsangiz quloqqa **gudok** eshitiladi;
 * - suhbatdosh band bo'lsa — "band" signali, qo'ng'iroq tugasa — qisqa "tugadi" signali;
 * - suhbat davomida ovoz to'g'ri chiqishga (quloqchin yoki karnay) yo'naltiriladi.
 *
 * ⚠️ **Audio rejim, fokus va marshrutning yagona egasi shu interfeys.** Ilgari ularning bir
 * qismi WebRTC dvigatelida edi; ikki egalik jiringlash bilan `MODE_IN_COMMUNICATION` ni
 * bir-biriga urib, qo'ng'iroqni ovozsiz qoldirardi.
 *
 * Holat mashinasi ([CallSessionManager]) uni holat o'zgarishlaridan boshqaradi, ya'ni
 * chaqiruv tartibi ikkala platformada bir xil.
 */
interface CallAudio {

    /**
     * Kiruvchi qo'ng'iroq — tizim jiringlashi + tebranish.
     *
     * Jimlik/tebranish rejimi hurmat qilinadi: telefon jim turgan bo'lsa ovoz chiqmaydi.
     */
    fun startIncomingRinging()

    /** Chiquvchi qo'ng'iroq — quloqqa eshitiladigan gudok ("ту-ту"). */
    fun startOutgoingRingback()

    /**
     * Suhbat boshlandi: barcha signallar to'xtaydi va ovoz suhbat rejimiga o'tadi.
     *
     * [speaker] — karnaymi (video qo'ng'iroq) yoki quloqchinmi (odatiy ovozli qo'ng'iroq).
     */
    fun onCallActive(speaker: Boolean)

    /** Karnay tugmasi. */
    fun setSpeaker(enabled: Boolean)

    /**
     * Qo'ng'iroq yopildi — sababga mos qisqa signal chalinadi va hamma narsa bo'shatiladi.
     *
     * [reason] `null` bo'lsa signalsiz jim yopiladi (masalan qo'ng'iroqqa boshqa
     * qurilmam javob berdi — bu yerda hech narsa bo'lmagandek tuyulishi kerak).
     */
    fun stop(reason: CallEndReason? = null)

    /** Platforma ilmog'i kerak bo'lmagan joylar uchun (testlar, iOS 1-bosqichi). */
    companion object Noop : CallAudio {
        override fun startIncomingRinging() = Unit
        override fun startOutgoingRingback() = Unit
        override fun onCallActive(speaker: Boolean) = Unit
        override fun setSpeaker(enabled: Boolean) = Unit
        override fun stop(reason: CallEndReason?) = Unit
    }
}
