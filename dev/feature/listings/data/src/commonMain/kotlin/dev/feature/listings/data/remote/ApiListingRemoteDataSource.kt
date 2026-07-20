package dev.feature.listings.data.remote

import dev.core.common.Resource
import dev.core.network.generated.api.ListingsApi
import dev.core.network.generated.api.MediaApi
import dev.core.network.generated.model.CreateListingRequestDto
import dev.core.network.generated.model.DiscountRequestDto
import dev.core.network.generated.model.DiscountTypeDto
import dev.core.network.generated.model.ListingStatusDto
import dev.core.network.generated.model.OptionDto
import dev.core.network.generated.model.OptionGroupDto
import dev.core.network.generated.model.PriceUnitDto
import dev.core.network.generated.model.RedemptionInfoDto
import dev.core.network.generated.model.RedemptionMethodDto
import dev.core.network.generated.model.SelectionTypeDto
import dev.feature.listings.domain.model.Listing
import dev.feature.listings.domain.model.ListingDetails
import dev.feature.listings.domain.model.ListingStatus
import io.ktor.client.request.forms.InputProvider
import kotlinx.datetime.Instant
import kotlinx.io.Buffer

/**
 * Real backend — spetsifikatsiya: `dev/core/network/openapi/student-clubs.json`
 * (`Listings`, `Media` tag'lari), hujjat: `DISCOUNTS_BUSINESS_API.md`.
 *
 * Ikki qadam: `POST /business/{id}/listings` (DRAFT yaratadi) → `POST /listings/{id}/submit`
 * (moderatsiyaga yuboradi). Yakuniy narxni **server** hisoblaydi, shuning uchun bu yerda
 * `finalPrice` yuborilmaydi.
 */
class ApiListingRemoteDataSource(
    private val listingsApi: ListingsApi,
    private val mediaApi: MediaApi,
) : ListingRemoteDataSource {

    override suspend fun publish(listing: Listing): Resource<Listing> {
        // OpenAPI v1 (`student-clubs.json`) faqat chegirma e'lonini biladi: `CreateListingRequestDto`
        // da ijara/xizmat/ish maydonlari yo'q. Spec — API'ning yagona manbasi, shuning uchun
        // bu yerda DTO o'ylab topilmaydi; spec kengaytirilgach shu joy to'ldiriladi.
        // Bugungi kunda bu turlar local rejimda ishlaydi (LocalListingRemoteDataSource).
        val discount = listing.discountDetails
            ?: return Resource.Error(
                "\"${listing.kind.label}\" e'lonini serverga yuborish hali qo'llab-quvvatlanmaydi",
            )

        // Backend e'lonni biznesga bog'laydi. Biznes profili hali yaratilmagan bo'lsa,
        // e'lonni jo'natishning ma'nosi yo'q — foydalanuvchini avval biznes ochishga yo'naltiramiz.
        val businessId = listing.businessId
            ?: return Resource.Error("Avval biznes profilini yarating")

        return try {
            val created = listingsApi.createListing(businessId, listing.toCreateRequest(discount)).body()
            val submitted = listingsApi.submitListing(created.id).body()
            Resource.Success(
                listing.copy(
                    id = submitted.id,
                    businessId = submitted.businessId,
                    status = submitted.status.toDomain(),
                    rejectionReason = submitted.rejectionReason,
                ),
            )
        } catch (e: Exception) {
            Resource.Error(e.message ?: "E'lonni yuborib bo'lmadi", e)
        }
    }

    override suspend fun uploadImage(bytes: ByteArray, fileName: String): Resource<String> = try {
        val part = InputProvider(bytes.size.toLong()) { Buffer().apply { write(bytes) } }
        Resource.Success(mediaApi.uploadMedia(part, purpose = "LISTING").body().url)
    } catch (e: Exception) {
        Resource.Error(e.message ?: "Rasmni yuklab bo'lmadi", e)
    }
}

private fun Listing.toCreateRequest(discount: ListingDetails.Discount) = CreateListingRequestDto(
    // Backendda filial alohida obyekt (`POST /business/{id}/branches`) va e'lon unga
    // `branchIds` orqali bog'lanadi. Bu yerda id'lar yuboriladi; filiallarning o'zi
    // (lat/lng bilan) biznes profili yaratilganda sinxronlanadi.
    branchIds = branches.map { it.id },
    categoryKey = discount.categoryKey,
    title = title,
    images = images,
    priceUnit = PriceUnitDto.entries.first { it.value == priceUnit.name },
    originalPrice = price,
    // `finalPrice` YO'Q — uni server hisoblaydi (spec §3.5).
    discount = DiscountRequestDto(
        type = DiscountTypeDto.entries.first { it.value == discount.discountType.name },
        value = discount.discountValue,
        conditions = discount.conditions,
        appliesToOptions = discount.appliesToOptions,
    ),
    redemption = RedemptionInfoDto(
        method = RedemptionMethodDto.entries.first { it.value == discount.redemption.method.name },
        promoCode = discount.redemption.promoCode,
        perUserLimit = discount.redemption.perUserLimit,
        totalLimit = discount.redemption.totalLimit,
    ),
    validFrom = Instant.fromEpochMilliseconds(validFrom),
    validTo = Instant.fromEpochMilliseconds(validTo),
    customCategoryName = discount.customCategoryName,
    description = description,
    currency = currency,
    attributes = attributes,
    optionGroups = optionGroups.map { group ->
        OptionGroupDto(
            name = group.name,
            selectionType = SelectionTypeDto.entries.first { it.value == group.selectionType.name },
            options = group.options.map { OptionDto(name = it.name, priceDelta = it.priceDelta, isAvailable = it.isAvailable) },
            isRequired = group.isRequired,
        )
    },
)

private fun ListingStatusDto.toDomain(): ListingStatus =
    ListingStatus.entries.firstOrNull { it.name == value } ?: ListingStatus.PENDING_REVIEW
