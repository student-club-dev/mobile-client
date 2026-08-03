package dev.feature.calls.domain.repository

import dev.core.common.error.AppException
import dev.feature.calls.domain.model.CallErrorCode
import dev.feature.calls.domain.model.CallMedia
import dev.feature.calls.domain.model.CallSession
import dev.feature.connections.domain.model.StudentSummary
import kotlinx.coroutines.flow.StateFlow

/**
 * Qo'ng'iroqni boshqarish — UI ko'radigan **yagona** eshik.
 *
 * Ortida uchta narsa turadi va ularning hech biri UI'ga ko'rinmaydi: `/calls` Socket.IO
 * kanali (signalizatsiya), WebRTC media qatlami va serverning holat mashinasi.
 *
 * Bitta jonli qo'ng'iroq ([session]) — ilova bo'ylab bitta nusxa (`single` Koin'da).
 * Shuning uchun qo'ng'iroq ekrani yopilsa ham qo'ng'iroq davom etadi va foydalanuvchi
 * chatga qaytib, ustidagi «qo'ng'iroqqa qaytish» chizig'i bilan qayta ochadi.
 */
interface CallController {

    /** Jonli qo'ng'iroq yoki `null`. Terminal holatda qisqa vaqt turadi, so'ng tozalanadi. */
    val session: StateFlow<CallSession?>

    /**
     * Kanalni ochadi — kirgan foydalanuvchi uchun ilova ishga tushganda chaqiriladi.
     *
     * Usiz kiruvchi qo'ng'iroq **umuman kelmaydi**: 1-bosqichda VoIP push yo'q, ya'ni
     * qo'ng'iroq faqat ilova ochiq va `/calls` socket'i ulangan bo'lganda ishlaydi
     * (`handoff/09-CALLS-README.md`).
     */
    fun start()

    /** Kanalni yopadi (chiqishda). Jonli qo'ng'iroq bo'lsa avval tugatiladi. */
    fun stop()

    /**
     * Chiquvchi qo'ng'iroq. Xato bo'lsa — foydalanuvchiga ko'rsatiladigan matn, aks holda `null`.
     *
     * Ichida ketma-ketlik: TURN hisobi → mikrofon/kamera ruxsati → offer → `call:invite`.
     * Har bosqich alohida xato beradi va **birortasi ham `call:invite` gacha yetmasa
     * server hech narsa bilmaydi** — chegaralar sarflanmaydi.
     */
    suspend fun call(peer: StudentSummary, media: CallMedia): String?

    /** Kiruvchi qo'ng'iroqqa javob beradi. Xato matni yoki `null`. */
    suspend fun accept(): String?

    /** Kiruvchi qo'ng'iroqni rad etadi (`call:decline { DECLINED }`). */
    suspend fun decline()

    /**
     * Tugatadi — holatga qarab to'g'ri hodisani tanlaydi.
     *
     * ⚠️ `RINGING` da `call:end` **hech narsa qilmaydi** (server uni jim no-op qiladi va
     * telefon jiringlashda davom etadi). Shuning uchun bu metod chaquvchi bo'lsa
     * `call:cancel`, chaqirilgan bo'lsa `call:decline`, aks holda `call:end` yuboradi
     * (`handoff/09-CALLS-PROTOCOL.md` §5).
     */
    suspend fun hangUp()

    fun toggleMic()
    fun toggleCamera()
    fun switchCamera()
    fun toggleSpeaker()

    /**
     * Access token yangilangach chaqiriladi — `call:auth { token }`.
     *
     * Qo'ng'iroq 4 soatgacha, token esa 15 daqiqa yashaydi. Socket handshake'da tokenning
     * `exp` ini **eslab qoladi**, ya'ni ilova fonda tokenni yangilagani socket uchun
     * ko'rinmaydi: usiz tokeni eskirgan odam «Javob berish» ni bosganda `TOKEN_EXPIRED`
     * oladi va qo'ng'iroq jimgina `MISSED` bo'ladi (`handoff/09-CALLS-PREREQUISITES.md` §3).
     */
    fun onTokenRefreshed(accessToken: String)
}

/**
 * Bu xato «qo'ng'iroq xususiyati o'chirilgan» degani (`503 NOT_IMPLEMENTED`).
 *
 * Uni umumiy «server ishlamayapti» dan ajratish **shart**: hozir `CALLS_ENABLED=false` va
 * coturn ko'tarilmagan, ya'ni bu javob kutilgan holat. Klient qo'ng'iroq tugmasini
 * o'chirib qo'yishi yoki «qo'ng'iroq hozircha mavjud emas» deyishi kerak.
 */
val AppException.callsUnavailable: Boolean
    get() = errorCode == CallErrorCode.NOT_IMPLEMENTED ||
        // Zaxira: konvert o'qilmagan (proksi o'zi 503 bergan) — o'shanda faqat status qoladi.
        (this is AppException.Server && this.code == HTTP_SERVICE_UNAVAILABLE)

private const val HTTP_SERVICE_UNAVAILABLE = 503
