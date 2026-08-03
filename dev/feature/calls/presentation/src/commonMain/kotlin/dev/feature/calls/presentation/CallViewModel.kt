package dev.feature.calls.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.feature.calls.domain.model.CallMedia
import dev.feature.calls.domain.model.CallSession
import dev.feature.calls.domain.model.CallStatus
import dev.feature.calls.domain.repository.CallController
import dev.feature.connections.domain.model.StudentSummary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * Qo'ng'iroq ekranining ViewModel'i — [CallController] ustidagi yupqa qatlam.
 *
 * Holat mashinasi bu yerda **emas**: u `CallSessionManager` da va ilova bo'ylab bitta
 * nusxada yashaydi. Sabab oddiy — qo'ng'iroq ekran ochiq turishiga bog'liq bo'lmasligi
 * kerak: foydalanuvchi orqaga bosib chatga qaytsa ham suhbat davom etadi.
 */
class CallViewModel(private val controller: CallController) : ViewModel() {

    private val clock = Clock.System

    val session: StateFlow<CallSession?> = controller.session

    private val _error = MutableStateFlow<String?>(null)

    /** Bir martalik xato matni — ko'rsatilgach [clearError] bilan tozalanadi. */
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Ekrandagi taymer matni: jiringlashda **qolgan** vaqt, suhbatda esa **o'tgan** vaqt.
     *
     * Alohida oqim: `CallSession` da faqat boshlanish paytlari bor va ularni har sekundda
     * qayta hisoblash kerak — buni holat obyektining o'ziga yozish har sekundda butun
     * ekranni qayta chizishga majbur qilardi.
     */
    private val _timer = MutableStateFlow("")
    val timer: StateFlow<String> = _timer.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                _timer.value = timerText(session.value)
                delay(TICK_MS)
            }
        }
    }

    /**
     * Ekrandagi vaqt.
     *
     * ⚠️ Jiringlash taymeri **serverdan kelgan `expiresAt`** dan hisoblanadi, o'z
     * soatimizdan emas: qurilma soati bir necha soniyaga qochsa taymer serverning haqiqiy
     * muddatidan ajralib ketardi (`handoff/09-CALLS-PROTOCOL.md` §6).
     */
    private fun timerText(current: CallSession?): String {
        if (current == null) return ""
        val connectedAt = current.connectedAt
        val expiresAt = current.ringingExpiresAt
        return when {
            current.status == CallStatus.ACTIVE && connectedAt != null ->
                formatDuration((clock.now() - connectedAt).inWholeMilliseconds)

            current.status == CallStatus.RINGING && expiresAt != null -> {
                val left = (expiresAt - clock.now()).inWholeSeconds
                if (left > 0) formatDuration(left * MILLIS_IN_SECOND) else ""
            }

            else -> ""
        }
    }

    /** Chiquvchi qo'ng'iroq — ruxsat allaqachon olingan bo'lishi kerak. */
    fun call(peer: StudentSummary, media: CallMedia) {
        viewModelScope.launch { _error.value = controller.call(peer, media) }
    }

    fun accept() {
        viewModelScope.launch { _error.value = controller.accept() }
    }

    fun decline() {
        viewModelScope.launch { controller.decline() }
    }

    fun hangUp() {
        viewModelScope.launch { controller.hangUp() }
    }

    fun toggleMic() = controller.toggleMic()
    fun toggleCamera() = controller.toggleCamera()
    fun switchCamera() = controller.switchCamera()
    fun toggleSpeaker() = controller.toggleSpeaker()

    fun clearError() {
        _error.value = null
    }

    private companion object {
        const val TICK_MS = 500L
        const val MILLIS_IN_SECOND = 1000L
    }
}

/**
 * `184000` → `"3:04"`, `3753000` → `"1:02:33"`.
 *
 * Soat qismi faqat kerak bo'lganda chiziladi — `0:03:04` telefon qo'ng'irog'ida g'alati
 * ko'rinadi va push matni ham aynan shu shaklda (`handoff/09-CALLS-REST.md` §4).
 */
fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val paddedSeconds = seconds.toString().padStart(2, '0')
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:$paddedSeconds"
    } else {
        "$minutes:$paddedSeconds"
    }
}
