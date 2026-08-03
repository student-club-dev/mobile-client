package dev.feature.connections.data.mapper

import dev.core.network.generated.model.BlockedStudentDto
import dev.core.network.generated.model.BlockedStudentPageDto
import dev.core.network.generated.model.ConnectionDto
import dev.core.network.generated.model.ConnectionStatusDto
import dev.core.network.generated.model.ConnectionSummaryDto
import dev.core.network.generated.model.ConnectionSummaryPageDto
import dev.core.network.generated.model.ConnectionViewDto
import dev.core.network.generated.model.CourseYearDto
import dev.core.network.generated.model.GenderDto
import dev.core.network.generated.model.ReportReasonDto
import dev.core.network.generated.model.RequestItemDto
import dev.core.network.generated.model.RequestItemPageDto
import dev.core.network.generated.model.SearchResultDto
import dev.core.network.generated.model.SearchResultPageDto
import dev.core.network.generated.model.StudentPhotoDto
import dev.core.network.generated.model.StudentSortDto
import dev.core.network.generated.model.StudentSummaryDto
import dev.feature.connections.domain.model.BlockedStudent
import dev.feature.connections.domain.model.ConnectedStudent
import dev.feature.connections.domain.model.Connection
import dev.feature.connections.domain.model.ConnectionRequest
import dev.feature.connections.domain.model.ConnectionStatus
import dev.feature.connections.domain.model.ConnectionView
import dev.feature.connections.domain.model.Gender
import dev.feature.connections.domain.model.Page
import dev.feature.connections.domain.model.ReportReason
import dev.feature.connections.domain.model.SearchedStudent
import dev.feature.connections.domain.model.StudentPhoto
import dev.feature.connections.domain.model.StudentSort
import dev.feature.connections.domain.model.StudentSummary

// Generatsiya qilingan DTO'lar → domen modellari. Bitta yo'nalish: domen backendga
// bog'lanmaydi, DTO esa spec o'zgarganda qayta generatsiya qilinadi.

fun StudentSummaryDto.toDomain(): StudentSummary = StudentSummary(
    id = id,
    username = username,
    fullName = fullName,
    avatarUrl = avatarUrl,
    universityId = universityId,
    gender = gender?.toDomain(),
    courseYear = courseYear?.value,
    online = online,
    lastSeenAt = lastSeenAt,
    photos = photos.map { it.toDomain() },
    bio = bio?.takeIf { it.isNotBlank() },
    phoneNumber = phoneNumber?.takeIf { it.isNotBlank() },
)

fun StudentPhotoDto.toDomain(): StudentPhoto = StudentPhoto(
    id = id,
    url = url,
    thumbUrl = thumbUrl,
    width = width ?: 0,
    height = height ?: 0,
)

fun SearchResultDto.toDomain(): SearchedStudent = SearchedStudent(
    student = StudentSummary(
        id = id,
        username = username,
        fullName = fullName,
        avatarUrl = avatarUrl,
        universityId = universityId,
        gender = gender?.toDomain(),
        courseYear = courseYear?.value,
        online = online,
        lastSeenAt = lastSeenAt,
        photos = photos.map { it.toDomain() },
        bio = bio?.takeIf { it.isNotBlank() },
        phoneNumber = phoneNumber?.takeIf { it.isNotBlank() },
    ),
    connectionStatus = connectionStatus.toDomain(),
)

fun GenderDto.toDomain(): Gender = when (this) {
    GenderDto.MALE -> Gender.MALE
    GenderDto.FEMALE -> Gender.FEMALE
}

fun Gender.toDto(): GenderDto = when (this) {
    Gender.MALE -> GenderDto.MALE
    Gender.FEMALE -> GenderDto.FEMALE
}

fun ConnectionView.toDto(): ConnectionViewDto = when (this) {
    ConnectionView.NONE -> ConnectionViewDto.NONE
    ConnectionView.PENDING_OUT -> ConnectionViewDto.PENDING_OUT
    ConnectionView.PENDING_IN -> ConnectionViewDto.PENDING_IN
    ConnectionView.CONNECTED -> ConnectionViewDto.CONNECTED
}

fun StudentSort.toDto(): StudentSortDto = when (this) {
    StudentSort.RECENT -> StudentSortDto.RECENT
    StudentSort.NAME -> StudentSortDto.NAME
}

/** `"1".."4"`/`"MASTER"` → enum. Noma'lum qiymat filtrga qo'shilmaydi (`null`). */
fun String.toCourseYearDtoOrNull(): CourseYearDto? =
    CourseYearDto.entries.firstOrNull { it.value == this }

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

/**
 * Presence maydonlari serverda allaqachon maskalangan (`online = false`, `lastSeenAt = null`) —
 * shu sabab bu yerda qo'shimcha tozalash yo'q, [StudentSummaryDto.toDomain] etarli.
 */
fun BlockedStudentDto.toDomain(): BlockedStudent = BlockedStudent(
    student = student.toDomain(),
    blockedAt = blockedAt,
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

fun BlockedStudentPageDto.toDomain(): Page<BlockedStudent> =
    Page(items.map { it.toDomain() }, page, propertySize, total, hasNext)
