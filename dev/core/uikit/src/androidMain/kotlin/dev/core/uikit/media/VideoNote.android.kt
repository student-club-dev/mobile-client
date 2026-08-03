package dev.core.uikit.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
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

@Composable
actual fun rememberVideoNotePreparer(): VideoPreparer {
    // ⚠️ `applicationContext` — `Activity` emas: tayyorlash yuborilgandan keyin, ekran
    // yopilganda ham davom etadi (u `viewModelScope` da ketadi).
    val context = LocalContext.current.applicationContext
    return remember(context) {
        VideoPreparer { video, onProgress -> context.prepareVideoNote(video, onProgress) }
    }
}

/**
 * Kvadratga kesish + qirqish + qayta kodlash — **bitta** eksportda.
 *
 * `Presentation.createForWidthAndHeight(..., LAYOUT_SCALE_TO_FIT_WITH_CROP)` aynan
 * kerakli ishni qiladi: kadr markazidan kvadrat kesib olinadi va so'ralgan o'lchamga
 * keltiriladi. Alohida `Crop` effekti kerak emasdi — u NDC koordinatalarini talab qiladi,
 * ya'ni manbaning nisbatini qo'lda hisoblab, har aylantirilgan videoda yana bir marta
 * xato qilish imkoni tug'ilardi.
 *
 * ⚠️ Bu yerda **remux yo'li yo'q**: kesish har doim GPU quvurini talab qiladi, ya'ni video
 * doim qayta kodlanadi. 60 soniyalik kvadrat kadr uchun bu bir necha soniya.
 */
@OptIn(UnstableApi::class)
private suspend fun Context.prepareVideoNote(
    video: PickedVideo,
    onProgress: (Float) -> Unit,
): PickedVideo? {
    val output = File(cacheDir, "outgoing_note_${System.currentTimeMillis()}.mp4")
    // Qirqish **manba** darajasida: uzun videoni kesib, keyin tashlab yuborish o'sha
    // kadrlarni bekorga kodlash degani.
    val clipped = MediaItem.Builder()
        .setUri(video.playbackUrl)
        .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setEndPositionMs(MAX_VIDEO_NOTE_MS.toLong())
                .build(),
        )
        .build()

    val durationMs = (video.durationMs ?: MAX_VIDEO_NOTE_MS).coerceAtMost(MAX_VIDEO_NOTE_MS)

    val exported = withContext(Dispatchers.Main) {
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
            // Tip ochiq beriladi: `onError` da `null` qaytadi va Kotlin aks holda
            // lambdaning turini `Nothing?` deb chiqarardi.
            suspendCancellableCoroutine<File?> { continuation ->
                val transformer = Transformer.Builder(this@prepareVideoNote)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(
                        DefaultEncoderFactory.Builder(this@prepareVideoNote)
                            .setRequestedVideoEncoderSettings(
                                VideoEncoderSettings.Builder()
                                    .setBitrate(noteBitrate(durationMs))
                                    .build(),
                            )
                            // Qurilma so'ralgan sozlamani bermasa tayyorlash TO'XTAMASIN —
                            // kodek eng yaqin variantni tanlaydi.
                            .setEnableFallback(true)
                            .build(),
                    )
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
                                output.delete()
                                if (continuation.isActive) continuation.resume(null)
                            }
                        },
                    )
                    .build()

                val item = EditedMediaItem.Builder(clipped)
                    .setEffects(
                        Effects(
                            emptyList(),
                            listOf(
                                Presentation.createForWidthAndHeight(
                                    VIDEO_NOTE_SIDE,
                                    VIDEO_NOTE_SIDE,
                                    Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP,
                                ),
                            ),
                        ),
                    )
                    .build()

                running = transformer
                transformer.start(item, output.absolutePath)
                continuation.invokeOnCancellation {
                    transformer.cancel()
                    output.delete()
                }
            }
        } finally {
            progressJob.cancel()
        }
    } ?: return null

    val size = exported.length()
    if (size !in 1..MAX_VIDEO_NOTE_BYTES) {
        // Chegaraga sig'madi — serverga yuborish ham `422` bilan tugardi.
        exported.delete()
        return null
    }

    // Asl nusxa endi keraksiz — lekin faqat **bizniki** bo'lsa (kameradan yozilgani).
    if (video.ownsFile) deleteMediaFile(video.path)

    return PickedVideo(
        path = exported.absolutePath,
        fileName = "video_note.mp4",
        durationMs = durationMs,
        sizeBytes = size,
        posterBytes = video.posterBytes,
        width = VIDEO_NOTE_SIDE,
        height = VIDEO_NOTE_SIDE,
        frameRate = TARGET_FPS,
        isH264 = true,
    )
}

/**
 * Bitreyt **maqsadli hajmdan** hisoblanadi, shunda to'liq 60 soniyalik xabar ham
 * 12 MB chegarasiga sig'adi.
 *
 * Ovoz uchun ~15% zaxira qoldiriladi: AAC yo'lagi ham shu faylda.
 */
private fun noteBitrate(durationMs: Int): Int {
    val seconds = (durationMs / 1000).coerceAtLeast(1)
    val budget = (MAX_VIDEO_NOTE_BYTES * BITS_PER_BYTE * VIDEO_SHARE / seconds).toLong()
    return budget.coerceIn(MIN_NOTE_BITRATE, MAX_NOTE_BITRATE).toInt()
}

private const val BITS_PER_BYTE = 8L
private const val VIDEO_SHARE = 0.85
private const val MIN_NOTE_BITRATE = 400_000L
private const val MAX_NOTE_BITRATE = 1_800_000L
private const val TARGET_FPS = 30f

private const val PROGRESS_TICK_MS = 250L
private const val PERCENT = 100f
