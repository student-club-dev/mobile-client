package dev.core.uikit.media

import android.content.Context
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
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import java.io.File

/** Pozitsiya so'rash oralig'i — 10 marta/sek: chiziq silliq to'ladi, batareya sezmaydi. */
private const val PROGRESS_TICK_MS = 100L

/**
 * Ko'rilgan videolar telefonda saqlanadigan kesh — **`StudentClub/video`**.
 *
 * Busiz ExoPlayer har ochilishda faylni **boshidan yuklab olardi**: story ikkinchi marta
 * ko'rilganda ham, chatdagi video qayta bosilganda ham. Telegram aynan shu sababdan o'z
 * keshini yuritadi.
 *
 * ⚠️ [SimpleCache] bitta papka uchun **yagona** bo'lishi shart — ikkita nusxa yaratilsa
 * `IllegalStateException` bilan yiqiladi. Shuning uchun u shu obyektda, ilova jarayoni
 * davomida bir marta ochiladi.
 *
 * Papka `filesDir` da: `cacheDir` ni Android xotira tugaganda ogohlantirmasdan tozalaydi
 * va "bir marta yuklab olingan" va'dasi buzilardi. O'sish esa LRU bilan cheklangan.
 */
@OptIn(UnstableApi::class)
private object VideoCache {

    private const val MAX_BYTES = 512L * 1024 * 1024

    private var cache: SimpleCache? = null

    private fun cache(context: Context): SimpleCache = synchronized(this) {
        cache ?: SimpleCache(
            File(context.filesDir, "StudentClub/video"),
            LeastRecentlyUsedCacheEvictor(MAX_BYTES),
            // Kesh indeksining o'zi baza: qaysi bo'lak yuklab olingani shu yerda turadi.
            StandaloneDatabaseProvider(context),
        ).also { cache = it }
    }

    /**
     * Keshdan o'qiydigan, bo'lmaganini tarmoqdan olib **yozib qo'yadigan** manba.
     *
     * `FLAG_IGNORE_CACHE_ON_ERROR` — kesh buzilgan bo'lsa (disk to'lgan, fayl o'chirilgan)
     * video baribir tarmoqdan o'ynaydi: kesh qulaylik, ijroning sharti emas.
     */
    fun dataSourceFactory(context: Context, upstream: DataSource.Factory): DataSource.Factory {
        val cached = CacheDataSource.Factory()
            .setCache(cache(context))
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        return DataSource.Factory { AlwaysCacheDataSource(cached.createDataSource()) }
    }
}

/**
 * Kesh **hajm noma'lum bo'lganda ham** yozilsin.
 *
 * ⚠️ Bu yerda nozik joy bor: `ProgressiveMediaSource` har so'rovga
 * [DataSpec.FLAG_DONT_CACHE_IF_LENGTH_UNKNOWN] bayrog'ini qo'yadi va serverimiz
 * `/v1/media/{id}/raw` uchun `Content-Length` bermaganda `CacheDataSink` **hech nima
 * yozmaydi**. Tashqaridan bu "kesh ishlamayapti" bo'lib ko'rinadi: papka yaratiladi, ichi
 * esa bo'sh qoladi va video har ochilganda qaytadan yuklanadi.
 *
 * Bayroqni olib tashlaymiz: media fayli **o'zgarmas** (`{id}` bo'yicha bir martalik
 * havola), ya'ni uzunligini bilmasak ham keshlash xavfsiz — eskirgan nusxa degan tushuncha
 * bu yerda yo'q.
 */
@OptIn(UnstableApi::class)
private class AlwaysCacheDataSource(private val delegate: DataSource) : DataSource by delegate {

    override fun open(dataSpec: DataSpec): Long =
        delegate.open(
            dataSpec.buildUpon()
                .setFlags(dataSpec.flags and DataSpec.FLAG_DONT_CACHE_IF_LENGTH_UNKNOWN.inv())
                .build(),
        )
}

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
                DefaultMediaSourceFactory(
                    VideoCache.dataSourceFactory(
                        context,
                        DefaultDataSource.Factory(context, httpFactory),
                    ),
                ),
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
