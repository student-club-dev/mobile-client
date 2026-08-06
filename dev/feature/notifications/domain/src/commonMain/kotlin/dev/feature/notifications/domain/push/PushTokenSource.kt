package dev.feature.notifications.domain.push

import kotlinx.coroutines.flow.Flow

/** Qurilma turi — `POST /v1/devices` tanasidagi `platform`. */
enum class DevicePlatform { ANDROID, IOS, WEB }

/**
 * Token qaysi kanalga tegishli — `POST /v1/devices` dagi `tokenType`
 * (`04-CALLS_RESPONSE.md` §2).
 *
 * ⚠️ [APNS_VOIP] — PushKit tokeni va u oddiysining O'RNIGA emas, **ikkinchi ro'yxatdan
 * o'tkazish** sifatida yuboriladi: bitta iPhone'da ikkita token bo'ladi va ikkalasi ham
 * kerak. Bittasini ikkinchisi bilan almashtirsak yo xabarlar, yo qo'ng'iroqlar jimgina
 * o'chadi.
 *
 * Server VoIP kanalini himoyalaydi: oddiy bildirishnoma yo'li `APNS_VOIP` tokenini
 * umuman ko'rmaydi (`targetsFor()` uni so'rovning o'zida chiqarib tashlaydi).
 */
enum class DeviceTokenType { FCM, APNS, APNS_VOIP }

/**
 * Qurilmaning push tokeni **manbai** — platformaga xos qism.
 *
 * Android: FCM (`FirebaseMessaging.getToken()`), iOS: APNs tokeni (Swift tomondan
 * ko'prik orqali beriladi). Domen qatlami ikkalasini ham shu bitta interfeys ortida ko'radi.
 */
interface PushTokenSource {

    val platform: DevicePlatform

    /**
     * Tokenning kanali. Berilmasa server o'zi taxmin qiladi (`IOS → APNS`, qolgani `FCM`),
     * lekin biz uni ATAYLAB aniq yuboramiz: taxmin server tomonda o'zgarsa, ilova o'z
     * tokenini adreslay olmaydigan xizmatga topshirib qo'yishi mumkin edi.
     */
    val tokenType: DeviceTokenType

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
        override val tokenType: DeviceTokenType = DeviceTokenType.FCM
        override suspend fun currentToken(): String? = null
        override val tokenRefreshes: Flow<String> = kotlinx.coroutines.flow.emptyFlow()
    }
}
