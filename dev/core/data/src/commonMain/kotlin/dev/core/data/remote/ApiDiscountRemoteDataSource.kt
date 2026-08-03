package dev.core.data.remote

import dev.core.common.Resource
import dev.core.data.dto.DiscountCategoryDto
import dev.core.data.dto.DiscountGroupDto
import dev.core.data.dto.DiscountOfferDto
import dev.core.data.dto.DiscountsResponseDto
import dev.core.domain.model.CatalogRules
import dev.core.domain.model.DiscountTag
import dev.core.domain.model.OfferAttribute
import dev.core.domain.model.OfferBranch
import dev.core.domain.model.OfferDetail
import dev.core.domain.model.OfferFilterSchema
import dev.core.domain.model.OfferSuggestion
import dev.core.domain.model.SchemaAttributeOption
import dev.core.domain.model.SchemaCategoryOption
import dev.core.domain.model.SchemaOption
import dev.core.domain.model.SuggestionKind
import dev.core.domain.repository.RegionRepository
import dev.core.network.generated.api.CatalogApi
import dev.core.network.generated.api.DiscountsApi
import dev.core.network.generated.model.CatalogGroupDto
import dev.core.network.generated.model.CatalogGroupsRequestDto
import dev.core.network.generated.model.CatalogTypeDto
import dev.core.network.generated.model.CatalogTypesRequestDto
import dev.core.network.generated.model.DetailBranchDto
import dev.core.network.generated.model.DetailRequestDto
import dev.core.network.generated.model.DiscountCardDto
import dev.core.network.generated.model.FavoriteToggleRequestDto
import dev.core.network.generated.model.FilterSchemaDto
import dev.core.network.generated.model.FilterSchemaRequestDto
import dev.core.network.generated.model.ListingDetailDto
import dev.core.network.generated.model.RedemptionMethodDto
import dev.core.network.generated.model.SearchFilterDto
import dev.core.network.generated.model.SearchGeoDto
import dev.core.network.generated.model.SearchPageDto
import dev.core.network.generated.model.SearchRequestDto
import dev.core.network.generated.model.SearchSortDto
import dev.core.network.generated.model.SuggestRequestDto
import dev.core.network.generated.model.SuggestionDto
import dev.core.network.generated.model.SuggestionKindDto
import dev.core.network.response.safeCall
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * "Siz uchun" bo'limining real backend manbasi. Spetsifikatsiya:
 * `dev/api-client-generator/student-club.json`, klient shundan generatsiya qilingan.
 *
 * Bo'lim uchta chaqiruvdan yig'iladi (ElonUz katalogi — 27 ta biznes turi):
 *
 * 1. `POST /v1/catalog/groups` — 8 ta bosh guruh (Ovqatlanish, Sport, Ta'lim...);
 * 2. `POST /v1/catalog/types` — o'sha guruhlar ichidagi **biznes turlari** = feed'dagi
 *    kategoriya chiplari ([DiscountCategoryDto]);
 * 3. `POST /v1/discounts/search` (`mode = LIST`) — e'lonlar kartalari. `listingKind = ALL`,
 *    ya'ni chegirmali ham, chegirmasiz oddiy e'lon ham keladi — UI ikkisini alohida chizadi.
 *
 * **Guruh doirasi majburiy va bir so'rovda 3 tadan oshmaydi** (spec: `groupKeys` `maxItems: 3`;
 * qidiruvda esa "`filter.groupKeys` yoki `filter.types` shart — 'hammasi' javob emas", Q3).
 * 8 ta guruh borligi uchun har bir "butun katalog" so'rovi 3 talik bo'laklarga bo'linib
 * ([MAX_GROUPS_PER_REQUEST]) parallel yuboriladi va natijalar birlashtiriladi. Guruhsiz yoki
 * 8 kalitli so'rov backend'da 422 bilan qaytadi.
 *
 * Bo'limning qolgan uch chaqiruvi feed'ni to'ldiradi: `discounts/detail` (bitta e'lon to'liq,
 * promo-kod bilan), `discounts/suggest` (qidiruv avtoto'ldirishi) va `catalog/filter-schema`
 * (filtr ekranidagi variantlar va sonlar) — ular ham xuddi shu bo'lak qoidasiga bo'ysunadi.
 */
class ApiDiscountRemoteDataSource(
    private val catalog: CatalogApi,
    private val discounts: DiscountsApi,
    private val regions: RegionRepository,
) : DiscountRemoteDataSource {

    /**
     * Feed geo filtri — tanlangan viloyat. `null` bo'lsa butun mamlakat bo'yicha qidiriladi.
     *
     * `radiusMeters` ATAYLAB `null`: DTO'da uning standart qiymati 5000 va `encodeDefaults`
     * yoqilgani uchun u so'rovga tushib ketardi; lat/lng bo'lmagan holda radius ma'nosiz.
     */
    private fun geoFilter(): SearchGeoDto? = regions.selectedId()?.let {
        SearchGeoDto(regionIds = listOf(it), radiusMeters = null)
    }

    /**
     * Guruh kalitlari va turlar bir marta tortiladi: `filter-schema` `groupKeys` ni majburiy
     * so'raydi, tafsilot esa emoji/rangni turdan oladi. Katalog kunlab o'zgarmaydi, shuning
     * uchun oddiy xotira keshi yetarli — ilova qayta ochilganda yangilanadi.
     */
    private var cachedGroups: List<CatalogGroupDto> = emptyList()
    private val cachedGroupKeys: List<String> get() = cachedGroups.map { it.key }
    private var cachedTypes: Map<String, CatalogTypeDto> = emptyMap()

    /**
     * Atribut ta'riflari (`kalit → yorliq/birlik`) — `filter-schema` javobidan yig'iladi.
     * E'lonning `attributes` xaritasi faqat kalit va qiymatni beradi, o'qiladigan nom esa
     * shu yerda; har bir biznes turi uchun bir marta tortiladi.
     */
    private var cachedAttributes: Map<String, SchemaAttributeOption> = emptyMap()
    private val attributeTypesLoaded = mutableSetOf<String>()

    override suspend fun fetchDiscounts(): Resource<DiscountsResponseDto> = safeCall {
        val types = loadTypes()

        // HAR GURUH ALOHIDA so'raladi. Ilgari guruhlar 3 talik bo'lakda birga so'ralardi va
        // "eng yangi" saralash bo'lakdagi bitta guruhga butun ulushni berib yuborardi: masalan
        // `FOOD + SPORT + GAMES` so'rovidan 20 ta kartaning hammasi Sport bo'lib chiqib,
        // Ovqatlanish va O'yin kartalari umuman kelmasdi — bosh ekranda o'sha bo'limlar bo'sh
        // qolib yashirinardi ("Ovqatlar" ko'rinmasligining sababi shu edi).
        val cards = coroutineScope {
            cachedGroupKeys.map { key ->
                async {
                    discounts.search(
                        SearchRequestDto(
                            mode = SearchRequestDto.Mode.LIST,
                            // Feed hech narsani yashirmaydi: chegirmali + oddiy e'lonlar birga.
                            filter = SearchFilterDto(
                                groupKeys = listOf(key),
                                listingKind = SearchFilterDto.ListingKind.ALL,
                                geo = geoFilter(),
                            ),
                            sort = SearchSortDto(by = SearchSortDto.By.NEWEST),
                            page = SearchPageDto(number = 0, propertySize = PER_GROUP_PAGE_SIZE),
                        ),
                    ).body().items
                }
            }.awaitAll()
        }.flatten()

        DiscountsResponseDto(
            // Bosh ekrandagi bo'limlar — aynan shu guruhlar, server tartibida.
            groups = cachedGroups.map { it.toGroupDto() },
            categories = types.values.map { it.toCategoryDto() },
            // Emoji/rang kartada emas, tur ma'lumotida keladi — shu jadval orqali bog'lanadi.
            // Yashirin turlar (ijara) aralash guruhdan kelib qolishi mumkin — tashlanadi.
            offers = cards.filterNot { CatalogRules.isHidden(it.businessType) }
                .map { it.toOfferDto(types[it.businessType]) }.withFeatured(),
        )
    }

    /**
     * Bitta guruhning e'lonlari (bo'lim ekrani uchun). Umumiy feed'dan farqi — faqat SHU
     * guruh so'raladi va sahifa kattaroq ([GROUP_PAGE_SIZE]).
     */
    override suspend fun fetchGroupOffers(groupKey: String): Resource<List<DiscountOfferDto>> = safeCall {
        val types = cachedTypes.ifEmpty { loadTypes() }
        discounts.search(
            SearchRequestDto(
                mode = SearchRequestDto.Mode.LIST,
                filter = SearchFilterDto(
                    groupKeys = listOf(groupKey),
                    listingKind = SearchFilterDto.ListingKind.ALL,
                    geo = geoFilter(),
                ),
                sort = SearchSortDto(by = SearchSortDto.By.NEWEST),
                page = SearchPageDto(number = 0, propertySize = GROUP_PAGE_SIZE),
            ),
        ).body().items
            .filterNot { CatalogRules.isHidden(it.businessType) }
            .map { it.toOfferDto(types[it.businessType]) }
    }

    override suspend fun setFavorite(listingId: String, saved: Boolean): Resource<Boolean> =
        safeCall { discounts.toggle(FavoriteToggleRequestDto(listingId = listingId, saved = saved)).body().saved }

    override suspend fun fetchDetail(listingId: String): Resource<OfferDetail> = safeCall {
        val types = cachedTypes.ifEmpty { loadTypes() } // emoji/rang shu yerdan
        val dto = discounts.getDetail(DetailRequestDto(listingId = listingId)).body()
        dto.toDomain(types[dto.businessType], attributeLabels(dto.businessType))
    }

    /**
     * Takliflar ham guruh doirasida so'raladi ("Required unless `types` is given", Q3), shuning
     * uchun bo'laklar bo'yicha parallel so'rov ketadi. Yig'ilgan ro'yxat spec tartibida
     * saralanadi: avval turi (CATEGORY → TYPE → BUSINESS → LISTING), keyin e'lonlar soni.
     */
    override suspend fun suggest(query: String): Resource<List<OfferSuggestion>> = safeCall {
        if (cachedGroupKeys.isEmpty()) loadTypes()
        coroutineScope {
            cachedGroupKeys.chunked(MAX_GROUPS_PER_REQUEST).map { keys ->
                async {
                    discounts.suggest(
                        SuggestRequestDto(query = query, groupKeys = keys, limit = SUGGEST_LIMIT),
                    ).body().suggestions
                }
            }.awaitAll()
        }.flatten()
            .map { it.toDomain() }
            // Ijara Takliflarda yo'q — taklif bo'lib ham chiqmasin.
            .filterNot { s -> s.typeKey?.let(CatalogRules::isHidden) == true }
            .sortedWith(compareBy<OfferSuggestion> { it.kind.ordinal }.thenByDescending { it.count })
            .take(SUGGEST_LIMIT)
    }

    override suspend fun fetchFilterSchema(typeKeys: List<String>): Resource<OfferFilterSchema> = safeCall {
        loadSchema(typeKeys)
    }

    /**
     * Guruhlar → turlar. `/catalog/types` bir so'rovda 3 tadan ko'p guruhni qabul qilmagani
     * uchun guruhlar bo'laklab so'raladi; natija `key → tur` jadvali.
     *
     * Ilovada ko'rinmaydigan turlar ([CatalogRules.HIDDEN_TYPES] — ijara) shu yerda tushib
     * qoladi: butun guruh o'shalardan iborat bo'lsa guruh ham so'ralmaydi, ya'ni ularning
     * e'lonlari umuman tortilmaydi.
     */
    private suspend fun loadTypes(): Map<String, CatalogTypeDto> {
        val groups = catalog.getGroups(CatalogGroupsRequestDto()).body()
            .filter { g -> g.types.any { !CatalogRules.isHidden(it) } }
            .sortedBy { it.sortOrder }
        val types = coroutineScope {
            groups.map { it.key }.chunked(MAX_GROUPS_PER_REQUEST).map { keys ->
                async { catalog.getTypes(CatalogTypesRequestDto(groupKeys = keys)).body() }
            }.awaitAll()
        }.flatten()
        cachedGroups = groups
        cachedTypes = types.filterNot { CatalogRules.isHidden(it.key) }.associateBy { it.key }
        return cachedTypes
    }

    /**
     * Sxemani tortadi va yo'l-yo'lakay atribut ta'riflarini keshga qo'shadi.
     *
     * Tur tanlangan bo'lsa — bitta so'rov, o'zining guruhi bilan (spec: `types` dagi har bir
     * qiymat `groupKeys` ichidagi guruhga tegishli bo'lishi shart). Tanlanmagan bo'lsa — butun
     * katalog 3 talik bo'laklarda so'raladi va javoblar birlashtiriladi.
     */
    private suspend fun loadSchema(typeKeys: List<String>): OfferFilterSchema {
        val types = cachedTypes.ifEmpty { loadTypes() }
        val selected = typeKeys.mapNotNull { types[it] }
        val requests: List<Pair<List<String>, List<String>?>> = if (selected.isEmpty()) {
            cachedGroupKeys.chunked(MAX_GROUPS_PER_REQUEST).map { it to null }
        } else {
            selected.groupBy { it.groupKey }.entries.chunked(MAX_GROUPS_PER_REQUEST)
                .map { part -> part.map { it.key } to part.flatMap { it.value.map { t -> t.key } } }
        }

        val merged = coroutineScope {
            requests.map { (groups, narrowTypes) ->
                async {
                    catalog.getFilterSchema(
                        FilterSchemaRequestDto(groupKeys = groups, types = narrowTypes),
                    ).body().toDomain()
                }
            }.awaitAll()
        }.merge()

        cachedAttributes = cachedAttributes + merged.attributes.associateBy { it.key }
        // Butun katalog so'ralganda hamma turning atributlari keldi.
        attributeTypesLoaded += if (selected.isEmpty()) types.keys else typeKeys
        return merged
    }

    /**
     * Berilgan biznes turi uchun atribut yorliqlari. Tur hali tortilmagan bo'lsa — sxema bir
     * marta so'raladi; xato bo'lsa tafsilot yiqilmasin uchun bor kesh qaytadi (yorliqsiz
     * kalitlar chiroyli ko'rinishga keltiriladi).
     */
    private suspend fun attributeLabels(typeKey: String): Map<String, SchemaAttributeOption> {
        if (typeKey !in attributeTypesLoaded) {
            runCatching { loadSchema(listOf(typeKey)) }
        }
        return cachedAttributes
    }
}

/**
 * Bitta so'rovga sig'adigan guruhlar soni (spec: `groupKeys` `maxItems: 3`). 8 ta guruh shu
 * bo'yicha bo'laklanadi — aks holda backend 422 qaytaradi.
 */
private const val MAX_GROUPS_PER_REQUEST = 3

/**
 * Bitta guruhdan tortiladigan e'lonlar soni — Home + "Chegirmalar" ekrani shundan ishlaydi.
 * Kafolat: har bo'limga o'z ulushi tegadi (8 guruh × shu son). Bosh ekran bo'limi baribir
 * 8 tadan ortig'ini ko'rsatmaydi, qolgani "Chegirmalar" ekranida qoladi.
 */
private const val PER_GROUP_PAGE_SIZE = 12

/**
 * Bitta BO'LIM ekrani ochilganda o'sha guruhdan tortiladigan e'lonlar soni.
 *
 * Bosh ekran uchun 12 ta yetarli, lekin "Ovqatlanish" ekranida ro'yxat ham, xarita ham
 * shu keshdan ishlaydi — 12 ta bilan chegaralansa e'lonlar yo'qolib qolgandek ko'rinardi.
 *
 * 50 — SERVER CHEGARASI: kattaroq so'rov `422 PAGE_SIZE_EXCEEDED` bilan qaytadi
 * ("Bir sahifada ko'pi bilan 50 ta e'lon"), ya'ni bo'lim umuman yangilanmay qolardi.
 */
private const val GROUP_PAGE_SIZE = 50

/** Qidiruv qatorida ko'rsatiladigan takliflar soni (spec chegarasi — 20). */
private const val SUGGEST_LIMIT = 8

private const val DEFAULT_ACCENT = 0xFF6C47FFL
private const val DEFAULT_EMOJI = "🎁"

/** Kartadagi jins atributi (`catalog-seed` dagi `_gender` kaliti) — asosan kiyim uchun. */
private const val GENDER_KEY = "_gender"

private fun CatalogGroupDto.toGroupDto() = DiscountGroupDto(
    key = key,
    name = nameUz,
    emoji = emoji ?: DEFAULT_EMOJI,
    accent = accentColor.toAccent(),
    sortOrder = sortOrder.toInt(),
)

private fun CatalogTypeDto.toCategoryDto() = DiscountCategoryDto(
    id = key,
    name = nameUz,
    emoji = emoji ?: DEFAULT_EMOJI,
    offerCount = listingsCount,
    accent = accentColor.toAccent(),
    groupKey = groupKey,
)

private fun DiscountCardDto.toOfferDto(type: CatalogTypeDto?) = DiscountOfferDto(
    id = id,
    // Feed kategoriyasi = biznes turi, shuning uchun `DiscountCategoryDto.id` bilan bir xil kalit.
    categoryId = businessType,
    // Bosh ekrandagi bo'lim. Kartada guruh yo'q — u biznes turi orqali aniqlanadi.
    groupKey = type?.groupKey.orEmpty(),
    subcategory = categoryLabel,
    gender = attributes[GENDER_KEY].orEmpty(),
    merchant = businessName,
    title = title,
    isDiscount = isDiscount,
    discountPercent = percentOff(),
    originalPrice = originalPrice.toLong(),
    finalPrice = finalPrice.toLong(),
    priceUnit = priceUnit.toPriceUnitLabel(),
    tag = if (redemptionMethod == "PROMO_CODE") "PROMO_CODE" else "STUDENT_ID",
    // Promo-kodning o'zi faqat batafsil ekranda beriladi (`/v1/discounts/detail`, D5).
    promoCode = null,
    location = nearestBranch?.name ?: branchesCount.takeIf { it > 0 }?.let { "$it filial" },
    expiry = validTo.take(10), // "2026-08-01T18:59:59.000Z" → "2026-08-01"
    emoji = type?.emoji ?: DEFAULT_EMOJI,
    bannerAccent = type?.accentColor.toAccent(),
    lat = nearestBranch?.lat ?: 0.0,
    lng = nearestBranch?.lng ?: 0.0,
    // Karta rasmi; yo'q bo'lsa biznes logotipi (Home kartasida ikkalasi ham bir xil joyni oladi).
    imageUrl = imageUrl?.takeIf { it.isNotBlank() } ?: businessLogoUrl?.takeIf { it.isNotBlank() },
    saved = isFavorite,
)

/**
 * Chegirma foizi. `PERCENT` da server bergan qiymat; `FIXED_AMOUNT`/`SPECIAL_PRICE` da esa
 * narxlardan hisoblanadi (UI hamma turda bir xil "−N%" nishonini chizadi).
 */
private fun DiscountCardDto.percentOff(): Int {
    val off = discount
    return when {
        !isDiscount || originalPrice <= 0 -> 0
        off?.type == "PERCENT" -> off.value
        else -> ((originalPrice - finalPrice).toLong() * 100 / originalPrice).toInt().coerceIn(0, 100)
    }
}

/**
 * Home'dagi katta promo karta uchun bitta e'lon tanlanadi — eng katta chegirmali.
 * Backend'da "featured" tushunchasi yo'q, shuning uchun tanlov klientda.
 */
private fun List<DiscountOfferDto>.withFeatured(): List<DiscountOfferDto> {
    val top = filter { it.isDiscount }.maxByOrNull { it.discountPercent } ?: return this
    return map { if (it.id == top.id) it.copy(featured = true) else it }
}

/** `#7C5CFF` → `0xFF7C5CFF`. Noto'g'ri/bo'sh qiymatda — mavzuning asosiy rangi. */
private fun String?.toAccent(): Long {
    val hex = this?.removePrefix("#")?.takeIf { it.length == 6 } ?: return DEFAULT_ACCENT
    val rgb = hex.toLongOrNull(radix = 16) ?: return DEFAULT_ACCENT
    return 0xFF000000L or rgb
}

// ---------------------------------------------------------------------------
// Tafsilot / takliflar / filtr sxemasi
// ---------------------------------------------------------------------------

private fun ListingDetailDto.toDomain(
    type: CatalogTypeDto?,
    attributeDefs: Map<String, SchemaAttributeOption>,
) = OfferDetail(
    id = id,
    categoryId = businessType,
    subcategory = categoryLabel,
    merchant = businessName,
    title = title,
    description = description,
    emoji = type?.emoji ?: DEFAULT_EMOJI,
    bannerAccent = type?.accentColor.toAccent(),
    isDiscount = isDiscount,
    discountPercent = percentOff(),
    discountBadge = discount?.badge,
    conditions = discount?.conditions,
    originalPrice = originalPrice.toLong(),
    finalPrice = finalPrice.toLong(),
    savedAmount = savedAmount?.toLong() ?: 0L,
    priceUnit = priceUnit.toPriceUnitLabel(),
    tag = if (redemption.method == RedemptionMethodDto.PROMO_CODE) DiscountTag.PROMO_CODE else DiscountTag.STUDENT_ID,
    promoCode = redemption.promoCode,
    redemptionUrl = redemption.url,
    perUserLimit = redemption.perUserLimit,
    remainingForUser = redemption.remainingForUser,
    validFrom = validFrom.take(10),
    validTo = validTo.take(10),
    imagesCount = imagesCount,
    viewsCount = viewsCount,
    saved = isFavorite,
    attributes = attributes.toOfferAttributes(attributeDefs),
    branches = branches.map { it.toDomain() },
    businessPhone = business.phone,
    businessRating = business.rating,
    telegram = business.contacts?.telegram,
    instagram = business.contacts?.instagram,
    website = business.contacts?.website,
)

/** [DiscountCardDto.percentOff] ning tafsilot uchun aynan nusxasi (DTO'lar umumiy ota-tipsiz). */
private fun ListingDetailDto.percentOff(): Int {
    val off = discount
    return when {
        !isDiscount || originalPrice <= 0 -> 0
        off?.type == "PERCENT" -> off.value
        else -> ((originalPrice - finalPrice).toLong() * 100 / originalPrice).toInt().coerceIn(0, 100)
    }
}

private fun DetailBranchDto.toDomain() = OfferBranch(
    id = branchId,
    name = name,
    address = address,
    landmark = landmark,
    tradeCenterName = tradeCenter?.name,
    distanceMeters = distanceMeters,
    lat = lat,
    lng = lng,
)

/**
 * Ichki kalitlar (`_regular`, `_gender`, `_phone`) foydalanuvchiga ko'rsatilmaydi. Qolganlarining
 * nomi va birligi `filter-schema` dagi atribut ta'riflaridan olinadi (`duration` → "Davomiyligi",
 * `40` → "40 daqiqa"); ta'rif topilmasa kalitning o'zi o'qiladigan ko'rinishga keltiriladi.
 *
 * Tartib ham sxemadagidek — biznes o'z formasida qanday ketma-ketlikda ko'rgan bo'lsa, talaba ham
 * shunday ko'radi; sxemada yo'q kalitlar oxiriga tushadi.
 */
private fun Map<String, String>.toOfferAttributes(
    defs: Map<String, SchemaAttributeOption>,
): List<OfferAttribute> {
    val order = defs.keys.toList()
    return entries
        .filterNot { it.key.startsWith("_") }
        .sortedBy { order.indexOf(it.key).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }
        .map { (key, value) ->
            val def = defs[key]
            OfferAttribute(
                label = def?.label ?: key.prettifyKey(),
                value = value.formatAttributeValue(def?.suffix),
            )
        }
}

/**
 * MULTI_SELECT qiymatlari vergul bilan saqlanadi (`"S,M,L"`) — bo'shliq bilan ajratamiz.
 * Birlik faqat bitta (sonli) qiymatga qo'shiladi: "40" + "daqiqa" → "40 daqiqa".
 */
private fun String.formatAttributeValue(suffix: String?): String {
    val parts = split(",").map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size > 1) return parts.joinToString(", ")
    val value = parts.firstOrNull() ?: this
    return if (suffix.isNullOrBlank()) value else "$value $suffix"
}

private fun String.prettifyKey(): String = replace('_', ' ').lowercase()
    .replaceFirstChar { it.uppercaseChar() }

private fun SuggestionDto.toDomain() = OfferSuggestion(
    kind = when (kind) {
        SuggestionKindDto.CATEGORY -> SuggestionKind.CATEGORY
        SuggestionKindDto.TYPE -> SuggestionKind.TYPE
        SuggestionKindDto.BUSINESS -> SuggestionKind.BUSINESS
        SuggestionKindDto.LISTING -> SuggestionKind.LISTING
    },
    label = label,
    count = count,
    typeKey = typeKey,
    categoryKey = categoryKey,
    businessId = businessId,
    listingId = listingId,
)

/**
 * Bo'laklab olingan sxemalarni bittaga yig'adi: ro'yxatlar birlashtiriladi (takrorlanmasdan),
 * sonlar qo'shiladi, oraliqlar esa eng keng chegaralarni oladi.
 */
private fun List<OfferFilterSchema>.merge(): OfferFilterSchema {
    if (size == 1) return first()
    val prices = mapNotNull { it.priceRange }
    val percents = mapNotNull { it.discountPercentRange }
    return OfferFilterSchema(
        types = flatMap { it.types }.distinctBy { it.key },
        // Kategoriya kaliti turlar bo'ylab takrorlanishi mumkin (masalan OTHER).
        categories = flatMap { it.categories }.distinctBy { it.typeKey to it.key },
        attributes = flatMap { it.attributes }.distinctBy { it.key },
        listingKinds = flatMap { it.listingKinds.entries }
            .groupingBy { it.key }.fold(0) { acc, e -> acc + e.value },
        priceRange = prices.takeIf { it.isNotEmpty() }
            ?.let { r -> r.minOf { it.first }..r.maxOf { it.last } },
        discountPercentRange = percents.takeIf { it.isNotEmpty() }
            ?.let { r -> r.minOf { it.first }..r.maxOf { it.last } },
        total = sumOf { it.total },
    )
}

private fun FilterSchemaDto.toDomain() = OfferFilterSchema(
    types = types.map { SchemaOption(it.key, it.nameUz, it.emoji, it.listingsCount) },
    categories = categories.map { SchemaCategoryOption(it.key, it.label, it.typeKey, it.count) },
    attributes = attributes.map { SchemaAttributeOption(it.key, it.label, it.suffix) },
    listingKinds = listingKind.associate { it.key to it.count },
    priceRange = priceRange?.let { it.min.toLong()..it.max.toLong() },
    discountPercentRange = discountPercentRange?.let { it.min..it.max },
    total = total,
)

/** `PriceUnitDto` kalitlari kartada matn sifatida keladi; UI o'zbekcha qisqa nomni ko'rsatadi. */
private fun String.toPriceUnitLabel(): String = when (this) {
    "PER_ITEM" -> "dona"
    "PER_HOUR" -> "soat"
    "PER_KG" -> "kg"
    "PER_MONTH" -> "oy"
    "PER_COURSE" -> "kurs"
    "PER_LESSON" -> "dars"
    "PER_TICKET" -> "chipta"
    "PER_PERSON" -> "kishi"
    "PER_SESSION" -> "seans"
    else -> "dona"
}
