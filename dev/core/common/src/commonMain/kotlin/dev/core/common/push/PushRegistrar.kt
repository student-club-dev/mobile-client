package dev.core.common.push

/**
 * Sessiya ochilganda/yopilganda **qurilma push tokenini** backendga bog'lash/uzish.
 *
 * Interfeys ataylab `:dev:core:common` da: auth qatlami push haqida hech nima bilmasligi
 * kerak, lekin tokenni ro'yxatdan o'tkazishning yagona to'g'ri momenti — sessiya
 * boshlangan/tugagan payt. Implementatsiya — `:dev:feature:notifications:data`.
 */
interface PushRegistrar {

    /**
     * Kirish muvaffaqiyatli tugadi (yoki ilova saqlangan sessiya bilan ochildi).
     * `POST /v1/devices` — token yangilanganda takror yuboriladi.
     *
     * **Xato tashlamaydi**: push — "eng yaxshi harakat", u kirishni to'xtatmasligi kerak.
     */
    suspend fun onSessionStarted()

    /**
     * Chiqish boshlandi. `DELETE /v1/devices/{token}` — **tokenlar tozalanishidan OLDIN**
     * chaqirilishi shart, aks holda so'rov `401` bilan qaytadi.
     */
    suspend fun onSessionEnding()

    /** Push o'chirilgan holat (testlar, push ulanmagan platformalar). */
    object None : PushRegistrar {
        override suspend fun onSessionStarted() = Unit
        override suspend fun onSessionEnding() = Unit
    }
}
