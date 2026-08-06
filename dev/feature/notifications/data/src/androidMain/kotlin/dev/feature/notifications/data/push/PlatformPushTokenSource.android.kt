package dev.feature.notifications.data.push

import com.google.firebase.messaging.FirebaseMessaging
import dev.feature.notifications.domain.push.DevicePlatform
import dev.feature.notifications.domain.push.DeviceTokenType
import dev.feature.notifications.domain.push.PushTokenSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

/**
 * Android — **FCM** tokeni.
 *
 * Yangilanishlar `FirebaseMessagingService.onNewToken` dan keladi va u tizim tomonidan
 * chaqiriladi (DI grafidan tashqarida), shuning uchun [PushTokenBridge] orqali o'tadi.
 */
private class FirebasePushTokenSource : PushTokenSource {

    override val platform = DevicePlatform.ANDROID
    override val tokenType = DeviceTokenType.FCM

    /**
     * `getToken()` — Play Services yo'q qurilmada yoki `google-services.json` sozlanmaganda
     * istisno tashlaydi. Bu **xato emas**: push shunchaki ishlamaydi, shuning uchun `null`.
     */
    override suspend fun currentToken(): String? =
        runCatching { FirebaseMessaging.getInstance().token.await() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

    override val tokenRefreshes: Flow<String> = PushTokenBridge.tokens
}

actual fun platformPushTokenSource(): PushTokenSource = FirebasePushTokenSource()
