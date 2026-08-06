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
 * Server yuboradigan tana — `02-PUSH_CATALOG_BACKEND.md` §2:
 * ```jsonc
 * {
 *   "title": "Aziz Karimov",           // yuboruvchining ismi; ism topilmasa "Yangi xabar"
 *   "body": "<matn>",
 *   "data": {
 *     "kind": "CHAT", "notificationId": "clx…",
 *     "targetType": "CHAT", "targetId": "cnv_01H8X",
 *     "conversationId": "cnv_01H8X"    // chat va qo'ng'iroqda saqlanib qolgan eski kalit
 *   }
 * }
 * ```
 * Konvertdagi qiymati yo'q kalit UMUMAN yuborilmaydi (bo'sh satr yoki `"null"` emas), ya'ni
 * yo'q extra — "bu bildirishnoma hech qayerga olib bormaydi" degani.
 * Sarlavhani **server** beradi va biz uni o'zgartirmasdan ko'rsatamiz: fondagi `notification`
 * xabarini tizim o'zi chizadi, ya'ni ismni ilova tomonda qo'yib bo'lmaydi.
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

    /**
     * Konvert kalitlari — push `data` sida ham, intent extra'sida ham AYNAN shu nom bilan
     * yuradi. Fonda kelgan `notification` xabarini tizim o'zi chizadi va `data` ni intent
     * extra'lariga o'zi ko'chiradi, ya'ni ikkala yo'lda ham nomlar bir xil bo'lishi shart.
     */
    const val EXTRA_CONVERSATION_ID = "conversationId"
    const val EXTRA_NOTIFICATION_ID = "notificationId"
    const val EXTRA_TARGET_TYPE = "targetType"
    const val EXTRA_TARGET_ID = "targetId"

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

    /**
     * Bitta bildirishnoma qatori.
     *
     * [data] — push konvertining o'zi. Konvert BUTUNLIGICHA intent'ga ko'chiriladi: qaysi
     * ekran ochilishini va qaysi qator o'qilgan deb belgilanishini ilova qatlami hal qiladi
     * (`StudentShell`), bu yer esa faqat qiymatlarni uzatadi.
     */
    fun showMessage(context: Context, title: String?, body: String?, data: Map<String, String>) {
        ensureChannel(context)

        val conversationId = data[EXTRA_CONVERSATION_ID]
        val targetId = data[EXTRA_TARGET_ID]
        val intent = Intent(context, MainActivity::class.java).apply {
            // Ilova allaqachon ochiq bo'lsa yangi nusxa yaratilmaydi — mavjud oynaga
            // `onNewIntent` orqali keladi (MainActivity `singleTop`).
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            EXTRAS.forEach { key -> data[key]?.let { putExtra(key, it) } }
        }
        // Guruhlash kaliti: suhbat bo'lsa suhbat bo'yicha, aks holda nishon bo'yicha. Ikkalasi
        // ham bo'lmasa (masalan tizim xabari) — barchasi bitta qatorga tushmasin uchun
        // sarlavha+matn bo'yicha.
        val groupKey = conversationId ?: targetId ?: "$title|$body"
        val pending = PendingIntent.getActivity(
            context,
            // Har guruh uchun alohida `requestCode` — aks holda PendingIntent qayta
            // ishlatilib, birinchi suhbatning id'si hamma bildirishnomada qolib ketardi.
            groupKey.hashCode(),
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
        runCatching {
            // Android 13+ da ruxsat berilmagan bo'lsa `notify` jimgina e'tiborsiz qoladi,
            // lekin ba'zi qurilmalarda SecurityException tashlaydi — servisni yiqitmaymiz.
            NotificationManagerCompat.from(context).notify(groupKey.hashCode(), notification)
        }
    }

    /** Intent'ga ko'chiriladigan konvert kalitlari. */
    private val EXTRAS = listOf(
        EXTRA_CONVERSATION_ID,
        EXTRA_NOTIFICATION_ID,
        EXTRA_TARGET_TYPE,
        EXTRA_TARGET_ID,
    )
}
