package dev.core.uikit.media

import androidx.compose.runtime.Composable

/** Tanlangan hujjat — `POST /v1/media/chat-upload?kind=FILE` ga shu ketadi. */
class PickedFile(
    val bytes: ByteArray,
    /** Asl fayl nomi (kengaytmasi bilan) — server uni tozalab saqlaydi. */
    val fileName: String,
    val mimeType: String?,
    val sizeBytes: Long,
)

/** Hujjat tanlashni ishga tushiruvchi. */
fun interface FilePicker {
    fun pick()
}

/**
 * Hujjat tanlagich (Android: `OpenDocument`, iOS: `UIDocumentPickerViewController`).
 * Rasm tanlagichdan alohida, chunki bu yerda **har qanday** turdagi fayl kerak va
 * chatga asl nomi bilan yuboriladi.
 *
 * Ikkala platformada ham **ruxsat so'ramaydi** — tizim tanlagichi faqat tanlangan
 * faylga vaqtinchalik ruxsat beradi.
 *
 * [onResult] `null` — bekor qilindi yoki faylni o'qib bo'lmadi (shu jumladan
 * [MAX_FILE_BYTES] dan katta bo'lgani uchun rad etildi).
 */
@Composable
expect fun rememberFilePicker(onResult: (PickedFile?) -> Unit): FilePicker

/**
 * Bitta faylning chegarasi — serverdagi yuklash chegarasi bilan bir xil, undan katta fayl
 * baribir rad etiladi.
 *
 * ⚠️ Tekshiruv **o'qishdan oldin** bajariladi: bunday hajmdagi faylni baytlarga aylantirish
 * telefon xotirasini bo'g'adi, ya'ni "yuklab bo'lmadi" xatosi emas, ilova qulashi bo'ladi.
 */
const val MAX_FILE_BYTES = 48 * 1024 * 1024
