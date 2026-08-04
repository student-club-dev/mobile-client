package dev.feature.listings.data.mapper

import dev.core.network.generated.model.StudentListingDto
import dev.feature.listings.domain.model.EmploymentType
import dev.feature.listings.domain.model.ExperienceLevel
import dev.feature.listings.domain.model.Listing
import dev.feature.listings.domain.model.ListingBranch
import dev.feature.listings.domain.model.ListingDetails
import dev.feature.listings.domain.model.ListingIds
import dev.feature.listings.domain.model.ListingStatus
import dev.feature.listings.domain.model.PayPeriod
import dev.feature.listings.domain.model.PriceUnit
import dev.feature.listings.domain.model.PropertyType
import dev.feature.listings.domain.model.TaskCategory
import dev.feature.listings.domain.model.TaskFormat
import dev.feature.listings.domain.model.TenantGender
import dev.feature.listings.domain.model.WeekDay
import dev.feature.listings.domain.model.WorkSchedule
import dev.feature.listings.domain.model.WorkShift
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `details` — server bilan kelishuvning eng nozik joyi: u generatorda tiplanmaydi
 * (`oneOf`), ya'ni kalit noto'g'ri yozilsa **kompilyator hech narsa demaydi** va e'lon
 * jimgina bo'sh tafsilot bilan ketadi.
 *
 * Shu sabab bu yerda ikki narsa tekshiriladi: sim ustidagi shakl `STUDENT_LISTINGS_BACKEND.md`
 * §4 ga aynan mos kelishi va aylanma (domen → sim → domen) yo'qotishsiz o'tishi.
 */
class StudentListingApiMapperTest {

    private val createdAt = Instant.parse("2026-08-01T09:00:00Z")
    private val deadline = Instant.parse("2026-08-14T18:00:00Z")

    // -----------------------------------------------------------------------
    // Domen → sim
    // -----------------------------------------------------------------------

    @Test
    fun `topshiriq details spec shaklida ketadi`() {
        val details = ListingDetails.Task(
            category = TaskCategory.WRITTEN,
            typeKey = "REFERAT",
            deadline = deadline.toEpochMilliseconds(),
            format = TaskFormat.ONLINE,
            volume = "20 bet",
        )

        val wire = listing(details).toCreateDto(submit = true)!!.details

        assertEquals("TASK", wire.str("kind"))
        assertEquals("WRITTEN", wire.str("category"))
        assertEquals("REFERAT", wire.str("typeKey"))
        // Sana ISO-8601, epoch millis EMAS — local ustundan aynan shu bilan farq qiladi.
        assertEquals("2026-08-14T18:00:00Z", wire.str("deadline"))
        assertEquals("20 bet", wire.str("volume"))
    }

    @Test
    fun `ish grafigi ichma-ich schedule obyektida ketadi`() {
        val details = ListingDetails.Job(
            employment = EmploymentType.PERMANENT,
            categoryKey = "COURIER",
            companyName = "Express",
            shift = WorkShift.MORNING,
            schedule = WorkSchedule(
                days = listOf(WeekDay.MONDAY, WeekDay.TUESDAY),
                startTime = "08:00",
                endTime = "17:00",
                hoursPerDay = 8,
            ),
            payPeriod = PayPeriod.MONTHLY,
            vacancies = 3,
        )

        val wire = listing(details).toCreateDto(submit = true)!!.details
        val schedule = wire["schedule"]!!.jsonObject

        assertEquals("08:00", schedule.str("startTime"))
        assertEquals("17:00", schedule.str("endTime"))
        assertEquals("8", schedule["hoursPerDay"]!!.jsonPrimitive.content)
        assertEquals(listOf("MONDAY", "TUESDAY"), schedule["days"]!!.toString().parseNames())
        // Yassi kalitlar local ustunning shakli — serverga ular ketmasligi kerak.
        assertNull(wire["scheduleDays"])
        assertNull(wire["startTime"])
    }

    @Test
    fun `chegirma bu API ga umuman yuborilmaydi`() {
        val discount = ListingDetails.Discount(
            businessType = dev.feature.listings.domain.model.BusinessType.CAFE_RESTAURANT,
        )
        assertNull(listing(discount).toCreateDto(submit = true))
        assertNull(listing(discount).toUpdateDto())
    }

    @Test
    fun `tahrirlashda kind yuborilmaydi`() {
        // `kind` o'zgarmas: yuborilsa server `409 LISTING_KIND_IMMUTABLE` beradi.
        assertNull(listing(ListingDetails.Rental()).toUpdateDto()!!.kind)
    }

    // -----------------------------------------------------------------------
    // Sim → domen
    // -----------------------------------------------------------------------

    @Test
    fun `ijara aylanma yo'qotishsiz o'tadi`() {
        val details = ListingDetails.Rental(
            propertyType = PropertyType.APARTMENT,
            roomCount = 3,
            currentTenants = 2,
            neededTenants = 2,
            gender = TenantGender.MALE,
            utilitiesIncluded = true,
            depositMonths = 1,
            floor = 4,
            totalFloors = 9,
            amenities = listOf("WIFI", "NEAR_METRO"),
            availableFrom = Instant.parse("2026-08-15T00:00:00Z").toEpochMilliseconds(),
        )

        val wire = listing(details).toCreateDto(submit = true)!!.details
        val back = dto(StudentListingDto.Kind.RENTAL, wire).toDomain(ORIGIN).details

        assertEquals(details, back)
    }

    @Test
    fun `noma'lum tafsilot e'lonni yiqitmaydi`() {
        // Server yangi maydon qo'shsa yoki `kind` noto'g'ri kelsa — e'lon baribir
        // ro'yxatda qoladi, faqat turga xos qismi bo'sh bo'ladi.
        val broken = JsonObject(emptyMap())
        val listing = dto(StudentListingDto.Kind.SERVICE, broken).toDomain(ORIGIN)

        assertTrue(listing.details is ListingDetails.Service)
        assertEquals("Chilonzorda sherik kerak", listing.title)
    }

    @Test
    fun `muddatsiz e'lon keshda darrov eskirmaydi`() {
        // `validTo` qoralamada bo'lmasligi mumkin. Uni `createdAt` bilan to'ldirish
        // e'lonni local "faol e'lonlar" so'rovidan (validTo >= now) darrov chiqarib
        // yuborardi.
        val listing = dto(StudentListingDto.Kind.TASK, JsonObject(emptyMap())).toDomain(ORIGIN)
        assertTrue(listing.validTo > listing.validFrom)
    }

    @Test
    fun `nisbiy rasm havolasi to'liq holga keltiriladi`() {
        val listing = dto(
            kind = StudentListingDto.Kind.TASK,
            details = JsonObject(emptyMap()),
            images = listOf("/uploads/LISTING/a.jpg"),
        ).toDomain(ORIGIN)

        assertEquals(listOf("$ORIGIN/uploads/LISTING/a.jpg"), listing.images)
    }

    // -----------------------------------------------------------------------
    // Yordamchilar
    // -----------------------------------------------------------------------

    private fun listing(details: ListingDetails) = Listing(
        id = ListingIds.newLocalId("42", createdAt.toEpochMilliseconds()),
        ownerId = "42",
        details = details,
        title = "Chilonzorda sherik kerak",
        priceUnit = PriceUnit.PER_MONTH,
        price = 1_500_000,
        contactPhone = "+998901234567",
        branches = listOf(ListingBranch("br1", 41.2856, 69.2034, "Chilonzor 9-kvartal")),
        validFrom = createdAt.toEpochMilliseconds(),
        validTo = deadline.toEpochMilliseconds(),
        createdAt = createdAt.toEpochMilliseconds(),
        updatedAt = createdAt.toEpochMilliseconds(),
    )

    private fun dto(
        kind: StudentListingDto.Kind,
        details: JsonObject,
        images: List<String> = emptyList(),
    ) = StudentListingDto(
        id = "lst_01H8",
        ownerId = "42",
        kind = kind,
        title = "Chilonzorda sherik kerak",
        images = images,
        price = 1_500_000,
        currency = "UZS",
        isNegotiable = false,
        audience = StudentListingDto.Audience.ALL,
        branches = emptyList(),
        attributes = emptyMap(),
        optionGroups = emptyList(),
        details = details,
        status = StudentListingDto.Status.ACTIVE,
        viewsCount = 0,
        createdAt = createdAt,
        updatedAt = createdAt,
        isMine = false,
        isFavorite = false,
        priceUnit = StudentListingDto.PriceUnit.MONTH,
    )

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.content

    /** `["MONDAY","TUESDAY"]` → ro'yxat. */
    private fun String.parseNames(): List<String> =
        trim('[', ']').split(',').map { it.trim().trim('"') }

    private companion object {
        const val ORIGIN = "https://api.studentclub.uz"
    }
}
