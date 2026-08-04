package dev.feature.listings.data.mapper

import dev.core.database.sql.ListingEntity
import dev.feature.listings.domain.model.ListingDetails
import dev.feature.listings.domain.model.ListingKind
import dev.feature.listings.domain.model.PropertyType
import dev.feature.listings.domain.model.TenantGender
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `LocalDataSeeder.seedRentals()` qo'lda yozadigan `detailsJson` shu mapper bilan o'qiladi.
 * Seed core:data da, mapper esa shu yerda — ular orasida kompilyator bog'lanishi yo'q,
 * shuning uchun shakl testda mahkamlanadi: JSON buzilsa e'lon jimgina bo'sh chegirmaga
 * aylanib qolardi (`decodeDetails` fallback'i).
 */
class RentalSeedJsonTest {

    private val seedDetailsJson =
        """{"kind":"RENTAL","propertyType":"APARTMENT","roomCount":2,"currentTenants":1,""" +
            """"neededTenants":1,"gender":"MALE","period":"MONTHLY","utilitiesIncluded":false,""" +
            """"depositMonths":1,"floor":4,"totalFloors":9,""" +
            """"amenities":["WIFI","FURNITURE","WASHER","NEAR_METRO"]}"""

    private val seedBranchesJson =
        """[{"id":"br-chilonzor","lat":41.2758,"lng":69.2035,""" +
            """"address":"Chilonzor, 11-kvartal","name":"Chilonzor"}]"""

    @Test
    fun `seed rental json maps to rental details`() {
        val listing = entity(seedDetailsJson, seedBranchesJson).toDomain()

        assertEquals(ListingKind.RENTAL, listing.kind)
        val rental = listing.details as ListingDetails.Rental
        assertEquals(PropertyType.APARTMENT, rental.propertyType)
        assertEquals(TenantGender.MALE, rental.gender)
        assertEquals(2, rental.roomCount)
        assertEquals(1, rental.neededTenants)
        assertTrue("NEAR_METRO" in rental.amenities)

        val branch = listing.branches.single()
        assertEquals("Chilonzor, 11-kvartal", branch.address)
        assertEquals(41.2758, branch.lat)
    }

    private fun entity(details: String, branches: String) = ListingEntity(
        id = "rent-chilonzor",
        ownerId = "seed-user",
        businessId = null,
        kind = "RENTAL",
        detailsJson = details,
        title = "Chilonzorda 2 xonali kvartira",
        description = null,
        imagesJson = "[]",
        priceUnit = "PER_MONTH",
        price = 1_200_000,
        priceMax = null,
        currency = "UZS",
        isNegotiable = 0,
        finalPrice = 1_200_000,
        contactPhone = null,
        universityId = null,
        audience = "ALL",
        branchesJson = branches,
        validFrom = 0,
        validTo = 4_102_444_800_000L,
        attributesJson = "{}",
        optionGroupsJson = "[]",
        status = "ACTIVE",
        rejectionReason = null,
        viewsCount = 0,
        createdAt = 0,
        updatedAt = 0,
    )
}
