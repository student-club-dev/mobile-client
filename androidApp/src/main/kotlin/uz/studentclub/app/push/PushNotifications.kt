package uz.studentclub.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import uz.studentclub.app.MainActivity
import uz.studentclub.app.R

/**
 * Push bildirishnomalarini ko'rsatish (Android).
 *
 * Server yuboradigan tana — `chat.md` §10:
 * ```jsonc
 * { "title": "Yangi xabar", "body": "<matn>", "data": { "conversationId": "cnv_01H8X" } }
 * ```
 *
 * ⚠️ Ikki xil holat bor va ikkalasi ham qo'llab-quvvatlanishi kerak:
 * - **Ilova fonda** va xabarda `notification` bloki bo'lsa — bildirishnomani **tizim** o'zi
 *   chizadi, `onMessageReceived` CHAQIRILMAYDI. Bosilganda launcher activity ochiladi va
 *   `data` intent'ning "extra"lariga tushadi (qarang `MainActivity`).
 * - **Ilova old planda** yoki xabar faqat `data` dan iborat bo'lsa — `onMessageReceived`
 *   chaqiriladi va bildirishnomani shu yerda o'zimiz chizamiz.
 */
object PushNotifications {

    /** Chat xabarlari kanali. Manifestdagi `default_notification_channel_id` bilan bir xil. */
    const val CHANNEL_CHAT = "chat_messages"

    /** Suhbat id'si — push `data` sida ham, intent extra'sida ham shu nom bilan yuradi. */
    const val EXTRA_CONVERSATION_ID = "conversationId"

    /**
     * Kanalni yaratadi (Android 8+). Idempotent — takror chaqirish xavfsiz, shuning uchun
     * `Application.onCreate` da chaqiriladi: tizim tray'idagi bildirishnoma servisdan
     * OLDIN kelishi mumkin va kanalsiz ko'rinmay qolardi.
     */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_CHAT,
            context.getString(R.string.push_channel_chat),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.push_channel_chat_description)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** Bitta suhbat uchun bildirishnoma. */
    fun showMessage(context: Context, title: String?, body: String?, conversationId: String?) {
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            // Ilova allaqachon ochiq bo'lsa yangi nusxa yaratilmaydi — mavjud oynaga
            // `onNewIntent` orqali keladi (MainActivity `singleTop`).
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_CONVERSATION_ID, conversationId)
        }
        val pending = PendingIntent.getActivity(
            context,
            // Har suhbat uchun alohida `requestCode` — aks holda PendingIntent qayta
            // ishlatilib, birinchi suhbatning id'si hamma bildirishnomada qolib ketardi.
            conversationId?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_CHAT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title ?: context.getString(R.string.push_default_title))
            .setContentText(body.orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.orEmpty()))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        // Bir suhbat — bitta bildirishnoma qatori (yangisi eskisini almashtiradi).
        val id = conversationId?.hashCode() ?: 0
        runCatching {
            // Android 13+ da ruxsat berilmagan bo'lsa `notify` jimgina e'tiborsiz qoladi,
            // lekin ba'zi qurilmalarda SecurityException tashlaydi — servisni yiqitmaymiz.
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }
}
