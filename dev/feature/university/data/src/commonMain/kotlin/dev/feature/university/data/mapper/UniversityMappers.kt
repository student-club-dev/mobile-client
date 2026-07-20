package dev.feature.university.data.mapper

import dev.core.database.sql.UniversityEntity
import dev.feature.university.domain.model.University

internal fun UniversityEntity.toDomain(): University = University(
    id = id, name = name, city = city, monogram = monogram, faculty = faculty, accent = accent,
)
