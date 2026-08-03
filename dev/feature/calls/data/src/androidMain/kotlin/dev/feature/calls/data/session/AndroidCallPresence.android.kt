package dev.feature.calls.data.session

import android.content.Context

/**
 * [CallPresence] ning Android tomoni — old plan xizmatini yoqadi/o'chiradi.
 *
 * ⚠️ Xizmatsiz Android 14+ da fonga o'tgan qo'ng'iroqning mikrofoni **jimgina** o'chadi
 * ([CallForegroundService] izohiga qarang).
 */
class AndroidCallPresence(private val context: Context) : CallPresence {

    override fun onCallStarted(peerName: String) = CallForegroundService.start(context, peerName)

    override fun onCallEnded() = CallForegroundService.stop(context)
}
