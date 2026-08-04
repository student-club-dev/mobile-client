package dev.feature.listings.domain

import dev.feature.listings.domain.model.BusinessType
import dev.feature.listings.domain.model.DiscountType
import dev.feature.listings.domain.model.GeoCatalog
import dev.feature.listings.domain.model.Listing
import dev.feature.listings.domain.model.ListingCatalog
import dev.feature.listings.domain.model.ListingDetails
import dev.feature.listings.domain.model.ListingField
import dev.feature.listings.domain.model.Geo
import dev.feature.listings.domain.model.ListingBranch
import dev.feature.listings.domain.model.ListingRedemption
import dev.feature.listings.domain.model.ListingValidator
import dev.feature.listings.domain.model.PriceUnit
import dev.feature.listings.domain.model.RedemptionMethod
import dev.feature.listings.domain.model.formatSum
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Chegirma formulasi va publish shartlari — `DISCOUNTS_BUSINESS_API.md` §3.5 va §6.1. */
class ListingValidatorTest {

    private val branch = ListingBranch(
        id = "br1",
        lat = 41.2856,
        lng = 69.2034,
        address = "Chilonzor 9-kvartal, 42-uy",
        name = "Chilonzor filiali",
    )

    private fun discountDetails(
        type: DiscountType = DiscountType.PERCENT,
        value: Long = 20,
        conditions: String? = null,
    ) = ListingDetails.Discount(
        businessType = BusinessType.CAFE_RESTAURANT,
        businessName = "Chaykhana Navruz",
        categoryKey = "PIZZA",
        discountType = type,
        discountValue = value,
        conditions = conditions,
        redemption = ListingRedemption(RedemptionMethod.QR),
    )

    private fun validListing(
        details: ListingDetails = discountDetails(),
        price: Long = 55_000,
        images: List<String> = listOf("data:image/jpeg;base64,AAA"),
        branches: List<ListingBranch> = listOf(branch),
    ) = Listing(
        id = "lst_1",
        ownerId = "u1",
        details = details,
        title = "Pepperoni pitsa",
        images = images,
        priceUnit = PriceUnit.PER_ITEM,
        price = price,
        contactPhone = "+998901234567",
        branches = branches,
        validFrom = 1_000,
        validTo = 2_000,
        createdAt = 0,
        updatedAt = 0,
    )

    // -----------------------------------------------------------------------
    // Chegirma
    // -----------------------------------------------------------------------

    @Test
    fun `to'g'ri e'lon xatosiz o'tadi`() {
        assertEquals(emptyList(), ListingValidator.validate(validListing()))
    }

    @Test
    fun `foiz chegirma yakuniy narxni to'g'ri hisoblaydi`() {
        // 55 000 dan 20% → 44 000 (spec'dagi misol).
        assertEquals(44_000, discountDetails(DiscountType.PERCENT, 20).finalPrice(55_000))
        assertEquals(45_000, discountDetails(DiscountType.FIXED_AMOUNT, 10_000).finalPrice(55_000))
        assertEquals(40_000, discountDetails(DiscountType.SPECIAL_PRICE, 40_000).finalPrice(55_000))
        // 1+1 — narx o'zgarmaydi.
        assertEquals(55_000, discountDetails(DiscountType.FREE_ITEM, 0).finalPrice(55_000))
    }

    @Test
    fun `chegirmasiz oddiy e'londa narx o'zgarmaydi`() {
        val regular = discountDetails(DiscountType.PERCENT, 20).copy(isDiscounted = false)
        assertEquals(55_000, regular.finalPrice(55_000))
        assertEquals(null, regular.badge())
        // Chegirma maydonlari ham tekshirilmaydi — ular oddiy e'londa ma'nosiz.
        assertEquals(emptyList(), ListingValidator.validate(validListing(details = regular)))
    }

    @Test
    fun `yakuniy narx hech qachon manfiy bo'lmaydi`() {
        assertEquals(0, discountDetails(DiscountType.FIXED_AMOUNT, 99_000).finalPrice(55_000))
    }

    @Test
    fun `90 foizdan yuqori chegirma rad etiladi`() {
        val errors = ListingValidator.validate(
            validListing(details = discountDetails(DiscountType.PERCENT, 95)),
        )
        assertTrue(errors.any { it.field == ListingField.DISCOUNT })
    }

    @Test
    fun `summa chegirmasi narxdan oshib ketolmaydi`() {
        val errors = ListingValidator.validate(
            validListing(details = discountDetails(DiscountType.FIXED_AMOUNT, 60_000)),
        )
        assertTrue(errors.any { it.field == ListingField.DISCOUNT })
    }

    @Test
    fun `1+1 aksiyasi shartsiz o'tmaydi`() {
        val errors = ListingValidator.validate(
            validListing(details = discountDetails(DiscountType.FREE_ITEM, 0, conditions = null)),
        )
        assertTrue(errors.any { it.field == ListingField.DISCOUNT })
    }

    // -----------------------------------------------------------------------
    // Umumiy shartlar
    // -----------------------------------------------------------------------

    @Test
    fun `rasm va kamida bitta manzil majburiy`() {
        val errors = ListingValidator.validate(validListing(images = emptyList(), branches = emptyList()))
        assertTrue(errors.any { it.field == ListingField.IMAGES })
        assertTrue(errors.any { it.field == ListingField.LOCATION })
    }

    @Test
    fun `telefon raqamsiz e'lon o'tmaydi`() {
        val errors = ListingValidator.validate(validListing().copy(contactPhone = null))
        assertTrue(errors.any { it.field == ListingField.CONTACT })
    }

    @Test
    fun `kelishilgan narx summasiz ham o'tadi`() {
        val errors = ListingValidator.validate(validListing().copy(price = 0, isNegotiable = true))
        assertTrue(errors.none { it.field == ListingField.PRICE })
    }

    @Test
    fun `koordinata O'zbekiston hududida bo'lishi kerak`() {
        // Parij — O'zbekistondan tashqarida.
        val errors = ListingValidator.validate(
            validListing(
                branches = listOf(ListingBranch("br1", lat = 48.85, lng = 2.35, address = "Paris")),
            ),
        )
        assertTrue(errors.any { it.field == ListingField.LOCATION })
    }

    @Test
    fun `bir joyda ikkita manzil belgilanmaydi`() {
        // 100 m dan yaqin ikki nuqta — dublikat (spec 6.6).
        val errors = ListingValidator.validate(
            validListing(
                branches = listOf(
                    ListingBranch("br1", 41.2856, 69.2034, "Chilonzor 9-kvartal"),
                    ListingBranch("br2", 41.2857, 69.2035, "Chilonzor 9-kvartal, yonida"),
                ),
            ),
        )
        assertTrue(errors.any { it.field == ListingField.LOCATION })
    }

    @Test
    fun `bir nechta filial qabul qilinadi`() {
        val errors = ListingValidator.validate(
            validListing(
                branches = listOf(
                    ListingBranch("br1", 41.2856, 69.2034, "Chilonzor filiali"),
                    ListingBranch("br2", 41.3260, 69.2280, "Yunusobod filiali"),
                ),
            ),
        )
        assertEquals(emptyList(), errors)
    }

    @Test
    fun `eng yaqin manzil masofasi bilan topiladi`() {
        val listing = validListing(
            branches = listOf(
                // Yunusobod — talabaga uzoqroq
                ListingBranch("br-far", 41.3600, 69.2890, "Yunusobod filiali"),
                // Chilonzor — talabaga yaqin
                ListingBranch("br-near", 41.2856, 69.2034, "Chilonzor filiali"),
            ),
        )

        // Talaba Chilonzor metrosi yonida.
        val nearest = listing.nearestBranch(userLat = 41.2830, userLng = 69.2050)
        assertEquals("br-near", nearest?.branch?.id)
        assertTrue((nearest?.distanceMeters ?: 0.0) < 1000, "Chilonzor filiali 1 km dan yaqin bo'lishi kerak")
        assertEquals("m", nearest?.distanceLabel()?.takeLast(1))

        // Joylashuv noma'lum — masofasiz, lekin ro'yxat baribir ishlaydi.
        val unknown = listing.nearestBranch(null, null)
        assertEquals("br-far", unknown?.branch?.id) // birinchi manzil
        assertEquals(null, unknown?.distanceLabel())
    }

    @Test
    fun `masofa haversine bilan to'g'ri hisoblanadi`() {
        // Chilonzor metrosi -> Mustaqillik maydoni: taxminan 5-6 km.
        val meters = Geo.distanceMeters(41.2755, 69.2044, 41.3111, 69.2797)
        assertTrue(meters in 5_000.0..8_000.0, "kutilgan 5-8 km, olindi: ${meters.toInt()} m")

        // Bir xil nuqta -> 0.
        assertEquals(0.0, Geo.distanceMeters(41.3, 69.2, 41.3, 69.2))
    }

    // -----------------------------------------------------------------------
    // Ijara
    // -----------------------------------------------------------------------

    private fun rental(
        gender: dev.feature.listings.domain.model.TenantGender? =
            dev.feature.listings.domain.model.TenantGender.MALE,
        roomCount: Int? = 3,
        currentTenants: Int? = 2,
        neededTenants: Int? = 2,
    ) = ListingDetails.Rental(
        propertyType = dev.feature.listings.domain.model.PropertyType.APARTMENT,
        roomCount = roomCount,
        currentTenants = currentTenants,
        neededTenants = neededTenants,
        gender = gender,
    )

    @Test
    fun `to'g'ri ijara e'loni xatosiz o'tadi`() {
        assertEquals(emptyList(), ListingValidator.validate(validListing(details = rental())))
    }

    @Test
    fun `ijarada jins tanlanmasa e'lon o'tmaydi`() {
        // Talaba uchun bu birinchi filtr — usiz e'lon foydasiz.
        val errors = ListingValidator.validate(validListing(details = rental(gender = null)))
        assertTrue(errors.any { it.field == ListingField.GENDER }, "jins majburiy bo'lishi kerak")
    }

    @Test
    fun `ijarada xona va kishilar soni majburiy`() {
        val errors = ListingValidator.validate(
            validListing(details = rental(roomCount = null, currentTenants = null, neededTenants = null)),
        )
        assertTrue(errors.any { it.field == ListingField.ROOMS })
        assertTrue(errors.any { it.field == ListingField.TENANTS })
    }

    @Test
    fun `bitta xonaga sig'maydigan odam soni rad etiladi`() {
        // 1 xonaga 20 kishi — deyarli har doim xato kiritish.
        val errors = ListingValidator.validate(
            validListing(details = rental(roomCount = 1, currentTenants = 10, neededTenants = 10)),
        )
        assertTrue(errors.any { it.field == ListingField.TENANTS })
    }

    // -----------------------------------------------------------------------
    // Xizmat
    // -----------------------------------------------------------------------

    @Test
    fun `xizmat sohasi tanlanmasa e'lon o'tmaydi`() {
        val errors = ListingValidator.validate(
            validListing(details = ListingDetails.Service(serviceType = null)),
        )
        assertTrue(errors.any { it.field == ListingField.SERVICE_TYPE })
    }

    @Test
    fun `repetitorda fan va majburiy maydonlar so'raladi`() {
        val errors = ListingValidator.validate(
            validListing(
                details = ListingDetails.Service(
                    serviceType = dev.feature.listings.domain.model.ServiceType.TUTOR,
                ),
            ),
        )
        assertTrue(errors.any { it.field == ListingField.SERVICE_SUBJECT }, "fan tanlanishi kerak")
        // "Daraja" va "Dars shakli" — katalogda `required` deb belgilangan.
        assertTrue(errors.any { it.field == ListingField.ATTRIBUTES })
    }

    @Test
    fun `to'liq to'ldirilgan repetitor e'loni o'tadi`() {
        val details = ListingDetails.Service(
            serviceType = dev.feature.listings.domain.model.ServiceType.TUTOR,
            fields = mapOf(
                dev.feature.listings.domain.model.ServiceCatalog.SUBJECT_KEY to "IELTS",
                "level" to "Talaba",
                "lessonMode" to "Yakka tartibda",
                // IELTS yo'nalishining o'z majburiy maydoni.
                "targetBand" to "7.0",
            ),
        )
        assertEquals(emptyList(), ListingValidator.validate(validListing(details = details)))
    }

    // -----------------------------------------------------------------------
    // Ish
    // -----------------------------------------------------------------------

    private fun job(
        employment: dev.feature.listings.domain.model.EmploymentType =
            dev.feature.listings.domain.model.EmploymentType.DAILY,
        workDate: Long? = 1_700_000_000_000,
        days: List<dev.feature.listings.domain.model.WeekDay> = emptyList(),
    ) = ListingDetails.Job(
        employment = employment,
        categoryKey = "COURIER",
        companyName = "Korzinka",
        shift = dev.feature.listings.domain.model.WorkShift.MORNING,
        schedule = dev.feature.listings.domain.model.WorkSchedule(
            days = days,
            startTime = "08:00",
            endTime = "17:00",
        ),
        vacancies = 3,
        workDate = workDate,
    )

    @Test
    fun `to'g'ri kunlik ish e'loni xatosiz o'tadi`() {
        assertEquals(emptyList(), ListingValidator.validate(validListing(details = job(), images = emptyList())))
    }

    @Test
    fun `ish e'lonida rasm majburiy emas`() {
        // Ish o'rnining surati odatda bo'lmaydi — majburiy qilish e'lon qo'yishga to'siq.
        val errors = ListingValidator.validate(validListing(details = job(), images = emptyList()))
        assertTrue(errors.none { it.field == ListingField.IMAGES })
    }

    @Test
    fun `kunlik ishda sana ko'rsatilmasa o'tmaydi`() {
        val errors = ListingValidator.validate(
            validListing(details = job(workDate = null), images = emptyList()),
        )
        assertTrue(errors.any { it.field == ListingField.JOB_SCHEDULE })
    }

    @Test
    fun `doimiy ishda ish kunlari ko'rsatilmasa o'tmaydi`() {
        val errors = ListingValidator.validate(
            validListing(
                details = job(
                    employment = dev.feature.listings.domain.model.EmploymentType.PERMANENT,
                    workDate = null,
                ),
                images = emptyList(),
            ),
        )
        assertTrue(errors.any { it.field == ListingField.JOB_SCHEDULE })
    }

    @Test
    fun `doimiy ish grafik bilan o'tadi`() {
        val errors = ListingValidator.validate(
            validListing(
                details = job(
                    employment = dev.feature.listings.domain.model.EmploymentType.PERMANENT,
                    workDate = null,
                    days = listOf(
                        dev.feature.listings.domain.model.WeekDay.MONDAY,
                        dev.feature.listings.domain.model.WeekDay.WEDNESDAY,
                    ),
                ),
                images = emptyList(),
            ),
        )
        assertEquals(emptyList(), errors)
    }

    @Test
    fun `erkin grafikda aniq vaqt so'ralmaydi`() {
        val details = job().copy(
            shift = dev.feature.listings.domain.model.WorkShift.FLEXIBLE,
            schedule = dev.feature.listings.domain.model.WorkSchedule(),
        )
        val errors = ListingValidator.validate(validListing(details = details, images = emptyList()))
        assertTrue(errors.none { it.field == ListingField.JOB_SCHEDULE })
    }

    // -----------------------------------------------------------------------
    // Kataloglar
    // -----------------------------------------------------------------------

    @Test
    fun `har bir biznes turida kategoriya va maydonlar bor`() {
        BusinessType.entries.forEach { type ->
            assertTrue(ListingCatalog.categories(type).size > 1, "${type.name}: kategoriya yo'q")
            assertTrue(ListingCatalog.attributes(type).isNotEmpty(), "${type.name}: maydon yo'q")
            assertTrue(ListingCatalog.priceUnits(type).isNotEmpty(), "${type.name}: narx birligi yo'q")
        }
    }

    @Test
    fun `har bir xizmat sohasida maydon va narx birligi bor`() {
        dev.feature.listings.domain.model.ServiceType.entries.forEach { type ->
            assertTrue(
                dev.feature.listings.domain.model.ServiceCatalog.fields(type).isNotEmpty(),
                "${type.name}: maydon yo'q",
            )
            assertTrue(
                dev.feature.listings.domain.model.ServiceCatalog.priceUnits(type).isNotEmpty(),
                "${type.name}: narx birligi yo'q",
            )
            // Yo'nalishi bor deb belgilangan soha bo'sh ro'yxat bilan qolib ketmasin.
            if (type.hasSubjects) {
                assertTrue(
                    dev.feature.listings.domain.model.ServiceCatalog.subjects(type).size > 1,
                    "${type.name}: yo'nalish yo'q",
                )
            }
        }
    }

    @Test
    fun `kunlik ishda smena va to'lov davri chegaralangan`() {
        val daily = dev.feature.listings.domain.model.JobCatalog.shifts(
            dev.feature.listings.domain.model.EmploymentType.DAILY,
        )
        // 2/2 smena — doimiy ish grafigi, kunlik ishda taklif qilinmaydi.
        assertTrue(dev.feature.listings.domain.model.WorkShift.SHIFT_2_2 !in daily)
        assertTrue(dev.feature.listings.domain.model.WorkShift.MORNING in daily)

        val dailyPay = dev.feature.listings.domain.model.JobCatalog.payPeriods(
            dev.feature.listings.domain.model.EmploymentType.DAILY,
        )
        assertEquals(dev.feature.listings.domain.model.PayPeriod.DAILY, dailyPay.first())
    }

    @Test
    fun `geo katalogda 14 viloyat va barqaror id'lar bor`() {
        assertEquals(14, GeoCatalog.regions().size)
        assertEquals("Chilonzor", GeoCatalog.district("TOSHKENT_SHAHRI", "CHILONZOR")?.name)
        // Apostrof id'dan tushib qoladi: "Mirzo Ulug'bek" → MIRZO_ULUGBEK
        assertEquals("Mirzo Ulug'bek", GeoCatalog.district("TOSHKENT_SHAHRI", "MIRZO_ULUGBEK")?.name)
    }

    /**
     * Mo'ljal chegarasi backend bilan bir xil bo'lishi shart
     * (`DISCOUNTS_BUSINESS_API_RESPONSE.md` §2.3): 3 km dan uzoqdagi bekat mo'ljal emas.
     * Bu zaxira (Nominatim) yo'lida hisoblanadi — ikki yo'l bir xil javob berishi kerak.
     */
    @Test
    fun `eng yaqin metro faqat 3 km ichida mo'ljal bo'ladi`() {
        val stations = listOf(
            dev.feature.listings.domain.model.MetroStation(
                id = "CHILONZOR", name = "Chilonzor", line = "CHILONZOR",
                lat = 41.27436, lng = 69.20497,
            ),
            dev.feature.listings.domain.model.MetroStation(
                id = "BUYUK_IPAK_YOLI", name = "Buyuk ipak yo'li", line = "OZBEKISTON",
                lat = 41.32536, lng = 69.33507,
            ),
        )

        // Chilonzor bekatining yonidagi nuqta — eng yaqini o'sha.
        assertEquals("CHILONZOR", GeoCatalog.nearestStation(stations, 41.2755, 69.2060)?.id)
        // Samarqand — Toshkentdagi hech bir bekat mo'ljal emas.
        assertNull(GeoCatalog.nearestStation(stations, 39.6542, 66.9597))
        // Ro'yxat bo'sh (metro yo'q shahar / ro'yxat hali yuklanmagan) — xato emas.
        assertNull(GeoCatalog.nearestStation(emptyList(), 41.2755, 69.2060))
    }

    @Test
    fun `narx uch xonali guruhlarga ajratiladi`() {
        assertEquals("55 000", 55_000L.formatSum())
        assertEquals("999", 999L.formatSum())
        assertEquals("1 250 000", 1_250_000L.formatSum())
    }
}
