package dev.feature.calls.domain.repository

import dev.core.common.Resource
import dev.feature.calls.domain.model.ActiveCall
import dev.feature.calls.domain.model.CallPage
import dev.feature.calls.domain.model.CallStats
import dev.feature.calls.domain.model.IceServers

/**
 * Qo'ng'iroqning **REST** tomoni — uchta endpoint (`handoff/09-CALLS-REST.md`).
 *
 * Signalizatsiya bu yerda emas: u WebSocket'da va [CallController] orqali boshqariladi.
 */
interface CallRepository {

    /**
     * TURN/STUN hisobi — `GET /v1/calls/ice-servers`.
     *
     * Kesh **repository ichida**: hisob qo'ng'iroqqa bog'lanmagan va muddati tugashiga
     * 5 daqiqa qolgunicha qayta ishlatiladi. Chegara — daqiqasiga 10 ta so'rov.
     *
     * ⚠️ `503 NOT_IMPLEMENTED` — **kutilgan javob** (`CALLS_ENABLED=false` yoki TURN
     * sozlanmagan). Uni [callsUnavailable] ajratadi; UI shunda «server ishlamayapti» emas,
     * «qo'ng'iroq hozircha mavjud emas» deydi.
     */
    suspend fun iceServers(forceRefresh: Boolean = false): Resource<IceServers>

    /**
     * Serverdagi **jonli** qo'ng'iroq — `GET /v1/calls/active` (`04-CALLS_RESPONSE.md` §4).
     *
     * `null` — faol qo'ng'iroq yo'q va bu **kutilgan javob**, xato emas: server `404` emas,
     * `200` + `call: null` qaytaradi. Muddati o'tgan (`expiresAt` o'tmishda) qo'ng'iroq ham
     * `null` bilan bir xil ma'noda.
     *
     * Nima uchun REST: ilova butunlay yopiq bo'lganda kelgan qo'ng'iroq (VoIP push) uchun
     * WebSocket ulanishini kutib bo'lmaydi — u ulangunicha qo'ng'iroq allaqachon tugagan
     * bo'lishi mumkin va telefon **bo'sh joyga jiringlab** turardi.
     */
    suspend fun activeCall(): Resource<ActiveCall?>

    /**
     * Qo'ng'iroqlar tarixi — `GET /v1/calls`, eng yangisi birinchi.
     *
     * Alohida ekran uchun. Chat lentasining o'zida qo'ng'iroq allaqachon `CALL` xabar
     * sifatida ko'rinadi, ya'ni bu ekran **majburiy emas**.
     */
    suspend fun history(page: Int = 1, size: Int = CallPage.DEFAULT_PAGE_SIZE): Resource<CallPage>

    /**
     * O'lchovni yuboradi — `POST /v1/calls/{id}/stats`, qo'ng'iroq tugagach **bir marta**.
     *
     * Takroran yuborish xavfsiz: bir xil `(callId, studentId)` uchun qator qayta yoziladi.
     *
     * ⚠️ Javobsiz qo'ng'iroq (`MISSED`/`DECLINED`/`CANCELED`) uchun **umuman yubormang** —
     * media oqmagan, o'lchash uchun narsa yo'q. Yuborilsa `409 INVALID_CALL_STATE` keladi
     * va bu **normal javob**, foydalanuvchiga ko'rsatilmaydi.
     */
    suspend fun reportStats(callId: String, stats: CallStats): Resource<Unit>
}
