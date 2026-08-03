package dev.core.uikit.media

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
actual fun rememberAudioPlayer(
    headers: Map<String, String>,
    onProgress: (positionMs: Int, durationMs: Int) -> Unit,
    onEnded: () -> Unit,
): AudioPlaybackController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ⚠️ `headers` va lambdalar har rekompozitsiyada yangi obyekt bo'lishi mumkin — ular
    // `remember` kalitiga QO'YILMAYDI, aks holda ijro paytida pleyer qayta yaratilib,
    // ovoz uzilib qolardi. O'rniga har doim eng oxirgi qiymat o'qiladi.
    val currentHeaders by rememberUpdatedState(headers)
    val currentOnProgress by rememberUpdatedState(onProgress)
    val currentOnEnded by rememberUpdatedState(onEnded)

    val controller = remember(context, scope) {
        MediaPlayerController(
            context = context,
            scope = scope,
            headers = { currentHeaders },
            onProgress = { position, duration -> currentOnProgress(position, duration) },
            onEnded = { currentOnEnded() },
        )
    }

    DisposableEffect(controller) {
        // ⚠️ Ekran yopilganda ovoz o'chsin va dekoder bo'shasin.
        onDispose { controller.stop() }
    }

    return controller
}

/**
 * `MediaPlayer` ustidagi qobiq.
 *
 * ExoPlayer olinmadi: bu yerda kerak bo'lgani — bitta qisqa AAC oqimini sarlavha bilan
 * ochish, buni platforma pleyeri qo'shimcha bog'liqliksiz uddalaydi.
 *
 * Progress `MediaPlayer` dan **so'rab** olinadi (callback yo'q), shuning uchun ijro davomida
 * kichik tsikl aylanadi va u kompozitsiya scope'iga bog'langan — ekran yopilishi bilan o'zi
 * to'xtaydi.
 */
private class MediaPlayerController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val headers: () -> Map<String, String>,
    private val onProgress: (Int, Int) -> Unit,
    private val onEnded: () -> Unit,
) : AudioPlaybackController {

    private var player: MediaPlayer? = null
    private var currentUrl: String? = null
    private var ticker: Job? = null

    override fun play(url: String) {
        val existing = player
        if (existing != null && currentUrl == url) {
            // Pauzadan (yoki tugagan joyidan — u yerda pozitsiya 0 ga qaytarilgan) davom etadi.
            runCatching { existing.start() }
            startTicker()
            return
        }

        stop() // boshqa yozuv eshitilayotgan bo'lsa — uni to'xtatamiz

        val mp = MediaPlayer()
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                // Nutq deb belgilash tizimga ovozni suhbat uchun moslashtirish imkonini beradi.
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        mp.setOnPreparedListener { prepared ->
            prepared.start()
            onProgress(0, prepared.durationOrZero())
            startTicker()
        }
        mp.setOnCompletionListener { finished ->
            ticker?.cancel()
            val duration = finished.durationOrZero()
            onProgress(duration, duration)
            // Pleyer saqlanadi va boshiga qaytariladi: qayta eshitish tarmoqqa chiqmasin.
            runCatching { finished.seekTo(0) }
            onEnded()
        }
        mp.setOnErrorListener { _, _, _ ->
            // `true` — xatoni "hal qilindi" deb belgilaymiz, shunda `onCompletion` chaqirilmaydi.
            stop()
            onEnded()
            true
        }

        val opened = runCatching {
            // Media havolasi ochiq emas — token sarlavhasi bo'lmasa server 401 qaytaradi.
            mp.setDataSource(context, Uri.parse(url), headers())
            mp.prepareAsync()
        }.isSuccess

        if (!opened) {
            mp.release()
            onEnded()
            return
        }

        player = mp
        currentUrl = url
    }

    override fun pause() {
        ticker?.cancel()
        ticker = null
        runCatching { player?.pause() }
    }

    override fun stop() {
        ticker?.cancel()
        ticker = null
        val mp = player
        player = null
        currentUrl = null
        if (mp != null) {
            runCatching { mp.reset() }
            mp.release()
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val mp = player ?: break
                // Pleyer bo'shatilgan bo'lsa `isPlaying` IllegalStateException tashlaydi.
                val playing = runCatching { mp.isPlaying }.getOrDefault(false)
                if (!playing) break
                onProgress(mp.currentPosition, mp.durationOrZero())
                delay(PROGRESS_TICK_MS)
            }
        }
    }
}

/** Jonli oqimda yoki tayyorlanmagan pleyerda `duration` manfiy bo'lishi mumkin. */
private fun MediaPlayer.durationOrZero(): Int =
    runCatching { duration }.getOrDefault(0).coerceAtLeast(0)
