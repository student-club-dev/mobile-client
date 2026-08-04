package dev.core.network.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.IBinder

/**
 * Yuklash davom etayotganini tizimga bildiradi — [MediaUploadService] ni ko'taradi/tushiradi.
 *
 * Xatolar **yutiladi**: xizmat ko'tarilmasa ham yuklashning o'zi ishlayveradi (ilova ochiq
 * turgan holatda hech narsa o'zgarmaydi). Bu yerda yiqilish esa butun yuborishni
 * to'xtatardi — narxi foydasidan katta.
 */
internal actual fun setUploadKeepAlive(active: Boolean) {
    val context = uploadKeepAliveContext ?: return
    runCatching {
        val intent = Intent(context, MediaUploadService::class.java)
        if (active) {
            // ⚠️ `startForegroundService` — Android 8+ da fondagi ilova oddiy `startService`
            // bilan xizmat ocha olmaydi (`IllegalStateException`).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            context.stopService(intent)
        }
    }
}

/**
 * Yuklash ketayotganda ishlaydigan old plan xizmati.
 *
 * `dataSync` turi — aynan shu holat uchun: tarmoq orqali ma'lumot ko'chirilyapti,
 * foydalanuvchi ilovadan chiqib ketgan bo'lishi mumkin. Bildirishnoma majburiy va uni
 * yashirib bo'lmaydi — tizim qoidasi shunday, va bu to'g'ri ham: fonda internetdan
 * foydalanayotgan ilova ko'rinib turishi kerak.
 */
class MediaUploadService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!startAsForeground()) stopSelf()
        // `START_NOT_STICKY` — jarayon o'ldirilgan bo'lsa yuklash ham o'lgan; xizmatni
        // qayta tiklashning ma'nosi yo'q, davom ettiradigan korutin qolmagan.
        return START_NOT_STICKY
    }

    private fun startAsForeground(): Boolean = runCatching {
        ensureChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        true
    }.getOrDefault(false)

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("Yuborilmoqda")
            .setContentText("Media serverga yuklanmoqda")
            // Tizim ikonkasi — modulning o'z resursi yo'q va ilovaning ikonkasiga
            // bog'lanish uni ishlatadigan har bir ilovadan qo'shimcha qadam talab qilardi.
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            // `LOW` — ovoz ham, kalqib chiqish ham yo'q: bu holat ko'rsatkichi, xabar emas.
            NotificationChannel(CHANNEL_ID, "Yuklanmoqda", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private companion object {
        const val CHANNEL_ID = "sc_media_upload"
        const val NOTIFICATION_ID = 4711
    }
}

/**
 * Ilova kontekstini ushlab qoladi — xizmatni ochish uchun.
 *
 * `ContentProvider` sababi `dev.core.uikit.media.ScMediaContextProvider` dagi bilan bir xil:
 * yuklash repozitoriydan chaqiriladi va u yerda `Context` yo'q. E'lon shu modulda turadi,
 * ya'ni network'ni ulagan ilova manifestga hech narsa qo'shmaydi.
 */
class UploadContextProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        uploadKeepAliveContext = context?.applicationContext
        return true
    }

    override fun query(uri: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<String>?): Int = 0
}

/** `null` — provider hali ishga tushmagan (test muhiti). Keep-alive shunda o'chib qoladi. */
private var uploadKeepAliveContext: Context? = null
