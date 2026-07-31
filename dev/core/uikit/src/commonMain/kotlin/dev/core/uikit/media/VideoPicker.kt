package dev.core.uikit.media

import androidx.compose.runtime.Composable

/** Tanlangan video — `POST /v1/media/chat-upload?kind=VIDEO` yoki `STORY_VIDEO` ga ketadi. */
class PickedVideo(
    val bytes: ByteArray,
    /**
     * Kengaytma **haqiqiy formatga** mos bo'lishi shart (`video.mp4` / `video.mov`): server
     * MIME'ni baytlardan aniqlaydi, lekin ba'zi proksilar nomga qarab multipart qismini
     * kesib tashlaydi.
     */
    val fileName: String,
    /** Aniqlab bo'lmasa `null` — o'shanda chegarani server tekshiradi. */
    val durationMs: Int?,
    val sizeBytes: Long,
    /** Birinchi kadr (poster) — yuborilgunicha ekranda ko'rsatish uchun; olinmasa `null`. */
    val posterBytes: ByteArray? = null,
)

/** Video tanlashni ishga tushiruvchi. */
fun interface VideoPicker {
    fun pick()
}

/**
 * Galereyadan video tanlagich (Android: `PickVisualMedia(VideoOnly)`,
 * iOS: `PHPickerViewController` + `videosFilter`). [rememberImagePicker] kabi
 * **ruxsat so'ramaydi** — tizim tanlagichi faqat tanlangan faylni beradi.
 *
 * [onResult] `null` — bekor qilindi, chegaradan oshdi yoki o'qib bo'lmadi.
 *
 * ⚠️ Chegaradan oshgani ham `null` bilan keladi (alohida xato turi yo'q), chunki UI'da ikkala
 * holat uchun ham bir xil "video yuborilmadi" xabari ko'rsatiladi.
 */
@Composable
expect fun rememberVideoPicker(onResult: (PickedVideo?) -> Unit): VideoPicker

/**
 * Yuborilishi mumkin bo'lgan eng katta hajm (64 MB).
 *
 * ⚠️ Serverda ham shu chegara bor, lekin bu yerda **oldin** to'sish kerak: bundan kattasini
 * [PickedVideo.bytes] ga o'qish telefon xotirasini bo'g'adi, so'rov esa baribir `413` bilan
 * qaytadi — ya'ni xotirani xato uchun sarflagan bo'lamiz.
 */
const val MAX_VIDEO_BYTES: Long = 64L * 1024 * 1024

/** Yuborilishi mumkin bo'lgan eng uzun davomiylik (3 daqiqa) — server ham shuni tekshiradi. */
const val MAX_VIDEO_MS: Int = 3 * 60 * 1000
