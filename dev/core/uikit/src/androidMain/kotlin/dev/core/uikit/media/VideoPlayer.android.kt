package dev.core.uikit.media

import android.graphics.Color
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

/** Pozitsiya so'rash oralig'i — 10 marta/sek: chiziq silliq to'ladi, batareya sezmaydi. */
private const val PROGRESS_TICK_MS = 100L

// `PlayerView`, `DefaultHttpDataSource` va boshqalar Media3'da hali "unstable" deb
// belgilangan — bu Java'cha `@RequiresOptIn`, shuning uchun Kotlin'niki emas,
// `androidx.annotation.OptIn` ishlatiladi.
@OptIn(UnstableApi::class)
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
    onEnded: () -> Unit,
    onProgress: (positionMs: Long, durationMs: Long) -> Unit,
) {
    val context = LocalContext.current
    // Listener pleyer bilan birga yashaydi, callback esa har kompozitsiyada yangilanishi
    // mumkin — eski lambda ushlanib qolmasin.
    val currentOnEnded by rememberUpdatedState(onEnded)
    val currentOnProgress by rememberUpdatedState(onProgress)

    // Pleyer faqat havola yoki sarlavha o'zgarganda qayta quriladi: qolgan parametrlar
    // (ovoz, takror, boshqaruv) mavjud pleyerga "issiq" qo'llanadi, aks holda har
    // sozlama o'zgarishida video boshidan yuklanardi.
    val player = remember(url, headers) {
        // Sarlavhalarni faqat HTTP manbasi tushunadi. Uni to'g'ridan-to'g'ri emas,
        // `DefaultDataSource` ichiga o'raymiz — shunda bir xil komponent `file://` va
        // `content://` havolalarini ham o'ynata oladi (galereyadan tanlangan videoni
        // yuborishdan oldin ko'rsatish).
        val httpFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(headers)
            // Media serverlari CDN'ga http→https yo'naltirishi odatiy hol; buni
            // yoqmasak, yo'naltirishda o'ynatish "manba topilmadi" bilan uziladi.
            .setAllowCrossProtocolRedirects(true)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(DefaultDataSource.Factory(context, httpFactory)),
            )
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(url))
                // Sozlamalarning boshlang'ich holati birinchi kadrdan oldin turishi kerak,
                // aks holda video bir lahza ovozli/ovozsiz "chirt" etadi.
                playWhenReady = autoPlay
                repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                volume = if (muted) 0f else 1f
                prepare()
            }
    }

    // Quyidagilar `LaunchedEffect` da — `SideEffect` bo'lsa, foydalanuvchi qo'lda
    // to'xtatgan videoni har qayta chizishda qaytadan ishga tushirib yuborardik.
    LaunchedEffect(player, autoPlay) { player.playWhenReady = autoPlay }
    LaunchedEffect(player, loop) {
        player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }
    LaunchedEffect(player, muted) { player.volume = if (muted) 0f else 1f }

    // Ijro pozitsiyasi — **so'rab olinadi**, chunki ExoPlayer'da "har kadrda pozitsiya"
    // hodisasi yo'q (`onPositionDiscontinuity` faqat sakrashda keladi). Story'ning tepasidagi
    // chiziq shu qiymat bo'yicha to'ladi, ya'ni buferlanish paytida u ham to'xtab turadi.
    LaunchedEffect(player) {
        while (true) {
            // `duration` metadata o'qilmaguncha `TIME_UNSET` (manfiy) bo'ladi — chaqiruvchi
            // manfiy son bilan bo'lib yubormasin uchun `0` beramiz.
            val duration = player.duration.takeIf { it > 0 } ?: 0L
            currentOnProgress(player.currentPosition.coerceAtLeast(0L), duration)
            delay(PROGRESS_TICK_MS)
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) currentOnEnded()
            }

            // Takror rejimida holat `ENDED` ga o'tmaydi — aylanish `MEDIA_ITEM_TRANSITION_REASON_REPEAT`
            // orqali bilinadi. GIF sanog'i shu yerdan yuritiladi.
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) currentOnEnded()
            }
        }
        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
            // ⚠️ `release()` SHART: ExoPlayer apparat dekoderini, ovoz kanalini va tarmoq
            // buferini ushlab turadi. Bo'shatilmasa ekran yopilgandan keyin ham ovoz davom
            // etadi, dekoder esa qurilmada cheklangan resurs — bir nechta "unutilgan" pleyer
            // keyingi videolarni umuman ochilmaydigan qilib qo'yadi.
            player.release()
        }
    }

    // Ilova fonga ketganda to'xtatamiz. `ON_STOP` — ON_PAUSE emas: dialog/bo'linadigan
    // ekranda video bekorga uzilib qolmasin.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        var wasPlaying = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    wasPlaying = player.isPlaying
                    player.pause()
                }
                // Qaytganda faqat fonga ketishdan OLDIN o'ynayotgan bo'lsa davom etamiz —
                // foydalanuvchi qo'lda to'xtatgan video o'z-o'zidan jonlanmaydi.
                Lifecycle.Event.ON_START -> if (wasPlaying) player.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val resizeMode = if (contentScaleFit) {
        AspectRatioFrameLayout.RESIZE_MODE_FIT
    } else {
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = showControls
                this.resizeMode = resizeMode
                // Birinchi kadrgacha ko'rinadigan qora to'siq shaffof bo'lsin — chat
                // pufakchasi va stiker fonida qora to'rtburchak yarqirab ketmasligi uchun.
                setShutterBackgroundColor(Color.TRANSPARENT)
            }
        },
        update = { view ->
            if (view.player !== player) view.player = player
            view.useController = showControls
            view.resizeMode = resizeMode
        },
        // ⚠️ View pleyerdan oldin o'lishi mumkin (ro'yxatda qayta ishlatilganda):
        // bog'lanishni uzmasak, `PlayerView` `Player.Listener` orqali pleyerni ushlab
        // qoladi va u bo'shatilgandan keyin ham chizishga urinadi.
        onRelease = { view -> view.player = null },
    )
}
