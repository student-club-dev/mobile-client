package dev.core.uikit.media

import androidx.compose.runtime.Composable

/** Ovozli xabarni ijro etishni boshqaruvchi. */
interface AudioPlaybackController {
    /**
     * Ijroni boshlaydi. Xuddi shu [url] pauzada turgan bo'lsa — **davom ettiradi**,
     * qaytadan yuklab olmaydi; boshqa havola berilsa avvalgisi to'xtatiladi.
     */
    fun play(url: String)

    fun pause()

    /** To'xtatadi va manbani bo'shatadi — keyingi [play] noldan boshlaydi. */
    fun stop()
}

/**
 * Tarmoqdagi ovozli xabar pleyeri (Android: `MediaPlayer`, iOS: `AVPlayer`).
 *
 * Bir vaqtda bitta yozuv eshitiladi: ro'yxatda har bir "xabar pufagi" uchun alohida pleyer
 * yaratmasdan, **bitta** pleyer ekran darajasida saqlanadi va `play(url)` bilan boshqariladi.
 *
 * [headers] — ilova serveridagi media token talab qiladi (`Authorization: Bearer`). Havola
 * ochiq emas, shuning uchun sarlavhasiz so'rov `401` bilan qaytadi.
 * [onProgress] — joriy pozitsiya va umumiy davomiylik (ms), waveform'ni bo'yash uchun.
 * [onEnded] — yozuv tugadi yoki ijro qilib bo'lmadi; ikkala holatda ham UI "to'xtagan"
 * ko'rinishga qaytishi kerak.
 *
 * ⚠️ Kompozitsiyadan chiqqanda pleyer majburan bo'shatiladi — aks holda ekran yopilgandan
 * keyin ham ovoz eshitilib turadi.
 */
@Composable
expect fun rememberAudioPlayer(
    headers: Map<String, String> = emptyMap(),
    onProgress: (positionMs: Int, durationMs: Int) -> Unit = { _, _ -> },
    onEnded: () -> Unit = {},
): AudioPlaybackController

/**
 * Progress qanchalik tez-tez xabar qilinadi. 60 ms waveform silliq ko'rinishi uchun yetarli,
 * lekin har kadrda emas — ortiqcha rekompozitsiya qilmaslik uchun ataylab siyrak.
 */
internal const val PROGRESS_TICK_MS = 60L
