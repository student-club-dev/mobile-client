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
 * Bitta faylning chegarasi — **100 MB**.
 *
 * ⚠️ Bu **serverning chegarasi emas**: 2026-08-03 dan `FILE` turida hajm cheklovi yo'q
 * (fayl bayt-baytga saqlanadi, uni faqat kunlik kvota va serverdagi bo'sh joy cheklaydi).
 * Bu raqam — **qurilma tomonidagi** cheklov: tanlangan hujjat hozircha to'liq baytlar
 * massiviga o'qiladi, ya'ni bundan kattasi "yuklab bo'lmadi" xatosi emas, ilova qulashi
 * bo'lardi.
 *
 * Chegarani butunlay olib tashlash uchun hujjat tanlagichi ham video kabi **fayl yo'lini**
 * qaytarishi kerak (o'shanda yuklash rezyumlanadigan oqim bilan ketadi) — bu alohida ish.
 *
 * ⚠️ Tekshiruv **o'qishdan oldin** bajariladi — aynan shu sababdan.
 */
const val MAX_FILE_BYTES = 100L * 1024 * 1024
