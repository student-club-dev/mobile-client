package dev.feature.university.data.dto

import dev.feature.university.domain.model.University
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `prof-emis.edu.uz` — OTM ro'yxati elementi. Ortiqcha maydonlar e'tiborsiz qoldiriladi. */
@Serializable
data class ProfEmisUniversityDto(
    val id: Int,
    @SerialName("name_uz") val nameUz: String = "",
    @SerialName("name_ru") val nameRu: String = "",
    @SerialName("name_en") val nameEn: String = "",
    val address: String = "",
    @SerialName("region_id") val regionId: Int = 0,
)

fun ProfEmisUniversityDto.toUniversity(): University {
    val name = nameUz.ifBlank { nameRu }.ifBlank { nameEn }.ifBlank { "Universitet #$id" }
    return University(
        id = "emis-$id",
        name = name,
        city = address.ifBlank { "O'zbekiston" },
        monogram = universityMonogram(name),
        faculty = null,
        accent = UNI_ACCENTS[(id % UNI_ACCENTS.size + UNI_ACCENTS.size) % UNI_ACCENTS.size],
    )
}

private val UNI_ACCENTS = listOf(
    0xFF6C47FF, 0xFF2563EB, 0xFF059669, 0xFFD97706, 0xFFBE185D, 0xFF0EA5E9, 0xFF7C3AED,
)

private fun universityMonogram(name: String): String {
    val skip = setOf("va", "nomidagi", "davlat", "the", "of", "and", "milliy")
    val letters = name.split(' ', '-', '.', ',')
        .map { it.trim() }
        .filter { it.length > 1 && it.lowercase() !in skip }
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
    return when {
        letters.size >= 2 -> letters.take(4).joinToString("")
        else -> name.filter { it.isLetter() }.take(3).uppercase().ifBlank { "OTM" }
    }
}
