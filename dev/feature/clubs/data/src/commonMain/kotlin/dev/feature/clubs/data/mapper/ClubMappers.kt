package dev.feature.clubs.data.mapper

import dev.core.database.sql.ClubEntity
import dev.feature.clubs.data.dto.ClubDto
import dev.feature.clubs.domain.model.Club

fun ClubDto.toDomain(): Club = Club(
    id = id, name = name, description = description, membersCount = membersCount, imageUrl = imageUrl,
)

fun ClubEntity.toDomain(): Club = Club(
    id = id, name = name, description = description, membersCount = membersCount.toInt(),
    imageUrl = imageUrl, joined = joined != 0L,
)
