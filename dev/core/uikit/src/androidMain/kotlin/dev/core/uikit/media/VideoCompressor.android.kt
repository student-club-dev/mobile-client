package dev.core.uikit.media

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Yuborishdan oldin videoni **yuborishga tayyorlaydi** — Telegramdagi kabi.
 *
 * G'oya: *keraksiz joyda qayta kodlamaslik, kerak bo'lganda esa imkon qadar kam kodlash*.
 *
 * - Video allaqachon chatga mos bo'lsa (H.264 · ≤[TARGET_HEIGHT] · ≤30 k/s · yengil bitreyt)
 *   u **qayta kodlanmaydi**, faqat konteyneri ko'chiriladi (*remux*) — bu bir necha soniya.
 * - Mos bo'lmasa 720p / 30 k/s / ≤2 Mbit/s H.264 ga o'giriladi.
 *
 * Nega muhim: ilgari har bir videoga `Presentation` effekti **shartsiz** qo'yilardi va
 * effekt borligi Transformer'ni doim "dekodlash → GPU → qayta kodlash" yo'liga tushirardi.
 * 4K/60 lavha 1080p va 60 k/s da qayta kodlanardi — 3 daqiqalik video uchun bu 10 800 kadr,
 * ya'ni bir necha daqiqa. Endi kadr tezligi 30 ga tushiriladi va bo'yi 720p bilan
 * chegaralanadi: taxminan **4 barobar kam ish** va yuklanadigan fayl ham yengilroq.
 *
 * Qaytadi: yuboriladigan fayl; tayyorlab bo'lmasa `null` (chaqiruvchi asl faylga qaytadi,
 * ya'ni yuborish to'xtamaydi).
 */
@OptIn(UnstableApi::class)
internal suspend fun compressVideo(
    context: Context,
    source: Uri,
    video: PickedVideo,
    /** Siqish ulushi (`0f..1f`) — ekrandagi foiz uchun. */
    onProgress: (Float) -> Unit = {},
): File? {
    val plan = exportPlan(video)

    val first = runExport(context, source, plan, video.durationMs, onProgress)
    if (first != null) return first

    // Remux ba'zi konteynerlarda (buzuq indeks, qo'llab-quvvatlanmaydigan atom) yiqiladi.
    // O'shanda bir marta **to'liq qayta kodlashga** tushamiz: sekinroq, lekin ishlaydi.
    // Reja allaqachon qayta kodlash bo'lgan bo'lsa takrorlashning ma'nosi yo'q.
    if (plan.transcode) return null
    return runExport(context, source, ExportPlan(transcode = true, effects = emptyList()), video.durationMs, onProgress)
}

/** Bitta eksport urinishi — qaysi rejada ketishi [plan] da. */
@OptIn(UnstableApi::class)
private suspend fun runExport(
    context: Context,
    source: Uri,
    plan: ExportPlan,
    durationMs: Int?,
    onProgress: (Float) -> Unit,
): File? {
    val output = File(context.cacheDir, "outgoing_video_${System.currentTimeMillis()}.mp4")

    // ⚠️ `Transformer` **asosiy oqimda** qurilishi va ishga tushirilishi shart: u o'zining
    // ichki `Looper` iga bog'lanadi va boshqa oqimdan chaqirilsa `IllegalStateException`
    // bilan yiqiladi. Kodlashning o'zi baribir Transformer'ning o'z oqimlarida ketadi.
    return withContext(Dispatchers.Main) {
        // Ishga tushgan eksport — jarayonni **so'rab** olish uchun kerak. Transformer'da
        // "har kadrda" hodisa yo'q, faqat `getProgress` bor va u ham asosiy oqimni talab
        // qiladi. Kuzatuvchi eksport boshlanmasdan oldin ishga tushishi mumkin, shuning
        // uchun havola nullable.
        var running: Transformer? = null

        val progressJob = launch {
            val holder = ProgressHolder()
            while (isActive) {
                delay(PROGRESS_TICK_MS)
                val transformer = running ?: continue
                if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(holder.progress / PERCENT)
                }
            }
        }

        try {
            suspendCancellableCoroutine { continuation ->
                val transformer = Transformer.Builder(context)
                    .apply {
                        // ⚠️ Kodek va enkoder sozlamalari **faqat** qayta kodlashda beriladi —
                        // aynan ular qayta kodlashni **majburlaydi**.
                        //
                        // Media3 ichida (`TransformerUtil.shouldTranscodeVideo`) qaror shunday
                        // chiqadi: `encoderFactory.videoNeedsEncoding()` — ya'ni so'ralgan
                        // `VideoEncoderSettings` standartdan farq qiladimi — yoki effektlar
                        // ro'yxati bo'sh emasmi. Bitreyt so'rashning o'zi birinchi shartni
                        // bajaradi.
                        //
                        // Shu sababdan bu blok remux rejimida qo'yilmaydi: qo'ysak, hech qanday
                        // effekt bo'lmasa ham video qayta kodlanardi va butun tejash yo'qolardi.
                        //
                        // ⚠️ Teskarisi ham muhim: faqat `setVideoMimeType(H264)` yetarli EMAS.
                        // Manba allaqachon H.264 bo'lsa Media3 "format bir xil" deb remux qiladi
                        // — og'ir bitreytli 720p video shunda kichraymay qolardi.
                        if (plan.transcode) {
                            setVideoMimeType(MimeTypes.VIDEO_H264)
                            setAudioMimeType(MimeTypes.AUDIO_AAC)
                            setEncoderFactory(
                                DefaultEncoderFactory.Builder(context)
                                    .setRequestedVideoEncoderSettings(
                                        VideoEncoderSettings.Builder()
                                            .setBitrate(targetBitrate(durationMs))
                                            .build(),
                                    )
                                    // Qurilma so'ralgan sozlamani bermasa, siqish TO'XTAMASIN:
                                    // kodek o'zining eng yaqin variantini tanlaydi. Aks holda
                                    // ba'zi telefonlarda video umuman yuborilmasdi.
                                    .setEnableFallback(true)
                                    .build(),
                            )
                        }
                    }
                    .addListener(
                        object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, result: ExportResult) {
                                if (continuation.isActive) continuation.resume(output)
                            }

                            override fun onError(
                                composition: Composition,
                                result: ExportResult,
                                exception: ExportException,
                            ) {
                                // Eksport bo'lmadi (kodek qo'llab-quvvatlamaydi, joy yo'q…) —
                                // chaqiruvchi qaror qiladi: qayta urinish yoki asl fayl.
                                output.delete()
                                if (continuation.isActive) continuation.resume(null)
                            }
                        },
                    )
                    .build()

                val item = EditedMediaItem.Builder(MediaItem.fromUri(source))
                    // ⚠️ Ro'yxat **bo'sh bo'lishi** mumkin va aynan shu muhim: effekt
                    // bo'lmasa Transformer remux yo'lini tanlaydi.
                    .setEffects(Effects(emptyList(), plan.effects))
                    .build()

                running = transformer
                transformer.start(item, output.absolutePath)

                continuation.invokeOnCancellation {
                    // Ekran yopilsa eksport ham to'xtaydi — aks holda kodek va fayl ochiq qolardi.
                    transformer.cancel()
                    output.delete()
                }
            }
        } finally {
            progressJob.cancel()
        }
    }
}

/** Eksport rejasi: qayta kodlanadimi va qanday effektlar qo'yiladi. */
private class ExportPlan(
    val transcode: Boolean,
    val effects: List<Effect>,
)

/**
 * Manbaning o'ziga qarab rejani tanlaydi.
 *
 * Qayta kodlash **to'rt** sababdan biri bo'lganda kerak bo'ladi:
 *
 * 1. kadr bo'yi [TARGET_HEIGHT] dan katta — chat pufagida bundan ortig'i ko'rinmaydi;
 * 2. kadr tezligi [FPS_TRANSCODE_ABOVE] dan yuqori (50/60 k/s) — kodlash ishi ikki barobar,
 *    foydasi esa yo'q;
 * 3. kodek H.264 emas (HEVC, VP9…) — server aynan H.264 kutadi, aks holda o'zi transkod
 *    qiladi va video `PROCESSING` da osilib turadi;
 * 4. bitreyt juda yuqori — remux hajmni **o'zgartirmaydi**, ya'ni 60 MB lik fayl o'sha
 *    holida yuklanardi va mobil internetda bu qayta kodlashdan ham uzoqroq ketardi.
 */
@OptIn(UnstableApi::class)
private fun exportPlan(video: PickedVideo): ExportPlan {
    val tooTall = video.height > TARGET_HEIGHT
    val tooFast = video.frameRate > FPS_TRANSCODE_ABOVE
    val tooHeavy = sourceBitrate(video) > REMUX_MAX_BITRATE

    val transcode = tooTall || tooFast || !video.isH264 || tooHeavy

    // Effektlar **faqat kerak bo'lganda**: masalan HEVC 720p/30 videoda ro'yxat bo'sh
    // qoladi va faqat kodek almashadi (GPU quvuri umuman ishga tushmaydi).
    val effects = buildList {
        if (tooTall) add(Presentation.createForHeight(TARGET_HEIGHT))
        if (tooFast) add(FrameDropEffect.createDefaultFrameDropEffect(TARGET_FPS))
    }

    return ExportPlan(transcode = transcode, effects = effects)
}

/**
 * Manbaning taxminiy bitreyti.
 *
 * Davomiylik noma'lum bo'lsa "juda katta" deb hisoblanadi: o'shanda remux o'rniga qayta
 * kodlash tanlanadi va hajm baribir nazoratda qoladi.
 */
private fun sourceBitrate(video: PickedVideo): Double {
    val seconds = (video.durationMs ?: 0) / 1000.0
    if (seconds <= 0.0 || video.sizeBytes <= 0L) return Double.MAX_VALUE
    return video.sizeBytes * BITS_PER_BYTE / seconds
}

/**
 * Bitreyt **maqsadli hajmdan** hisoblanadi: shunda uzun video ham chegaraga sig'adi.
 * Faqat o'lchamni kichraytirish yetmasdi — 3 daqiqalik 4K lavha 720p da ham katta chiqardi.
 */
private fun targetBitrate(durationMs: Int?): Int {
    val seconds = ((durationMs ?: 0) / 1000).coerceAtLeast(1)
    return (TARGET_BYTES * BITS_PER_BYTE_LONG / seconds).coerceIn(MIN_BITRATE, MAX_BITRATE).toInt()
}

/**
 * Siqilgan videoning eng katta balandligi — 720p.
 *
 * Ilgari 1080p edi. Telegram ham ancha kichik yuboradi: telefon ekranidagi pufakda farq
 * deyarli sezilmaydi, kodlash va yuklash vaqtidagi farq esa sezilarli.
 */
private const val TARGET_HEIGHT = 720

/** Qayta kodlashda kadr tezligi shunga tushiriladi. */
private const val TARGET_FPS = 30f

/**
 * Shundan tez videogina kadr tashlashga tushadi.
 *
 * Chegara 30 emas, 33: kamera "30 k/s" deb yozgan videoning haqiqiy tezligi 29.97 yoki
 * 30.02 bo'lishi mumkin va aniq 30 bilan solishtirsak ular bekorga qayta kodlanardi.
 */
private const val FPS_TRANSCODE_ABOVE = 33f

/**
 * Siqilgan faylning **maqsadli hajmi** — 12 MB.
 *
 * Serverning chegarasi 64 MB, lekin unga tiralish maqsad emas: yuborish vaqti to'g'ridan
 * to'g'ri hajmga bog'liq va mobil internetda 50 MB bir necha daqiqa ketardi.
 */
private const val TARGET_BYTES = 12L * 1024 * 1024
private const val BITS_PER_BYTE = 8.0
private const val BITS_PER_BYTE_LONG = 8L

/**
 * Bitreyt chegaralari: pastda video "loyqa", tepada esa siqishning ma'nosi qolmaydi.
 * 720p uchun 2 Mbit/s toza tasvir beradi.
 */
private const val MIN_BITRATE = 600_000L
private const val MAX_BITRATE = 2_000_000L

/**
 * Remux uchun ruxsat etilgan eng yuqori bitreyt.
 *
 * Remux hajmni o'zgartirmaydi — bundan og'ir video qayta kodlanmasa, tejagan vaqtimizni
 * yuklashda ikki barobar qaytarib berardik.
 */
private const val REMUX_MAX_BITRATE = 2_500_000.0

/** Jarayonni so'rash oralig'i va foizni ulushga o'girish. */
private const val PROGRESS_TICK_MS = 250L
private const val PERCENT = 100f
