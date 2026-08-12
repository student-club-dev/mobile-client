package dev.feature.calls.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import dev.core.common.locale.AppLocale
import dev.core.uikit.locale.rememberStrings

/** Qo'ng'iroq ekranining matnlari. Sukut qiymatlar — inglizcha. */
data class CallScreenStrings(
    val ringing: String = "Ringing…",
    val videoCall: String = "Video call",
    val voiceCall: String = "Voice call",
    val connecting: String = "Connecting…",
    val connected: String = "Connected",
    val missed: String = "Missed call",
    val busy: String = "Busy",
    val declined: String = "Declined",
    val canceled: String = "Canceled",
    val failed: String = "Connection lost",
    val ended: String = "Call ended",
    val decline: String = "Decline",
    val answer: String = "Answer",
    val mic: String = "Mic",
    val micOff: String = "Muted",
    val camera: String = "Camera",
    val speaker: String = "Speaker",
    val switchCamera: String = "Flip",
    val hangUp: String = "End",
)

private val CallEn = CallScreenStrings()

private val CallRu = CallScreenStrings(
    ringing = "Вызов…",
    videoCall = "Видеозвонок",
    voiceCall = "Голосовой звонок",
    connecting = "Соединение…",
    connected = "Соединено",
    missed = "Пропущенный звонок",
    busy = "Занято",
    declined = "Отклонён",
    canceled = "Отменён",
    failed = "Связь потеряна",
    ended = "Звонок завершён",
    decline = "Отклонить",
    answer = "Ответить",
    mic = "Микрофон",
    micOff = "Выключен",
    camera = "Камера",
    speaker = "Динамик",
    switchCamera = "Сменить",
    hangUp = "Завершить",
)

private val CallUz = CallScreenStrings(
    ringing = "Jiringlamoqda…",
    videoCall = "Video qo\'ng\'iroq",
    voiceCall = "Ovozli qo\'ng\'iroq",
    connecting = "Ulanmoqda…",
    connected = "Ulandi",
    missed = "Javobsiz qo\'ng\'iroq",
    busy = "Band",
    declined = "Rad etildi",
    canceled = "Bekor qilindi",
    failed = "Aloqa uzildi",
    ended = "Qo\'ng\'iroq tugadi",
    decline = "Rad etish",
    answer = "Javob berish",
    mic = "Mikrofon",
    micOff = "O\'chirilgan",
    camera = "Kamera",
    speaker = "Karnay",
    switchCamera = "Almashtirish",
    hangUp = "Tugatish",
)

@Composable
@ReadOnlyComposable
internal fun callScreenStrings(): CallScreenStrings = rememberStrings(CallEn, CallRu, CallUz)

/** `statusText` sof funksiya — Compose'dan tashqarida chaqiriladi. */
internal fun callScreenStringsNow(): CallScreenStrings = AppLocale.pick(CallEn, CallRu, CallUz)
