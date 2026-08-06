package dev.feature.notifications.data.push

import dev.feature.notifications.domain.push.DevicePlatform
import dev.feature.notifications.domain.push.DeviceTokenType
import dev.feature.notifications.domain.push.PushTokenSource
import kotlinx.coroutines.flow.Flow

/**
 * iOS — **APNs** tokeni.
 *
 * Tokenni so'rab olib bo'lmaydi: u `AppDelegate` ning
 * `didRegisterForRemoteNotificationsWithDeviceToken` metodiga *keladi*. Swift tomoni uni
 * [IosPushBridge] orqali yozadi (qarang `iosApp/iosApp/iOSApp.swift`), Kotlin esa shu
 * yerdan o'qiydi.
 *
 * ⚠️ Ilova birinchi ochilganda token bir necha yuz millisekunddan keyin keladi, shuning
 * uchun `currentToken()` boshida `null` bo'lishi normal — token yetib kelgach
 * [PushTokenSource.tokenRefreshes] ishga tushadi va ro'yxatdan o'tkazish takrorlanadi.
 */
private class ApnsPushTokenSource : PushTokenSource {
    override val platform = DevicePlatform.IOS

    /**
     * ⚠️ `APNS`, `FCM` EMAS. iPhone o'zining XOM APNs tokenini ro'yxatdan o'tkazadi va
     * server Apple bilan to'g'ridan-to'g'ri gaplashadi (`PUSH_APNS_BACKEND.md`). Bu yerda
     * `FCM` yozilsa token uni umuman adreslay olmaydigan xizmatga topshirilardi.
     *
     * PushKit (VoIP) tokeni bu manbaga TUSHMAYDI — u alohida, `APNS_VOIP` turi bilan
     * ro'yxatdan o'tadi va oddiysini ALMASHTIRMAYDI (`04-CALLS_RESPONSE.md` §2).
     */
    override val tokenType = DeviceTokenType.APNS
    override suspend fun currentToken(): String? = PushTokenBridge.latest
    override val tokenRefreshes: Flow<String> = PushTokenBridge.tokens
}

actual fun platformPushTokenSource(): PushTokenSource = ApnsPushTokenSource()

/**
 * Swift'dan chaqiriladigan ko'prik (`IosPushBridge.shared.setToken(...)`).
 * Google Sign-In ko'prigi bilan aynan bir xil naqsh.
 */
object IosPushBridge {

    /** `AppDelegate` APNs tokenini o'n oltilik matn ko'rinishida uzatadi. */
    fun setToken(token: String) = PushTokenBridge.publish(token)

    /**
     * Push bosilganda — `userInfo` dagi konvert (`02-PUSH_CATALOG_BACKEND.md` §2).
     *
     * Swift tomoni kalitlarni bittalab uzatadi: `userInfo` — `[AnyHashable: Any]`, ya'ni
     * uni Kotlin tomonga xarita sifatida berish har bir qiymatni `Any` qilib tashlardi.
     */
    fun openTarget(
        notificationId: String?,
        targetType: String?,
        targetId: String?,
        conversationId: String?,
    ) = dev.core.common.push.PushRoute.set(
        dev.core.common.push.PushRoute.Payload(
            notificationId = notificationId,
            targetType = targetType,
            targetId = targetId,
            conversationId = conversationId,
        ),
    )
}
