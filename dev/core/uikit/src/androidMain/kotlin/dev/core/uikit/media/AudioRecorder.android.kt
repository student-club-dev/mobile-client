package dev.core.uikit.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
actual fun rememberAudioRecorder(onResult: (RecordedAudio?) -> Unit): AudioRecorderController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Callback har rekompozitsiyada yangilanadi, lekin kontroller — yo'q. Aks holda
    // `MediaRecorder` va yozilayotgan fayl har chizilishda qaytadan yaratilardi.
    val currentOnResult by rememberUpdatedState(onResult)

    val controller = remember(context, scope) {
        MicRecorderController(context, scope) { currentOnResult(it) }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // Ruxsat berilgach yozishni O'ZIMIZ boshlaymiz: foydalanuvchi tugmani bir marta
        // bosgan, ikkinchi marta bosishini kutish g'alati bo'lardi. Rad etilsa — `null`.
        if (granted) controller.start() else currentOnResult(null)
    }
    controller.requestPermission = { launcher.launch(Manifest.permission.RECORD_AUDIO) }

    DisposableEffect(controller) {
        // ⚠️ MAJBURIY: `MediaRecorder` bo'shatilmasa mikrofon butun jarayon bo'yicha band
        // qolib ketadi va boshqa ekranda yozuv umuman boshlanmaydi.
        onDispose { controller.release() }
    }

    return controller
}

/**
 * `MediaRecorder` ustidagi qobiq.
 *
 * Yozuv to'g'ridan-to'g'ri xotiraga emas, **vaqtinchalik faylga** boradi: `MediaRecorder`
 * faqat fayl yoki file-descriptor'ga yoza oladi, MPEG-4 konteyneri esa oxirida sarlavhasini
 * qayta yozgani uchun oqim sifatida o'qib bo'lmaydi. Fayl baytlari olingandan keyin darrov
 * o'chiriladi — cache'da eshitilmagan ovozlar yig'ilib qolmasin.
 */
private class MicRecorderController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onResult: (RecordedAudio?) -> Unit,
) : AudioRecorderController {

    /** Ruxsat yo'qligida chaqiriladi — launcher'ni faqat composable yarata oladi. */
    var requestPermission: () -> Unit = {}

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAtMs = 0L

    override fun start() {
        if (recorder != null) return // tugma ikki marta bosilgan
        if (!context.hasMicPermission()) {
            requestPermission()
            return
        }

        val target = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        val rec = createRecorder(context)
        rec.setAudioSource(MediaRecorder.AudioSource.MIC)
        // MPEG_4 + AAC = `.m4a`. Server `audio/mp4` ni qabul qiladi va bu format iOS'da ham
        // hech qanday konvertatsiyasiz ochiladi — bitta yozuv ikkala platformada eshitiladi.
        rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        // Nutq uchun mono/64 kbps yetarli: 5 daqiqa ≈ 2,4 MB, ya'ni 16 MB chegarasidan ancha uzoq.
        rec.setAudioChannels(1)
        rec.setAudioSamplingRate(SAMPLE_RATE_HZ)
        rec.setAudioEncodingBitRate(BIT_RATE)
        rec.setOutputFile(target.absolutePath)
        rec.setMaxDuration(MAX_VOICE_DURATION_MS)
        rec.setOnInfoListener { _, what, _ ->
            // ⚠️ `stop()` bu yerda MediaRecorder'niki emas, kontrollerniki bo'lishi shart —
            // chegaraga yetilganda yozuv tugab, natija foydalanuvchiga yetkaziladi.
            if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                this@MicRecorderController.stop()
            }
        }

        val started = runCatching {
            rec.prepare()
            rec.start()
        }.isSuccess

        if (!started) {
            // Mikrofonni boshqa ilova egallagan yoki kodek band — ekran yiqilmasin.
            rec.runCatching { reset() }
            rec.release()
            target.delete()
            onResult(null)
            return
        }

        recorder = rec
        file = target
        startedAtMs = SystemClock.elapsedRealtime()
    }

    override fun stop() {
        val rec = recorder ?: return
        val target = file
        // Davomiylik soat bo'yicha o'lchanadi: konteynerdan o'qish uchun faylni qaytadan
        // parse qilish kerak bo'lardi, farqi esa bir necha o'n millisekund.
        val durationMs = (SystemClock.elapsedRealtime() - startedAtMs).toInt()
        recorder = null
        file = null

        // ⚠️ Yozuv ≈1 soniyadan qisqa bo'lsa `stop()` RuntimeException tashlaydi va fayl
        // yaroqsiz qoladi — bunday holatni bekor qilingan deb hisoblaymiz.
        val stopped = runCatching { rec.stop() }.isSuccess
        rec.reset()
        rec.release()

        if (!stopped || target == null) {
            target?.delete()
            onResult(null)
            return
        }

        scope.launch {
            val audio = withContext(Dispatchers.IO) {
                val bytes = runCatching { target.readBytes() }.getOrNull()
                target.delete()
                bytes?.takeIf { it.isNotEmpty() }?.let {
                    RecordedAudio(bytes = it, fileName = VOICE_FILE_NAME, durationMs = durationMs)
                }
            }
            onResult(audio)
        }
    }

    override fun cancel() {
        releaseInternal()
        onResult(null)
    }

    /** Kompozitsiyadan chiqishda — natija berilmaydi, faqat resurs bo'shatiladi. */
    fun release() = releaseInternal()

    private fun releaseInternal() {
        val rec = recorder
        val target = file
        recorder = null
        file = null
        if (rec != null) {
            runCatching { rec.stop() }
            rec.reset()
            rec.release()
        }
        target?.delete()
    }

    private companion object {
        const val SAMPLE_RATE_HZ = 44_100
        const val BIT_RATE = 64_000
    }
}

private fun Context.hasMicPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Android 12+ da parametrsiz konstruktor eskirgan: tizim yozuv manbasini ilova konteksti
 * (attribution) bilan bog'lashni talab qiladi.
 */
@Suppress("DEPRECATION")
private fun createRecorder(context: Context): MediaRecorder =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
