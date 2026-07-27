package dev.core.data.mapper

import dev.core.database.sql.DiscountCategoryEntity
import dev.core.database.sql.DiscountOfferEntity
import dev.core.domain.model.DiscountCategory
import dev.core.domain.model.DiscountOffer
import dev.core.domain.model.DiscountTag
import dev.core.domain.model.OfferBranch
import dev.core.domain.model.OfferDetail

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

/**
 * Keshdagi kartadan yig'ilgan tafsilot — tarmoq yo'q bo'lganda ekran bo'sh qolmasin uchun.
 * Promo-kod, filiallar va shartlar faqat `POST /v1/discounts/detail` da bo'lgani uchun bu
 * yerda yo'q; UI buni [OfferDetail.fromNetwork] orqali biladi.
 */
fun DiscountOffer.toOfflineDetail(saved: Boolean): OfferDetail = OfferDetail(
    id = id,
    categoryId = categoryId,
    subcategory = subcategory,
    merchant = merchant,
    title = title,
    emoji = emoji,
    bannerAccent = bannerAccent,
    isDiscount = isDiscount,
    discountPercent = discountPercent,
    originalPrice = originalPrice,
    finalPrice = finalPrice,
    savedAmount = savedAmount,
    priceUnit = priceUnit,
    tag = tag,
    promoCode = promoCode,
    validTo = expiry,
    saved = saved,
    branches = listOfNotNull(
        location?.let { OfferBranch(id = "$id-branch", name = it, address = "", lat = lat, lng = lng) },
    ),
    fromNetwork = false,
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
