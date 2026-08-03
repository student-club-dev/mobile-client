package dev.feature.calls.data.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Jonli qo'ng'iroq davomida ishlaydigan old plan xizmati.
 *
 * ⚠️ **Ixtiyoriy emas.** Android 14+ da ilova fonga o'tganda tizim mikrofonni
 * **jimgina** o'chirib qo'yadi: suhbatdosh sizni eshitmay qoladi, hech qanday xato
 * ko'rinmaydi va foydalanuvchi «aloqa yomon» deb o'ylaydi. Xizmat
 * `foregroundServiceType = microphone|camera` bilan ishga tushgach bu cheklov tushadi.
 *
 * Bildirishnoma **o'chirib bo'lmaydigan** (`setOngoing`) — foydalanuvchi qo'ng'iroq
 * ketayotganini har doim ko'rib turadi va bosib ilovaga qaytadi.
 */
class CallForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val peerName = intent?.getStringExtra(EXTRA_PEER).orEmpty()
        startAsForeground(peerName)
        // `START_NOT_STICKY` — jarayon o'ldirilsa qo'ng'iroq allaqachon tugagan bo'ladi
        // (socket ham uzilgan), ya'ni xizmatni tiklashning ma'nosi yo'q.
        return START_NOT_STICKY
    }

    private fun startAsForeground(peerName: String) {
        ensureChannel()
        val notification = buildNotification(peerName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(peerName: String): Notification {
        // Ilovaning o'z ishga tushirish intent'i — bildirishnoma bosilganda qo'ng'iroq
        // ekraniga qaytariladi (`CallHost` uni sessiya bo'yicha o'zi chizadi).
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val pending = launch?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(peerName.ifBlank { "Qo'ng'iroq" })
            .setContentText("Qo'ng'iroq davom etmoqda")
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(pending)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Qo'ng'iroqlar",
                // `LOW` — ovoz va tebranish kerak emas: qo'ng'iroqning o'zi allaqachon
                // jiringlab turibdi, bildirishnoma faqat holatni ko'rsatadi.
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    companion object {
        private const val CHANNEL_ID = "calls_ongoing"
        private const val NOTIFICATION_ID = 4711
        private const val EXTRA_PEER = "peer"

        /**
         * Xizmatni ishga tushiradi.
         *
         * Xatolar **yutiladi**: `startForegroundService` fon cheklovlari sababli
         * `ForegroundServiceStartNotAllowedException` tashlashi mumkin, va bu qo'ng'iroqni
         * yiqitmasligi kerak — ilova ochiq turganda qo'ng'iroq xizmatsiz ham ishlaydi.
         */
        fun start(context: Context, peerName: String) = runCatching {
            val intent = Intent(context, CallForegroundService::class.java)
                .putExtra(EXTRA_PEER, peerName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }.getOrNull().let { }

        fun stop(context: Context) = runCatching {
            context.stopService(Intent(context, CallForegroundService::class.java))
        }.getOrNull().let { }
    }
}
