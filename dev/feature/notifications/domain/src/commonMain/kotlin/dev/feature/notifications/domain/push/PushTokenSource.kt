package dev.feature.notifications.domain.push

import kotlinx.coroutines.flow.Flow

/** Qurilma turi — `POST /v1/devices` tanasidagi `platform`. */
enum class DevicePlatform { ANDROID, IOS, WEB }

/**
 * Qurilmaning push tokeni **manbai** — platformaga xos qism.
 *
 * Android: FCM (`FirebaseMessaging.getToken()`), iOS: APNs tokeni (Swift tomondan
 * ko'prik orqali beriladi). Domen qatlami ikkalasini ham shu bitta interfeys ortida ko'radi.
 */
interface PushTokenSource {

    val platform: DevicePlatform

    /**
     * Joriy token yoki `null` (ruxsat berilmagan, Play Services yo'q, hali kelmagan).
     * `null` — xato emas: push shunchaki ishlamaydi.
     */
    suspend fun currentToken(): String?

    /**
     * Token **almashganda** yangi qiymat keladi. FCM tokeni ilova qayta o'rnatilganda,
     * ma'lumot tozalanganda yoki server aylantirganda o'zgaradi — eskisi bilan qolgan
     * qurilmaga push yetib bormaydi, shuning uchun har o'zgarishda qayta ro'yxatdan o'tamiz.
     */
    val tokenRefreshes: Flow<String>

    /** Push mavjud bo'lmagan muhit (testlar, Play Services'siz qurilma). */
    object Unavailable : PushTokenSource {
        override val platform: DevicePlatform = DevicePlatform.ANDROID
        override suspend fun currentToken(): String? = null
        override val tokenRefreshes: Flow<String> = kotlinx.coroutines.flow.emptyFlow()
    }
}
