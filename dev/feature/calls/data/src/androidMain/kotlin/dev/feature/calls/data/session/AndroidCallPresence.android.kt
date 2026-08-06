package dev.feature.calls.data.session

import android.content.Context

/**
 * [CallPresence] ning Android tomoni — ikkita tizim ilmog'i:
 *
 * - **kiruvchi** qo'ng'iroq: to'liq ekranli bildirishnoma ([IncomingCallNotification]),
 *   ilova fonda yoki ekran o'chiq bo'lsa ham qo'ng'iroq ko'rinsin;
 * - **jonli** qo'ng'iroq: old plan xizmati ([CallForegroundService]), usiz Android 14+
 *   fonga o'tgan qo'ng'iroqning mikrofonini **jimgina** o'chirib qo'yadi.
 */
class AndroidCallPresence(private val context: Context) : CallPresence {

    override fun onIncomingCall(peerName: String, video: Boolean) =
        IncomingCallNotification.show(context, peerName, video)

    override fun onCallStarted(peerName: String, video: Boolean) {
        // Javob berildi — jiringlash oynasi o'rnini "qo'ng'iroq davom etmoqda" ga bo'shatadi.
        IncomingCallNotification.hide(context)
        CallForegroundService.start(context, peerName, video)
    }

    override fun onCallEnded() {
        IncomingCallNotification.hide(context)
        CallForegroundService.stop(context)
    }
}
