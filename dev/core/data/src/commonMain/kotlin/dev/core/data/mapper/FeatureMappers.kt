package dev.core.data.mapper

import dev.core.database.sql.DiscountCategoryEntity
import dev.core.database.sql.DiscountOfferEntity
import dev.core.domain.model.DiscountCategory
import dev.core.domain.model.DiscountOffer
import dev.core.domain.model.DiscountTag

// --- List <-> TEXT ("|" bilan) ---
internal fun List<String>.joinDb(): String = joinToString("|")
internal fun String.splitDb(): List<String> =
    if (isBlank()) emptyList() else split("|").filter { it.isNotBlank() }

// --- Boolean <-> INTEGER ---
internal fun Boolean.toDb(): Long = if (this) 1L else 0L
internal fun Long.toBool(): Boolean = this != 0L

// --- Enum xavfsiz o'qish ---
private inline fun <reified T : Enum<T>> parseEnum(value: String, default: T): T =
    runCatching { enumValueOf<T>(value) }.getOrDefault(default)

fun DiscountCategoryEntity.toDomain(): DiscountCategory = DiscountCategory(
    id = id, name = name, emoji = emoji, offerCount = offerCount.toInt(), accent = accent,
)

fun DiscountOfferEntity.toDomain(): DiscountOffer = DiscountOffer(
    id = id,
    categoryId = categoryId,
    subcategory = subcategory,
    gender = gender,
    merchant = merchant,
    title = title,
    isDiscount = isDiscount.toBool(),
    discountPercent = discountPercent.toInt(),
    originalPrice = originalPrice,
    finalPrice = finalPrice,
    priceUnit = priceUnit,
    tag = parseEnum(tag, DiscountTag.STUDENT_ID),
    promoCode = promoCode,
    location = location,
    expiry = expiry,
    emoji = emoji,
    bannerAccent = bannerAccent,
    featured = featured.toBool(),
    lat = lat,
    lng = lng,
)
