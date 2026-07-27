package dev.feature.notifications.data.push

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Platforma qatlamidan kelgan **yangi push token** uchun ko'prik.
 *
 * Token har doim ilova kodidan tashqarida tug'iladi: Android'da `FirebaseMessagingService`
 * (tizim chaqiradigan servis), iOS'da `AppDelegate`. Ikkalasi ham DI grafiga kira olmaydi,
 * shuning uchun global obyekt — Google Sign-In ko'prigi bilan bir xil naqsh.
 *
 * `replay = 1`: token servis DI'dan oldin kelishi mumkin (ilova sovuq ishga tushganda),
 * kech obuna bo'lgan kolektor ham oxirgi qiymatni oladi.
 */
object PushTokenBridge {

    private val _tokens = MutableSharedFlow<String>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val tokens: Flow<String> = _tokens.asSharedFlow()

    /**
     * Oxirgi ma'lum token — iOS'da `currentToken()` shuni o'qiydi (APNs tokenini so'rab
     * olib bo'lmaydi, u faqat `AppDelegate` ga *keladi*). `replayCache` — SharedFlow'ning
     * o'zi kafolatlaydigan yagona nusxa, alohida `var` ga ehtiyoj yo'q.
     */
    val latest: String? get() = _tokens.replayCache.lastOrNull()

    /** Platforma qatlami chaqiradi (`onNewToken` / `didRegisterForRemoteNotifications`). */
    fun publish(token: String) {
        if (token.isBlank()) return
        _tokens.tryEmit(token)
    }
}
