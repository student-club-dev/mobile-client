package dev.core.uikit.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.addPeriodicTimeObserverForInterval
import platform.AVFoundation.currentItem
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.removeTimeObserver
import platform.AVFoundation.seekToTime
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.darwin.dispatch_get_main_queue

@Composable
actual fun rememberAudioPlayer(
    headers: Map<String, String>,
    onProgress: (positionMs: Int, durationMs: Int) -> Unit,
    onEnded: () -> Unit,
): AudioPlaybackController {
    // ⚠️ `headers` va lambdalar har rekompozitsiyada yangi obyekt bo'lishi mumkin — ular
    // `remember` kalitiga QO'YILMAYDI, aks holda ijro paytida pleyer qayta yaratilib,
    // ovoz uzilib qolardi.
    val currentHeaders by rememberUpdatedState(headers)
    val currentOnProgress by rememberUpdatedState(onProgress)
    val currentOnEnded by rememberUpdatedState(onEnded)

    val controller = remember {
        AvPlaybackController(
            headers = { currentHeaders },
            onProgress = { position, duration -> currentOnProgress(position, duration) },
            onEnded = { currentOnEnded() },
        )
    }

    DisposableEffect(controller) {
        // ⚠️ Ekran yopilganda ovoz o'chsin va kuzatuvchilar olib tashlansin — aks holda
        // `AVPlayer` blok ichida `self` ni ushlab, hech qachon yig'ilmaydi.
        onDispose { controller.stop() }
    }

    return controller
}

/**
 * `AVPlayer` ustidagi qobiq — `AVAudioPlayer` emas.
 *
 * Sabab: `AVAudioPlayer` faqat lokal fayl yoki xotiradagi `NSData` bilan ishlaydi, ya'ni
 * yozuvni **butunlay yuklab olib** keyin ochish kerak bo'lardi. `AVPlayer` esa oqim bilan
 * ishlaydi va — eng muhimi — `AVURLAsset` orqali so'rovga token sarlavhasini qo'sha oladi.
 */
@OptIn(ExperimentalForeignApi::class)
private class AvPlaybackController(
    private val headers: () -> Map<String, String>,
    private val onProgress: (Int, Int) -> Unit,
    private val onEnded: () -> Unit,
) : AudioPlaybackController {

    private var player: AVPlayer? = null
    private var currentUrl: String? = null
    private var timeObserver: Any? = null
    private var endObserver: Any? = null

    override fun play(url: String) {
        val existing = player
        if (existing != null && currentUrl == url) {
            // Pauzadan (yoki tugab, boshiga qaytarilgan joyidan) davom etadi.
            activateSession()
            existing.play()
            return
        }

        stop() // boshqa yozuv eshitilayotgan bo'lsa — uni to'xtatamiz

        val nsUrl = NSURL.URLWithString(url)
        if (nsUrl == null) {
            onEnded()
            return
        }

        // ⚠️ `AVURLAssetHTTPHeaderFieldsKey` hujjatlashtirilmagan, lekin oqimga sarlavha
        // qo'shishning yagona amaliy yo'li — muqobili `AVAssetResourceLoaderDelegate` bilan
        // butun HTTP oqimini qo'lda yozib chiqish bo'lardi.
        val options = headers().takeIf { it.isNotEmpty() }
            ?.let { mapOf<Any?, Any?>(HTTP_HEADERS_OPTION to it) }
        val asset = AVURLAsset(uRL = nsUrl, options = options)
        val item = AVPlayerItem(asset = asset)
        val avPlayer = AVPlayer(playerItem = item)

        endObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = item,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            val duration = avPlayer.durationMs()
            onProgress(duration, duration)
            // Boshiga qaytaramiz: qayta eshitish uchun oqim qaytadan ochilmasin.
            avPlayer.seekToTime(CMTimeMakeWithSeconds(0.0, TIMESCALE))
            onEnded()
        }

        timeObserver = avPlayer.addPeriodicTimeObserverForInterval(
            interval = CMTimeMakeWithSeconds(PROGRESS_TICK_MS / 1000.0, TIMESCALE),
            queue = dispatch_get_main_queue(),
        ) { time ->
            onProgress(CMTimeGetSeconds(time).toMillis(), avPlayer.durationMs())
        }

        player = avPlayer
        currentUrl = url
        activateSession()
        avPlayer.play()
    }

    override fun pause() {
        player?.pause()
    }

    override fun stop() {
        val avPlayer = player
        player = null
        currentUrl = null

        endObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        endObserver = null

        if (avPlayer != null) {
            timeObserver?.let { avPlayer.removeTimeObserver(it) }
            avPlayer.pause()
        }
        timeObserver = null
    }

    /**
     * `Playback` toifasi: ovozli xabar telefon "jim" rejimida ham eshitilsin va karnayga
     * chiqsin. Yozuvchi sessiyani `PlayAndRecord` ga o'tkazgan bo'lsa — bu uni qaytaradi.
     */
    private fun activateSession() {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, error = null)
        session.setActive(true, error = null)
    }

    private companion object {
        const val HTTP_HEADERS_OPTION = "AVURLAssetHTTPHeaderFieldsKey"

        /** CMTime uchun odatiy bo'luvchi — 600 barcha standart kadr chastotalariga bo'linadi. */
        const val TIMESCALE = 600
    }
}

/** Element hali tayyor bo'lmaganda davomiylik `NaN`/cheksiz bo'ladi — bunda 0 beramiz. */
@OptIn(ExperimentalForeignApi::class)
private fun AVPlayer.durationMs(): Int {
    val item = currentItem ?: return 0
    return CMTimeGetSeconds(item.duration).toMillis()
}

private fun Double.toMillis(): Int =
    if (isNaN() || isInfinite() || this < 0.0) 0 else (this * 1000).toInt()
