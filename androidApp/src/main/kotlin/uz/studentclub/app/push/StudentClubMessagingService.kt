package uz.studentclub.app.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dev.feature.notifications.data.push.PushTokenBridge

/**
 * FCM servisi — tizim tomonidan chaqiriladi, DI grafidan **tashqarida** turadi.
 * Shu sabab ikkala natija ham global ko'priklar orqali ilovaga kiradi:
 * yangi token → [PushTokenBridge], bosilgan bildirishnoma → `PushRoute` (`MainActivity`).
 */
class StudentClubMessagingService : FirebaseMessagingService() {

    /**
     * Token ilova qayta o'rnatilganda, ma'lumot tozalanganda yoki server aylantirganda
     * o'zgaradi. Eski token bilan push yetib bormaydi, shuning uchun uni darhol ko'prikka
     * beramiz — sessiya ochiq bo'lsa `PushRepositoryImpl` uni backendga yozadi.
     */
    override fun onNewToken(token: String) {
        PushTokenBridge.publish(token)
    }

    /**
     * Ilova **old planda** bo'lganda (yoki xabar faqat `data` dan iborat bo'lganda)
     * chaqiriladi. Fondagi `notification` xabarlarini tizim o'zi chizadi — qarang
     * [PushNotifications] izohi.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        PushNotifications.showMessage(
            context = this,
            // `notification` bloki bo'lmasa sarlavha/matn `data` dan olinadi.
            title = message.notification?.title ?: message.data["title"],
            body = message.notification?.body ?: message.data["body"],
            // Konvert butunligicha uzatiladi — `notificationId`, `targetType`/`targetId` va
            // eski `conversationId` shu yerdan intent'ga tushadi.
            data = message.data,
        )
    }
}
