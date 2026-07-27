package dev.feature.connections.data.mapper

import dev.core.network.generated.model.ConnectionDto
import dev.core.network.generated.model.ConnectionStatusDto
import dev.core.network.generated.model.ConnectionSummaryDto
import dev.core.network.generated.model.ConnectionSummaryPageDto
import dev.core.network.generated.model.ConnectionViewDto
import dev.core.network.generated.model.ReportReasonDto
import dev.core.network.generated.model.RequestItemDto
import dev.core.network.generated.model.RequestItemPageDto
import dev.core.network.generated.model.SearchResultDto
import dev.core.network.generated.model.SearchResultPageDto
import dev.core.network.generated.model.StudentSummaryDto
import dev.feature.connections.domain.model.ConnectedStudent
import dev.feature.connections.domain.model.Connection
import dev.feature.connections.domain.model.ConnectionRequest
import dev.feature.connections.domain.model.ConnectionStatus
import dev.feature.connections.domain.model.ConnectionView
import dev.feature.connections.domain.model.Page
import dev.feature.connections.domain.model.ReportReason
import dev.feature.connections.domain.model.SearchedStudent
import dev.feature.connections.domain.model.StudentSummary

// Generatsiya qilingan DTO'lar → domen modellari. Bitta yo'nalish: domen backendga
// bog'lanmaydi, DTO esa spec o'zgarganda qayta generatsiya qilinadi.

fun StudentSummaryDto.toDomain(): StudentSummary = StudentSummary(
    id = id,
    username = username,
    fullName = fullName,
    avatarUrl = avatarUrl,
    online = online,
    lastSeenAt = lastSeenAt,
)

fun SearchResultDto.toDomain(): SearchedStudent = SearchedStudent(
    student = StudentSummary(
        id = id,
        username = username,
        fullName = fullName,
        avatarUrl = avatarUrl,
        online = online,
        lastSeenAt = lastSeenAt,
    ),
    connectionStatus = connectionStatus.toDomain(),
)

fun ConnectionViewDto.toDomain(): ConnectionView = when (this) {
    ConnectionViewDto.NONE -> ConnectionView.NONE
    ConnectionViewDto.PENDING_OUT -> ConnectionView.PENDING_OUT
    ConnectionViewDto.PENDING_IN -> ConnectionView.PENDING_IN
    ConnectionViewDto.CONNECTED -> ConnectionView.CONNECTED
}

fun ConnectionStatusDto.toDomain(): ConnectionStatus = when (this) {
    ConnectionStatusDto.PENDING -> ConnectionStatus.PENDING
    ConnectionStatusDto.ACCEPTED -> ConnectionStatus.ACCEPTED
    ConnectionStatusDto.DECLINED -> ConnectionStatus.DECLINED
}

fun ConnectionDto.toDomain(): Connection = Connection(
    id = id,
    requesterId = requesterId,
    addresseeId = addresseeId,
    status = status.toDomain(),
    createdAt = createdAt,
    respondedAt = respondedAt,
)

fun RequestItemDto.toDomain(): ConnectionRequest = ConnectionRequest(
    connectionId = connectionId,
    student = student.toDomain(),
    createdAt = createdAt,
)

fun ConnectionSummaryDto.toDomain(): ConnectedStudent = ConnectedStudent(
    student = student.toDomain(),
    connectedAt = connectedAt,
)

fun ReportReason.toDto(): ReportReasonDto = when (this) {
    ReportReason.SPAM -> ReportReasonDto.SPAM
    ReportReason.SCAM -> ReportReasonDto.SCAM
    ReportReason.HARASSMENT -> ReportReasonDto.HARASSMENT
    ReportReason.INAPPROPRIATE -> ReportReasonDto.INAPPROPRIATE
    ReportReason.OTHER -> ReportReasonDto.OTHER
}

// --- Sahifa konvertlari ---------------------------------------------------------------
// Generator `size` ni `propertySize` deb nomlaydi (Kotlin'da `size` List bilan to'qnashadi).

fun SearchResultPageDto.toDomain(): Page<SearchedStudent> =
    Page(items.map { it.toDomain() }, page, propertySize, total, hasNext)

fun RequestItemPageDto.toDomain(): Page<ConnectionRequest> =
    Page(items.map { it.toDomain() }, page, propertySize, total, hasNext)

fun ConnectionSummaryPageDto.toDomain(): Page<ConnectedStudent> =
    Page(items.map { it.toDomain() }, page, propertySize, total, hasNext)
