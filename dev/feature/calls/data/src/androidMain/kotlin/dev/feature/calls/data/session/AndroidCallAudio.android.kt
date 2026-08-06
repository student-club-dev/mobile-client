package dev.feature.calls.data.session

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dev.feature.calls.domain.model.CallEndReason
import io.github.aakira.napier.Napier

/**
 * [CallAudio] ning Android tomoni — tizim telefonining ovoz xatti-harakatini takrorlaydi.
 *
 * Uch bosqich, uchtasi ham boshqa audio kanalda ishlaydi va aynan shu tartib muhim:
 *
 * | Bosqich | Nima chalinadi | Oqim / usage |
 * |---|---|---|
 * | Kiruvchi jiringlash | tizim ringtone + tebranish | `USAGE_NOTIFICATION_RINGTONE` |
 * | Chiquvchi gudok | `TONE_SUP_RINGTONE` | `STREAM_VOICE_CALL` |
 * | Suhbat | WebRTC oqimi | `MODE_IN_COMMUNICATION` |
 *
 * ⚠️ Jiringlashni suhbat oqimida chalib bo'lmaydi: `MODE_IN_COMMUNICATION` da tizim ovozni
 * quloqchinga yo'naltiradi va telefon stolda turganda **hech narsa eshitilmaydi**. Shuning
 * uchun suhbat rejimiga o'tish faqat [onCallActive] da — jiringlash tugagandan keyin.
 */
class AndroidCallAudio(private val context: Context) : CallAudio {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var ringtone: Ringtone? = null
    private var toneGenerator: ToneGenerator? = null
    private var focusRequest: AudioFocusRequest? = null

    /** Tugash signali tugashini kutish uchun — boshqa kechiktirilgan ish yo'q. */
    private val handler = Handler(Looper.getMainLooper())

    /**
     * ⚠️ Aynan **shu nusxa** post qilinadi va shu nusxa bekor qilinadi. Metod havolasi
     * (`::releaseSystemAudio`) har chaqiruvda yangi `Runnable` yaratadi, ya'ni
     * `removeCallbacks` hech qachon mos kelmasdi va bekor qilish jimgina ishlamay qolardi.
     */
    private val releaseAudio = Runnable { releaseSystemAudio() }

    /** Qo'ng'iroqdan oldingi rejim — yopilganda aynan shunga qaytariladi. */
    private var previousMode: Int? = null

    // -------------------------------------------------------------------------------------
    // Jiringlash va gudok
    // -------------------------------------------------------------------------------------

    override fun startIncomingRinging() = runCatching {
        stopSignals()
        // Jimlik/tebranish rejimi — tizim telefonidagi kabi hurmat qilinadi.
        val mode = audioManager.ringerMode
        if (mode != AudioManager.RINGER_MODE_SILENT) vibrateRinging()
        if (mode != AudioManager.RINGER_MODE_NORMAL) return@runCatching

        requestFocus(AudioAttributes.USAGE_NOTIFICATION_RINGTONE, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: return@runCatching
        ringtone = RingtoneManager.getRingtone(context, uri)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            // `isLooping` faqat API 28+ da bor; undan eskisida `Ringtone` o'zi takrorlaydi.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
            play()
        }
    }.orLog("jiringlash boshlanmadi")

    /**
     * Chiquvchi gudok.
     *
     * `STREAM_VOICE_CALL` ataylab: gudok suhbat ovozi bilan **bir xil** yo'ldan chiqadi,
     * ya'ni foydalanuvchi telefonni quloqqa tutgan bo'lsa gudokni ham o'sha yerda eshitadi
     * va ulangandan keyin ovoz balandligi sakramaydi.
     */
    override fun startOutgoingRingback() = runCatching {
        stopSignals()
        enterCommunicationMode()
        toneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, RINGBACK_VOLUME).apply {
            startTone(ToneGenerator.TONE_SUP_RINGTONE)
        }
    }.orLog("gudok boshlanmadi")

    // -------------------------------------------------------------------------------------
    // Suhbat
    // -------------------------------------------------------------------------------------

    override fun onCallActive(speaker: Boolean) = runCatching {
        stopSignals()
        enterCommunicationMode()
        setSpeaker(speaker)
    }.orLog("suhbat rejimiga o'tilmadi")

    /**
     * Karnay/quloqchin.
     *
     * Android 12+ (API 31) da yagona ishonchli yo'l — `setCommunicationDevice`. Eskirgan
     * `isSpeakerphoneOn` **shu versiyalarda jimgina e'tiborsiz qoldirilishi mumkin**, ya'ni
     * karnay tugmasi bosiladi-yu ovoz quloqchinda qolaveradi. Eski API — 30 va undan
     * pastdagilar uchun zaxira.
     */
    override fun setSpeaker(enabled: Boolean) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val wanted = if (enabled) {
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                } else {
                    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                }
                val device = audioManager.availableCommunicationDevices.firstOrNull { it.type == wanted }
                if (device != null && audioManager.setCommunicationDevice(device)) return
                // Quloqchin topilmadi (planshetlarda umuman yo'q) — karnayda qolamiz.
            }
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = enabled
        }.orLog("ovoz marshruti o'zgarmadi")
    }

    // -------------------------------------------------------------------------------------
    // Yopish
    // -------------------------------------------------------------------------------------

    /**
     * Yopish ikki bosqichda: signal chalinadi, so'ng **u tugagach** audio rejimi tiklanadi.
     *
     * Tartib muhim — rejimni va fokusni darhol qaytarsak tizim signalni yarim yo'lda
     * kesib qo'yadi va foydalanuvchi "chirq" degan tovush eshitadi.
     */
    override fun stop(reason: CallEndReason?) {
        stopSignals()
        val playing = playEndTone(reason)
        if (playing) {
            handler.postDelayed(releaseAudio, END_TONE_MS + END_TONE_TAIL_MS)
        } else {
            releaseSystemAudio()
        }
    }

    /** Tugash signali — tizim telefonidagidek qisqa. `true` — signal chalinmoqda. */
    private fun playEndTone(reason: CallEndReason?): Boolean {
        val tone = when (reason) {
            CallEndReason.BUSY -> ToneGenerator.TONE_SUP_BUSY
            CallEndReason.FAILED -> ToneGenerator.TONE_SUP_CONGESTION
            CallEndReason.HANGUP, CallEndReason.DECLINED -> ToneGenerator.TONE_PROP_PROMPT
            // Javobsiz, bekor qilingan, boshqa qurilmam oldi — jim yopiladi.
            else -> return false
        }
        return runCatching {
            // Signal shu maydonda saqlanadi va `releaseSystemAudio` da bo'shatiladi —
            // aks holda har qo'ng'iroq bittadan `AudioTrack` ni oqizib ketardi.
            val generator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, END_TONE_VOLUME)
            toneGenerator = generator
            generator.startTone(tone, END_TONE_MS)
            true
        }.getOrElse {
            Napier.w("Qo'ng'iroq ovozi: tugash signali chalinmadi", it, tag = LOG_TAG)
            false
        }
    }

    /** Audio rejimi, marshrut va fokusni qo'ng'iroqdan oldingi holatiga qaytaradi. */
    private fun releaseSystemAudio() {
        runCatching {
            toneGenerator?.release()
            toneGenerator = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audioManager.clearCommunicationDevice()
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
            previousMode?.let { audioManager.mode = it }
            previousMode = null
        }.orLog("audio rejimi tiklanmadi")
        abandonFocus()
    }

    /** Jiringlash, tebranish va gudok — hammasi to'xtaydi. */
    private fun stopSignals() {
        // Oldingi qo'ng'iroqning tugash signali hali kutayotgan bo'lishi mumkin — yangi
        // qo'ng'iroq boshlangach u audio rejimini o'rtada tiklab yuborardi.
        handler.removeCallbacks(releaseAudio)
        runCatching { ringtone?.stop() }
        ringtone = null
        runCatching {
            toneGenerator?.stopTone()
            toneGenerator?.release()
        }
        toneGenerator = null
        runCatching { vibrator()?.cancel() }
    }

    // -------------------------------------------------------------------------------------
    // Tizim ilmoqlari
    // -------------------------------------------------------------------------------------

    private fun enterCommunicationMode() = runCatching {
        requestFocus(
            AudioAttributes.USAGE_VOICE_COMMUNICATION,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE,
        )
        if (previousMode == null) previousMode = audioManager.mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
    }.orLog("suhbat rejimi qo'yilmadi")

    /**
     * Audio fokus — usiz musiqa pleyeri jim bo'lmaydi va qo'ng'iroq ovozi uning ustiga
     * qo'shilib eshitiladi (foydalanuvchi buni "ovoz yo'q" deb qabul qiladi).
     */
    private fun requestFocus(usage: Int, gain: Int) = runCatching {
        abandonFocus()
        val attributes = AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(gain)
                .setAudioAttributes(attributes)
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, gain)
        }
    }.orLog("audio fokus olinmadi")

    private fun abandonFocus() = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }.orLog("audio fokus bo'shatilmadi")

    private fun vibrateRinging() = runCatching {
        val vibrator = vibrator() ?: return@runCatching
        if (!vibrator.hasVibrator()) return@runCatching
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val effect = VibrationEffect.createWaveform(VIBRATE_PATTERN, VIBRATE_REPEAT_INDEX)
        vibrator.vibrate(effect, attributes)
    }.orLog("tebranish ishlamadi")

    private fun vibrator(): Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    /**
     * Ovoz — qo'ng'iroqning **yordamchi** qismi: birorta ilmoq ishlamasa ham suhbat davom
     * etishi kerak. Shuning uchun hammasi `runCatching` ichida va faqat jurnalga tushadi.
     */
    private fun <T> Result<T>.orLog(message: String) {
        exceptionOrNull()?.let { Napier.w("Qo'ng'iroq ovozi: $message", it, tag = LOG_TAG) }
    }

    private companion object {
        const val LOG_TAG = "CallAudio"

        /** `ToneGenerator` balandligi 0..100. Gudok suhbat ovozidan bosiq bo'lsin. */
        const val RINGBACK_VOLUME = 70
        const val END_TONE_VOLUME = 60
        const val END_TONE_MS = 250

        /** Signal tugagach audio rejimini tiklashdan oldingi kichik zaxira. */
        const val END_TONE_TAIL_MS = 120L

        /** «jim — tebra — jim — tebra…» tizim jiringlashiga yaqin naqsh. */
        val VIBRATE_PATTERN = longArrayOf(0, 800, 900)

        /** Naqsh shu indeksdan takrorlanadi — ya'ni to'xtatilgunicha davom etadi. */
        const val VIBRATE_REPEAT_INDEX = 0
    }
}
