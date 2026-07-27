package dev.core.domain.model

/**
 * O'zbekiston viloyati (`GET /v1/geo/regions`).
 *
 * E'lonlar feed'i shu bo'yicha toraytiriladi: talabaga butun mamlakat emas, o'z viloyatidagi
 * chegirmalar kerak. Tanlov local saqlanadi va har `discounts/search` so'roviga
 * `filter.geo.regionIds` bo'lib qo'shiladi.
 */
data class Region(
    val id: String,
    val name: String,
)
