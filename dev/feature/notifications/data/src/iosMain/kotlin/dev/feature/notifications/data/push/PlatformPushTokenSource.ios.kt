package dev.feature.notifications.data.push

import dev.feature.notifications.domain.push.DevicePlatform
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

    /** Push bosilganda — `userInfo["conversationId"]`. */
    fun openConversation(conversationId: String?) =
        dev.core.common.push.PushRoute.set(conversationId)
}
