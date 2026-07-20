package dev.feature.ads.data.mapper

import dev.core.database.sql.AdEntity
import dev.feature.ads.domain.model.Ad
import dev.feature.ads.domain.model.AdType

internal fun AdEntity.toDomain(): Ad = Ad(
    id = id,
    type = parseEnum(type, AdType.OTHER),
    title = title,
    category = category,
    price = price,
    description = description,
    images = images.splitDb(),
    ownerId = ownerId,
    createdAgo = createdAgo,
)

// --- DB <-> domen yordamchilari (core:data'dagi internal helper'lar bilan bir xil) ---
internal fun List<String>.joinDb(): String = joinToString("|")
internal fun String.splitDb(): List<String> = if (isBlank()) emptyList() else split("|").filter { it.isNotBlank() }
internal inline fun <reified T : Enum<T>> parseEnum(name: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
