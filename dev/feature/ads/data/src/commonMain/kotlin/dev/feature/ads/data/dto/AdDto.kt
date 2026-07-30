package dev.feature.ads.data.dto

import kotlinx.serialization.Serializable

/**
 * E'lon API DTO'si (B4 offline-first shabloni). Real API spek kelganda maydon nomlarini
 * servernikiga moslang (@SerialName), oqim o'zgarmaydi.
 */
@Serializable
data class AdDto(
    val id: String,
    val type: String = "OTHER",
    val title: String,
    val category: String = "",
    val price: String = "",
    val description: String = "",
    val images: List<String> = emptyList(),
    val ownerId: String = "",
    val createdAgo: String = "",
)
