package dev.feature.university.data.mapper

import dev.core.database.sql.UniversityEntity
import dev.feature.university.domain.model.University

/**
 * ⚠️ `monogram` ustuni ataylab **o'qilmaydi**: u yozilgan paytdagi qoida bo'yicha hisoblangan.
 * Qisqartma har safar nomdan qayta chiqariladi, shunda qoida yaxshilanganda eski
 * o'rnatmalardagi qatorlar ham migratsiyasiz to'g'ri ko'rinadi.
 */
internal fun UniversityEntity.toDomain(): University = University(
    id = id, name = name, city = city, faculty = faculty, accent = accent,
)
