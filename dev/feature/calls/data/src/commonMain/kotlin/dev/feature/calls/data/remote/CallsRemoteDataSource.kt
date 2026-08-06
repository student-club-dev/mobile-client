package dev.feature.calls.data.remote

import dev.core.common.Resource
import dev.core.common.network.NetworkConnectivity
import dev.core.network.generated.api.CallsApi
import dev.core.network.generated.model.ActiveCallResponseDto
import dev.core.network.generated.model.CallListDto
import dev.core.network.generated.model.IceServersDto
import dev.core.network.generated.model.RecordCallStatsDto
import dev.core.network.response.safeCall
import dev.feature.calls.domain.model.CallStats
import dev.feature.calls.domain.model.CandidateType
import io.ktor.client.call.body

/**
 * Qo'ng'iroqning uchta REST endpointi (`handoff/09-CALLS-REST.md`).
 *
 * Hammasi `Bearer` va **faqat STUDENT** hisobi uchun; boshqa turdagi hisob `403 FORBIDDEN`
 * oladi. Xatolarni [safeCall] typed `AppException` ga aylantiradi, `error.code` esa
 * `AppException.errorCode` da saqlanadi — `NOT_IMPLEMENTED` ni umumiy 5xx dan ajratish
 * aynan shunga tayanadi.
 */
class CallsRemoteDataSource(
    private val api: CallsApi,
    private val connectivity: NetworkConnectivity,
) {

    /** `GET /v1/calls/ice-servers` — parametrsiz, `studentId` faqat tokendan olinadi. */
    suspend fun iceServers(): Resource<IceServersDto> =
        safeCall(connectivity) { api.iceServers().body() }

    /**
     * `GET /v1/calls/active` — sovuq startdan keyin: serverda jonli qo'ng'iroq bormi.
     *
     * Ikkita Redis o'qish, bazaga yozuv yo'q — javob tez keladi. `call: null` **xato emas**.
     */
    suspend fun activeCall(): Resource<ActiveCallResponseDto> =
        safeCall(connectivity) { api.active().body() }

    /** `GET /v1/calls?page=&size=` — eng yangisi birinchi. */
    suspend fun history(page: Int, size: Int): Resource<CallListDto> =
        safeCall(connectivity) { api.callsList(page = page, size = size).body() }

    /**
     * `POST /v1/calls/{callId}/stats` — javob kodi **200** (201 emas): takroran yuborilsa
     * bir xil qator qayta yoziladi, ikkinchi qator paydo bo'lmaydi.
     *
     * ⚠️ `studentId` **yuborilmaydi** — u tokendan olinadi va yuborilsa
     * `forbidNonWhitelisted` uni `422` bilan rad etadi.
     */
    suspend fun reportStats(callId: String, stats: CallStats): Resource<Unit> =
        safeCall(connectivity) {
            api.recordStats(callId = callId, recordCallStatsDto = stats.toDto())
            Unit
        }
}

private fun CallStats.toDto(): RecordCallStatsDto = RecordCallStatsDto(
    candidateType = when (candidateType) {
        CandidateType.HOST -> RecordCallStatsDto.CandidateType.HOST
        CandidateType.SRFLX -> RecordCallStatsDto.CandidateType.SRFLX
        CandidateType.RELAY -> RecordCallStatsDto.CandidateType.RELAY
    },
    rttMs = rttMs,
    jitterMs = jitterMs,
    packetsLost = packetsLost,
    packetsReceived = packetsReceived,
    bytesSent = bytesSent,
    bytesReceived = bytesReceived,
)
