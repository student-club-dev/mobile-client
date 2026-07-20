package dev.feature.jobs.data.dto

import kotlinx.serialization.Serializable

/** Ishlar API javob DTO'si (B4 offline-first shabloni). */
@Serializable
data class JobDto(
    val id: String,
    val title: String,
    val company: String,
    val companyMonogram: String = "",
    val location: String = "",
    val category: String = "",
    val tags: List<String> = emptyList(),
    val salary: String = "",
    val remote: Boolean = false,
    val partTime: Boolean = false,
    val postedAgo: String = "",
    val field: String = "",
    val bookmarked: Boolean = false,
)
