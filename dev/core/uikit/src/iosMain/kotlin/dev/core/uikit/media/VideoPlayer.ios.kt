package dev.core.uikit.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.interop.UIKitViewController
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.addPeriodicTimeObserverForInterval
import platform.AVFoundation.currentItem
import platform.AVFoundation.duration
import platform.AVFoundation.muted
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.removeTimeObserver
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVKit.AVPlayerViewController
import platform.CoreGraphics.CGRectZero
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.darwin.NSEC_PER_SEC
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.QuartzCore.CATransaction
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIColor
import platform.UIKit.UIView

/**
 * `AVURLAsset` sarlavhalarni faqat shu (hujjatlashtirilmagan, lekin barqaror) kalit orqali
 * qabul qiladi — `NSURLRequest` bilan ishlaydigan ochiq API AVFoundation'da yo'q.
 */
private const val HTTP_HEADERS_KEY = "AVURLAssetHTTPHeaderFieldsKey"

/** Pozitsiya kuzatuvi oralig'i — 10 marta/sek (Android'dagi bilan bir xil). */
private const val PROGRESS_TICK_SECONDS = 0.1

/**
 * `CMTimeGetSeconds` **NaN** yoki cheksizlik qaytarishi mumkin (metadata hali yo'q, jonli
 * oqim) — bunday qiymat millisekundga o'girilsa ma'nosiz katta son bo'lardi, shuning uchun
 * u `0` ga tushadi.
 */
private fun Double.toMillis(): Long =
    if (isNaN() || isInfinite() || this <= 0.0) 0L else (this * 1000).toLong()

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun ScVideoPlayer(
    url: String,
    modifier: Modifier,
    headers: Map<String, String>,
    autoPlay: Boolean,
    loop: Boolean,
    muted: Boolean,
    showControls: Boolean,
    contentScaleFit: Boolean,
    state: ScVideoState?,
    onEnded: () -> Unit,
    onProgress: (positionMs: Long, durationMs: Long) -> Unit,
) {
    val currentOnEnded by rememberUpdatedState(onEnded)
    val currentLoop by rememberUpdatedState(loop)
    val currentOnProgress by rememberUpdatedState(onProgress)

    // Pleyer faqat havola yoki sarlavha o'zgarganda qayta quriladi — qolgan sozlamalar
    // mavjud `AVPlayer` ga qo'llanadi (aks holda video har safar boshidan yuklanardi).
    val player = remember(url, headers) {
        val nsUrl = NSURL(string = url)
        // ⚠️ Sarlavhalar `AVURLAsset` yaratilishida berilishi shart: keyin o'zgartirib
        // bo'lmaydi, chunki asset birinchi so'rovni o'zi yuboradi va token'siz 401 oladi.
        val options: Map<Any?, Any?>? =
            if (headers.isEmpty()) null else mapOf(HTTP_HEADERS_KEY to headers)
        val asset = AVURLAsset(uRL = nsUrl, options = options)
        // ⚠️ `apply { }` ishlatilmaydi: AVPlayer'ning o'z `muted` xossasi shu funksiyaning
        // `muted` parametrini to'sib qo'yadi va sozlama jimgina o'z-o'ziga tayinlanadi.
        val created = AVPlayer(playerItem = AVPlayerItem(asset = asset))
        // Boshlang'ich holat birinchi kadrdan oldin turishi kerak — aks holda
        // ovozsiz video bir lahza ovoz chiqarib yuboradi.
        created.muted = muted
        if (autoPlay) created.play()
        created
    }

    // `SideEffect` emas, `LaunchedEffect`: foydalanuvchi qo'lda to'xtatgan videoni
    // har qayta chizishda qaytadan ishga tushirib yubormaslik uchun.
    LaunchedEffect(player, autoPlay) { if (autoPlay) player.play() else player.pause() }
    LaunchedEffect(player, muted) { player.muted = muted }

    // AVPlayer'da "cheksiz takror" tayyor sozlamasi yo'q — video tugagani haqidagi
    // bildirishnomani ushlab, qo'lda boshiga qaytaramiz (GIF/stiker uchun kerak).
    DisposableEffect(player) {
        val center = NSNotificationCenter.defaultCenter
        val token = center.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = player.currentItem,
            queue = NSOperationQueue.mainQueue,
            usingBlock = {
                if (currentLoop) {
                    player.seekToTime(CMTimeMake(value = 0, timescale = 1))
                    player.play()
                }
                currentOnEnded()
            },
        )
        onDispose { center.removeObserver(token) }
    }

    // Ijro pozitsiyasi — `AVPlayer` ning davriy kuzatuvchisi orqali (Android'da u so'rab
    // olinadi). Story tepasidagi chiziq shu bo'yicha to'ladi: taymer bilan chizilsa video
    // buferlanayotganda chiziq oldinga ketib qolardi.
    DisposableEffect(player) {
        val interval = CMTimeMakeWithSeconds(PROGRESS_TICK_SECONDS, NSEC_PER_SEC.toInt())
        val observer = player.addPeriodicTimeObserverForInterval(interval, null) { time ->
            val position = CMTimeGetSeconds(time)
            // Davomiylik jonli oqimda `indefinite`, metadata o'qilmaguncha esa `NaN` —
            // ikkalasi ham `0` bo'lib ketadi (chaqiruvchi bunda chiziqni to'ldirmaydi).
            val duration = player.currentItem?.duration?.let { CMTimeGetSeconds(it) } ?: 0.0
            currentOnProgress(position.toMillis(), duration.toMillis())
            state?.let {
                // Ko'chirish so'rovi hali bajarilmagan bo'lsa pozitsiyani yozmaymiz —
                // aks holda chiziq barmoq qo'yib yuborilgach eski joyga sakrardi.
                if (it.seekRequest == null) it.positionMs = position.toMillis()
                it.durationMs = duration.toMillis()
                // `rate` — 0 dan katta bo'lsa ijro ketmoqda.
                it.isPlaying = player.rate > 0f
            }
        }
        onDispose { player.removeTimeObserver(observer) }
    }

    // Tashqi boshqaruv: play/pause va ko'chirish buyruqlari.
    if (state != null) {
        LaunchedEffect(player, state) {
            snapshotFlow { state.playRequest }.collect { request ->
                if (request != null) {
                    if (request) player.play() else player.pause()
                    state.isPlaying = request
                    state.consumePlay()
                }
            }
        }
        LaunchedEffect(player, state) {
            snapshotFlow { state.seekRequest }.collect { target ->
                if (target != null) {
                    player.seekToTime(CMTimeMakeWithSeconds(target / 1000.0, NSEC_PER_SEC.toInt()))
                    state.consumeSeek()
                }
            }
        }
    }

    // Ilova fonga ketganda to'xtatamiz: iOS o'zi to'xtatmaydi, video fonda ovoz chiqarib
    // ijro davom etaveradi.
    DisposableEffect(player) {
        val center = NSNotificationCenter.defaultCenter
        var wasPlaying = false
        val background = center.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
            usingBlock = {
                wasPlaying = player.rate != 0f
                player.pause()
            },
        )
        val foreground = center.addObserverForName(
            name = UIApplicationWillEnterForegroundNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
            // Faqat fonga ketishdan oldin o'ynayotgan bo'lsa davom etamiz — qo'lda
            // to'xtatilgan video o'z-o'zidan jonlanmaydi.
            usingBlock = { if (wasPlaying) player.play() },
        )
        onDispose {
            center.removeObserver(background)
            center.removeObserver(foreground)
        }
    }

    // ⚠️ Android'dagi `release()` ning iOS muqobili: `AVPlayer` elementni bo'shatmasa,
    // dekoder va tarmoq ulanishi ekran yopilgandan keyin ham ushlanib qoladi
    // (ovoz davom etadi, xotira sizadi).
    DisposableEffect(player) {
        onDispose {
            player.pause()
            player.replaceCurrentItemWithPlayerItem(null)
        }
    }

    val gravity = if (contentScaleFit) {
        AVLayerVideoGravityResizeAspect
    } else {
        AVLayerVideoGravityResizeAspectFill
    }

    if (showControls) {
        // AVPlayerLayer'da boshqaruv paneli yo'q — u faqat kadrni chizadi. Play/pause,
        // ko'chirgich va to'liq ekran kerak bo'lsa AVKit'ning tayyor ekrani ishlatiladi
        // (o'zimiz chizsak, iOS'ning odatiy xatti-harakatidan uzoqlashardik).
        UIKitViewController(
            modifier = modifier,
            factory = {
                val controller = AVPlayerViewController()
                controller.player = player
                controller.showsPlaybackControls = true
                controller.videoGravity = gravity
                controller
            },
            update = { controller ->
                (controller as AVPlayerViewController).player = player
                controller.videoGravity = gravity
            },
            // Kontroller pleyerni kuchli ushlaydi — bog'lanishni o'zimiz uzamiz.
            onRelease = { controller -> (controller as AVPlayerViewController).player = null },
        )
        return
    }

    val playerLayer = remember(player) { AVPlayerLayer.playerLayerWithPlayer(player) }
    SideEffect { playerLayer.videoGravity = gravity }

    UIKitView(
        modifier = modifier,
        factory = { VideoSurfaceView(playerLayer) },
        onRelease = { playerLayer.setPlayer(null) },
    )
}

/**
 * `AVPlayerLayer` ni sig'dirib turadigan eng sodda konteyner.
 *
 * ⚠️ CALayer avtomatik joylashuvga (Auto Layout) bo'ysunmaydi: konteyner o'lchami
 * o'zgarganda qatlam eski o'lchamda qolib, video qiyshayib ketadi. Shuning uchun
 * `layoutSubviews` da o'lcham qo'lda ko'chiriladi.
 */
@OptIn(ExperimentalForeignApi::class)
private class VideoSurfaceView(
    private val playerLayer: AVPlayerLayer,
) : UIView(frame = CGRectZero.readValue()) {

    init {
        backgroundColor = UIColor.clearColor
        layer.addSublayer(playerLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        // CALayer o'lcham o'zgarishini o'zi animatsiya qiladi — klaviatura ochilishi yoki
        // aylantirishda video "sirg'alib" keladi. Implicit animatsiyani o'chiramiz.
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        playerLayer.setFrame(bounds)
        CATransaction.commit()
    }
}
