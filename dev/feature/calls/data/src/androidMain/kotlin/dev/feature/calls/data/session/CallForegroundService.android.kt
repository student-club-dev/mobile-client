package dev.feature.calls.data.session

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.aakira.napier.Napier
import dev.feature.calls.domain.model.CallStrings

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
        val video = intent?.getBooleanExtra(EXTRA_VIDEO, false) == true
        if (!startAsForeground(peerName, video)) {
            // Old planga chiqa olmadik — xizmatni ushlab turishning ma'nosi yo'q.
            // Qo'ng'iroqning o'zi davom etadi (ilova ochiq bo'lgunicha).
            stopSelf()
        }
        // `START_NOT_STICKY` — jarayon o'ldirilsa qo'ng'iroq allaqachon tugagan bo'ladi
        // (socket ham uzilgan), ya'ni xizmatni tiklashning ma'nosi yo'q.
        return START_NOT_STICKY
    }

    /**
     * Old plan rejimiga o'tadi. Muvaffaqiyatli bo'lsa `true`.
     *
     * ⚠️ **Tur maskasi ruxsatlardan hisoblanadi, qotirilmaydi.** Android 14+ (`targetSdk 35`)
     * da `startForeground` e'lon qilingan har bir tur uchun **berilgan** ruxsatni talab
     * qiladi: `camera` turi `CAMERA` ni, `microphone` turi `RECORD_AUDIO` ni. Ovozli
     * qo'ng'iroqda foydalanuvchi kameraga ruxsat bermagan bo'ladi, ya'ni ikkala turni
     * shartsiz e'lon qilish `SecurityException` bilan **butun ilovani yiqitardi**
     * (`onStartCommand` asosiy oqimda ishlaydi va u yerdagi istisno — halokat).
     */
    private fun startAsForeground(peerName: String, video: Boolean): Boolean = runCatching {
        ensureChannel()
        val notification = buildNotification(peerName)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification)
            return@runCatching true
        }
        var types = 0
        if (granted(Manifest.permission.RECORD_AUDIO)) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        if (video && granted(Manifest.permission.CAMERA)) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        }
        // Hech qanday tur yo'q — mikrofon ham berilmagan, ya'ni qo'ng'iroqning o'zi
        // boshlanmasligi kerak edi. Xizmatni ochishning ma'nosi yo'q.
        if (types == 0) return@runCatching false
        startForeground(NOTIFICATION_ID, notification, types)
        true
    }.getOrElse {
        // Tizim old planga chiqishni rad etdi (fon cheklovlari, ruxsat qaytarib olindi).
        // Bu qo'ng'iroqni to'xtatmaydi — u faqat fonda mikrofonni yo'qotishi mumkin.
        Napier.w("Qo'ng'iroq xizmati ochilmadi: ${it.message}", it, tag = LOG_TAG)
        false
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

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
            .setContentTitle(peerName.ifBlank { CallStrings.ongoingCall })
            .setContentText(CallStrings.ongoingCallBody)
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
                CallStrings.callsChannel,
                // `LOW` — ovoz va tebranish kerak emas: qo'ng'iroqning o'zi allaqachon
                // jiringlab turibdi, bildirishnoma faqat holatni ko'rsatadi.
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    companion object {
        private const val LOG_TAG = "Calls"
        private const val CHANNEL_ID = "calls_ongoing"
        private const val NOTIFICATION_ID = 4711
        private const val EXTRA_PEER = "peer"
        private const val EXTRA_VIDEO = "video"

        /**
         * Xizmatni ishga tushiradi.
         *
         * [video] — kamera turi e'lon qilinsinmi. Ovozli qo'ng'iroqda `false`: kamera
         * ruxsati berilmagan bo'ladi va turni bekorga e'lon qilish `SecurityException`
         * keltirardi.
         *
         * Xatolar **yutiladi**: `startForegroundService` fon cheklovlari sababli
         * `ForegroundServiceStartNotAllowedException` tashlashi mumkin, va bu qo'ng'iroqni
         * yiqitmasligi kerak — ilova ochiq turganda qo'ng'iroq xizmatsiz ham ishlaydi.
         */
        fun start(context: Context, peerName: String, video: Boolean) = runCatching {
            val intent = Intent(context, CallForegroundService::class.java)
                .putExtra(EXTRA_PEER, peerName)
                .putExtra(EXTRA_VIDEO, video)
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
