package dev.feature.listings.data.mapper

import dev.core.database.sql.ListingEntity
import dev.feature.listings.domain.model.ListingDetails
import dev.feature.listings.domain.model.ListingKind
import dev.feature.listings.domain.model.TaskCategory
import dev.feature.listings.domain.model.TaskFormat
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `LocalDataSeeder.seedTasks()` qo'lda yozadigan `detailsJson` — [RentalSeedJsonTest] dagi
 * bilan bir xil sabab: seed core:data da, mapper esa shu yerda va ular orasida kompilyator
 * bog'lanishi yo'q. Kalit noto'g'ri yozilsa topshiriq jimgina bo'sh chegirmaga aylanib
 * qolardi, ya'ni "Fanlardan yordam" bo'limi bo'sh ko'rinardi.
 *
 * Universitetga bog'lanish ([Listing.universityId]) ham shu yerda tekshiriladi:
 * "Universitetim" ekrani aynan shu maydon bo'yicha filtrlaydi.
 */
class TaskSeedJsonTest {

    private val seedDetailsJson =
        """{"kind":"TASK","category":"EXACT","typeKey":"MATH",""" +
            """"deadline":1797768000000,"format":"ONLINE","volume":"12 ta masala"}"""

    @Test
    fun `seed task json maps to task details`() {
        val listing = entity(seedDetailsJson, universityId = "tatu").toDomain()

        assertEquals(ListingKind.TASK, listing.kind)
        assertEquals("tatu", listing.universityId)

        val task = listing.details as ListingDetails.Task
        assertEquals(TaskCategory.EXACT, task.category)
        assertEquals("MATH", task.typeKey)
        assertEquals(TaskFormat.ONLINE, task.format)
        assertEquals(1_797_768_000_000L, task.deadline)
        // Kartochkadagi qator: "Matematika (…) · 12 ta masala · Onlayn".
        assertEquals("12 ta masala", task.volume)
    }

    private fun entity(details: String, universityId: String?) = ListingEntity(
        id = "task-tatu-algoritm",
        ownerId = "seed-user",
        businessId = null,
        kind = "TASK",
        detailsJson = details,
        title = "Algoritmlar fanidan 12 ta masala",
        description = null,
        imagesJson = "[]",
        priceUnit = "PER_ITEM",
        price = 150_000,
        priceMax = null,
        currency = "UZS",
        isNegotiable = 0,
        finalPrice = 150_000,
        contactPhone = null,
        universityId = universityId,
        branchesJson = "[]",
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
