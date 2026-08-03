package dev.core.uikit.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioQualityMedium
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioRecorderDelegateProtocol
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVEncoderAudioQualityKey
import platform.AVFAudio.AVEncoderBitRateKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFAudio.setActive
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

@Composable
actual fun rememberAudioRecorder(onResult: (RecordedAudio?) -> Unit): AudioRecorderController {
    // Callback yangilanadi, kontroller — yo'q: `AVAudioRecorder` va yozilayotgan fayl
    // rekompozitsiyalar orasida saqlanishi shart.
    val currentOnResult by rememberUpdatedState(onResult)

    val controller = remember { AvRecorderController { currentOnResult(it) } }

    DisposableEffect(controller) {
        // ⚠️ MAJBURIY: yozuvchi bo'shatilmasa audio sessiya `PlayAndRecord` da qolib ketadi —
        // mikrofon band bo'ladi va boshqa ekranlarda ovoz karnay o'rniga quloqchinga chiqadi.
        onDispose { controller.release() }
    }

    return controller
}

/**
 * `AVAudioRecorder` ustidagi qobiq.
 *
 * 5 daqiqalik chegara `recordForDuration` orqali tizimga topshiriladi va u yozuvni o'zi
 * to'xtatganda xabarni delegat metodi yetkazadi.
 *
 * ⚠️ Delegat alohida klass ([RecorderDelegate]): Kotlin/Native bitta klassda Kotlin
 * interfeysi bilan Objective-C protokolini aralashtirishga ruxsat bermaydi.
 */
@OptIn(ExperimentalForeignApi::class)
private class AvRecorderController(
    private val onResult: (RecordedAudio?) -> Unit,
) : AudioRecorderController {

    private var recorder: AVAudioRecorder? = null
    private var fileUrl: NSURL? = null
    private var startedAtSec = 0.0

    /**
     * ⚠️ Delegat maydonda saqlanadi: `AVAudioRecorder.delegate` **weak** havola, obyektni
     * o'zimiz ushlab turmasak u yig'ilib ketadi va chegara haqidagi xabar kelmaydi.
     */
    private val delegate = RecorderDelegate { successfully ->
        // Tizim yozuvni o'zi tugatdi — deyarli har doim 5 daqiqalik chegara.
        if (successfully) stop() else cancel()
    }

    override fun start() {
        if (recorder != null) return // tugma ikki marta bosilgan

        // Ruxsat so'rovi ixtiyoriy oqimda javob beradi — natijani asosiy oqimga qaytaramiz,
        // chunki undan keyin darrov UI holati o'zgaradi.
        AVAudioSession.sharedInstance().requestRecordPermission { granted ->
            dispatch_async(dispatch_get_main_queue()) {
                if (granted) begin() else onResult(null)
            }
        }
    }

    override fun stop() {
        val rec = recorder ?: return
        val url = fileUrl
        recorder = null
        fileUrl = null

        // Davomiylik soat bo'yicha o'lchanadi: `currentTime` to'xtatilgandan keyin nolga
        // tushadi, faylni qaytadan parse qilish esa bir necha o'n millisekund uchun ortiqcha.
        val durationMs = ((NSDate().timeIntervalSince1970 - startedAtSec) * 1000).toInt()
        rec.delegate = null
        rec.stop()
        deactivateSession()

        val bytes = url?.let { NSData.dataWithContentsOfURL(it)?.toKotlinBytes() }
        url?.let { NSFileManager.defaultManager.removeItemAtURL(it, error = null) }

        onResult(
            bytes?.takeIf { it.isNotEmpty() }
                ?.let { RecordedAudio(bytes = it, fileName = VOICE_FILE_NAME, durationMs = durationMs) },
        )
    }

    override fun cancel() {
        releaseInternal()
        onResult(null)
    }

    /** Kompozitsiyadan chiqishda — natija berilmaydi, faqat resurs bo'shatiladi. */
    fun release() = releaseInternal()

    private fun begin() {
        val session = AVAudioSession.sharedInstance()
        // `PlayAndRecord` (faqat `Record` emas): shu ekranning o'zida yozilgan ovozni darrov
        // eshitib ko'rish mumkin bo'lsin, sessiyani qayta sozlamasdan.
        session.setCategory(AVAudioSessionCategoryPlayAndRecord, error = null)
        session.setActive(true, error = null)

        val url = newVoiceFileUrl()
        // Android tomondagi bilan bir xil profil: AAC, mono, 44.1 kHz, 64 kbps.
        val settings = mapOf<Any?, Any?>(
            AVFormatIDKey to kAudioFormatMPEG4AAC.toInt(),
            AVSampleRateKey to SAMPLE_RATE_HZ,
            AVNumberOfChannelsKey to 1,
            AVEncoderBitRateKey to BIT_RATE,
            AVEncoderAudioQualityKey to AVAudioQualityMedium,
        )

        val rec = runCatching { AVAudioRecorder(uRL = url, settings = settings, error = null) }.getOrNull()
        if (rec == null) {
            deactivateSession()
            onResult(null)
            return
        }
        rec.delegate = delegate

        // `recordForDuration` chegarani tizimga topshiradi — o'zimiz taymer yuritmaymiz,
        // ilova fonga o'tsa ham chegara ishlaydi.
        if (!rec.recordForDuration(MAX_VOICE_DURATION_MS / 1000.0)) {
            // Mikrofonni boshqa jarayon egallagan yoki sessiya faollashmadi.
            rec.delegate = null
            NSFileManager.defaultManager.removeItemAtURL(url, error = null)
            deactivateSession()
            onResult(null)
            return
        }

        recorder = rec
        fileUrl = url
        startedAtSec = NSDate().timeIntervalSince1970
    }

    private fun releaseInternal() {
        val rec = recorder
        val url = fileUrl
        recorder = null
        fileUrl = null
        if (rec != null) {
            rec.delegate = null
            rec.stop()
            deactivateSession()
        }
        url?.let { NSFileManager.defaultManager.removeItemAtURL(it, error = null) }
    }

    /** Sessiyani bo'shatamiz — to'xtatilgan musiqa va h.k. o'z-o'zidan davom etsin. */
    private fun deactivateSession() {
        AVAudioSession.sharedInstance().setActive(false, error = null)
    }
}

private const val SAMPLE_RATE_HZ = 44_100.0
private const val BIT_RATE = 64_000

/**
 * Yozuv tugaganini kuzatuvchi.
 *
 * ⚠️ Metod qo'lda `stop()` chaqirilganda ham ishga tushadi — kontrollerda `recorder`
 * allaqachon `null` bo'lgani uchun ikkinchi marta natija yuborilmaydi.
 */
private class RecorderDelegate(
    private val onFinish: (successfully: Boolean) -> Unit,
) : NSObject(), AVAudioRecorderDelegateProtocol {

    override fun audioRecorderDidFinishRecording(recorder: AVAudioRecorder, successfully: Boolean) {
        onFinish(successfully)
    }
}

/**
 * Vaqtinchalik fayl aynan `Caches` da: tizim joy tugaganda uni o'zi tozalay oladi va
 * iCloud'ga zaxiralanmaydi (yuborilgan ovoz baribir serverda).
 */
private fun newVoiceFileUrl(): NSURL {
    val dir = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
        .first() as String
    val stamp = (NSDate().timeIntervalSince1970 * 1000).toLong()
    return NSURL.fileURLWithPath("$dir/voice_$stamp.m4a")
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toKotlinBytes(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }
}
