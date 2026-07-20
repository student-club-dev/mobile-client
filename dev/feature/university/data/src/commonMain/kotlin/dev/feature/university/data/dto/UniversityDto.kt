package dev.feature.university.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class UniversityDto(
    val id: String,
    val name: String,
    val city: String = "",
    val monogram: String = "",
    val faculty: String? = null,
    val accent: Long = 0xFF6C47FF,
)
