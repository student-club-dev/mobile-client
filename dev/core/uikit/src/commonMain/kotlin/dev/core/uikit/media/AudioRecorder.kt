package dev.core.uikit.media

import androidx.compose.runtime.Composable

/**
 * Yozib olingan ovoz — `POST /v1/media/chat-upload?kind=VOICE` ga aynan shu ketadi.
 *
 * [durationMs] serverdan qaytmaydi: waveform va "0:07" yozuvi xabar yuborilishidan **oldin**
 * ko'rinishi kerak, shuning uchun davomiylikni yozib oluvchining o'zi o'lchaydi.
 */
class RecordedAudio(
    val bytes: ByteArray,
    /** Kengaytma MIME turini belgilaydi — `MediaUploader.mimeTypeOf` ga qara. */
    val fileName: String,
    val durationMs: Int,
)

/** Yozib olishni boshqaruvchi — bosib turiladigan mikrofon tugmasi shu uchtasini chaqiradi. */
interface AudioRecorderController {
    /** Ruxsat bo'lmasa uni so'raydi va ruxsat berilgach yozishni boshlaydi. */
    fun start()

    /** Yozishni tugatib, natijani `onResult` ga beradi. */
    fun stop()

    /** Bekor qiladi — fayl o'chiriladi, `onResult(null)` keladi. */
    fun cancel()
}

/**
 * Mikrofondan ovoz yozib oluvchi (Android: `MediaRecorder`, iOS: `AVAudioRecorder`).
 *
 * Rasm tanlagichdan farqli o'laroq bu yerda **ish vaqtidagi ruxsat** kerak: tizim tanlagichi
 * yo'q, ilova mikrofonga to'g'ridan-to'g'ri kiradi. Ruxsat [AudioRecorderController.start]
 * ichida so'raladi — ya'ni foydalanuvchi mikrofon tugmasini bosgandagina, ekran ochilishida
 * emas (aks holda so'rov sababsiz ko'rinadi va ko'pincha rad etiladi).
 *
 * [onResult] `null` qaytsa — bekor qilindi, ruxsat berilmadi yoki yozuv juda qisqa bo'lgani
 * uchun buzuq chiqdi.
 *
 * ⚠️ Composable kompozitsiyadan chiqqanda resurslar bo'shatiladi. Agar shu paytda yozuv
 * ketayotgan bo'lsa u **jimgina** tashlab yuboriladi (`onResult` chaqirilmaydi) — ekran
 * yopilgandan keyin holatni yangilashga urinmaslik uchun.
 */
@Composable
expect fun rememberAudioRecorder(onResult: (RecordedAudio?) -> Unit): AudioRecorderController

/**
 * Fayl nomi ikkala platformada bir xil — server turni baytlardan aniqlasa ham, `.m4a`
 * kengaytmasi qismga to'g'ri `audio/mp4` sarlavhasini qo'ydiradi.
 */
const val VOICE_FILE_NAME = "voice.m4a"

/**
 * Server ovozli xabarni **5 daqiqa** bilan cheklaydi (16 MB dan ham oshmasin). Shu chegaraga
 * yetganda yozuv o'zi to'xtaydi — foydalanuvchi 6 daqiqa gapirib, keyin `422` olmasin.
 */
const val MAX_VOICE_DURATION_MS = 5 * 60 * 1000
