package dev.feature.listings.domain

import dev.feature.listings.domain.model.EmploymentType
import dev.feature.listings.domain.model.ExperienceLevel
import dev.feature.listings.domain.model.Listing
import dev.feature.listings.domain.model.ListingBranch
import dev.feature.listings.domain.model.ListingDetails
import dev.feature.listings.domain.model.ListingFilters
import dev.feature.listings.domain.model.ListingKind
import dev.feature.listings.domain.model.PriceUnit
import dev.feature.listings.domain.model.PropertyType
import dev.feature.listings.domain.model.ServiceFormat
import dev.feature.listings.domain.model.ServiceType
import dev.feature.listings.domain.model.TenantGender
import dev.feature.listings.domain.model.WorkShift
import dev.feature.listings.domain.model.filterBy
import dev.feature.listings.domain.model.matchesQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Talaba tomonidagi ro'yxat filtrlari — `ListingFilters`. */
class ListingFiltersTest {

    private fun listing(
        details: ListingDetails,
        price: Long = 2_000_000,
        title: String = "E'lon",
        negotiable: Boolean = false,
        branches: List<ListingBranch> = emptyList(),
    ) = Listing(
        id = "l-${title.hashCode()}",
        ownerId = "u1",
        details = details,
        title = title,
        priceUnit = PriceUnit.PER_MONTH,
        price = price,
        isNegotiable = negotiable,
        branches = branches,
        validFrom = 0,
        validTo = 1,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun rental(
        gender: TenantGender? = TenantGender.MALE,
        rooms: Int? = 3,
        needed: Int? = 2,
        type: PropertyType? = PropertyType.APARTMENT,
    ) = ListingDetails.Rental(
        propertyType = type,
        roomCount = rooms,
        currentTenants = 1,
        neededTenants = needed,
        gender = gender,
    )

    // -----------------------------------------------------------------------
    // Ijara
    // -----------------------------------------------------------------------

    @Test
    fun `jins bo'yicha filtr mos kelmaganini chiqarib tashlaydi`() {
        val filters = ListingFilters(gender = TenantGender.FEMALE)
        assertFalse(filters.matches(listing(rental(gender = TenantGender.MALE))))
        assertTrue(filters.matches(listing(rental(gender = TenantGender.FEMALE))))
    }

    @Test
    fun `farqi yo'q e'loni har qanday jins filtriga tushadi`() {
        // "Qizlar uchun" izlayotgan talabaga "farqi yo'q" e'loni ham to'g'ri keladi —
        // aks holda eng ochiq e'lonlar ro'yxatdan tushib qolardi.
        val listing = listing(rental(gender = TenantGender.ANY))
        assertTrue(ListingFilters(gender = TenantGender.FEMALE).matches(listing))
        assertTrue(ListingFilters(gender = TenantGender.MALE).matches(listing))
    }

    @Test
    fun `kamida N xona filtri kichik uylarni kesadi`() {
        val filters = ListingFilters(minRooms = 3)
        assertTrue(filters.matches(listing(rental(rooms = 3))))
        assertTrue(filters.matches(listing(rental(rooms = 4))))
        assertFalse(filters.matches(listing(rental(rooms = 2))))
    }

    @Test
    fun `bo'sh joyi bor filtri to'lgan uylarni yashiradi`() {
        val filters = ListingFilters(onlyAvailable = true)
        assertTrue(filters.matches(listing(rental(needed = 2))))
        assertFalse(filters.matches(listing(rental(needed = 0))))
    }

    @Test
    fun `turarjoy turi bo'yicha filtrlanadi`() {
        val filters = ListingFilters(propertyType = PropertyType.DORMITORY)
        assertFalse(filters.matches(listing(rental(type = PropertyType.APARTMENT))))
        assertTrue(filters.matches(listing(rental(type = PropertyType.DORMITORY))))
    }

    // -----------------------------------------------------------------------
    // Narx
    // -----------------------------------------------------------------------

    @Test
    fun `narx chegarasi qimmat e'lonni kesadi`() {
        val filters = ListingFilters(maxPrice = 2_000_000)
        assertTrue(filters.matches(listing(rental(), price = 1_500_000)))
        assertTrue(filters.matches(listing(rental(), price = 2_000_000)))
        assertFalse(filters.matches(listing(rental(), price = 3_000_000)))
    }

    @Test
    fun `kelishilgan narxli e'lon narx chegarasidan tushmaydi`() {
        // Uning summasi yo'q (0), lekin talaba uchun baribir mos bo'lishi mumkin.
        val filters = ListingFilters(maxPrice = 1_000_000)
        val negotiable = listing(rental(), price = 9_000_000, negotiable = true)
        assertTrue(filters.matches(negotiable))
    }

    // -----------------------------------------------------------------------
    // Xizmat
    // -----------------------------------------------------------------------

    @Test
    fun `aralash formatdagi xizmat ikkala filtrga ham tushadi`() {
        val hybrid = listing(
            ListingDetails.Service(serviceType = ServiceType.TUTOR, format = ServiceFormat.HYBRID),
        )
        assertTrue(ListingFilters(serviceFormat = ServiceFormat.ONLINE).matches(hybrid))
        assertTrue(ListingFilters(serviceFormat = ServiceFormat.OFFLINE).matches(hybrid))

        val onlineOnly = listing(
            ListingDetails.Service(serviceType = ServiceType.TUTOR, format = ServiceFormat.ONLINE),
        )
        assertFalse(ListingFilters(serviceFormat = ServiceFormat.OFFLINE).matches(onlineOnly))
    }

    @Test
    fun `xizmat sohasi bo'yicha filtrlanadi`() {
        val tutor = listing(ListingDetails.Service(serviceType = ServiceType.TUTOR))
        assertTrue(ListingFilters(serviceType = ServiceType.TUTOR).matches(tutor))
        assertFalse(ListingFilters(serviceType = ServiceType.PRINTING).matches(tutor))
    }

    // -----------------------------------------------------------------------
    // Ish
    // -----------------------------------------------------------------------

    @Test
    fun `erkin grafik har qanday smena filtriga tushadi`() {
        val flexible = listing(ListingDetails.Job(shift = WorkShift.FLEXIBLE))
        assertTrue(ListingFilters(shift = WorkShift.NIGHT).matches(flexible))
        assertTrue(ListingFilters(shift = WorkShift.MORNING).matches(flexible))

        val nightOnly = listing(ListingDetails.Job(shift = WorkShift.NIGHT))
        assertFalse(ListingFilters(shift = WorkShift.MORNING).matches(nightOnly))
    }

    @Test
    fun `kunlik va doimiy ish ajratiladi`() {
        val daily = listing(ListingDetails.Job(employment = EmploymentType.DAILY))
        assertTrue(ListingFilters(employment = EmploymentType.DAILY).matches(daily))
        assertFalse(ListingFilters(employment = EmploymentType.PERMANENT).matches(daily))
    }

    @Test
    fun `tajribasiz filtri tajriba talab qiladiganlarni kesadi`() {
        val filters = ListingFilters(noExperienceOnly = true)
        assertTrue(filters.matches(listing(ListingDetails.Job(experience = ExperienceLevel.NONE))))
        assertFalse(filters.matches(listing(ListingDetails.Job(experience = ExperienceLevel.ONE_TO_THREE))))
    }

    // -----------------------------------------------------------------------
    // Tur almashishi va sanoq
    // -----------------------------------------------------------------------

    @Test
    fun `tur almashganda boshqa turning filtrlari tushadi`() {
        // Ijarada "faqat qizlar" qo'yib, Ish tab'iga o'tgan talaba ko'rinmas filtr bilan
        // qolmasligi kerak. Narx chegarasi esa har turda bir xil ma'noga ega — saqlanadi.
        val filters = ListingFilters(gender = TenantGender.FEMALE, minRooms = 3, maxPrice = 500_000)
        val reset = filters.resetForKind()

        assertEquals(null, reset.gender)
        assertEquals(null, reset.minRooms)
        assertEquals(500_000L, reset.maxPrice)
    }

    @Test
    fun `faol filtrlar soni faqat o'z turini sanaydi`() {
        val filters = ListingFilters(
            gender = TenantGender.FEMALE,
            minRooms = 3,
            employment = EmploymentType.DAILY,
        )
        assertEquals(2, filters.activeCount(ListingKind.RENTAL))
        assertEquals(1, filters.activeCount(ListingKind.JOB))
        assertEquals(0, filters.activeCount(ListingKind.SERVICE))
    }

    @Test
    fun `ish e'lonida narx chegarasi taklif qilinmaydi`() {
        // Maoshni yuqoridan cheklash talabaga zarar — u ko'proq to'laydiganini qidiradi.
        assertTrue(ListingFilters.priceOptions(ListingKind.JOB).isEmpty())
        assertTrue(ListingFilters.priceOptions(ListingKind.RENTAL).isNotEmpty())
    }

    // -----------------------------------------------------------------------
    // Qidiruv
    // -----------------------------------------------------------------------

    @Test
    fun `qidiruv manzil bo'ylab ham ishlaydi`() {
        // Talaba odatda e'lon nomini emas, tumanni yozadi.
        val withBranch = listing(
            rental(),
            title = "Sherik izlanmoqda",
            branches = listOf(ListingBranch("br1", 41.28, 69.20, "Chilonzor 9-kvartal, 42-uy")),
        )
        assertTrue(withBranch.matchesQuery("chilonzor"))
        assertTrue(withBranch.matchesQuery("Sherik"))
        assertFalse(withBranch.matchesQuery("Yunusobod"))
    }

    @Test
    fun `bo'sh qidiruv hammasini qoldiradi`() {
        val all = listOf(listing(rental()), listing(ListingDetails.Job()))
        assertEquals(2, all.filterBy(ListingFilters(), "   ").size)
    }

    @Test
    fun `ish e'loni talablari bo'yicha topiladi`() {
        val job = listing(
            ListingDetails.Job(companyName = "Korzinka", requirements = listOf("Rus tilini bilish")),
            title = "Kassir kerak",
        )
        assertTrue(job.matchesQuery("korzinka"))
        assertTrue(job.matchesQuery("rus tili"))
    }
}
