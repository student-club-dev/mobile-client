package dev.core.common.event

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Modullararo **real vaqt signallari** — "nimadir sodir bo'ldi, o'zingizni yangilang".
 *
 * Nega shina: chat soketidan kelgan yangi xabar bildirishnomalar ro'yxatini ham eskiradi,
 * lekin `:dev:feature:notifications` va `:dev:feature:chat` bir-biriga bog'lanmaydi (va
 * bog'lanmasligi ham kerak — ular mustaqil feature'lar). [AppMessageBus] bilan bir xil
 * yondashuv: umumiy modulda turgan juda yupqa shina, ikkala tomon ham faqat unga bog'lanadi.
 *
 * Signal — **ma'lumot emas, turtki**: qabul qiluvchi o'z manbasini o'zi qayta o'qiydi.
 * Shu sababli yuk (payload) yo'q va signalning yo'qolib ketishi xavfli emas.
 */
object RealtimeSignals {

    private val _incomingMessages = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * Suhbatga **kiruvchi** yangi xabar keldi (WebSocket `message:new`).
     *
     * Bildirishnomalar ro'yxati shuni kuzatadi: ilgari yangi xabar u yerda faqat
     * foydalanuvchi ekranni qo'lda yangilagandan keyin paydo bo'lardi.
     */
    val incomingMessages: SharedFlow<Unit> = _incomingMessages.asSharedFlow()

    fun incomingMessage() {
        _incomingMessages.tryEmit(Unit)
    }
}
