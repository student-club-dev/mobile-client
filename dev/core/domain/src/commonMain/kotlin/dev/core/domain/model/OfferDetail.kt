package dev.core.domain.model

/**
 * Bitta e'lonning to'liq ko'rinishi — `POST /v1/discounts/detail`.
 *
 * Feed kartasi ([DiscountOffer]) faqat qisqa ma'lumot beradi; promo-kod, shartlar, filiallar va
 * biznes kontaktlari faqat shu yerda keladi (backend promo-kodni tizimga kirgan talabagagina
 * qaytaradi). Tarmoq yo'q bo'lsa keshdagi kartadan minimal variant yig'iladi.
 */
data class OfferDetail(
    val id: String,
    val categoryId: String,
    val subcategory: String,
    val merchant: String,
    val title: String,
    val description: String? = null,
    val emoji: String = "🎁",
    val bannerAccent: Long = 0xFF6C47FF,
    val isDiscount: Boolean = true,
    val discountPercent: Int = 0,
    /** Server tayyorlagan nishon matni ("−30%", "2 000 so'm chegirma"). */
    val discountBadge: String? = null,
    val conditions: String? = null,
    val originalPrice: Long = 0,
    val finalPrice: Long = 0,
    val savedAmount: Long = 0,
    val priceUnit: String = "dona",
    val tag: DiscountTag = DiscountTag.STUDENT_ID,
    val promoCode: String? = null,
    /** `ONLINE_LINK` usulida — chegirma olinadigan havola. */
    val redemptionUrl: String? = null,
    val perUserLimit: Int? = null,
    val remainingForUser: Int? = null,
    val validFrom: String? = null,
    val validTo: String? = null,
    val imagesCount: Int = 0,
    val viewsCount: Int = 0,
    val saved: Boolean = false,
    /** Ichki (`_regular`, `_gender`...) kalitlarsiz atributlar — "40 daqiqa", "PS5"... */
    val attributes: List<OfferAttribute> = emptyList(),
    val branches: List<OfferBranch> = emptyList(),
    val businessPhone: String? = null,
    val businessRating: Double? = null,
    val telegram: String? = null,
    val instagram: String? = null,
    val website: String? = null,
    /** `false` — ma'lumot keshdan yig'ilgan (tarmoq yo'q), ya'ni promo-kod/filiallar yo'q. */
    val fromNetwork: Boolean = true,
)

/** E'lon atributi: `label` — ko'rsatiladigan nom, `value` — qiymat (birlik bilan). */
data class OfferAttribute(val label: String, val value: String)

/** E'lon amal qiladigan filial. */
data class OfferBranch(
    val id: String,
    val name: String,
    val address: String,
    val landmark: String? = null,
    val tradeCenterName: String? = null,
    val distanceMeters: Int? = null,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
)

/** Qidiruv qatoridagi takliflar turi (`POST /v1/discounts/suggest`). */
enum class SuggestionKind { CATEGORY, TYPE, BUSINESS, LISTING }

/**
 * Bitta avtoto'ldirish taklifi. `count` — shu taklif beradigan e'lonlar soni (hech qachon 0 emas),
 * kalitlar esa tanlanganda qanday filtr qo'llanishini aytadi.
 */
data class OfferSuggestion(
    val kind: SuggestionKind,
    val label: String,
    val count: Int,
    val typeKey: String? = null,
    val categoryKey: String? = null,
    val businessId: String? = null,
    val listingId: String? = null,
)

/**
 * Filtr ekranining serverdan kelgan sxemasi — `POST /v1/catalog/filter-schema`.
 * Faqat haqiqatda uchraydigan qiymatlar qaytadi, har biri o'z soni bilan; shuning uchun
 * "0 ta natija" beradigan tanlov bo'lmaydi.
 */
data class OfferFilterSchema(
    val types: List<SchemaOption> = emptyList(),
    val categories: List<SchemaCategoryOption> = emptyList(),
    /** Atributlar — filtr maydonlari va ayni paytda tafsilotdagi yorliqlar manbasi. */
    val attributes: List<SchemaAttributeOption> = emptyList(),
    /** `ALL` / `DISCOUNT` / `REGULAR` sonlari. */
    val listingKinds: Map<String, Int> = emptyMap(),
    val priceRange: LongRange? = null,
    val discountPercentRange: IntRange? = null,
    val total: Int = 0,
)

/** Biznes turi tanlovi (soni bilan). */
data class SchemaOption(val key: String, val label: String, val emoji: String?, val count: Int)

/** Tur ichidagi bo'lim (kategoriya) tanlovi. */
data class SchemaCategoryOption(val key: String, val label: String, val typeKey: String, val count: Int)

/**
 * Atribut ta'rifi: `key` — e'lon `attributes` xaritasidagi kalit, `label` — foydalanuvchiga
 * ko'rinadigan nom, `suffix` — qiymat birligi ("daqiqa", "kishi").
 */
data class SchemaAttributeOption(val key: String, val label: String, val suffix: String? = null)
