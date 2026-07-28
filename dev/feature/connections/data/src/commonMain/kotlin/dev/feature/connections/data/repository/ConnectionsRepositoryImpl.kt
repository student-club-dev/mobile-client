package dev.feature.connections.data.repository

import dev.core.common.Resource
import dev.core.common.map
import dev.core.common.network.NetworkConnectivity
import dev.core.network.generated.api.ConnectionsApi
import dev.core.network.generated.model.BlockDto
import dev.core.network.generated.model.CreateReportDto
import dev.core.network.generated.model.SendConnectionRequestDto
import dev.core.network.response.safeCall
import dev.feature.connections.data.mapper.toCourseYearDtoOrNull
import dev.feature.connections.data.mapper.toDomain
import dev.feature.connections.data.mapper.toDto
import dev.feature.connections.domain.model.ConnectedStudent
import dev.feature.connections.domain.model.Connection
import dev.feature.connections.domain.model.ConnectionRequest
import dev.feature.connections.domain.model.Page
import dev.feature.connections.domain.model.ReportReason
import dev.feature.connections.domain.model.RequestDirection
import dev.feature.connections.domain.model.SearchedStudent
import dev.feature.connections.domain.model.StudentFilter
import dev.feature.connections.domain.repository.ConnectionsRepository

/**
 * `Connections` bo'limining yagona implementatsiyasi — generatsiya qilingan [ConnectionsApi]
 * ustida (spec: `dev/api-client-generator/student-club.json`, handoff: `connections.md`).
 *
 * Hamma chaqiruv [safeCall] orqali o'tadi: u internetni tekshiradi, konvert xatolarini typed
 * `AppException` ga aylantiradi va 422 ning `error.fields` ini saqlab qoladi. Shu sabab bu
 * yerda birorta `try/catch` yo'q.
 */
class ConnectionsRepositoryImpl(
    private val api: ConnectionsApi,
    private val connectivity: NetworkConnectivity,
) : ConnectionsRepository {

    /**
     * `GET /v1/students` — generatorda `studentSearchList` (eski `studentSearch` =
     * `/v1/students/search`, u **deprecated** va shu endpointga delegatsiya qiladi).
     *
     * Ko'p qiymatli parametrlar bo'sh ro'yxat bo'lganda **umuman yuborilmaydi**: bo'sh
     * `?gender=` server tomonda `422` beradi.
     */
    override suspend fun students(
        filter: StudentFilter,
        page: Int,
        size: Int,
    ): Resource<Page<SearchedStudent>> = safeCall(connectivity) {
        api.studentSearchList(
            page = page,
            size = size,
            q = filter.query?.trim()?.takeIf { it.isNotEmpty() },
            universityId = filter.universityIds.takeIf { it.isNotEmpty() },
            gender = filter.genders.map { it.toDto() }.takeIf { it.isNotEmpty() },
            // Noma'lum kurs qiymati (eski kesh) filtrni 422 ga olib kelmasin — tashlab yuboramiz.
            courseYear = filter.courseYears.mapNotNull { it.toCourseYearDtoOrNull() }
                .takeIf { it.isNotEmpty() },
            birthYearFrom = filter.birthYearFrom,
            birthYearTo = filter.birthYearTo,
            connectionStatus = filter.connectionStatus?.toDto(),
            sort = filter.sort.toDto(),
        ).body()
    }.map { it.toDomain() }

    override suspend fun sendRequest(studentId: String): Resource<Connection> =
        safeCall(connectivity) {
            api.connectionsSend(SendConnectionRequestDto(addresseeId = studentId)).body()
        }.map { it.toDomain() }

    override suspend fun requests(
        direction: RequestDirection,
        page: Int,
        size: Int,
    ): Resource<Page<ConnectionRequest>> = safeCall(connectivity) {
        api.requests(direction = direction.toDto(), page = page, size = size).body()
    }.map { it.toDomain() }

    override suspend fun accept(connectionId: String): Resource<Connection> =
        safeCall(connectivity) { api.accept(connectionId).body() }.map { it.toDomain() }

    override suspend fun decline(connectionId: String): Resource<Unit> =
        safeCall(connectivity) { api.decline(connectionId).body() }

    override suspend fun connections(page: Int, size: Int): Resource<Page<ConnectedStudent>> =
        safeCall(connectivity) { api.connectionsList(page = page, size = size).body() }
            .map { it.toDomain() }

    override suspend fun disconnect(studentId: String): Resource<Unit> =
        safeCall(connectivity) { api.connectionsRemove(studentId).body() }

    override suspend fun block(studentId: String): Resource<Unit> =
        safeCall(connectivity) { api.block(BlockDto(studentId = studentId)).body() }

    override suspend fun unblock(studentId: String): Resource<Unit> =
        safeCall(connectivity) { api.unblock(studentId).body() }

    override suspend fun report(
        reason: ReportReason,
        targetStudentId: String?,
        messageId: String?,
        note: String?,
    ): Resource<Unit> = safeCall(connectivity) {
        api.report(
            CreateReportDto(
                reason = reason.toDto(),
                targetStudentId = targetStudentId,
                messageId = messageId,
                // Server 1000 belgidan uzun izohni rad etadi — shu yerda kesamiz.
                note = note?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_NOTE_LENGTH),
            ),
        ).body()
    }.map { }

    /** `direction` query'da **kichik harflar** bilan ketishi shart (aks holda `422`). */
    private fun RequestDirection.toDto(): ConnectionsApi.DirectionRequests = when (this) {
        RequestDirection.INCOMING -> ConnectionsApi.DirectionRequests.incoming
        RequestDirection.OUTGOING -> ConnectionsApi.DirectionRequests.outgoing
    }

    private companion object {
        const val MAX_NOTE_LENGTH = 1000
    }
}
