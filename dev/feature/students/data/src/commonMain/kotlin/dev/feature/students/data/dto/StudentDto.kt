package dev.feature.students.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class StudentDto(
    val id: String,
    val firstName: String,
    val lastName: String,
    val initial: String = "",
    /** Profil rasmi — server bermasa `null`, u holda avatarda bosh harf ko'rinadi. */
    val avatarUrl: String? = null,
    val universityId: String = "",
    val universityMonogram: String = "",
    val course: Int = 1,
    val faculty: String = "",
    val friendStatus: String = "NONE",
    val interests: List<String> = emptyList(),
    val friendsCount: Int = 0,
    val adsCount: Int = 0,
    val rating: Double = 0.0,
)
