package dev.core.common.error

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.time.TimeSource.Monotonic.ValueTimeMark

/** Toast turi — rangi va ikonasi shunga qarab tanlanadi. */
enum class AppMessageKind { ERROR, SUCCESS, INFO }

/**
 * Ekranda toast bo'lib chiqadigan bir martalik xabar.
 *
 * [code] — backend konvertidagi `error.code` (`RATE_LIMITED`, `USER_BLOCKED`…). Ko'rsatilmaydi,
 * lekin log/telemetriya va kelajakda kodga qarab boshqacha ko'rsatish uchun saqlanadi.
 */
data class AppMessage(
    val text: String,
    val kind: AppMessageKind = AppMessageKind.ERROR,
    val code: String? = null,
)

/**
 * Butun ilova uchun **yagona xabar shinasi** — UI qatlamiga bog'liq emas.
 *
 * Har bir API javobidagi xato shu yerga tushadi (`safeCall`/`safeApiCall` ichida, ya'ni
 * bitta joyda) va ildizdagi `ScToastHost` uni foydalanuvchi qaysi ekranda turganidan
 * qat'i nazar toast qilib ko'rsatadi. Shu sababli ekranlar endi xatoni "yutib yuborishi"
 * mumkin emas: `Resource.Error` ni hech kim ko'rsatmasa ham toast baribir chiqadi.
 *
 * Ekranlardagi inline xato matnlari saqlanib qoladi — ular maydonga bog'langan kontekst
 * beradi, toast esa umumiy bildirishnoma.
 */
object AppMessageBus {

    // Kompozitsiya hali ulgurmagan payt (ilova ochilishidagi birinchi so'rov) xabar
    // yo'qolmasligi uchun bufer; to'lib qolsa eng eskisi tashlanadi.
    private val _messages = MutableSharedFlow<AppMessage>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val messages: SharedFlow<AppMessage> = _messages.asSharedFlow()

    /** Typed xatoni foydalanuvchi matni bilan yuboradi. */
    fun error(e: AppException) =
        emit(AppMessage(e.userMessage, AppMessageKind.ERROR, e.errorCode))

    fun error(text: String, code: String? = null) =
        emit(AppMessage(text, AppMessageKind.ERROR, code))

    fun success(text: String) = emit(AppMessage(text, AppMessageKind.SUCCESS))

    fun info(text: String) = emit(AppMessage(text, AppMessageKind.INFO))

    private fun emit(message: AppMessage) {
        if (message.text.isBlank()) return
        if (isRepeat(message)) return
        _messages.tryEmit(message)
    }

    private var lastText: String? = null
    private var lastAt: ValueTimeMark? = null

    /**
     * Bir xil matnli xabar qisqa oraliqda TAKRORLANMAYDI.
     *
     * Ilova ochilganda bir necha bo'lim bir vaqtda ma'lumot tortadi (bosh ekran, chat,
     * bildirishnomalar, katalog). Tarmoq bir lahza uzilsa ularning HAMMASI bitta xil
     * "Internet aloqasi yo'q" xatosini beradi va foydalanuvchi ketma-ket bir nechta
     * bir xil toast ko'rardi. Xato o'zi to'g'ri — takrori shovqin.
     */
    private fun isRepeat(message: AppMessage): Boolean {
        val now = TimeSource.Monotonic.markNow()
        val previousAt = lastAt
        val repeat = message.text == lastText &&
            previousAt != null &&
            previousAt.elapsedNow() < DEDUPE_WINDOW
        lastText = message.text
        lastAt = now
        return repeat
    }

    /** Shu oraliqda kelgan bir xil matnli xabar bitta hisoblanadi. */
    private val DEDUPE_WINDOW = 4.seconds
}
