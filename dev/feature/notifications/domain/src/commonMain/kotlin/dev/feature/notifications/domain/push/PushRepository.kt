package dev.feature.notifications.domain.push

import dev.core.common.Resource

/**
 * Qurilma push tokenini backend bilan sinxronlaydi (`POST /v1/devices`,
 * `DELETE /v1/devices/{token}`).
 *
 * Oflayn push (`chat.md` §10): qabul qiluvchining ochiq soketi bo'lmasa, serverda ro'yxatdan
 * o'tgan tokenga xabar yuboriladi. **Token yo'q bo'lsa push ham yo'q — xato ham bermaydi**,
 * ya'ni bu qatlam "eng yaxshi harakat".
 */
interface PushRepository {

    /** Joriy tokenni ro'yxatdan o'tkazadi. Token bo'lmasa hech nima qilmaydi. */
    suspend fun register(): Resource<Unit>

    /** Joriy tokenni ro'yxatdan chiqaradi (chiqishda). */
    suspend fun unregister(): Resource<Unit>
}
