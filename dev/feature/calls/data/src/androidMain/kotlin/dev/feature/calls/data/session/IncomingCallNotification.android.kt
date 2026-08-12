package dev.feature.calls.data.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import dev.feature.calls.domain.repository.CallController
import dev.feature.calls.domain.model.CallStrings

/**
 * Kiruvchi qo'ng'iroqning **to'liq ekranli** bildirishnomasi — tizim qo'ng'irog'idek.
 *
 * Nega kerak: [CallHost] qo'ng'iroq ekranini faqat ilova old planda turganda chiza oladi.
 * Foydalanuvchi boshqa ilovada bo'lsa yoki ekran o'chiq bo'lsa kiruvchi qo'ng'iroq
 * **umuman ko'rinmasdi** — jiringlash ham, tugma ham yo'q edi.
 *
 * `setFullScreenIntent` + `CATEGORY_CALL` + `IMPORTANCE_HIGH` uchligi tizimga "bu
 * qo'ng'iroq" deb aytadi: ekran o'chiq bo'lsa yoqiladi va oyna to'liq ekranda ochiladi,
 * yoqiq bo'lsa tepada "heads-up" bo'lib turadi.
 *
 * ⚠️ Kanalning **o'z ovozi yo'q** (`setSound(null, null)`): jiringlashni [AndroidCallAudio]
 * chaladi. Ikkalasi ham chalsa ikkita ohang bir-birining ustiga tushardi.
 */
internal object IncomingCallNotification {

    private const val CHANNEL_ID = "calls_incoming"
    private const val NOTIFICATION_ID = 4712

    const val ACTION_ANSWER = "dev.feature.calls.ANSWER"
    const val ACTION_DECLINE = "dev.feature.calls.DECLINE"

    fun show(context: Context, peerName: String, video: Boolean) = runCatching {
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        // POST_NOTIFICATIONS berilmagan bo'lsa tizim jimgina tashlab yuboradi — qo'ng'iroq
        // baribir davom etadi va ilova ochilganda ekrani ko'rinadi.
        if (!manager.areNotificationsEnabled()) return@runCatching
        manager.notify(NOTIFICATION_ID, build(context, peerName, video))
    }.getOrElse {
        Napier.w("Kiruvchi qo'ng'iroq bildirishnomasi chiqmadi", it, tag = LOG_TAG)
    }

    fun hide(context: Context) = runCatching {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }.getOrElse { }

    private fun build(context: Context, peerName: String, video: Boolean): Notification {
        val title = peerName.ifBlank { CallStrings.unknownCaller }
        val text = if (video) CallStrings.videoCall else CallStrings.voiceCall

        val fullScreen = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.let { launch ->
                PendingIntent.getActivity(
                    context,
                    REQUEST_OPEN,
                    launch,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            // Ovoz va tebranish bu yerda EMAS — ularni `AndroidCallAudio` boshqaradi.
            .setSilent(true)
            .setContentIntent(fullScreen)
            // `true` — ekran o'chiq bo'lsa tizim to'g'ridan-to'g'ri shu oynani ochadi.
            .setFullScreenIntent(fullScreen, true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                CallStrings.decline,
                actionIntent(context, ACTION_DECLINE, REQUEST_DECLINE),
            )
            .addAction(
                android.R.drawable.stat_sys_phone_call,
                CallStrings.answer,
                actionIntent(context, ACTION_ANSWER, REQUEST_ANSWER),
            )
            .build()
    }

    private fun actionIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, CallActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CallStrings.incomingCallsChannel,
            // `HIGH` — bu to'liq ekranli bildirishnomaning SHARTI: past muhimlikda tizim
            // `fullScreenIntent` ni umuman ishlatmaydi va oyna ochilmaydi.
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = CallStrings.incomingCallsChannelBody
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    private const val LOG_TAG = "Calls"
    private const val REQUEST_OPEN = 0
    private const val REQUEST_ANSWER = 1
    private const val REQUEST_DECLINE = 2
}

/**
 * Bildirishnomadagi CallStrings.answer / CallStrings.decline tugmalari.
 *
 * Qabul qiluvchi Koin'dan [CallController] ni oladi — u ilova bo'ylab bitta `single`,
 * ya'ni jonli qo'ng'iroqning aynan o'shanisi. `goAsync()` ishlatilmaydi: ikkala amal ham
 * socket'ga bitta freym yuboradi va uni qo'ng'iroqning **o'z** doirasida bajarish
 * to'g'riroq — qabul qiluvchi qaytgandan keyin ham ish davom etaveradi.
 */
class CallActionReceiver : BroadcastReceiver(), KoinComponent {

    private val controller: CallController by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        when (intent.action) {
            IncomingCallNotification.ACTION_ANSWER -> {
                // Javob berilganda ilova ham ochiladi: suhbat ekrani kerak (mikrofon
                // ruxsati so'ralishi va video ko'rinishi mumkin).
                context.packageManager.getLaunchIntentForPackage(context.packageName)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ?.let { runCatching { context.startActivity(it) } }
                scope.launch { controller.accept() }
            }

            IncomingCallNotification.ACTION_DECLINE -> scope.launch { controller.decline() }
        }
    }
}
