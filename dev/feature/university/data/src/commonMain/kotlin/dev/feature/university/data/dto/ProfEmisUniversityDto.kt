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

/**
 * ⚠️ Manba **logotip bermaydi** (`licence_pdf_file`, ijtimoiy tarmoq havolalari bor, rasm
 * yo'q), shuning uchun ro'yxatdagi belgi — nomdan hosil qilingan qisqartma
 * (`UniversityNaming`). Nom va manzil xom holicha saqlanadi: qisqartirish qoidasi
 * yaxshilanganda saqlangan qatorlarni qayta yuklash kerak bo'lmasin.
 */
fun ProfEmisUniversityDto.toUniversity(): University {
    val name = nameUz.ifBlank { nameRu }.ifBlank { nameEn }.ifBlank { "Universitet #$id" }
    return University(
        id = "emis-$id",
        name = name,
        city = address,
        faculty = null,
        accent = UNI_ACCENTS[(id % UNI_ACCENTS.size + UNI_ACCENTS.size) % UNI_ACCENTS.size],
    )
}

private val UNI_ACCENTS = listOf(
    0xFF6C47FF, 0xFF2563EB, 0xFF059669, 0xFFD97706, 0xFFBE185D, 0xFF0EA5E9, 0xFF7C3AED,
)
