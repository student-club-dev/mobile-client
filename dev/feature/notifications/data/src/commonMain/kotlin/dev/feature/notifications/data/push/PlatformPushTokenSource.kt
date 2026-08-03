package dev.feature.notifications.data.push

import dev.feature.notifications.domain.push.PushTokenSource

/**
 * Platformaga xos push token manbai.
 *
 * - **Android** — FCM (`FirebaseMessaging.getToken()`), yangilanishlar
 *   `FirebaseMessagingService.onNewToken` dan [PushTokenBridge] orqali keladi.
 * - **iOS** — APNs tokeni: uni faqat `AppDelegate` beradi, shuning uchun Swift tomoni
 *   [PushTokenBridge] ga yozadi (Google Sign-In ko'prigi bilan bir xil naqsh).
 */
expect fun platformPushTokenSource(): PushTokenSource
