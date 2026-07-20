package dev.feature.jobs.data.mapper

import dev.core.database.sql.JobApplicationEntity
import dev.core.database.sql.JobEntity
import dev.feature.jobs.domain.model.ApplicationStatus
import dev.feature.jobs.domain.model.Job
import dev.feature.jobs.domain.model.JobApplication

internal fun JobEntity.toDomain(): Job = Job(
    id = id,
    title = title,
    company = company,
    companyMonogram = companyMonogram,
    location = location,
    category = category,
    tags = tags.splitDb(),
    salary = salary,
    remote = remote.toBool(),
    partTime = partTime.toBool(),
    postedAgo = postedAgo,
    field = field_,
    bookmarked = bookmarked.toBool(),
)

internal fun JobApplicationEntity.toDomain(): JobApplication = JobApplication(
    id = id,
    jobId = jobId,
    jobTitle = jobTitle,
    company = company,
    status = parseEnum(status, ApplicationStatus.SENT),
    appliedAgo = appliedAgo,
)

// --- DB <-> domen yordamchilari (core:data'dagi internal helper'lar bilan bir xil) ---
internal fun List<String>.joinDb(): String = joinToString("|")
internal fun String.splitDb(): List<String> = if (isBlank()) emptyList() else split("|")
internal fun Boolean.toDb(): Long = if (this) 1L else 0L
internal fun Long.toBool(): Boolean = this != 0L
internal inline fun <reified T : Enum<T>> parseEnum(name: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
